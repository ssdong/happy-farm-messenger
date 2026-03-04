package shared

import upickle.Reader
import zio.schema.{ DeriveSchema, Schema }

import java.util.UUID

case class LoginResponse(accessToken: String, userId: UUID) derives Reader

object LoginResponse:
  given schema: Schema[LoginResponse] = DeriveSchema.gen[LoginResponse]
