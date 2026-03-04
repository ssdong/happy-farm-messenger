package com.happyfarm.backend.actor

import com.happyfarm.backend.subscriber.UserPresence
import shared.model.Message
import shared.{ MessageId, RoomId, UserId }
import zio.{ Fiber, Hub, Promise, Queue }

import scala.util.control.NoStackTrace

case class ChatState(lastSeq: Long)

sealed trait ChatCommand

// Things that need to talk back to the sender
sealed trait TrackingCommand extends ChatCommand:
  def replyTo: ReplyTo

sealed trait FireAndForgetCommand extends ChatCommand

case class PostMessage(body: String, roomId: RoomId, userId: UserId, messageId: MessageId, replyTo: ReplyTo)
    extends TrackingCommand

case class PostImage(
    body: Vector[Byte],
    roomId: RoomId,
    userId: UserId,
    messageId: MessageId,
    replyTo: ReplyTo
) extends TrackingCommand

case class Typing(
    roomId: RoomId,
    userId: UserId
) extends FireAndForgetCommand

case class StoppedTyping(
    roomId: RoomId,
    userId: UserId
) extends FireAndForgetCommand

case class ChatActor(
    mailbox: Queue[ChatCommand],
    fiber: Fiber[Nothing, Unit],
    userPresenceManager: UserPresence
)

case class ChatActorCreationError(message: String) extends Exception(message) with NoStackTrace

sealed trait PersistenceOperatorResult

case class Success(message: Message, replyTo: ReplyTo) extends PersistenceOperatorResult
case class Failure(replyTo: ReplyTo)                   extends PersistenceOperatorResult
case object NoOp                                       extends PersistenceOperatorResult

case class MessagePersistenceResponse(maybeMessage: Option[Message])

case class ReplyTo(promise: Promise[Nothing, MessagePersistenceResponse])
