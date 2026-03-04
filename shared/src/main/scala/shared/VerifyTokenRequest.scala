package shared

import zio.schema.{ DeriveSchema, Schema }

case class VerifyTokenRequest(token: String)

object VerifyTokenRequest:
  given schema: Schema[VerifyTokenRequest] = DeriveSchema.gen[VerifyTokenRequest]
