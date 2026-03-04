package shared.model

import upickle.ReadWriter
import zio.schema.{ DeriveSchema, Schema }

enum FriendshipStatus derives ReadWriter:
  case pending, accepted

case class Friendship(
    userInfo: UserInfo,
    status: FriendshipStatus
) derives ReadWriter

object Friendship:
  given Schema[Friendship] = DeriveSchema.gen
