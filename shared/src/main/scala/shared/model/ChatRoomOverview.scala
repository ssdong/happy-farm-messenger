package shared.model

import upickle.ReadWriter
import zio.schema.{ DeriveSchema, Schema }

import java.util.UUID

case class ChatRoomOverview(
    id: UUID,
    title: String, // This will be the other person's name if it's a DM
    lastReadSeq: Long,
    maybeLatestMessage: Option[Message]
) derives ReadWriter

object ChatRoomOverview:
  given Schema[ChatRoomOverview] = DeriveSchema.gen
