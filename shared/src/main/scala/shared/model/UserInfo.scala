package shared.model

import upickle.ReadWriter
import zio.schema.{ DeriveSchema, Schema }

case class UserInfo(
    userName: String,
    joined: String // yyyy-MM-ddTHH:mm:ssZ ISO-8601 compliant string
) derives ReadWriter

object UserInfo:
  given Schema[UserInfo] = DeriveSchema.gen
