package com.happyfarm.frontend.pages

import com.happyfarm.frontend.*
import com.happyfarm.frontend.Globals.{ logout, routeVar, userNameVar }
import com.happyfarm.frontend.Route.{ ChatRoomsOverview, Profile }
import com.happyfarm.frontend.assets.HtmlGadgets.{ backButton, errorView, loadingView }
import com.happyfarm.frontend.assets.{ Css, Html, HtmlGadgets }
import com.happyfarm.frontend.pages.FriendsPage.State.*
import com.happyfarm.frontend.pages.ProfilePage.ProfileUseCase
import com.happyfarm.frontend.pages.ProfilePage.ProfileUseCase.SearchFriend
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import io.laminext.websocket.WebSocket
import org.scalajs.dom.HTMLDivElement
import shared.*
import shared.model.{ Friendship, FriendshipStatus, UserInfo }

class FriendsPage(chatWS: WebSocket[ChatResponse, ChatRequest]) extends Page:
  private val loadingVar: Var[FriendsPage.State] = Var(FriendsLoading)
  private val errorVar: Var[String]              = Var("")

  private val searchVar = Var("")

  private def friendsList(friendships: Seq[Friendship]): ReactiveHtmlElement[HTMLDivElement] =
    val (pending, accepted) = friendships.partition(_.status == FriendshipStatus.pending)

    div(
      cls := Css.FriendsPage.friendsListWrapper,
      if friendships.isEmpty then
        div(
          cls := Css.FriendsPage.friendsListNoFriendsStyle,
          Html.FriendsPage.noFriends
        )
      else
        div(
          if pending.nonEmpty then
            div(
              div(
                cls := Css.FriendsPage.friendsListPendingListWrapper,
                Html.FriendsPage.pendingListTitle
              ),
              pending.map(renderFriendItem)
            )
          else emptyNode,
          div(
            div(
              cls := Css.FriendsPage.friendsListExistingListWrapper,
              Html.FriendsPage.existingListTitle
            ),
            accepted.map(renderFriendItem)
          )
        )
    )

  private def renderFriendItem(friendship: Friendship): ReactiveHtmlElement[HTMLDivElement] =
    val user = friendship.userInfo
    div(
      cls := Css.FriendsPage.friendItemContainer,
      // Navigates to profile on click
      onClick --> (_ => routeVar.set(Profile(ProfileUseCase.ExistingFriend(user)))),
      div(HtmlGadgets.avatarNode(title = user.userName, sizePx = 50)),
      div(
        cls := Css.FriendsPage.friendInfoOuter,
        div(cls := Css.FriendsPage.friendName, user.userName),
        div(cls := Css.FriendsPage.friendJoinedDate, s"Joined: ${user.joined.split("T")(0)}")
      ),
      div(
        cls := Css.FriendsPage.friendAcceptButtonWrapper,
        friendship.status match
          case FriendshipStatus.accepted => emptyNode
          case FriendshipStatus.pending =>
            button(
              cls := Css.FriendsPage.friendAcceptButtonStyle,
              // Use stopPropagation to avoid triggering the navigation to profile page
              onClick.stopImmediatePropagation --> { _ =>
                chatWS.sendOne(AcceptFriend(user.userName))
              },
              "Accept"
            )
      )
    )

  override def run: ReactiveHtmlElement[HTMLDivElement] =
    div(
      cls := Css.containerView,
      chatWS.errors --> { error =>
        chatWS.reconnectNow()
      },
      onMountCallback { _ => chatWS.sendOne(FetchFriends()) },
      chatWS.received --> {
        case FriendList(friends) =>
          loadingVar.set(FriendsLoaded(friends))

        case BroadcastAddFriendRequest(from) =>
          loadingVar.update {
            case FriendsLoaded(current) =>
              val newFriendship = Friendship(from, FriendshipStatus.pending)
              FriendsLoaded(current :+ newFriendship)
            case other => other
          }

        case BroadcastFriendAccepted(newFriend) =>
          loadingVar.update {
            case FriendsLoaded(current) =>
              // Remove pending one from the list
              val updated       = current.filter(_.userInfo.userName != newFriend.userName)
              val newFriendship = Friendship(newFriend, FriendshipStatus.accepted)
              FriendsLoaded(updated :+ newFriendship)
            case other => other
          }

        case ReceiveError(reason, isFatal, _) =>
          if isFatal then logout(Some(reason))
          else
            errorVar.set(reason)
            loadingVar.set(PageError)
        case _ => ()
      },
      div(
        cls := Css.header,
        backButton.amend(onClick --> (_ => routeVar.set(ChatRoomsOverview()))),
        div(cls := Css.title, Html.FriendsPage.title)
      ),

      // Search Bar Section
      div(
        cls := Css.FriendsPage.searchBarWrapper,
        div(
          cls := Css.FriendsPage.searchBarPosition,
          input(
            cls         := Css.FriendsPage.searchBarInputBoxStyle,
            placeholder := Html.FriendsPage.search,
            controlled(
              value <-- searchVar,
              onInput.mapToValue --> searchVar
            ),
            onKeyDown.filter(_.key == "Enter") --> { _ =>
              val name = searchVar.now().trim

              if name.nonEmpty then
                val maybeExistingFriend = loadingVar.now() match
                  case FriendsLoaded(friends) => friends.find(_.userInfo.userName == name)
                  case _                      => None

                maybeExistingFriend match
                  case Some(friendship) =>
                    routeVar.set(Profile(ProfileUseCase.ExistingFriend(friendship.userInfo)))
                  case None =>
                    routeVar.set(Profile(ProfileUseCase.SearchFriend(name)))
                    chatWS.sendOne(FetchUserInfo(name))
            }
          )
        )
      ),

      // Main Content
      div(
        cls := Css.FriendsPage.scrollableArea,
        child <-- loadingVar.signal.map {
          case FriendsLoading   => loadingView(Html.FriendsPage.loading)
          case PageError        => errorView(errorVar.now())
          case FriendsLoaded(f) => friendsList(f)
        }
      )
    )

object FriendsPage:
  def apply(chatWS: WebSocket[ChatResponse, ChatRequest]): FriendsPage =
    new FriendsPage(chatWS)

  enum State:
    case FriendsLoading
    case PageError
    case FriendsLoaded(friends: Seq[Friendship])
