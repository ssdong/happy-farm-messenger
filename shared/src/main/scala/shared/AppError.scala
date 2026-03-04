package shared

import upickle.Reader
import zio.schema.{ DeriveSchema, Schema }

enum Reason derives Reader:
  // Login
  case InvalidCredential
  case TokenInsertionFailure
  case UserNameAlreadyExist

  // Registration
  case InvalidRegistrationToken
  case RegistrationFailed

  // Chats
  case ExpiredSession
  case UnknownServerError

case class AppError(reason: Reason) derives Reader

object AppError:
  given schemaReason: Schema[Reason] = DeriveSchema.gen[Reason]
  given schema: Schema[AppError]     = DeriveSchema.gen[AppError]
