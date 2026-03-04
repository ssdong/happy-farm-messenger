package shared.model

import upickle.ReadWriter
import zio.schema.{ DeriveSchema, Schema }

import java.util.UUID

enum MessageType derives ReadWriter:
  case text, image, audio

case class Message(
    roomId: UUID,
    userId: String,
    userName: Option[String] = None,
    seq: Long,
    createdAt: String, // yyyy-MM-ddTHH:mm:ssZ ISO-8601 compliant string - Scala.js doesn’t bundle java.time by default. Works with JS.Date
    `type`: MessageType,
    text: Option[String],
    imageType: Option[String],
    image: Vector[Byte],
    audioType: Option[String],
    audio: Vector[Byte],
    isMe: Boolean = false
) derives ReadWriter

object Message:
  given Schema[Message] = DeriveSchema.gen
