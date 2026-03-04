package com.happyfarm.backend.handler

import com.happyfarm.backend.persistence.HappyFarmRepository.AuthError.{
  InvalidCredential,
  InvalidRegistrationToken,
  RegistrationFailed,
  TokenInsertionFailed,
  UnknownServerError,
  UserNameAlreadyExists
}
import com.happyfarm.backend.persistence.HappyFarmRepository
import shared.{
  AppError,
  LoginRequest,
  LoginResponse,
  Reason,
  RegisterRequest,
  RegisterResponse,
  VerifyTokenRequest,
  VerifyTokenResponse
}
import zio.http.codec.HttpCodec
import zio.http.endpoint.Endpoint
import zio.http.{ RoutePattern, Routes, Status }

class AuthHandler(repo: HappyFarmRepository):
  private val verifyTokenEndpoint = Endpoint(
    RoutePattern.POST / "verify-token"
  )
    .in[VerifyTokenRequest]
    .out[VerifyTokenResponse]
    .outError[AppError](Status.InternalServerError)

  private val registerEndPoint = Endpoint(
    RoutePattern.POST / "register"
  )
    .in[RegisterRequest]
    .out[RegisterResponse]
    .outErrors[AppError](
      HttpCodec.error[AppError](Status.Unauthorized),
      HttpCodec.error[AppError](Status.InternalServerError)
    )

  private val loginEndpoint = Endpoint(
    RoutePattern.POST / "login"
  )
    .in[LoginRequest]
    .out[LoginResponse]
    .outErrors[AppError](
      HttpCodec.error[AppError](Status.Unauthorized),
      HttpCodec.error[AppError](Status.InternalServerError)
    )

  val routes: Routes[Any, Nothing] =
    Routes(
      verifyTokenEndpoint.implement { request =>
        repo
          .verifyToken(token = request.token)
          .map(valid => VerifyTokenResponse(valid))
          .mapError(_ => AppError(Reason.UnknownServerError))
      },
      registerEndPoint.implement { request =>
        repo
          .register(
            name = request.name,
            password = request.password,
            registrationToken = request.registrationToken
          )
          .map(RegisterResponse(_))
          .mapError {
            case InvalidRegistrationToken => AppError(Reason.InvalidRegistrationToken)
            case RegistrationFailed       => AppError(Reason.RegistrationFailed)
            case UserNameAlreadyExists    => AppError(Reason.UserNameAlreadyExist)
            case _                        => AppError(Reason.UnknownServerError)
          }
      },
      loginEndpoint.implement { request =>
        repo
          .login(name = request.name, password = request.password)
          .map((token, userId) => LoginResponse(accessToken = token, userId = userId))
          .mapError {
            case InvalidCredential    => AppError(Reason.InvalidCredential)
            case TokenInsertionFailed => AppError(Reason.TokenInsertionFailure)
            case _                    => AppError(Reason.UnknownServerError)
          }
      }
    )

object AuthHandler:
  def apply(repo: HappyFarmRepository) = new AuthHandler(repo)
