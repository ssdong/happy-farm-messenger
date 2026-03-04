package com.happyfarm.backend.handler

import com.happyfarm.backend.actor.{
  ChatActorCache,
  ChatActorCreationError,
  MessagePersistenceResponse,
  PostMessage,
  ReplyTo,
  StoppedTyping,
  Typing
}
import com.happyfarm.backend.persistence.HappyFarmRepository
import com.happyfarm.backend.persistence.HappyFarmRepository.PersistenceError.UnableToBufferMessage
import com.happyfarm.backend.subscriber.UserPresence
import shared.{
  AcceptFriend,
  AddFriend,
  BroadcastAddFriendRequest,
  BroadcastFriendAccepted,
  CatchUpLastReadSeq,
  ChatRequest,
  ChatResponse,
  ReceiveChatStarted,
  FetchFriends,
  FetchMessages,
  FetchRooms,
  FetchSelfInfo,
  FetchUserInfo,
  FriendList,
  HistoryMessages,
  MessageId,
  MessagePersisted,
  ReceiveError,
  ReceiveSelfInfo,
  ReceiveUserInfo,
  RequestOrigin,
  RoomId,
  RoomList,
  SendTextMessage,
  StartChat,
  UserId,
  UserIsTyping,
  UserStoppedTyping
}
import zio.http.{ ChannelEvent, Handler, Method, Request, Response, Routes, Status, WebSocketFrame, handler }
import zio.http.ChannelEvent.{ Read, UserEvent, UserEventTriggered }
import zio.{ Promise, ZIO, http }

import java.util.UUID
import scala.util.Try

