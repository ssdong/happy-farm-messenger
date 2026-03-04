package com.happyfarm.backend.actor

import com.happyfarm.backend.persistence.HappyFarmRepository
import com.happyfarm.backend.persistence.HappyFarmRepository.PersistenceError.{
  DuplicateMessage,
  MessagePersistenceFailure,
  NoMessagePersisted,
  UnableToBufferMessage
}
import com.happyfarm.backend.subscriber.UserPresence
import shared.{ BroadCastMessage, ReceiveUserStoppedTyping, ReceiveUserTyping, RoomId }
import shared.model.Message
import zio.{ Queue, Ref, ZIO, durationInt }

/** A per–chat room actor that handles messages synchronously in a single-threaded manner. When the actor
  * starts, it fetches the latest message sequence for the room once and then increments it locally. This way,
  * we don’t have to keep hitting the DB to calculate the next sequence number for each message.
  *
  * It also gives us a deterministic way to increment the sequence, so messages can be persisted in a
  * predictable order.
  *
  * Each actor is responsible for a single chat room ID, and if one actor fails, it doesn’t impact other chat
  * rooms.
  *
  * It’s kind of reinventing the actor model wheel (like Akka/Pekko/ShardCake), but honestly, building the
  * logic was a lot of fun.
  */
def makeChatActor(
    roomId: RoomId,
    repo: HappyFarmRepository,
    userPresenceManager: UserPresence
): ZIO[Any, ChatActorCreationError, ChatActor] =
  (for
    seq      <- repo.fetchLatestMessageSeq(roomId).map(_.getOrElse(0L))
    stateRef <- Ref.Synchronized.make(ChatState(seq))
    mailbox  <- Queue.bounded[ChatCommand](100) // Maximum 100 messages to be buffered

    fiber <- (for
      maybeCmd <- mailbox.take.timeout(31.minutes)
      state    <- stateRef.get
      persistenceResult <-
        maybeCmd match
          case Some(Typing(rId, uId)) =>
            (for
              userIds <- repo.fetchUsers(rId)
              // Broadcast to other online users except for the sender
              _ <- ZIO.foreach(userIds) { userId =>
                if userId != uId then
                  userPresenceManager.publishToUser(userId, ReceiveUserTyping(rId, isMe = uId == userId))
                else ZIO.unit
              }
            yield NoOp).catchAll(_ => ZIO.succeed(NoOp)) // We don't care if broadcasting typing signal failed

          case Some(StoppedTyping(rId, uId)) =>
            (for
              userIds <- repo.fetchUsers(rId)
              // Broadcast to other online users except for the sender
              _ <- ZIO.foreach(userIds) { userId =>
                if userId != uId then
                  userPresenceManager.publishToUser(
                    userId,
                    ReceiveUserStoppedTyping(rId, isMe = uId == userId)
                  )
                else ZIO.unit
              }
            yield NoOp).catchAll(_ =>
              ZIO.succeed(NoOp)
            ) // We also don't care if broadcasting stopped typing signal failed

          case Some(PostMessage(m, rid, uid, mid, replyTo)) =>
            val lastSeq = state.lastSeq
            repo
              .persistTextMessage(message = m, roomId = rid, userId = uid, messageId = mid, seq = lastSeq + 1)
              .flatMap(message =>
                stateRef.update(state => state.copy(lastSeq = lastSeq + 1)) *> ZIO.succeed(message)
              )
              .foldZIO(
                {
                  case NoMessagePersisted | MessagePersistenceFailure => ZIO.succeed(Failure(replyTo))
                  case DuplicateMessage                               =>
                    // We ignore duplicate message persistence
                    ZIO.succeed(NoOp)
                  case UnableToBufferMessage =>
                    // This is not possible - this is to silence compiler non-exhaustive warning
                    ZIO.succeed(NoOp)
                },
                m => ZIO.succeed(Success(m, replyTo))
              ): ZIO[Any, Nothing, PersistenceOperatorResult]

          case None =>
            // PASSIVATION LOGIC
            // Since ZIO.Cache does not have a mechanism to attach a callback on evicted item, the
            // actor itself needs to be responsible for self-destruction if a certain span of time
            // has elapsed with no messages coming at all.

            // Ideally, if actor has access to cache in there we could invalidate manually when timeout
            // is up for "mailbox.take". However, it creates a circular dependency where actor is produced
            // within the "Lookup" in cache. A less ideal way is to define TimeToLive to be X mins in Cache
            // and set timeout on "mailbox.take" to be X + 1 mins, so the daemon fiber can kill itself after
            // the actor has been evicted from the actor, which prevents resource leak.

            // Keep it this way til I find a better solution or making callback available on
            // evicted item in ZIO Cache?
            ZIO.logInfo(s"Passivating room $roomId due to inactivity") *>
              ZIO.interrupt
          case _ => ZIO.succeed(NoOp)
      _ <- persistenceResult match
        case Success(m, replyTo) =>
          (for
            userIds <- repo.fetchUsers(RoomId(m.roomId))
            // It will only publish to online users
            _ <- ZIO.foreach(userIds) { userId =>
              userPresenceManager.publishToUser(
                userId,
                BroadCastMessage(m.copy(isMe = userId.toUUID.toString == m.userId))
              )
            }
            _ <- replyTo.promise
              .succeed(MessagePersistenceResponse(maybeMessage = Some(m)))
          yield ()).catchAll(e => ZIO.logError(s"Error fetching users for room $roomId due to $e"))
        case Failure(replyTo) =>
          replyTo.promise.succeed(MessagePersistenceResponse(maybeMessage = None)).unit
        case NoOp =>
          // Ignore
          ZIO.unit
    yield ()).forever.forkDaemon
  yield ChatActor(mailbox = mailbox, fiber = fiber, userPresenceManager = userPresenceManager))
    .tapError(e => ZIO.logError(s"Unable to create chat actor for roomId: $roomId due to ${e.getMessage}"))
    .mapError(e => ChatActorCreationError(e.getMessage))
