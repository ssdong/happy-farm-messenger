package com.happyfarm.frontend.pages

import com.happyfarm.frontend.Globals.{ logout, routeVar }
import com.happyfarm.frontend.pages.ChatPage.State.{ MessagesLoaded, MessagesLoading, PageError }
import com.happyfarm.frontend.pages.ChatPage.*
import ChatRoomsOverviewPage.DraftMessage
import com.happyfarm.frontend.assets.HtmlGadgets.{ errorView, loadingView }
import Page.Ignore
import com.happyfarm.frontend.Route.ChatRoomsOverview as ChatRoomsOverviewRoute
import com.happyfarm.frontend.utils.Utils.fetchDateAndTime
import com.happyfarm.frontend.*
import com.happyfarm.frontend.assets.{ Css, Html, HtmlGadgets }
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import io.laminext.websocket.WebSocket
import org.scalajs.dom.HTMLDivElement
import shared.model.Message
import shared.*

import java.util.UUID
import scala.scalajs.js.Date

class ChatPage(
    chatWS: WebSocket[ChatResponse, ChatRequest],
    roomId: UUID,
    roomTitle: String,
    maybeLatestMessage: Option[Message],
    maybeDraftMessage: Option[String]
) extends Page:
  private val loadingVar: Var[State] = Var[State](MessagesLoading)

  private val errorVar: Var[String] = Var[String]("")

  private val selectedFailedMessageVar: Var[Option[UIMessage]] = Var(None)

  private val isFetchingMoreHistoricalMessagesVar: Var[Boolean] = Var(false)

  private val unreadCountVar = Var(0)

  private val isPeerTypingVar = Var(false)

  private var haveWeFetchedTheInitialBatchOfMessages = false

  private val messageVar = Var(maybeDraftMessage.getOrElse(""))

  private val fetchHistoricalMessagesBatchSize = 10

  private val waitingForLoadingMessages = loadingView(Html.ChatPage.loading)

  private val forceScrollBus = new EventBus[Unit]

  private var messagesContainerEl: Option[org.scalajs.dom.html.Div] = None

  private def pageErrorView =
    errorView(errorVar.now())

  private def isNearBottom(el: org.scalajs.dom.html.Div): Boolean =
    val threshold = 100
    el.scrollHeight - el.scrollTop - el.clientHeight < threshold

  private def isNearTop(el: org.scalajs.dom.html.Div): Boolean =
    val threshold = 30
    // Trigger with 30px from top to be a bit more sensitive
    el.scrollTop <= threshold

  // We're injecting 'date' text, e.g. 2026-05-01, before the first message in the UI if
  // a group of messages belongs to the same day
  private def injectDateSeparators(messages: Seq[UIMessage]): Seq[UIMessage] =
    messages.zipWithIndex.map { case (message, index) =>
      val previousMessage = if index > 0 then Some(messages(index - 1)) else None

      // Only attempt to calculate a date string if the message has a timestamp
      val dateStr = message.maybeCreatedAt.flatMap(_ => fetchDateAndTime(message).map(_._1))

      val prevDateStr = previousMessage.flatMap { pm =>
        pm.maybeCreatedAt.flatMap(_ => fetchDateAndTime(pm).map(_._1))
      }

      // A separator is only needed if:
      // 1. This message has a valid date (dateStr.isDefined)
      // 2. AND (It's the first message OR the date is different from the previous one)

      // This prevents we render date separator for "typing" dummy UI message and pending messages
      val shouldShowSeparator = dateStr.isDefined && (previousMessage.isEmpty || prevDateStr != dateStr)

      if shouldShowSeparator then message.copy(maybeDateSeparator = dateStr)
      else message
    }

  private def renderMessageWithStatus(msg: UIMessage): ReactiveHtmlElement[HTMLDivElement] =
    if msg.id == IS_TYPING_SIGNAL then
      div(
        cls := Css.ChatPage.messageBoxPeerPosition,
        div(
          cls := Css.ChatPage.messageBoxShapeStyle + " " + Css.ChatPage.messageBoxPeerColorStyle,
          div(
            cls := Css.ChatPage.messageBoxPeerTypingSignalStyle,
            div(cls := Css.ChatPage.messageBoxPeerTypingSignalFirstDotStyle),
            div(cls := Css.ChatPage.messageBoxPeerTypingSignalSecondDotStyle),
            div(cls := Css.ChatPage.messageBoxPeerTypingSignalThirdDotStyle)
          )
        )
      )
    else
      div(
        onClick --> { _ =>
          if msg.self && msg.failedToSent then selectedFailedMessageVar.set(Some(msg))
        },
        cls := (if msg.self then Css.ChatPage.messageBoxSelfPosition
                else Css.ChatPage.messageBoxPeerPosition),
        div(
          cls := Css.ChatPage.messageBoxWrapperStyle,

          // Spinner / failure indicator
          if msg.isPending && msg.self then
            if msg.failedToSent then
              div(
                cls := Css.ChatPage.messageBoxFailedToSentExclamationMark,
                "!"
              )
            else div(cls := Css.ChatPage.messageBoxMessageStillSendingSpinner)
          else emptyNode,
          // Message Box
          div(
            cls := Css.ChatPage.messageBoxShapeStyle,
            cls := (
              if msg.self then Css.ChatPage.messageBoxSelfColorStyle
              else Css.ChatPage.messageBoxPeerColorStyle
            ),
            child.maybe <-- Val(
              Option.when(!msg.self)(
                div(cls := Css.ChatPage.messageBoxUserNameDisplay, msg.maybeUserName.getOrElse("N/A"))
              )
            ),
            div(cls := Css.ChatPage.messageBoxContentDisplay, msg.maybeText),
            div(
              cls := Css.ChatPage.messageBoxTimestampDisplay,
              if msg.isPending then
                "\u200b" // A white "placeholder" so the message box appears to be the same size as if the timestamp is present
              else fetchDateAndTime(msg).map(_._2).getOrElse("")
            )
          )
        )
      )

  private def messagesList: ReactiveHtmlElement[HTMLDivElement] =
    div(
      cls := Css.ChatPage.scrollableHistory,
      onMountCallback { ctx =>
        messagesContainerEl = Some(ctx.thisNode.ref)
      },
      onScroll --> { ev =>
        val el = ev.target.asInstanceOf[org.scalajs.dom.html.Div]
        if isNearTop(el) && !isFetchingMoreHistoricalMessagesVar.now() then
          loadingVar.now() match
            case MessagesLoaded(messages, _) =>
              messages.headOption.foreach { oldestMsg =>
                // Only fetch if we aren't at the very beginning (seq > 1)
                if oldestMsg.seq > 1 then
                  isFetchingMoreHistoricalMessagesVar.set(true)
                  chatWS.sendOne(
                    FetchMessages(RoomId(roomId), oldestMsg.seq - 1, fetchHistoricalMessagesBatchSize)
                  )
              }
            case _ => Ignore

        if isNearBottom(el) && unreadCountVar.now() > 0 then
          // Use the auto-scroll to clear the unread count var and to reuse
          // the sending "CatchUpLastReadSeq". Otherwise, we'll have to call
          // sending "CatchUpLastReadSeq" here again because we have removed
          // sending "CatchUpLastReadSeq" whenever we receive a "BroadCastMessage".

          // The catching-up is taken care of whenever user scrolls to the bottom,
          // either voluntarily or by clicking "unread" button. It'll be a good
          // spot to claim user has caught up reading the latest message.
          forceScrollBus.writer.onNext(())
      },
      child <-- loadingVar.signal.map {
        case MessagesLoading => waitingForLoadingMessages
        case PageError       => pageErrorView
        case _               => emptyNode
      },
      child.maybe <-- isFetchingMoreHistoricalMessagesVar.signal.map { fetching =>
        if fetching then Some(HtmlGadgets.loadingDots) else None
      },
      children <-- loadingVar.signal
        .combineWith(isPeerTypingVar)
        .map {
          case (PageError, _)       => Seq.empty
          case (MessagesLoading, _) => Seq.empty
          case (MessagesLoaded(messages, pending), isPeerTyping) =>
            val serverMessages = messages.map(_.toUIMessage)
            // "Dummy" UI Message for rendering typing floating dots
            val maybeTypingUI =
              if isPeerTyping then
                Seq(
                  UIMessage(
                    id = ChatPage.IS_TYPING_SIGNAL,
                    self = false,
                    maybeUserName = None,
                    maybeText = None,
                    maybeCreatedAt = None
                  )
                )
              else Seq.empty

            val pendingMessages = pending.map(_.toUIMessage)
            val processed       = injectDateSeparators(serverMessages ++ maybeTypingUI ++ pendingMessages)

            processed.map(m => m.id -> m)
        }
        .split(_._1) { (_, _, messageSignal) =>
          // Laminar split allows re-rendering if existing item values change or just renders
          // new items without replacing the whole list
          div(
            // Render the date separator if it exists
            child.maybe <-- messageSignal.map(_._2.maybeDateSeparator.map { date =>
              div(
                cls := Css.ChatPage.messageListDateSeparatorPosition,
                span(cls := Css.ChatPage.messageListDateSeparatorStyle, date)
              )
            }),
            // Render the actual message
            child <-- messageSignal.map(e => renderMessageWithStatus(e._2))
          )
        },

      // This will be triggered ONLY once to focus on the newest message when user navigates to the chat page
      inContext { thisNode =>
        loadingVar.signal.changes --> {
          case MessagesLoaded(_, _) if !haveWeFetchedTheInitialBatchOfMessages =>
            // Use a small delay to let Laminar finish the DOM injection for the
            // initial batch of messages
            scala.scalajs.js.timers.setTimeout(0) {
              haveWeFetchedTheInitialBatchOfMessages = true

              // TODO: Needs to scroll to last unseen messages or to the bottom
              //       if all messages have been seen or put a marker at the place where it's lsat seen
              thisNode.ref.scrollTop = thisNode.ref.scrollHeight
            }
          case _ => ()
        }
      },

      // Auto-Scroll to latest message by receiving a scrolling signal, whether
      // it's pending message or received broadcast message
      inContext { thisNode =>
        // Force scroll handler
        forceScrollBus.events --> { _ =>
          scala.scalajs.js.timers.setTimeout(0) {
            val el = thisNode.ref
            el.scrollTop = el.scrollHeight

            if unreadCountVar.now() > 0 then
              unreadCountVar.set(0)

              // If user is at the top of screenshot navigating historical messages while new messages
              // comes, and if they don't click on the "X unread messages" button to navigate to the
              // button but clicked "back" button to the overview page, it shouldn't be count as "Read"

              // Therefore, we have removed sending "CatchUpLastReadSeq" when user receives a "BroadCastMessage"
              // and only catch up when user scrolls to the bottom volunteerism or being scrolled to the bottom

              chatWS.sendOne(
                CatchUpLastReadSeq(
                  roomId = RoomId(roomId)
                )
              )
          }
        }
      }
    )

  private def resendMessage(msg: UIMessage): Unit =
    loadingVar.update {
      case MessagesLoaded(existingMessages, pendingMessages) =>
        val updatedPending = pendingMessages.map { pendingMessage =>
          if pendingMessage.tempId == msg.id then pendingMessage.copy(failed = false)
          else pendingMessage
        }
        MessagesLoaded(existingMessages, updatedPending)
      case other => other
    }

    chatWS.sendOne(
      SendTextMessage(
        roomId = RoomId(roomId),
        text = msg.maybeText.getOrElse(""),
        tempId = msg.id
      )
    )

    // If user navigate to overview page they won't have updated last read seq if the last thing
    // they did was resending failed message
    chatWS.sendOne(
      CatchUpLastReadSeq(
        roomId = RoomId(roomId)
      )
    )

  override def run: ReactiveHtmlElement[HTMLDivElement] =
    div(
      cls := Css.containerView,
      styleTag(Css.hideScrollbarStyles),
      child.maybe <-- selectedFailedMessageVar.signal.map {
        case Some(failedMsg) =>
          Some(
            div(
              cls := Css.ChatPage.resendBoxWrapper,
              // If user clicks on other area, it should close the resend box too
              onClick --> { _ => selectedFailedMessageVar.set(None) },
              div(
                cls := Css.ChatPage.resendBoxContainer,
                // Prevent closing when clicking buttons due to propagation from the above
                onClick.stopImmediatePropagation --> { _ => () },
                button(
                  cls := Css.ChatPage.resendBoxResendButton,
                  Html.ChatPage.resendButton,
                  onClick --> { _ =>
                    resendMessage(failedMsg)
                    selectedFailedMessageVar.set(None)
                  }
                ),
                button(
                  cls := Css.ChatPage.resendBoxCancelButton,
                  Html.ChatPage.cancelButton,
                  onClick --> { _ => selectedFailedMessageVar.set(None) }
                )
              )
            )
          )
        case None => None
      },

      // Fetch initial batch of messages
      onMountCallback { _ =>
        maybeLatestMessage match
          case Some(latestMessage) =>
            chatWS.sendOne(
              FetchMessages(
                roomId = RoomId(roomId),
                offset = latestMessage.seq,
                size = fetchHistoricalMessagesBatchSize
              )
            )

            // Shouldn't be a big deal if we claim catching up latest messages even though the fetching
            // hasn't come back yet.
            chatWS.sendOne(
              CatchUpLastReadSeq(
                roomId = RoomId(roomId)
              )
            )

          case None =>
            // No historical messages need to be loaded
            loadingVar.set(MessagesLoaded(Seq.empty, Seq.empty))
      },

      // Trigger scroll whenever peer starts typing
      // I haven't really figured out why it doesn't do the scrolling to
      // the bottom to render the full floating dots box, currently it's just
      // showing half of it. This should be the same case whenever we
      // receive a new message from BroadCast, except for this time it's a
      // synthetic dummy UI message injected and rendered from user typing signal.
      isPeerTypingVar.signal.changes.filter(identity) --> { _ =>
        // Use a slightly longer delay (10-50ms) to ensure Laminar has
        // finished the DOM injection of UI message.
        scala.scalajs.js.timers.setTimeout(50) {
          forceScrollBus.writer.onNext(())
        }
      },

      // Update state when data comes
      chatWS.received --> { response =>
        response match
          case HistoryMessages(newMessages) =>
            loadingVar.update {
              case MessagesLoading => MessagesLoaded(newMessages, Seq.empty)
              case MessagesLoaded(existingMessages, pendingMessages) =>
                isFetchingMoreHistoricalMessagesVar.set(false)
                MessagesLoaded(newMessages ++ existingMessages, pendingMessages)
              case PageError => PageError
            }

          case MessagePersisted(maybeMessage, tempId) =>
            loadingVar.update {
              case MessagesLoaded(existingMessages, pendingMessages) =>
                maybeMessage match
                  case Some(message) =>
                    // Add message to existing messages and remove pending
                    MessagesLoaded(
                      (existingMessages :+ message)
                        .distinctBy(_.seq)
                        .sortBy(
                          _.seq
                        ), // In case BE sends multiple 'MessagePersisted' signal for the same message
                      pendingMessages.filterNot(_.tempId == tempId)
                    )
                  case None =>
                    // Mark pending as failed to show red exclamation
                    val updatedPending = pendingMessages.map { pendingMessage =>
                      if pendingMessage.tempId == tempId then pendingMessage.copy(failed = true)
                      else pendingMessage
                    }

                    MessagesLoaded(existingMessages, updatedPending)

              case other => other
            }
          case BroadCastMessage(message) =>
            // ONLY add if it's in the same room and NOT from current user since messages
            // from current user has already been taken care of through pending and MessagePersisted signal
            if message.roomId == roomId && !message.isMe then
              loadingVar.update {
                case MessagesLoaded(existingMessages, pendingMessages) =>
                  val updatedMessages = (existingMessages :+ message)
                    .distinctBy(_.seq)
                    .sortBy(_.seq) // In case BE broadcasts the same message multiple times
                  MessagesLoaded(updatedMessages, pendingMessages)
                case other => other
              }

              messagesContainerEl match
                case Some(el) =>
                  if isNearBottom(el) then forceScrollBus.writer.onNext(())
                  else unreadCountVar.update(_ + 1)
                case None => ()

          case ReceiveUserTyping(fromRoomId, isMe) =>
            if fromRoomId == roomId && !isMe then isPeerTypingVar.set(true)

          case ReceiveUserStoppedTyping(fromRoomId, isMe) =>
            if fromRoomId == roomId && !isMe then isPeerTypingVar.set(false)

          case ReceiveError(reason, isFatal, maybeOrigin) =>
            // Only handle if origin is Page or empty (global)
            maybeOrigin match
              case Some(RequestOrigin.ChatPage) | None =>
                if isFatal then logout(Some(reason))
                else
                  errorVar.set(reason)
                  loadingVar.set(PageError)
              case _ => Ignore

          case _ => Ignore
      },
      chatWS.errors --> { error =>
        chatWS.reconnectNow()
      },
      div(
        cls := Css.header,
        HtmlGadgets.backButton.amend(
          onClick --> { _ =>
            val currentText = messageVar.now()
            val maybeDraft =
              if currentText.nonEmpty then Some(DraftMessage(RoomId(roomId), currentText)) else None
            routeVar.set(ChatRoomsOverviewRoute(maybeDraft))
          }
        ),
        div(
          cls := Css.title,
          roomTitle
        ),
        child.maybe <-- unreadCountVar.signal.map { count =>
          Option.when(count > 0)(
            div(
              cls := Css.ChatPage.messageListUnreadMessageNotificationStyle,
              s"$count unread messages",
              onClick --> { _ =>
                forceScrollBus.writer.onNext(())
              }
            )
          )
        }
      ),

      // Scrollable Chat History
      messagesList,

      // Message Input Section
      div(
        cls := Css.ChatPage.inputContainerStyle,
        div(
          cls := Css.ChatPage.inputContainerAlignmentStyle,
          textArea(
            cls         := Css.ChatPage.textInputStyle,
            placeholder := Html.ChatPage.typeMessagePlaceHolder,
            onInput.mapToValue --> { content =>
              messageVar.set(content)
              // NOTICE: We should probably only support UserIsTyping signal if it's a DM chat otherwise we'll see too
              // many "typing" floating symbols from different users if it's a group chat.
              chatWS.sendOne(
                UserIsTyping(
                  roomId = RoomId(roomId)
                )
              )
            },
            onBlur --> { _ =>
              chatWS.sendOne(
                UserStoppedTyping(
                  roomId = RoomId(roomId)
                )
              )
            },
            onUnmountCallback { _ =>
              chatWS.sendOne(
                UserStoppedTyping(
                  roomId = RoomId(roomId)
                )
              )
            },
            value <-- messageVar
          ),
          div(
            cls := Css.ChatPage.sendButtonStyle,
            HtmlGadgets.sendButton,
            onClick --> { _ =>
              val text = messageVar.now()
              if text.nonEmpty then
                val tempId     = s"$IS_PENDING_PREFIX-${Date.now()}"
                val newPending = PendingMessage(tempId, text)

                messageVar.set("")
                ChatRoomsOverviewPage.clearDraft(RoomId(roomId))

                loadingVar.update {
                  case MessagesLoaded(messages, pending) => MessagesLoaded(messages, pending :+ newPending)
                  case other                             => other
                }

                chatWS.sendOne(
                  SendTextMessage(
                    roomId = RoomId(roomId),
                    text = text,
                    tempId = tempId
                  )
                )

                // This deals with an edge case where if there's a network glitch
                // and the SPA pushes user back to chat overview page(right now the
                // laminar SPA reconnects and evaluates all credentials and direct user
                // on chat overview page by default, if there's a glitch), the user
                // will have an unread message, but it's from their own message because
                // when they land on the chat overview page and their own pending message
                // is just being persisted and increments the seq

                // The BE runs on an Actor model for each room and since WebSocket connection
                // guarantees that messages sent over a single connection are delivered in the
                // exact order they were sent. The 'CatchUpLastReadSeq' will 100% sees the previous
                // sent message by user and ensure a proper catch up.
                chatWS.sendOne(
                  CatchUpLastReadSeq(
                    roomId = RoomId(roomId)
                  )
                )

                // Force user to scroll to bottom if they try to send a message
                forceScrollBus.writer.onNext(())
            }
          )
        ),
        div(cls := Css.ChatPage.footerPadding)
      )
    )

