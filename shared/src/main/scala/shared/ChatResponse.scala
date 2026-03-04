package shared

import upickle.ReadWriter
import shared.model.{ ChatRoomOverview, Friendship, Message, UserInfo }
import zio.schema.{ DeriveSchema, Schema }

sealed trait ChatResponse derives ReadWriter

case class RoomList(chatRooms: Vector[ChatRoomOverview]) extends ChatResponse derives ReadWriter
case class FriendList(friends: Vector[Friendship])       extends ChatResponse derives ReadWriter
case class HistoryMessages(messages: Vector[Message])    extends ChatResponse derives ReadWriter
case class MessagePersisted(maybeMessage: Option[Message], tempId: String) extends ChatResponse
    derives ReadWriter

// Broadcast-related events
case class BroadCastMessage(message: Message)      extends ChatResponse derives ReadWriter
case class BroadcastAddFriendRequest(from: UserInfo) extends ChatResponse derives ReadWriter
case class BroadcastFriendAccepted(newFriend: UserInfo)   extends ChatResponse derives ReadWriter

case class ReceiveSelfInfo(userInfo: UserInfo)                     extends ChatResponse derives ReadWriter
case class ReceiveUserInfo(maybeUserInfo: Option[UserInfo])        extends ChatResponse derives ReadWriter
case class ReceiveUserTyping(roomId: RoomId, isMe: Boolean)        extends ChatResponse derives ReadWriter
case class ReceiveUserStoppedTyping(roomId: RoomId, isMe: Boolean) extends ChatResponse derives ReadWriter
case class ReceiveError(reason: String, isFatal: Boolean, maybeOrigin: Option[RequestOrigin])
    extends ChatResponse derives ReadWriter
case class ReceiveChatStarted(roomId: RoomId, roomTitle: String, maybeLatestMessage: Option[Message])
    extends ChatResponse derives ReadWriter

object RoomList:
  given schema: Schema[RoomList] = DeriveSchema.gen[RoomList]

object HistoryMessages:
  given schema: Schema[HistoryMessages] = DeriveSchema.gen[HistoryMessages]
