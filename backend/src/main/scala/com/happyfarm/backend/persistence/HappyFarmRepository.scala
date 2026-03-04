package com.happyfarm.backend.persistence

import com.happyfarm.backend.persistence.HappyFarmRepository.{ AuthIO, ChatIO, PersistenceIO, Token }
import shared.{ MessageId, RoomId, UserId }
import shared.model.{ ChatRoomOverview, Friendship, Message, UserInfo }
import zio.ZIO

import java.util.UUID
import scala.util.control.NoStackTrace

trait HappyFarmRepository:
  // Authentication
  def register(name: String, password: String, registrationToken: String): AuthIO[String]
  def login(name: String, password: String): AuthIO[(Token, UUID)]
  def verifyToken(token: String): AuthIO[Boolean]
  def fetchUserIdFromToken(token: String): AuthIO[UserId]

  // Chats
  def fetchUserInfoById(userId: UserId): ChatIO[UserInfo] // This is currently only used to fetch self info.
  def fetchUserInfoByName(name: String): ChatIO[Option[UserInfo]]
  def fetchUsers(roomId: RoomId): ChatIO[Seq[UserId]]
  def fetchFriends(userId: UserId): ChatIO[Seq[Friendship]]
  def fetchChatRooms(userId: UserId): ChatIO[Seq[ChatRoomOverview]]
  def fetchHistoryMessages(
      roomId: RoomId,
      currentUserId: UserId,
      offset: Long,
      size: Long
  ): ChatIO[Seq[Message]]
  def fetchLatestMessageSeq(roomId: RoomId): ChatIO[Option[Long]]
  def addFriend(userId: UserId, friendName: String): ChatIO[UserId]
  def acceptFriend(userId: UserId, friendName: String): ChatIO[UserId]
  def catchUpLastReadSeq(roomId: RoomId, userId: UserId): ChatIO[Unit]
  def startChat(currentUser: UserId, friendName: String): ChatIO[(RoomId, String, Option[Message])]

  def persistTextMessage(
      message: String,
      roomId: RoomId,
      userId: UserId,
      messageId: MessageId,
      seq: Long
  ): PersistenceIO[Message]

object HappyFarmRepository:
  type Token             = String
  type AuthIO[+A]        = ZIO[Any, AuthError, A]
  type ChatIO[+A]        = ZIO[Any, ChatsError, A]
  type PersistenceIO[+A] = ZIO[Any, PersistenceError, A]

  trait Error extends Exception with NoStackTrace

  enum AuthError extends Error:
    case InvalidCredential
    case InvalidRegistrationToken
    case TokenInsertionFailed
    case RegistrationFailed
    case UserNameAlreadyExists
    case UnknownServerError

  enum ChatsError extends Error:
    case CorruptedCredential
    case FetchUserInfoFailure
    case FetchUsersFailure
    case FetchChatRoomsFailure
    case FetchHistoryMessagesFailure
    case FetchLatestMessageSeqFailure
    case CatchUpLastReadSeqFailure
    case AddNonExistentUserFailure
    case AddSelfAsFriendFailure
    case AddExistingFriendFailure
    case AddFriendFailure
    case AcceptNonExistentFriendFailure
    case AcceptFriendFailure
    case StartChatFailure
    case StartChatWithNonExistentFriendFailure

    def isFrontendFacing: Boolean = this match
      case FetchUsersFailure | CatchUpLastReadSeqFailure | FetchLatestMessageSeqFailure => false
      case _                                                                            => true

    private val userDoesNotExistEdgeCase =
      "User does not exist. Please contact admin" // This is technically not possible cuz we do not allow account deletion unless there's data corruption.
    def userMessage: Option[String] = this match
      case CorruptedCredential            => Some("You data might have been corrupted. Please contact admin")
      case FetchUserInfoFailure           => Some("Could not load profile data")
      case FetchChatRoomsFailure          => Some("Could not load your chat list")
      case FetchHistoryMessagesFailure    => Some("Could not load message history")
      case AddNonExistentUserFailure      => Some(userDoesNotExistEdgeCase)
      case AddSelfAsFriendFailure         => Some("Cold not add yourself as a friend")
      case AddExistingFriendFailure       => Some("User is already a friend")
      case AddFriendFailure               => Some("Could not add user as friend. Please try again")
      case AcceptNonExistentFriendFailure => Some(userDoesNotExistEdgeCase)
      case AcceptFriendFailure            => Some("Could not accept friendship request. Please try again")
      case StartChatFailure               => Some("Could you start chat. Please try again")
      case StartChatWithNonExistentFriendFailure => Some(userDoesNotExistEdgeCase)
      // Backend domain error - no need for user error message
      case _ => None

    def isFatal: Boolean = this match
      case CorruptedCredential => true
      case _                   => false

  enum PersistenceError extends Error:
    case DuplicateMessage
    case MessagePersistenceFailure
    case NoMessagePersisted
    case UnableToBufferMessage
