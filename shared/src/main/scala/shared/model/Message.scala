package shared.model

import upickle.ReadWriter
import zio.schema.{ DeriveSchema, Schema }

import java.util.UUID

enum MessageType derives ReadWriter:
  case text, image, audio

// 'replace' is useful when user loses connection in FE(e.g. mobile browser) and
// Laminar reconnects websocket and fetches history messages all the way to user's last
// unread message. The fetched messages need to replace the existing messages in FE
// rather than be appended.
enum HistoryMessageMode derives ReadWriter:
  case append, replace

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
    isMe: Boolean = false,
    unreadMarker: Boolean = false
) derives ReadWriter

object Message:
  given Schema[Message] = DeriveSchema.gen