class ChatHandler(
    repo: HappyFarmRepository,
    actorCache: ChatActorCache,
    userPresenceManager: UserPresence
):
  var int = 0
  private def handleUserTyping(
      roomId: RoomId,
      userId: UserId
  ): ZIO[Any, Nothing, Unit] =
    (for
      actor           <- actorCache.cache.get(roomId)
      messageBuffered <- actor.mailbox.offer(Typing(roomId, userId))
    yield ()).catchAll(_ => ZIO.unit)

  private def handleUserStoppedTyping(
      roomId: RoomId,
      userId: UserId
  ): ZIO[Any, Nothing, Unit] =
    (for
      actor           <- actorCache.cache.get(roomId)
      messageBuffered <- actor.mailbox.offer(StoppedTyping(roomId, userId))
    yield ()).catchAll(_ => ZIO.unit)

  private def handleSendMessage(
      roomId: RoomId,
      userId: UserId,
      mid: MessageId,
      text: String,
      tempId: String
  ): ZIO[Any, Nothing, MessagePersisted] =
    Thread.sleep(2000)
    if int < 3 then
      int += 1
      ZIO.succeed(
        MessagePersisted(maybeMessage = None, tempId = tempId)
      )
    else
      (for
        actor   <- actorCache.cache.get(roomId)
        promise <- Promise.make[Nothing, MessagePersistenceResponse]
        messageBuffered <- actor.mailbox
          .offer(PostMessage(text, roomId, userId, mid, ReplyTo(promise)))

        _ <- ZIO.fail(UnableToBufferMessage).unless(messageBuffered)

        result <- promise.await
      yield MessagePersisted(
        maybeMessage = result.maybeMessage,
        tempId = tempId
      ))
        .catchAll {
          case UnableToBufferMessage | _: ChatActorCreationError =>
            ZIO.logWarning(s"Failed to buffer message $mid") *>
              ZIO.succeed(
                MessagePersisted(maybeMessage = None, tempId = tempId)
              )
          case e =>
            ZIO.logWarning(s"Unable to persist message due to $e") *>
              ZIO.succeed(
                MessagePersisted(maybeMessage = None, tempId = tempId)
              )
        }

  private def chatRoomsSocket(authenticatedUserId: UserId) =
    Handler.webSocket { channel =>
      for
        _ <- userPresenceManager.register(authenticatedUserId)

        _ <- ZIO.scoped {
          userPresenceManager.subscribe(authenticatedUserId).flatMap {
            case Some(subscription) =>
              // Start the broadcast for the user forever til the user goes offline
              (for
                response <- subscription.take
                json = upickle.default.write(response)
                _ <-
                  channel.send(Read(WebSocketFrame.Text(json)))
              yield ()).forever
            case None =>
              // This is technically not possible
              ZIO.logError(s"User $authenticatedUserId connected but no hub found!")
          }
        }.forkDaemon

        _ <- channel
          .receiveAll {
            case UserEventTriggered(UserEvent.HandshakeComplete) =>
              ZIO.logInfo("Chat Rooms WebSocket Connected!")
            case Read(WebSocketFrame.Text(rawRequest)) =>
              (for
                request <- ZIO.fromTry(Try(upickle.default.read[ChatRequest](rawRequest)))

                origin = request match
                  case _: FetchSelfInfo => Some(RequestOrigin.ProfilePage)
                  case _: FetchRooms    => Some(RequestOrigin.OverviewPage)
                  case _: FetchMessages => Some(RequestOrigin.ChatPage)
                  case _: FetchUserInfo => Some(RequestOrigin.FriendPage)
                  case _: AddFriend     => Some(RequestOrigin.ProfilePage)
                  case _: AcceptFriend  => Some(RequestOrigin.FriendPage)
                  case _                => None

                maybeResponse <- (request match
                  case _: FetchSelfInfo =>
                    repo
                      .fetchUserInfoById(authenticatedUserId)
                      .map(userInfo => Some(ReceiveSelfInfo(userInfo)))

                  case FetchUserInfo(name) =>
                    repo
                      .fetchUserInfoByName(name)
                      .map(maybeUserInfo => Some(ReceiveUserInfo(maybeUserInfo)))

                  case _: FetchFriends =>
                    repo
                      .fetchFriends(authenticatedUserId)
                      .map(friends => Some(FriendList(friends.toVector)))

                  case _: FetchRooms =>
                    repo
                      .fetchChatRooms(authenticatedUserId)
                      .map(rooms => Some(RoomList(rooms.toVector)))

                  case FetchMessages(roomId, offset, size) =>
                    repo
                      .fetchHistoryMessages(roomId, authenticatedUserId, offset, size)
                      .map(messages => Some(HistoryMessages(messages.toVector)))

                  case AddFriend(friendName) =>
                    for
                      friendUserId <- repo.addFriend(authenticatedUserId, friendName)
                      userInfo     <- repo.fetchUserInfoById(authenticatedUserId)
                      _            <-
                        // Broadcast to the receiver - only if they are online
                        userPresenceManager.publishToUser(
                          friendUserId,
                          BroadcastAddFriendRequest(from = userInfo)
                        )
                    yield None

                  case AcceptFriend(friendName) =>
                    for
                      friendUserId <- repo.acceptFriend(authenticatedUserId, friendName)
                      userInfo     <- repo.fetchUserInfoById(authenticatedUserId)
                      friendInfo   <- repo.fetchUserInfoById(friendUserId)
                      _            <-
                      // Broadcast to both users if they become friends
                      userPresenceManager.publishToUser(
                        friendUserId,
                        BroadcastFriendAccepted(newFriend = userInfo)
                      ) *>
                        userPresenceManager.publishToUser(
                          authenticatedUserId,
                          BroadcastFriendAccepted(newFriend = friendInfo)
                        )
                    yield None

                  case SendTextMessage(roomId, text, tempId) =>
                    handleSendMessage(roomId, authenticatedUserId, MessageId(UUID.randomUUID()), text, tempId)
                      .map(persistedSignal => Some(persistedSignal))

                  case CatchUpLastReadSeq(roomId) =>
                    repo.catchUpLastReadSeq(roomId, authenticatedUserId).as(None)

                  case UserIsTyping(roomId) =>
                    handleUserTyping(roomId, authenticatedUserId).as(None)

                  case UserStoppedTyping(roomId) =>
                    handleUserStoppedTyping(roomId, authenticatedUserId).as(None)

                  case StartChat(friendName) =>
                    repo
                      .startChat(authenticatedUserId, friendName)
                      .map((roomId, roomTitle, maybeMessage) =>
                        Some(ReceiveChatStarted(roomId, roomTitle, maybeMessage))
                      )
                ).catchAll { chatError =>
                  ZIO.succeed {
                    if chatError.isFrontendFacing then
                      chatError.userMessage
                        .map(m => ReceiveError(reason = m, isFatal = chatError.isFatal, maybeOrigin = origin))
                    else None
                  }
                }
                _ <- ZIO.foreach(maybeResponse) { response =>
                  channel.send(Read(WebSocketFrame.Text(upickle.default.write(response))))
                }
              yield ()).catchAll(ex => ZIO.logError(s"WebSocket Error: ${ex.getMessage}"))
            case ChannelEvent.Unregistered =>
              ZIO.logInfo("Chat Rooms WebSocket Disconnected/Closed")
            case _ => ZIO.unit
          }
          .ensuring {
            userPresenceManager.remove(authenticatedUserId) *> ZIO.logInfo(
              s"User $authenticatedUserId went offline."
            )
          }
      yield ()
    }

  val routes: Routes[Any, Nothing] =
    Routes(
      Method.GET / "chat" -> handler { (request: Request) =>
        (for
          token <- ZIO.fromOption(request.queryParam("token"))

          userId <- repo.fetchUserIdFromToken(token)

          response <- chatRoomsSocket(userId).toResponse
        yield response).catchAll(_ => ZIO.succeed(Response.status(Status.Unauthorized)))
      }
    )

object ChatHandler:
  def apply(repo: HappyFarmRepository, actorCache: ChatActorCache, userPresenceManager: UserPresence) =
    new ChatHandler(repo, actorCache, userPresenceManager)
