package com.happyfarm.frontend.api

import com.raquo.airstream.core.EventStream
import com.raquo.airstream.web.{ FetchBuilder, FetchStream }
import shared.Reason.{
  InvalidCredential,
  InvalidRegistrationToken,
  RegistrationFailed,
  TokenInsertionFailure,
  UserNameAlreadyExist
}
import shared.*
import upickle.read

import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal
import scala.scalajs.js.JSON.stringify
import scala.util.{ Failure, Success, Try }

object Api:
  private val loginApiBuilder: FetchBuilder[LoginRequest, Try[LoginResponse]] =
    FetchStream.withCodec[LoginRequest, Try[LoginResponse]](
      request =>
        stringify(
          literal(
            name = request.name,
            password = request.password
          )
        ),
      response =>
        EventStream
          .fromJsPromise(response.text())
          .map { r =>
            if response.ok then Success(read[LoginResponse](r))
            else
              val error: AppError = read[AppError](r)
              val message = error.reason match
                case InvalidCredential     => "Please enter valid credential"
                case TokenInsertionFailure => "Unexpected error has occurred. Please try again later"
                case _                     => "Unknown Error. Please try again later"
              Failure(new RuntimeException(message))
          }
    )

  private val registerApiBuilder: FetchBuilder[RegisterRequest, Try[RegisterResponse]] =
    FetchStream.withCodec[RegisterRequest, Try[RegisterResponse]](
      request =>
        stringify(
          literal(
            name = request.name,
            password = request.password,
            registrationToken = request.registrationToken
          )
        ),
      response =>
        EventStream
          .fromJsPromise(response.text())
          .map { r =>
            if response.ok then Success(read[RegisterResponse](r))
            else
              val error: AppError = read[AppError](r)
              val message = error.reason match
                case InvalidRegistrationToken => "Please enter valid registration token"
                case RegistrationFailed       => "Registration Failed. Please try again later"
                case UserNameAlreadyExist     => "Name has been taken. Please try with a different username"
                case _                        => "Unknown Error. Please try again later"
              Failure(new RuntimeException(message))
          }
    )

  private val verifyTokenApiBuilder: FetchBuilder[VerifyTokenRequest, Try[VerifyTokenResponse]] =
    FetchStream.withCodec[VerifyTokenRequest, Try[VerifyTokenResponse]](
      request =>
        stringify(
          literal(
            token = request.token
          )
        ),
      response =>
        EventStream
          .fromJsPromise(response.text())
          .map { r =>
            if response.ok then Success(read[VerifyTokenResponse](r))
            else
              val error: AppError = read[AppError](r)
              val message = error.reason match
                case _ => "Unable to verify token. Please try again later."
              Failure(new RuntimeException(message))
          }
    )

  def loginApi(name: String, password: String): EventStream[Try[LoginResponse]] =
    loginApiBuilder.post(
      "/login",
      _.headers("Content-Type" -> "application/json"),
      _.body(LoginRequest(name = name, password = password)),
      _.abortOnStop()
    )

  def registerApi(
      name: String,
      password: String,
      registrationToken: String
  ): EventStream[Try[RegisterResponse]] =
    registerApiBuilder.post(
      "/register",
      _.headers("Content-Type" -> "application/json"),
      _.body(RegisterRequest(name = name, password = password, registrationToken = registrationToken)),
      _.abortOnStop()
    )

  def verifyTokenApi(token: String): EventStream[Try[VerifyTokenResponse]] =
    verifyTokenApiBuilder.post(
      "/verify-token",
      _.headers("Content-Type" -> "application/json"),
      _.body(VerifyTokenRequest(token = token)),
      _.abortOnStop()
    )
