package shared

import zio.schema.{ DeriveSchema, Schema }

case class RegisterRequest(name: String, password: String, registrationToken: String)

object RegisterRequest:
  given schema: Schema[RegisterRequest] = DeriveSchema.gen[RegisterRequest]
