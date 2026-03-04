package shared

import upickle.ReadWriter
import zio.schema.{ DeriveSchema, Schema }

sealed trait ChatRequest derives ReadWriter

enum RequestOrigin derives ReadWriter:
  case OverviewPage, ChatPage, ProfilePage, FriendPage

case class FetchSelfInfo()                                         extends ChatRequest derives ReadWriter
case class FetchUserInfo(name: String)                             extends ChatRequest derives ReadWriter
case class FetchFriends()                                          extends ChatRequest derives ReadWriter
case class FetchRooms()                                            extends ChatRequest derives ReadWriter
case class FetchMessages(roomId: RoomId, offset: Long, size: Long) extends ChatRequest derives ReadWriter
case class SendTextMessage(roomId: RoomId, text: String, tempId: String) extends ChatRequest
    derives ReadWriter
case class UserIsTyping(roomId: RoomId)       extends ChatRequest derives ReadWriter
case class UserStoppedTyping(roomId: RoomId)  extends ChatRequest derives ReadWriter
case class CatchUpLastReadSeq(roomId: RoomId) extends ChatRequest derives ReadWriter
case class AddFriend(name: String)            extends ChatRequest derives ReadWriter
case class AcceptFriend(name: String)         extends ChatRequest derives ReadWriter
case class StartChat(name: String)            extends ChatRequest derives ReadWriter

object FetchRooms:
  given schema: Schema[FetchRooms] = DeriveSchema.gen[FetchRooms]

object FetchMessages:
  given schema: Schema[FetchMessages] = DeriveSchema.gen[FetchMessages]