object ChatPage:
  def apply(
      chatWS: WebSocket[ChatResponse, ChatRequest],
      roomId: UUID,
      roomTitle: String,
      maybeLatestMessage: Option[Message],
      maybeDraftMessage: Option[String] = None
  ) =
    new ChatPage(chatWS, roomId, roomTitle, maybeLatestMessage, maybeDraftMessage)

  private val IS_PENDING_PREFIX = "pending-"

  private val IS_TYPING_SIGNAL = "TYPING"

  /** UI domain message for rendering purpose
    */
  case class UIMessage(
      id: String,
      self: Boolean,
      maybeUserName: Option[String],
      maybeText: Option[String],
      maybeCreatedAt: Option[String], // yyyy-MM-ddTHH:mm:ssZ ISO-8601 compliant string
      maybeDateSeparator: Option[String] = None,
      isPending: Boolean = false,
      failedToSent: Boolean = false
  )

  extension (message: Message)
    def toUIMessage: UIMessage =
      UIMessage(
        id = message.seq.toString,
        self = message.isMe,
        maybeUserName = message.userName,
        maybeText = message.text,
        maybeCreatedAt = Some(message.createdAt)
      )

  extension (pending: PendingMessage)
    def toUIMessage: UIMessage =
      UIMessage(
        id = pending.tempId,
        self = true,
        maybeUserName = None,
        maybeText = Some(pending.text),
        maybeCreatedAt = None,
        isPending = true,
        failedToSent = pending.failed
      )

  case class PendingMessage(tempId: String, text: String, failed: Boolean = false)

  enum State:
    case PageError
    case MessagesLoading
    case MessagesLoaded(
        messages: Seq[Message],
        pendingMessage: Seq[PendingMessage]
    )
