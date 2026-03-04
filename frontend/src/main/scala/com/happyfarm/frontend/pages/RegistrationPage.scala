package com.happyfarm.frontend.pages

import com.happyfarm.frontend.Globals.routeVar
import RegistrationPage.{ happyFarmLogo, registerIcon }
import com.happyfarm.frontend.Route.Login
import com.happyfarm.frontend.api.Api
import com.happyfarm.frontend.assets.{ Css, Html }
import com.happyfarm.frontend.pages.Page
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.HTMLDivElement

import scala.util.matching.Regex
import scala.util.{ Failure, Success }

class RegistrationPage extends Page:
  private val maybeErrorMessageVar: Var[Option[String]] = Var(None)
  private val maybeSuccessMessage: Var[Option[String]]  = Var(None)

  private val nameVar: Var[String]              = Var[String]("")
  private val passwordVar: Var[String]          = Var[String]("")
  private val registrationTokenVar: Var[String] = Var[String]("")

  private val registrationComplete: Var[Boolean] = Var(false)

  private def registerObserver(using Owner) =
    Observer[(String, String, String)](onNext = (name, password, registrationToken) =>
      val trimmedName = name.trim
      if Seq(trimmedName, password, registrationToken).exists(_.isBlank) then
        maybeErrorMessageVar.set(Some("Please enter complete and valid information"))
      else if trimmedName.length < 1 || trimmedName.length > 32 then
        maybeErrorMessageVar.set(
          Some("Name must be between 1 and 32 characters")
        )
      else
        Api
          .registerApi(name = trimmedName, password = password, registrationToken = registrationToken)
          .foreach {

            /** foreach creates an external observer and needs to bind to some "owner" so it can be destroyed
              * to avoid memory leak
              */
            case Success(response) =>
              nameVar.set("")
              passwordVar.set("")
              registrationTokenVar.set("")
              maybeErrorMessageVar.set(None)
              maybeSuccessMessage.set(Some("Registration successful! Please go to sign in"))
            case Failure(error) =>
              maybeSuccessMessage.set(None)
              maybeErrorMessageVar.set(Some(error.getMessage))
          })

  override def run: ReactiveHtmlElement[HTMLDivElement] =
    div(
      div(
        className := Css.RegistrationPage.happyFarmLogoStyle,
        img(
          src := happyFarmLogo
        )
      ),
      div(
        className := Css.RegistrationPage.registrationFormWrapper,
        h2(
          className := Css.RegistrationPage.registrationFormTitle,
          Html.RegistrationPage.registration
        ),
        div(
          child <-- maybeErrorMessageVar.splitOption(
            (initialErrorMessage, currentErrorMessage) =>
              div(
                className := Css.RegistrationPage.registrationFormErrorMessage,
                text <-- currentErrorMessage
              ),
            ifEmpty = emptyNode
          )
        ),
        div(
          child <-- maybeSuccessMessage.splitOption(
            (initialSuccessMessage, currentSuccessMessage) =>
              div(
                className := Css.RegistrationPage.registrationFormSuccessMessage,
                text <-- currentSuccessMessage
              ),
            ifEmpty = emptyNode
          )
        ),
        form(
          className := Css.RegistrationPage.registrationFormStyle,
          method    := "post",
          div(
            label(
              className := Css.RegistrationPage.registrationNameLabelStyle,
              forId     := "name",
              Html.RegistrationPage.name
            ),
            input(
              `type`    := "text",
              nameAttr  := "name",
              idAttr    := "name",
              className := Css.RegistrationPage.registrationNameInputBoxStyle,
              value <-- nameVar,
              onInput.mapToValue --> nameVar
            )
          ),
          div(
            label(
              className := Css.RegistrationPage.registrationPasswordLabelStyle,
              forId     := "password",
              Html.RegistrationPage.password
            ),
            input(
              `type`    := "password",
              nameAttr  := "password",
              idAttr    := "password",
              className := Css.RegistrationPage.registrationPasswordInputBoxStyle,
              value <-- passwordVar,
              onInput.mapToValue --> passwordVar
            )
          ),
          div(
            label(
              className := Css.RegistrationPage.registrationTokenLabelStyle,
              forId     := "registration token",
              Html.RegistrationPage.token
            ),
            input(
              `type`    := "text",
              nameAttr  := "registrationToken",
              idAttr    := "registrationToken",
              className := Css.RegistrationPage.registrationTokenInputBoxStyle,
              value <-- registrationTokenVar,
              onInput.mapToValue --> registrationTokenVar
            )
          ),
          div(
            className := Css.RegistrationPage.registrationButtonWrapper,
            input(
              `type`    := "image",
              src       := registerIcon,
              alt       := "Register",
              className := Css.RegistrationPage.registrationButtonStyle
            )
          ),
          div(
            className := Css.RegistrationPage.registrationSignInWrapper,
            a(
              onClick --> (_ => routeVar.set(Login())),
              className := Css.RegistrationPage.registrationSignInStyle,
              Html.RegistrationPage.navigateToSignIn
            )
          ),
          onMountBind { ctx =>
            given Owner = ctx.owner

            onSubmit.preventDefault.map { _ =>
              (nameVar.now(), passwordVar.now(), registrationTokenVar.now())
            } --> registerObserver
          }
        )
      )
    )

object RegistrationPage:
  private val happyFarmLogo = "public/assets/happy_farm.png"
  private val registerIcon  = "public/assets/register.png"
