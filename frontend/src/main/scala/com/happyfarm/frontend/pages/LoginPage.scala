package com.happyfarm.frontend.pages

import com.happyfarm.frontend.AppRoot
import com.happyfarm.frontend.Globals.routeVar
import com.happyfarm.frontend.Globals.webStorageAccessTokenVar
import com.happyfarm.frontend.Route.Register
import com.happyfarm.frontend.api.Api
import com.happyfarm.frontend.assets.{ Css, Html }
import com.happyfarm.frontend.pages.LoginPage.{ happyFarmLogo, signInIcon }
import com.happyfarm.frontend.pages.Page
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.HTMLDivElement

import scala.util.{ Failure, Success }

class LoginPage(maybeErrorMessage: Option[String] = None) extends Page:
  private val maybeErrorMessageVar: Var[Option[String]] = Var(maybeErrorMessage)

  private val nameVar: Var[String]     = Var[String]("")
  private val passwordVar: Var[String] = Var[String]("")

  private def signInObserver(using Owner) = Observer[(String, String)](onNext =
    (name, password) =>
      if Seq(name, password).exists(_.isBlank) then
        maybeErrorMessageVar.set(Some("Please enter valid credentials"))
      else
        Api
          .loginApi(name = name, password = password)
          .foreach {

            /** foreach creates an external observer and needs to bind to some "owner" so it can be destroyed
              * to avoid memory leak
              */
            case Success(response) =>
              webStorageAccessTokenVar.set(response.accessToken)

              AppRoot.initWebSocket(response.accessToken)
            case Failure(error) =>
              maybeErrorMessageVar.set(Some(error.getMessage))
          }
  )

  override def run: ReactiveHtmlElement[HTMLDivElement] =
    div(
      div(
        className := Css.LoginPage.happyFarmLogoStyle,
        img(
          src := happyFarmLogo
        )
      ),
      div(
        className := Css.LoginPage.loginFormWrapper,
        h2(
          className := Css.LoginPage.loginFormTitle,
          "Sign in with HappyFarm id"
        ),
        div(
          child <-- maybeErrorMessageVar.splitOption(
            (initialErrorMessage, currentErrorMessage) =>
              div(
                className := Css.LoginPage.loginFormErrorMessage,
                text <-- currentErrorMessage
              ),
            ifEmpty = emptyNode
          )
        ),
        form(
          className := Css.LoginPage.loginFormStyle,
          method    := "post",
          div(
            label(
              className := Css.LoginPage.loginFormNameLabelStyle,
              forId     := "name",
              Html.LoginPage.name
            ),
            input(
              `type`    := "text",
              nameAttr  := "name",
              idAttr    := "name",
              className := Css.LoginPage.loginFormNameInputBoxStyle,
              value <-- nameVar,
              onInput.mapToValue --> nameVar
            )
          ),
          div(
            label(
              className := Css.LoginPage.loginFormPasswordLabelStyle,
              forId     := "password",
              Html.LoginPage.password
            ),
            input(
              `type`    := "password",
              nameAttr  := "password",
              idAttr    := "password",
              className := Css.LoginPage.loginFormPasswordInputBoxStyle,
              value <-- passwordVar,
              onInput.mapToValue --> passwordVar
            )
          ),
          div(
            className := Css.LoginPage.loginFormSignInWrapper,
            input(
              `type`    := "image",
              src       := signInIcon,
              alt       := "Sign In",
              className := Css.LoginPage.loginFormSignInButtonStyle
            )
          ),
          div(
            className := Css.LoginPage.loginFormRegisterWrapper,
            a(
              onClick --> (_ => routeVar.set(Register)),
              className := Css.LoginPage.loginFormRegisterButtonStyle,
              Html.LoginPage.navigateToRegister
            )
          ),
          onMountBind { ctx =>
            given Owner = ctx.owner

            onSubmit.preventDefault.map { _ =>
              (nameVar.now(), passwordVar.now())
            } --> signInObserver
          }
        )
      )
    )

object LoginPage:
  private val happyFarmLogo = "public/assets/happy_farm.png"
  private val signInIcon    = "public/assets/sign_in.png"

  def apply(maybeErrorMessage: Option[String] = None) = new LoginPage(maybeErrorMessage)
