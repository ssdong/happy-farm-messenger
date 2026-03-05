package com.happyfarm.frontend.pages

import com.happyfarm.frontend.*
import com.happyfarm.frontend.Globals.{ getSelfInfo, logout, routeVar }
import com.happyfarm.frontend.Route.{ Chat, Friends, Profile }
import com.happyfarm.frontend.assets.HtmlGadgets.{ errorView, loadingView }
import com.happyfarm.frontend.assets.{ Css, Html, HtmlGadgets }
import com.happyfarm.frontend.pages.ChatRoomsOverviewPage.*
import com.happyfarm.frontend.pages.ChatRoomsOverviewPage.State.{
  ChatRoomsLoaded,
  ChatRoomsLoading,
  PageError
}
import com.happyfarm.frontend.pages.Page.Ignore
import com.happyfarm.frontend.pages.ProfilePage.ProfileUseCase
import com.happyfarm.frontend.utils.Utils.fetchDateAndTime
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import io.laminext.websocket.WebSocket
import org.scalajs.dom.HTMLDivElement
import shared.*
import shared.model.{ Message, MessageType }

import java.util.UUID

class ChatRoomsOverviewPage(
    chatWS: WebSocket[ChatResponse, ChatRequest]
) extends Page:
  private val loadingVar: Var[State] = Var[State](ChatRoomsLoading)

  private val errorVar: Var[String] = Var[String]("")

  private val waitingForLoadingChatsView = loadingView(Html.ChatRoomsOverviewPage.loading)

  private def pageErrorView =
    errorView(errorVar.now())

  private def onEnterChatRoomObserver =
    (
        roomId: UUID,
        roomTitle: String,
        maybeLatestMessage: Option[Message],
        maybeDraftMessage: Option[String]
    ) => routeVar.set(Chat(roomId, roomTitle, maybeLatestMessage, maybeDraftMessage))

  private def fetchDisplayedMessage(maybeMessage: Option[Message]): String =
    maybeMessage
      .map { message =>
        message.`type` match
          case MessageType.`text`  => message.text.getOrElse("")
          case MessageType.`image` => "[image]"
          case MessageType.`audio` => "[audio]"
      }
      .getOrElse("")

  private def ChatsList(rooms: Seq[UIChatRoomOverview]): ReactiveHtmlElement[HTMLDivElement] =
    div(
      // Ensure the container allows scrolling and has a base padding
      cls := Css.ChatRoomsOverviewPage.listScrollableArea,
      rooms.map { room =>
        val dateAndTime = fetchDateAndTime(room.maybeLatestMessage)
        val unreadCount = room.maybeLatestMessage.map(_.seq).map(_ - room.lastReadSeq).getOrElse(0L)
        val displayedMessage = room.maybeDraftMessage match
          case Some(draftMessage) =>
            div(
              cls := Css.ChatRoomsOverviewPage.roomSummaryMessagePreview,
              span(cls := Css.ChatRoomsOverviewPage.roomSummaryDraftMessageAlert, "[Draft] "),
              draftMessage
            )
          case None =>
            div(
              cls := Css.ChatRoomsOverviewPage.roomSummaryMessagePreview,
              fetchDisplayedMessage(room.maybeLatestMessage)
            )
        div(
          cls := Css.ChatRoomsOverviewPage.roomSummaryContainer,
          onClick --> { _ =>
            onEnterChatRoomObserver(room.id, room.title, room.maybeLatestMessage, room.maybeDraftMessage)
          },
          div(
            cls := Css.ChatRoomsOverviewPage.roomSummaryAvatar,
            HtmlGadgets.avatarNode(title = room.title)
          ),
          div(
            cls := Css.ChatRoomsOverviewPage.roomSummaryOuter,
            // Top Line: Title and Date
            div(
              cls := Css.ChatRoomsOverviewPage.roomSummaryInner,
              div(
                cls := Css.ChatRoomsOverviewPage.roomSummaryTitle,
                room.title
              ),
              div(
                cls := Css.ChatRoomsOverviewPage.roomSummaryDate,
                dateAndTime.map(_._1).getOrElse("") // Displaying just the Date
              )
            ),

            // Bottom Line: Message Preview + Unread Badge
            div(
              cls := Css.ChatRoomsOverviewPage.roomSummaryBottomRow,
              displayedMessage,
              // Render badge only if unreadCount > 0
              if unreadCount > 0 then
                div(
                  cls := Css.ChatRoomsOverviewPage.roomSummaryUnreadBadge,
                  if unreadCount > 99 then "99+" else unreadCount.toString
                )
              else emptyNode
            )
          )
        )
      },
      div(cls := Css.ChatRoomsOverviewPage.listScrollableAreaSpaceFiller),
      div(cls := Css.ChatRoomsOverviewPage.listScrollableAreaFooterPadding)
    )

  override def run: ReactiveHtmlElement[HTMLDivElement] =
    div(
      cls := Css.containerView,
      onMountCallback { _ => chatWS.sendOne(FetchRooms()) },
      chatWS.errors --> { error =>
        chatWS.reconnectNow()
      },

      // Update state when data is ready
      chatWS.received --> { response =>
        response match
          case RoomList(rooms) =>
            val drafts = ChatRoomsOverviewPage.draftMessagesVar.now()
            val uiMessages = rooms.map { room =>
              UIChatRoomOverview(
                id = room.id,
                title = room.title,
                lastReadSeq = room.lastReadSeq,
                maybeLatestMessage = room.maybeLatestMessage,
                maybeDraftMessage = drafts.get(RoomId(room.id))
              )
            }
            loadingVar.set(State.ChatRoomsLoaded(uiMessages))
          case BroadCastMessage(message) =>
            loadingVar.update {
              case ChatRoomsLoaded(rooms) =>
                val roomExists = rooms.exists(_.id == message.roomId)

                if roomExists then
                  val updatedRooms = rooms.map { room =>
                    if room.id == message.roomId then room.copy(maybeLatestMessage = Some(message))
                    else room
                  }
                  ChatRoomsLoaded(updatedRooms)
                else
                  val newRoom = UIChatRoomOverview(
                    id = message.roomId,
                    title = message.userName.getOrElse("New Chat"),
                    lastReadSeq = 0,
                    maybeLatestMessage = Some(message)
                  )
                  ChatRoomsLoaded(rooms :+ newRoom)

              case other => other
            }
          case ReceiveError(reason, isFatal, maybeOrigin) =>
            // Only handle if origin is Page or empty (global)
            maybeOrigin match
              case Some(RequestOrigin.OverviewPage) | None =>
                if isFatal then logout(Some(reason))
                else
                  errorVar.set(reason)
                  loadingVar.set(PageError)
              case _ => Ignore

          case _ => Ignore
      },
      div(cls := Css.ChatRoomsOverviewPage.header),

      // Chats List - Scrollable Area
      div(
        cls := Css.ChatRoomsOverviewPage.scrollableArea,
        child <-- loadingVar.signal.map {
          case PageError              => pageErrorView
          case ChatRoomsLoading       => waitingForLoadingChatsView // TODO: too much scrolling
          case ChatRoomsLoaded(rooms) => ChatsList(rooms)
        }
      ),

      // Bottom Navigation Section
      div(
        cls := Css.ChatRoomsOverviewPage.bottomNavLayout,
        button(
          cls := Css.ChatRoomsOverviewPage.navButton,
          img(
            src := profileIcon,
            alt := Html.ChatRoomsOverviewPage.profileAlt,
            cls := Css.ChatRoomsOverviewPage.navIconEffect
          ),
          onClick --> (_ => routeVar.set(Profile(ProfileUseCase.Self(getSelfInfo))))
        ),
        button(
          cls := Css.ChatRoomsOverviewPage.navButton,
          img(
            src := friendsIcon,
            alt := Html.ChatRoomsOverviewPage.addFriendAlt,
            cls := Css.ChatRoomsOverviewPage.navIconEffect
          ),
          onClick.mapTo(()) --> (_ => routeVar.set(Friends))
        )
      )
    )

object ChatRoomsOverviewPage:
  def apply(
      chatWS: WebSocket[ChatResponse, ChatRequest],
      maybeNewDraftMessage: Option[DraftMessage] = None
  ): ChatRoomsOverviewPage =
    maybeNewDraftMessage.foreach { newDraft =>
      draftMessagesVar.update(_ + (newDraft.roomId -> newDraft.text))
    }
    new ChatRoomsOverviewPage(chatWS)

  private val draftMessagesVar: Var[Map[RoomId, String]] = Var(Map.empty)

  def clearDraft(roomId: RoomId): Unit = draftMessagesVar.update(_ - roomId)

  private val friendsIcon = "public/assets/friends.png"

  private val profileIcon = "public/assets/profile.png"

  case class UIChatRoomOverview(
      id: UUID,
      title: String, // This will be the other person's name if it's a DM
      lastReadSeq: Long,
      maybeLatestMessage: Option[Message],
      maybeDraftMessage: Option[
        String
      ] = None // If user navigates back to chat overview page from chat page with unsent message
  )

  case class DraftMessage(
      roomId: RoomId,
      text: String
  )
  enum State:
    case PageError
    case ChatRoomsLoading
    case ChatRoomsLoaded(rooms: Seq[UIChatRoomOverview])
