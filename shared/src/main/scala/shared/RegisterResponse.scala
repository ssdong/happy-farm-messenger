package shared

import upickle.Reader
import zio.schema.{DeriveSchema, Schema}

case class RegisterResponse(message: String) derives Reader

object RegisterResponse:
  given schema: Schema[RegisterResponse] = DeriveSchema.gen[RegisterResponse]