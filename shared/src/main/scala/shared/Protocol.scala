package shared

import upickle.{ ReadWriter, readwriter }
import zio.schema.Schema

import java.util.UUID

inline def uuidReadWriter[A]: ReadWriter[A] =
  readwriter[UUID].bimap(id => id.asInstanceOf[UUID], uuid => uuid.asInstanceOf[A])

opaque type RoomId    = UUID
opaque type UserId    = UUID
opaque type MessageId = UUID

object RoomId:
  def apply(uuid: UUID): RoomId = uuid
  
  extension (r: RoomId) def toUUID: UUID = r

  given schema: Schema[RoomId] = Schema[UUID].transform(
    (uuid: UUID) => RoomId(uuid),
    (roomId: RoomId) => roomId.toUUID
  )
  given rw: ReadWriter[RoomId] = uuidReadWriter

object UserId:
  def apply(uuid: UUID): UserId = uuid
  
  extension (r: UserId) def toUUID: UUID = r

  given schema: Schema[UserId] = Schema[UUID].transform(
    (uuid: UUID) => UserId(uuid),
    (userId: UserId) => userId.toUUID
  )
  given rw: ReadWriter[UserId] = uuidReadWriter

object MessageId:
  def apply(uuid: UUID): MessageId = uuid

  extension (r: MessageId) def toUUID: UUID = r

  given schema: Schema[MessageId] = Schema[UUID].transform(
    (uuid: UUID) => MessageId(uuid),
    (messageId: MessageId) => messageId.toUUID
  )
  given rw: ReadWriter[MessageId] = uuidReadWriter
