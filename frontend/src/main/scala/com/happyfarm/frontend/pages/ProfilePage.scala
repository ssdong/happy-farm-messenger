package com.happyfarm.frontend.pages

import com.happyfarm.frontend.Globals.{ joinedDateVar, logout, routeVar, userNameVar }
import com.happyfarm.frontend.Route.{ Chat, ChatRoomsOverview, Friends }
import com.happyfarm.frontend.assets.HtmlGadgets.{ errorView, loadingView }
import com.happyfarm.frontend.assets.{ Css, Html, HtmlGadgets }
import com.happyfarm.frontend.pages.Page.Ignore
import com.happyfarm.frontend.pages.ProfilePage.{ ProfileUseCase, State, addIcon, chatIcon, signOutIcon }
import com.happyfarm.frontend.pages.ProfilePage.State.{ InfoLoaded, InfoLoading, PageError }
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import io.laminext.websocket.WebSocket
import org.scalajs.dom.HTMLDivElement
import shared.*
import shared.model.UserInfo

class ProfilePage(chatWS: WebSocket[ChatResponse, ChatRequest], useCase: ProfileUseCase) extends Page:
  private val errorVar: Var[String]        = Var("")
  private val requestSentVar: Var[Boolean] = Var(false)

  private val activeUserVar: Var[Option[UserInfo]] = Var(
    useCase match
      case ProfileUseCase.ExistingFriend(info) => Some(info)
      case ProfileUseCase.Self(maybeInfo)      => maybeInfo
      case _                                   => None
  )

  private val stateVar: Var[State] = Var(
    useCase match
      case ProfileUseCase.ExistingFriend(_)            => InfoLoaded
      case ProfileUseCase.Self(info) if info.isDefined => InfoLoaded
      case _                                           => InfoLoading
  )

  private def actionButton(icon: String, altStr: String, action: () => Unit) =
    button(
      cls := Css.ProfilePage.profileButtonStyle,
      onClick --> { _ => action() },
      img(src := icon, alt := altStr, cls := Css.ProfilePage.profileButtonIconStyle)
    )

  private def renderProfileCard(user: UserInfo) =
    div(
      cls := Css.ProfilePage.profileInfoContainer,
      HtmlGadgets.avatarNode(user.userName),
      div(
        cls := Css.ProfilePage.profileInfoWrapper,
        div(cls := Css.ProfilePage.profileInfoNameStyle, user.userName),
        div(cls := Css.ProfilePage.profileInfoJoinedDateStyle, s"Member since ${user.joined.split("T")(0)}")
      ),
      div(
        cls := Css.ProfilePage.profileButtonWrapper,
        child <-- requestSentVar.signal.map {
          case true =>
            span(
              cls := Css.ProfilePage.profileFriendshipRequestedStyle,
              Html.ProfilePage.friendShipRequested
            )
          case false =>
            useCase match
              case ProfileUseCase.Self(_) =>
                actionButton(signOutIcon, "Sign Out", () => logout())

              case ProfileUseCase.ExistingFriend(friend) =>
                actionButton(
                  chatIcon,
                  "Chat",
                  () =>
                    // TODO: For better UX, maybe we should navigate chat page right away
                    //       and create a chat room once user has sent first message
                    chatWS.sendOne(StartChat(friend.userName))
                )

              case ProfileUseCase.SearchFriend(_) =>
                actionButton(
                  addIcon,
                  "Add Friend",
                  () =>
                    chatWS.sendOne(AddFriend(user.userName))
                    requestSentVar.set(true)
                )
        }
      )
    )

  override def run: ReactiveHtmlElement[HTMLDivElement] =
    div(
      cls := Css.containerView,
      chatWS.errors --> { error =>
        chatWS.reconnectNow()
      },
      onMountCallback { _ =>
        useCase match
          case ProfileUseCase.Self(None)         => chatWS.sendOne(FetchSelfInfo())
          case ProfileUseCase.SearchFriend(name) => chatWS.sendOne(FetchUserInfo(name))
          case _                                 => ()
      },
      div(
        cls := Css.header,
        HtmlGadgets.backButton.amend(
          onClick --> (_ =>
            routeVar.set(
              useCase match
                case _: ProfileUseCase.Self => ChatRoomsOverview()
                case _                      => Friends
            )
          )
        ),
        div(
          cls := Css.title,
          useCase match
            case _: ProfileUseCase.Self                => "My Profile"
            case ProfileUseCase.ExistingFriend(friend) => s"${friend.userName}'s Profile"
            case ProfileUseCase.SearchFriend(name)     => s"Search '$name'"
        )
      ),
      div(
        cls := Css.ProfilePage.profileWrapper,
        child <-- stateVar.signal.map {
          case InfoLoading               => loadingView(Html.ProfilePage.loading)
          case PageError(needRefreshing) => errorView(errorVar.now(), needRefreshing)
          case InfoLoaded =>
            val user = activeUserVar.now().getOrElse(UserInfo("Unknown", ""))
            renderProfileCard(user)
        }
      ),
      chatWS.received --> {
        case ReceiveSelfInfo(userInfo) =>
          userNameVar.set(Some(userInfo.userName))
          joinedDateVar.set(Some(userInfo.joined))
          activeUserVar.set(Some(userInfo))
          stateVar.set(InfoLoaded)

        case ReceiveUserInfo(maybeUserInfo) =>
          maybeUserInfo match
            case Some(userInfo) =>
              activeUserVar.set(Some(userInfo))
              stateVar.set(InfoLoaded)
            case None =>
              errorVar.set(s"User '${useCase match
                  case ProfileUseCase.SearchFriend(name) => name
                  case _                                 => "Unknown"
                }' could not be found.")
              stateVar.set(PageError(needRefreshing = false))

        case ReceiveChatStarted(roomId, roomTitle, maybeLatestMessage) =>
          routeVar.set(Chat(roomId.toUUID, roomTitle, maybeLatestMessage))

        case ReceiveError(reason, isFatal, origin) =>
          origin match
            case Some(RequestOrigin.ProfilePage) | None =>
              if isFatal then logout(Some(reason))
              else
                errorVar.set(reason)
                stateVar.set(PageError(needRefreshing = true))
            case _ => Ignore

        case _ => Ignore
      }
    )

object ProfilePage:
  def self(chatWS: WebSocket[ChatResponse, ChatRequest], maybeSelfInfo: Option[UserInfo]) =
    new ProfilePage(chatWS, ProfileUseCase.Self(maybeSelfInfo))

  def friend(chatWS: WebSocket[ChatResponse, ChatRequest], info: UserInfo) =
    new ProfilePage(chatWS, ProfileUseCase.ExistingFriend(info))

  def search(chatWS: WebSocket[ChatResponse, ChatRequest], name: String) =
    new ProfilePage(chatWS, ProfileUseCase.SearchFriend(name))

  private val signOutIcon = "public/assets/sign_out.png"
  private val chatIcon    = "public/assets/chat.png"
  private val addIcon     = "public/assets/add.png"

  enum ProfileUseCase:
    case Self(maybeInfo: Option[UserInfo])
    case ExistingFriend(info: UserInfo)
    case SearchFriend(name: String)

  enum State:
    case PageError(needRefreshing: Boolean)
    case InfoLoading
    case InfoLoaded
