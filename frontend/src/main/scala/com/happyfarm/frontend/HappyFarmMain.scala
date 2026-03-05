package com.happyfarm.frontend

import com.happyfarm.frontend.pages.ChatRoomsOverviewPage.DraftMessage
import com.happyfarm.frontend.Globals.{ chatWSVar, logout, routeVar, webStorageAccessTokenVar }
import com.happyfarm.frontend.Route.{ Loading, Login }
import com.happyfarm.frontend.api.Api
import com.happyfarm.frontend.assets.HtmlGadgets.loadingView
import com.happyfarm.frontend.assets.{ Css, Html }
import com.happyfarm.frontend.pages.ProfilePage.ProfileUseCase
import com.happyfarm.frontend.pages.{
  ChatPage,
  ChatRoomsOverviewPage,
  FriendsPage,
  LoginPage,
  ProfilePage,
  RegistrationPage
}
import com.raquo.airstream.web.WebStorageVar
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import io.laminext.websocket.WebSocket
import org.scalajs.dom
import org.scalajs.dom.HTMLDivElement
import shared.model.{ Message, UserInfo }
import shared.{ ChatRequest, ChatResponse }
import upickle.default.{ read, write }

import java.util.UUID
import scala.scalajs.js.timers.*
import scala.util.Success

sealed trait Route
object Route:
  case object Loading                                                                extends Route
  case object Register                                                               extends Route
  case object Friends                                                                extends Route
  final case class Profile(useCase: ProfileUseCase)                                  extends Route
  final case class ChatRoomsOverview(maybeDraftMessage: Option[DraftMessage] = None) extends Route
  final case class Login(maybeErrorMessage: Option[String] = None)                   extends Route
  final case class Chat(
      roomId: UUID,
      roomTitle: String,
      maybeLatestMessage: Option[Message] = None,
      maybeDraftMessage: Option[String] = None
  ) extends Route

object Globals:
  private val accessTokenName = "happyFarmAccessToken"
  private val userIdName      = "happyFarmUserId"
  private val empty           = ""
  val webStorageAccessTokenVar: WebStorageVar[String] =
    WebStorageVar.localStorage(accessTokenName, None).text(empty)
  val userNameVar: Var[Option[String]]   = Var(None)
  val joinedDateVar: Var[Option[String]] = Var(None)
  val routeVar: Var[Route]               = Var[Route](Loading)

  val chatWSVar: Var[Option[WebSocket[ChatResponse, ChatRequest]]] = Var(None)

  def getSelfInfo: Option[UserInfo] =
    for
      userName   <- userNameVar.now()
      joinedDate <- joinedDateVar.now()
    yield UserInfo(
      userName = userName,
      joined = joinedDate
    )

  def logout(maybeErrorMessage: Option[String] = None): Unit =
    webStorageAccessTokenVar.set(empty)
    userNameVar.set(None)
    joinedDateVar.set(None)
    chatWSVar.now().foreach(_.disconnectNow())
    chatWSVar.set(None)
    routeVar.set(Login(maybeErrorMessage))

