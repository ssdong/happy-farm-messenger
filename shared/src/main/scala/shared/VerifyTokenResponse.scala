package shared

import upickle.Reader
import zio.schema.{ DeriveSchema, Schema }

case class VerifyTokenResponse(valid: Boolean) derives Reader

object VerifyTokenResponse:

  given schema: Schema[VerifyTokenResponse] = DeriveSchema.gen[VerifyTokenResponse]