object AppRoot:
  import Route.*

  private var hasInitializedWS = false

  def initWebSocket(token: String): Unit =
    if chatWSVar.now().isEmpty then
      // Using the current window's host to be safe
      val host = org.scalajs.dom.window.location.host
      val isSecure =
        org.scalajs.dom.window.location.protocol.startsWith("https") // Easier way for both local and prod
      val protocol = if isSecure then "wss" else "ws"
      val url      = s"$protocol://$host/chat?token=$token"
      val websocket = WebSocket
        .url(url)
        .receiveText((data: String) => Right(read[ChatResponse](data)))
        .sendText((request: ChatRequest) => write(request))
        // We want to manually manage websocket lifecycle so we can disconnect when user logs out. Otherwise,
        // it binds to the life cycle of wsContainer and won't disconnect unless user refreshes the page, which
        // destroys wsContainer
        .build(managed = false)

      chatWSVar.set(Some(websocket))

  private val waitingForAutoLoggingView = loadingView(Html.Main.loading)

  private val wsContainer = div()

  val view: ReactiveHtmlElement[HTMLDivElement] = div(
    wsContainer,
    chatWSVar.signal --> { maybeWS =>
      maybeWS match
        case Some(ws) =>
          ws.reconnectNow()

          // The challenge is we have to dynamically bind ws.connected(a Binder) to a HTML element.
          // The websocket instance is only available after user logs in or if existing
          // access token has been verified. This is to ensure there is functional websocket
          // when we navigate to other pages. (And if navigating back & forth between those pages)
          wsContainer
            .amend(
              ws.connected --> { _ =>
                val current = routeVar.now()
                current match
                  case Loading | Login(_) => routeVar.set(ChatRoomsOverview())
                  case _               =>
              },
              ws.closed --> { _ =>
                // Since we manage websocket connection manually, if we detect a closed signal, try
                // refreshing the connection automatically before signing user out.
                val token          = webStorageAccessTokenVar.now()
                val isManualLogout = token.isEmpty

                if !isManualLogout then
                  Api
                    .verifyTokenApi(token)
                    .foreach {
                      case Success(response) if response.valid =>
                        ws.reconnectNow()
                      case _ =>
                        // Token is now dead. Try login again
                        logout(Some(Html.Main.loginAgain))
                    }(using unsafeWindowOwner)
              }
            )
        case None => ()
    },
    onMountCallback { ctx =>
      webStorageAccessTokenVar.now() match
        case "" =>
          // Providing UX that we're checking session
          setTimeout(2000) {
            routeVar.set(Login())
          }
        case token =>
          Api
            .verifyTokenApi(token)
            .foreach {
              case Success(response) if response.valid =>
                initWebSocket(token)
              case _ =>
                // Verification may fail for reasons other than token expiration.
                // Regardless of the underlying cause, we treat the failure as an expired session
                // and prompt the user to obtain a new access token.
                logout(Some(Html.Main.loginAgain))
            }(using ctx.owner)
    },
    child <-- routeVar.signal.map {
      case ChatRoomsOverview(maybeDraftMessage) =>
        chatWSVar.now() match
          case Some(ws) => ChatRoomsOverviewPage(ws, maybeDraftMessage).run
          case None     =>
            // Theoretically not possible - Push back to Login page to restart
            LoginPage(Some(Html.Main.failedToConnectToServer)).run
      case Chat(roomId, roomTitle, maybeLatestMessage, maybeDraftMessage) =>
        chatWSVar.now() match
          case Some(ws) => ChatPage(ws, roomId, roomTitle, maybeLatestMessage, maybeDraftMessage).run
          case None     =>
            // Theoretically not possible - Push back to Login page to restart
            LoginPage(Some(Html.Main.failedToConnectToServer)).run
      case Profile(useCase) =>
        chatWSVar.now() match
          case Some(ws) =>
            useCase match
              case ProfileUseCase.Self(maybeInfo) =>
                ProfilePage.self(ws, maybeInfo).run
              case ProfileUseCase.ExistingFriend(info) =>
                ProfilePage.friend(ws, info).run
              case ProfileUseCase.SearchFriend(name) =>
                ProfilePage.search(ws, name).run
          case None =>
            // Theoretically not possible - Push back to Login page to restart
            LoginPage(Some(Html.Main.failedToConnectToServer)).run
      case Friends =>
        chatWSVar.now() match
          case Some(ws) => FriendsPage(ws).run
          case None     =>
            // Theoretically not possible - Push back to Login page to restart
            LoginPage(Some(Html.Main.failedToConnectToServer)).run
      case Loading =>
        div(
          cls := Css.containerView,
          waitingForAutoLoggingView
        )

      case Register                 => RegistrationPage().run
      case Login(maybeErrorMessage) => LoginPage(maybeErrorMessage).run
    }
  )

@main def happyFarmMain(): Unit =
  lazy val appContainer = dom.document.querySelector("#happyFarmContainer")

  renderOnDomContentLoaded(appContainer, AppRoot.view)
