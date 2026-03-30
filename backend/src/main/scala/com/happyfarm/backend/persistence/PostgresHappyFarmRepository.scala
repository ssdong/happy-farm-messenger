package com.happyfarm.backend.persistence

import com.happyfarm.backend.persistence.HappyFarmRepository.AuthError.{
  InvalidCredential,
  InvalidRegistrationToken,
  RegistrationFailed,
  TokenInsertionFailed,
  UnknownServerError,
  UserNameAlreadyExists,
  UnknownServerError as AuthUnknownServerError
}
import com.happyfarm.backend.persistence.HappyFarmRepository.ChatsError.{
  AcceptFriendFailure,
  AcceptNonExistentFriendFailure,
  AddExistingFriendFailure,
  AddFriendFailure,
  AddNonExistentUserFailure,
  AddSelfAsFriendFailure,
  CatchUpLastReadSeqFailure,
  CorruptedCredential,
  FetchChatRoomsFailure,
  FetchHistoryMessagesFailure,
  FetchLatestMessageSeqFailure,
  FetchUserInfoFailure,
  FetchUsersFailure,
  StartChatFailure,
  StartChatWithNonExistentFriendFailure
}
import com.happyfarm.backend.persistence.HappyFarmRepository.PersistenceError.{
  DuplicateMessage,
  MessagePersistenceFailure,
  NoMessagePersisted
}
import com.happyfarm.backend.persistence.HappyFarmRepository.{ AuthIO, ChatIO, PersistenceIO, Token }
import com.happyfarm.backend.persistence.PostgresHappyFarmRepository.{ RawChatRoomOverview, RawMessage }
import com.happyfarm.backend.setting.EncryptionSettings
import com.happyfarm.backend.utils.CommonUtils
import com.typesafe.scalalogging.LazyLogging
import shared.model.MessageType.text
import shared.{ MessageId, RoomId, UserId }
import shared.model.{ ChatRoomOverview, Friendship, FriendshipStatus, Message, MessageType, UserInfo }
import zio.{ Task, ZIO }

import java.sql.{ Connection, SQLException, SQLIntegrityConstraintViolationException }
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.sql.DataSource
import scala.util.{ Try, Using }
import scala.math.max

class PostgresHappyFarmRepository private (
    connectionProvider: () => Connection,
    encryptionService: EncryptionService
) extends HappyFarmRepository
    with LazyLogging:

  override def register(name: String, password: String, registrationToken: String): AuthIO[String] =
    for
      userExists <- db { connection =>
        val userCheckSql = s"SELECT 1 FROM happyfarm.users WHERE name = ?"
        Using.resource(connection.prepareStatement(userCheckSql)) { ps =>
          ps.setString(1, name)
          Using.resource(ps.executeQuery())(_.next())
        }
      }
        .tapError(e => ZIO.logError(s"Database failure: ${e.getMessage}"))
        .mapError(_ => UnknownServerError)

      _ <- ZIO.fail(UserNameAlreadyExists).when(userExists)

      validToken <- db { connection =>
        val sql =
          s"""
            DELETE
            FROM happyfarm.registration_tokens
            WHERE token = ?
            RETURNING 1
          """.stripMargin
        Using.resource(connection.prepareStatement(sql)) { preparedStatement =>
          preparedStatement.setString(1, registrationToken)

          /*
           * DELETE with RETURNING will create a temporary 'ResultSet' object
           * and will need to be closed.
           */
          Using.resource(preparedStatement.executeQuery())(_.next())
        }
      }
        .tapError(e => ZIO.logError(s"Database failure: ${e.getMessage}"))
        .mapError(_ => AuthUnknownServerError)

      registrationSuccessful <-
        if !validToken then ZIO.fail(InvalidRegistrationToken)
        else
          val hashedPassword = CommonUtils.hashPassword(password)
          db { connection =>
            val sql =
              s"""
                 INSERT INTO happyfarm.users
                 (name, password_hash)
                 VALUES (?, ?)
                 """.stripMargin

            Using.resource(connection.prepareStatement(sql)) { preparedStatement =>
              preparedStatement.setString(1, name)
              preparedStatement.setString(2, hashedPassword)
              preparedStatement.executeUpdate()
            }
          }
            .flatMap { result =>
              if result == 1 then ZIO.succeed("Registration Successful!")
              else ZIO.fail(RegistrationFailed)
            }
            .tapError(e => ZIO.logError(s"Database failure: ${e.getMessage}"))
            .mapError(_ => AuthUnknownServerError)
    yield registrationSuccessful

  override def login(name: String, password: String): AuthIO[(Token, UUID)] =
    for
      (maybeUserCanonicalId, maybeStoredPasswordHash) <- db { connection =>
        val sql =
          s"""
             SELECT user_canonical_id, password_hash
             FROM happyfarm.users u
             WHERE u.name = ?
             """.stripMargin
        Using.resource(connection.prepareStatement(sql)) { preparedStatement =>
          preparedStatement.setString(1, name)

          Using.resource(preparedStatement.executeQuery()) { result =>
            if result.next() then (Option(result.getString(1)), Option(result.getString(2))) else (None, None)
          }
        }
      }
        .tapError(e => ZIO.logError(s"Database failure: ${e.getMessage}"))
        .mapError(_ => AuthUnknownServerError)

      (userCanonicalId, storedPasswordHash) <- ZIO
        .fromOption(
          for
            userCanonicalIdStr <- maybeUserCanonicalId
            userCanonicalId    <- Try(UUID.fromString(userCanonicalIdStr)).toOption
            storedPasswordHash <- maybeStoredPasswordHash
          yield (userCanonicalId, storedPasswordHash)
        )
        .orElseFail(InvalidCredential)

      valid <- ZIO
        .succeed(
          CommonUtils.verifyPassword(password = password, storedHashedPassword = storedPasswordHash)
        )

      // We're storing encrypted access tokens in DB so even if they have existing valid
      // tokens, it won't be retrievable again. Security Trade-off for more 'ghost' tokens which can
      // be cleaned up periodically.
      token <-
        if valid then
          generateToken(userCanonicalId).tapError(e => ZIO.logError(s"Database failure: ${e.getMessage}"))
        else ZIO.fail(InvalidCredential)
    yield (token, userCanonicalId)

  override def verifyToken(token: String): AuthIO[Boolean] =
    val hashedToken = CommonUtils.base64EncodeSHA256(token)
    db { connection =>
      val sql =
        s"""
          SELECT EXISTS(
            SELECT 1
            FROM happyfarm.access_tokens
            WHERE token_hash = ? AND expires_at > now()
          )
          """.stripMargin

      /** https://theholyjava.wordpress.com/2013/02/18/jdbc-what-resources-you-have-to-close-and-when/
        */
      Using.resource(connection.prepareStatement(sql)) { preparedStatement =>
        preparedStatement.setString(1, hashedToken)
        Using.resource(preparedStatement.executeQuery()) { result =>
          result.next()
          result.getBoolean(1)
        }
      }
    }.mapError(_ => AuthUnknownServerError)

  override def fetchUserIdFromToken(token: Token): AuthIO[UserId] =
    val hashedToken = CommonUtils.base64EncodeSHA256(token)
    db { connection =>
      val sql =
        s"""
           SELECT user_canonical_id
           FROM happyfarm.access_tokens
           WHERE token_hash = ? AND expires_at > now()
         """.stripMargin

      Using.resource(connection.prepareStatement(sql)) { preparedStatement =>
        preparedStatement.setString(1, hashedToken)
        Using.resource(preparedStatement.executeQuery()) { rs =>
          if rs.next() then Some(UserId(rs.getObject("user_canonical_id", classOf[UUID])))
          else None
        }
      }
    }
      .flatMap {
        case Some(userId) => ZIO.succeed(userId)
        case None         => ZIO.fail(InvalidCredential)
      }
      .tapError(e => ZIO.logError(s"Database failure: ${e.getMessage}"))
      .mapError(_ => AuthUnknownServerError)

  override def fetchFriends(userId: UserId): ChatIO[Seq[Friendship]] =
    db { connection =>
      val sql =
        """
          SELECT 
            u.name, 
            u.created_at, 
            f.status, 
            f.requester_id
          FROM happyfarm.friendships f
          JOIN happyfarm.users u ON (
            (f.user_lo = ? AND f.user_hi = u.user_canonical_id) OR
            (f.user_hi = ? AND f.user_lo = u.user_canonical_id)
          )
          WHERE f.status = 'accepted' OR (f.status = 'pending' AND f.requester_id != ?)
          """.stripMargin

      Using.resource(connection.prepareStatement(sql)) { ps =>
        ps.setObject(1, userId.toUUID)
        ps.setObject(2, userId.toUUID)
        // We only fetch pending requests if the requester is NOT the current user
        ps.setObject(3, userId.toUUID)

        Using.resource(ps.executeQuery()) { rs =>
          Iterator
            .continually(rs.next())
            .takeWhile(identity)
            .map { _ =>
              val statusStr = rs.getString("status")
              val status =
                if statusStr == "accepted" then FriendshipStatus.accepted else FriendshipStatus.pending

              Friendship(
                userInfo = UserInfo(
                  userName = rs.getString("name"),
                  joined = formatter.format(rs.getTimestamp("created_at").toInstant)
                ),
                status = status
              )
            }
            .toList
        }
      }
    }
      .tapError(e => ZIO.logError(s"Database failure fetching friends for ${userId.toUUID}: ${e.getMessage}"))
      .mapError(_ => FetchUserInfoFailure)

  override def fetchUserInfoByName(name: String): ChatIO[Option[UserInfo]] =
    db { connection =>
      val sql =
        """
          SELECT name, created_at
          FROM users u
          WHERE u.name = ?
          """.stripMargin

      Using.resource(connection.prepareStatement(sql)) { ps =>
        ps.setObject(1, name)

        Using.resource(ps.executeQuery()) { rs =>
          if rs.next() then
            Some(
              UserInfo(
                userName = rs.getString("name"),
                joined = formatter.format(rs.getTimestamp("created_at").toInstant)
              )
            )
          else None
        }
      }
    }
      .flatMap(ZIO.succeed(_))
      .tapError(e => ZIO.logError(s"Database failure: ${e.getMessage}"))
      .mapError(_ => FetchUserInfoFailure)

  override def fetchUserInfoById(userId: UserId): ChatIO[UserInfo] =
    db { connection =>
      val sql =
        """
          SELECT name, created_at
          FROM users u
          WHERE u.user_canonical_id = ?
          """.stripMargin

      Using.resource(connection.prepareStatement(sql)) { ps =>
        ps.setObject(1, userId.toUUID)

        Using.resource(ps.executeQuery()) { rs =>
          if rs.next() then
            Some(
              UserInfo(
                userName = rs.getString("name"),
                joined = formatter.format(rs.getTimestamp("created_at").toInstant)
              )
            )
          else None
        }
      }
    }
      .flatMap {
        case Some(userInfo) => ZIO.succeed(userInfo)
        case None           =>
          // This is NOT supposed to happen since user has already authenticated
          ZIO.fail(CorruptedCredential)
      }
      .tapError(e => ZIO.logError(s"Database failure: ${e.getMessage}"))
      .mapError(_ => FetchUserInfoFailure)

  override def fetchUsers(roomId: RoomId): ChatIO[Seq[UserId]] =
    db { connection =>
      val sql =
        """
          SELECT user_canonical_id
          FROM room_members rm
          WHERE rm.room_id = ?
          """.stripMargin

      Using.resource(connection.prepareStatement(sql)) { ps =>
        ps.setObject(1, roomId.toUUID)

        Using.resource(ps.executeQuery()) { rs =>
          val userIds = Vector.newBuilder[UserId]

          while rs.next() do userIds += UserId(rs.getObject("user_canonical_id", classOf[UUID]))

          userIds.result()
        }
      }
    }
      .flatMap(ZIO.succeed(_))
      .tapError(e => ZIO.logError(s"Database failure: ${e.getMessage}"))
      .mapError(_ => FetchUsersFailure)

  override def fetchChatRooms(userId: UserId): ChatIO[Seq[ChatRoomOverview]] =
    for
      rawChatRooms <- fetchRawChatRooms(userId)
      rooms        <- ZIO.foreachPar(rawChatRooms)(sanitizeRawChatRoomOverview)
    yield rooms

  override def fetchHistoryMessages(
      roomId: RoomId,
      currentUserId: UserId,
      offset: Long,
      size: Long
  ): ChatIO[Seq[Message]] =
    (for
      rawMessages <- db { connection =>
        val lastReadSeq = getLastReadSeq(connection, roomId.toUUID, currentUserId.toUUID)

        fetchHistoryMessagesInternal(connection, roomId, currentUserId, offset, size, lastReadSeq)
      }
      messages <- ZIO.foreachPar(rawMessages)(sanitizeRawMessage)
    yield messages)
      .tapError(e => ZIO.logError(s"Database failure: ${e.getMessage}"))
      .mapError(_ => FetchHistoryMessagesFailure)

  override def fetchUnreadMessages(roomId: RoomId, currentUserId: UserId): ChatIO[Seq[Message]] =
    (for
      maybeLatestMessageSeq <- fetchLatestMessageSeq(roomId)

      latestSeq = maybeLatestMessageSeq.getOrElse(0L)

      rawMessages <-
        db { connection =>
          val lastReadSeq = getLastReadSeq(connection, roomId.toUUID, currentUserId.toUUID)
          // Potential improvement:

          // Frankly, this isn't the best solution, but it's enough for a private chat app.
          // If there are 500 unread messages, this query will try to load all 500 at once.
          // In a very active room, this could lead to a large memory spike or a slow query
          // on the server.
          // It should have a cap to prevent loading thousands of messages if a user has been
          // offline for a month.

          // Fetch 10 more history messages beyond unread if applicable
          val extraHistoryMessages = max(0L, latestSeq - lastReadSeq + 10L)
          fetchHistoryMessagesInternal(
            connection,
            roomId,
            currentUserId,
            latestSeq,
            extraHistoryMessages,
            lastReadSeq
          )
        }
      messages <- ZIO.foreachPar(rawMessages)(sanitizeRawMessage)
    yield messages)
      .tapError(e => ZIO.logError(s"Database failure: ${e.getMessage}"))
      .mapError(_ => FetchHistoryMessagesFailure) // This is essentially still fetching "history" messages

  override def fetchLatestMessageSeq(roomId: RoomId): ChatIO[Option[Long]] =
    db { connection =>
      val sql =
        """
          SELECT
            m.seq
          FROM happyfarm.messages m
          WHERE m.room_id = ?
          ORDER BY m.seq DESC
          LIMIT 1
          """.stripMargin

      Using.resource(connection.prepareStatement(sql)) { ps =>
        // Explicit casting - proves we know what we're doing that roomId is a UUID
        ps.setObject(1, roomId.toUUID)

        Using.resource(ps.executeQuery()) { rs =>
          if rs.next() then
            val seq = rs.getLong("seq")
            Some(seq)
          else None
        }
      }
    }
      .flatMap(ZIO.succeed)
      .tapError(e => ZIO.logError(s"Database failure: ${e.getMessage}"))
      .mapError(_ => FetchLatestMessageSeqFailure)

  override def acceptFriend(userId: UserId, friendName: Token): ChatIO[UserId] =
    val receiverUuid = userId.toUUID

    for
      maybeFriendUuid <- db { conn =>
        val sql = "SELECT user_canonical_id FROM happyfarm.users WHERE name = ?"
        Using.resource(conn.prepareStatement(sql)) { ps =>
          ps.setString(1, friendName)
          Using.resource(ps.executeQuery()) { rs =>
            if rs.next() then Some(rs.getObject(1, classOf[java.util.UUID])) else None
          }
        }
      }.mapError(_ => AcceptFriendFailure)

      friendUuid <- ZIO.fromOption(maybeFriendUuid).orElseFail(AcceptNonExistentFriendFailure)

      maybeUpdated <- db { conn =>
        val (lo, hi) = orderUUIDs(receiverUuid, friendUuid)

        val sql =
          """
            UPDATE happyfarm.friendships
            SET status = 'accepted'
            WHERE user_lo = ? AND user_hi = ? AND status = 'pending'
            """.stripMargin

        Using.resource(conn.prepareStatement(sql)) { ps =>
          ps.setObject(1, lo)
          ps.setObject(2, hi)
          ps.executeUpdate()
        }
      }.mapError(_ => AcceptFriendFailure)

      // It's likely impossible we'll have no friendship row to updates
      _ <- ZIO.fail(AcceptFriendFailure).when(maybeUpdated == 0)
    yield UserId(friendUuid)

  override def addFriend(userId: UserId, friendName: String): ChatIO[UserId] =
    val senderUuid = userId.toUUID

    for
      maybeFriendUuid <- db { connection =>
        val sql = "SELECT user_canonical_id FROM happyfarm.users WHERE name = ?"
        Using.resource(connection.prepareStatement(sql)) { ps =>
          ps.setString(1, friendName)
          Using.resource(ps.executeQuery()) { rs =>
            if rs.next() then Some(rs.getObject(1, classOf[java.util.UUID])) else None
          }
        }
      }
        .tapError(e => ZIO.logError(s"Database failure: ${e.getMessage}"))
        .mapError(_ => AddFriendFailure)

      friendUuid <- ZIO
        .fromOption(maybeFriendUuid)
        .orElseFail(
          AddNonExistentUserFailure
        ) // Edge case where user deleted their account right after user searched their name and click "add". Though we currently do not support account deletion
      _ <- ZIO.fail(AddSelfAsFriendFailure).when(friendUuid == senderUuid)

      _ <- db { connection =>
        val (lo, hi) = orderUUIDs(senderUuid, friendUuid)

        val sql =
          """
            INSERT INTO happyfarm.friendships (user_lo, user_hi, status, requester_id)
            VALUES (?, ?, 'pending', ?)
            """.stripMargin

        Using.resource(connection.prepareStatement(sql)) { ps =>
          ps.setObject(1, lo)
          ps.setObject(2, hi)
          ps.setObject(3, senderUuid)
          ps.executeUpdate()
        }
      }
        .tapError(e => ZIO.logError(s"Database failure: ${e.getMessage}"))
        .mapError {
          case e: SQLException if e.getSQLState.startsWith("23") => AddExistingFriendFailure
          case _                                                 => AddFriendFailure
        }
    yield UserId(friendUuid)

  override def catchUpLastReadSeq(roomId: RoomId, userId: UserId): ChatIO[Unit] =
    db { connection =>
      val sql =
        """
          UPDATE happyfarm.room_members
          SET last_read_seq = COALESCE((
              SELECT MAX(seq)
              FROM happyfarm.messages
              WHERE room_id = ?
          ), 0)
          WHERE room_id = ? AND user_canonical_id = ?
          """
      Using.resource(connection.prepareStatement(sql)) { ps =>
        ps.setObject(1, roomId.toUUID)
        ps.setObject(2, roomId.toUUID)
        ps.setObject(3, userId.toUUID)

        ps.executeUpdate()
      }
    }
      .flatMap(_ => ZIO.succeed(()))
      .tapError(e => ZIO.logError(s"Database failure: ${e.getMessage}"))
      .mapError(_ => CatchUpLastReadSeqFailure)

  override def startChat(currentUser: UserId, friendName: String): ChatIO[(RoomId, String, Option[Message])] =
    val currentUserIdUuid = currentUser.toUUID
    for
      maybeFriendUuid <- db { connection =>
        val sql = "SELECT user_canonical_id FROM happyfarm.users WHERE name = ?"
        Using.resource(connection.prepareStatement(sql)) { ps =>
          ps.setString(1, friendName)
          Using.resource(ps.executeQuery()) { rs =>
            if rs.next() then Some(rs.getObject(1, classOf[java.util.UUID])) else None
          }
        }
      }.mapError(_ => StartChatFailure)

      friendUuid <- ZIO
        .fromOption(maybeFriendUuid)
        .orElseFail(
          StartChatWithNonExistentFriendFailure
        )

      fingerprint =
        val (lo, hi) = orderUUIDs(currentUserIdUuid, friendUuid)
        s"$lo:$hi"

      maybeRoomId <- db { connection =>
        val sql =
          // dummy 'no-op' update(setting the value to what it already is) to make sure RETURNING id
          // returns a valid id. Otherwise, it returns nothing if we do ON CONFLICT DO NOTHING
          """
            WITH ins_room AS (
              INSERT INTO happyfarm.rooms (room_type, dm_fingerprint)
              VALUES ('direct', ?)
              ON CONFLICT (dm_fingerprint) DO UPDATE SET room_type = EXCLUDED.room_type
              RETURNING id
            ),
            ins_members AS (
              INSERT INTO happyfarm.room_members (room_id, user_canonical_id)
              SELECT id, ? FROM ins_room
              UNION ALL
              SELECT id, ? FROM ins_room
              ON CONFLICT DO NOTHING
            )
            SELECT id FROM ins_room;
        """.stripMargin

        Using.resource(connection.prepareStatement(sql)) { ps =>
          ps.setString(1, fingerprint)
          ps.setObject(2, currentUserIdUuid)
          ps.setObject(3, friendUuid)
          Using.resource(ps.executeQuery()) { rs =>
            if rs.next() then Some(rs.getObject(1, classOf[java.util.UUID])) else None
          }
        }
      }.mapError(_ => StartChatFailure)

      roomId <- ZIO.fromOption(maybeRoomId).orElseFail(StartChatFailure)

      maybeRawMessage <- db { connection =>
        val sql =
          """
            SELECT
                m.room_id,
                m.seq,
                m.message_type,
                m.message_text,
                m.encrypted_dek,
                m.nonce,
                m.created_at,
                CAST(u.user_canonical_id AS text) AS string_user_id,
                u.name,
                i.content_type AS image_type,
                i.data         AS image_data,
                a.content_type AS audio_type,
                a.data         AS audio_data
            FROM happyfarm.messages m
            LEFT JOIN happyfarm.users u ON m.user_canonical_id = u.user_canonical_id
            LEFT JOIN happyfarm.image_blobs i ON m.image_id = i.image_id
            LEFT JOIN happyfarm.audio_blobs a ON m.audio_id = a.audio_id
            WHERE m.room_id = ?
            ORDER BY m.seq DESC -- Get the LATEST messages
            LIMIT 1
          """.stripMargin

        Using.resource(connection.prepareStatement(sql)) { ps =>
          ps.setObject(1, roomId)
          Using.resource(ps.executeQuery()) { rs =>
            if rs.next() then
              val userId = rs.getString("string_user_id")
              Some(
                RawMessage(
                  roomId = rs.getObject("room_id", classOf[UUID]),
                  userId = userId,
                  userName = Some(rs.getString("name")),
                  seq = rs.getLong("seq"),
                  createdAt = formatter.format(rs.getTimestamp("created_at").toInstant),
                  `type` = MessageType.valueOf(rs.getString("message_type")),
                  encryptedText = Option(rs.getBytes("message_text")),
                  encryptedDek = Option(rs.getBytes("encrypted_dek")),
                  nonce = Option(rs.getBytes("nonce")),
                  imageType = Option(rs.getString("image_type")),
                  image = Option(rs.getBytes("image_data")),
                  audioType = Option(rs.getString("audio_type")),
                  audio = Option(rs.getBytes("audio_data")),
                  isMe = currentUserIdUuid.toString == userId
                )
              )
            else None
          }
        }
      }.mapError(_ => StartChatFailure)
      maybeMessage <- ZIO.foreach(maybeRawMessage)(sanitizeRawMessage)
    yield (RoomId(roomId), friendName, maybeMessage)

  override def persistTextMessage(
      message: String,
      roomId: RoomId,
      userId: UserId,
      messageId: MessageId,
      seq: Long
  ): PersistenceIO[Message] =
    for
      encryptedTuple <- ZIO
        .fromEither(encryptionService.encrypt(message))
        .tapError(e => ZIO.logError(s"Encryption failure: $e"))
        .mapError(_ => MessagePersistenceFailure)

      (encryptedText, encryptedDek, nonce) = encryptedTuple

      result <-
        db { connection =>
          val sql =
            """
              |WITH inserted AS (
              |    INSERT INTO happyfarm.messages
              |    (room_id, seq, message_id, user_canonical_id, message_type, message_text, encrypted_dek, nonce, created_at)
              |    VALUES (?, ?, ?, ?, 'text', ?, ?, ?, now())
              |    RETURNING room_id, seq, message_id, user_canonical_id, message_type, message_text, created_at
              |)
              |SELECT
              |    i.*,
              |    u.name
              |FROM inserted as i
              |JOIN happyfarm.users u
              |ON i.user_canonical_id = u.user_canonical_id
              |""".stripMargin

          Using.resource(connection.prepareStatement(sql)) { preparedStatement =>
            // Explicit casting - proves we know what we're doing that the follow ids are UUID
            preparedStatement.setObject(1, roomId.toUUID)
            preparedStatement.setLong(2, seq)
            preparedStatement.setObject(3, messageId.toUUID)
            preparedStatement.setObject(4, userId.toUUID)
            preparedStatement.setBytes(5, encryptedText)
            preparedStatement.setBytes(6, encryptedDek)
            preparedStatement.setBytes(7, nonce)

            val rs = preparedStatement.executeQuery()
            if rs.next() then
              Some(
                Message(
                  roomId = rs.getObject("room_id", classOf[UUID]),
                  userId = rs.getString("user_canonical_id"),
                  userName = Some(rs.getString("name")),
                  seq = rs.getLong("seq"),
                  createdAt = formatter.format(rs.getTimestamp("created_at").toInstant),
                  `type` = MessageType.valueOf(rs.getString("message_type")),
                  text = Some(message),
                  imageType = None,
                  image = Vector.empty,
                  audioType = None,
                  audio = Vector.empty,
                  isMe = true
                )
              )
            else None
          }
        }.flatMap {
          case Some(message) => ZIO.succeed(message)
          case None          =>
            // This is a bizarre situation. The SQL is syntactically correct but did not result in any
            // rows being inserted. Need further investigation..
            ZIO.logError(s"Message $message failed to be persisted for room $roomId from user $userId") *> ZIO
              .fail(NoMessagePersisted)
        }.tapError { e =>
          ZIO.logError(s"Database failure: ${e.getMessage}")
        }.mapError {
          case _: SQLIntegrityConstraintViolationException =>
            // This is likely due to user retries sending the same message when there are network hiccups
            // and one of the retries worked while the rest are rejected with violation
            DuplicateMessage
          case e: SQLException if e.getSQLState.startsWith("23") =>
            // https://www.ibm.com/docs/en/db2w-as-a-service?topic=messages-sqlstate#rsttmsg__code23
            // A more defensive approach that indicates a "Constraint Violation" code if the driver did
            // not wrap the exception into a "SQLIntegrityConstraintViolationException" intelligently
            DuplicateMessage
          case _ =>
            MessagePersistenceFailure
        }
    yield result

  private def fetchRawChatRooms(userId: UserId): ChatIO[Seq[RawChatRoomOverview]] =
    db { connection =>
      val sql =
        s"""
          WITH members AS (
            SELECT
              room_id,
              last_read_seq
            FROM happyfarm.room_members
            WHERE user_canonical_id = ?
          )
          SELECT DISTINCT ON (r.id)
            r.id              AS room_id,
            r.title           AS room_title,
            rm.last_read_seq,
            m.seq,
            m.message_type,
            m.message_text,
            m.encrypted_dek,
            m.nonce,
            m.created_at   AS last_chatted,
            CAST(user_canonical_id AS text) AS string_user_id,
            i.content_type AS image_type,
            i.data         AS image_data,
            a.content_type AS audio_type,
            a.data         AS audio_data
          FROM happyfarm.rooms r
          JOIN members rm ON rm.room_id = r.id
          LEFT JOIN happyfarm.messages m ON m.room_id = r.id
          LEFT JOIN happyfarm.image_blobs i ON m.image_id = i.image_id
          LEFT JOIN happyfarm.audio_blobs a ON m.audio_id = a.audio_id
          ORDER BY r.id, m.seq DESC;
          """.stripMargin

      Using.resource(connection.prepareStatement(sql)) { ps =>
        ps.setObject(1, userId.toUUID)

        Using.resource(ps.executeQuery()) { rs =>
          val buf = Vector.newBuilder[RawChatRoomOverview]

          while rs.next() do
            val roomId             = rs.getObject("room_id", classOf[UUID])
            val lastReadSeq        = rs.getLong("last_read_seq")
            val maybeGroupTitle    = Option(rs.getString("room_title")) // TODO: or use room_type
            val maybeLastChatTs    = Option(rs.getTimestamp("last_chatted")).map(_.toInstant)
            val maybeLastChatTsStr = maybeLastChatTs.map(formatter.format)

            val groupOrPrivateChatTitle =
              maybeGroupTitle.getOrElse(resolvePrivateChatTitle(connection, roomId, userId.toUUID))

            val maybeLastMessage = maybeLastChatTsStr match
              case Some(lastChatTsStr) =>
                Some(
                  RawMessage(
                    roomId = roomId,
                    userId = rs.getString("string_user_id"),
                    seq = rs.getLong("seq"),
                    createdAt = lastChatTsStr,
                    `type` = MessageType.valueOf(rs.getString("message_type")),
                    encryptedText = Option(rs.getBytes("message_text")),
                    encryptedDek = Option(rs.getBytes("encrypted_dek")),
                    nonce = Option(rs.getBytes("nonce")),
                    imageType = Option(rs.getString("image_type")),
                    image = Option(rs.getBytes("image_data")),
                    audioType = Option(rs.getString("audio_type")),
                    audio = Option(rs.getBytes("audio_data"))
                  )
                )
              case None => None

            buf += RawChatRoomOverview(
              id = roomId,
              title = groupOrPrivateChatTitle,
              lastReadSeq = lastReadSeq,
              maybeLatestMessage = maybeLastMessage
            )

          buf.result()
        }
      }
    }
      .flatMap(ZIO.succeed(_))
      .tapError(e => ZIO.logError(s"Database failure: ${e.getMessage}"))
      .mapError(_ => FetchChatRoomsFailure)

  private def sanitizeRawChatRoomOverview(raw: RawChatRoomOverview): ZIO[Any, Nothing, ChatRoomOverview] =
    for maybeSanitizedMessage <- ZIO.foreach(raw.maybeLatestMessage)(sanitizeRawMessage)
    yield ChatRoomOverview(
      id = raw.id,
      title = raw.title,
      lastReadSeq = raw.lastReadSeq,
      maybeLatestMessage = maybeSanitizedMessage
    )

  private def sanitizeRawMessage(raw: RawMessage): ZIO[Any, Nothing, Message] =
    val decryptedTextTask = (for
      text  <- ZIO.fromOption(raw.encryptedText)
      dek   <- ZIO.fromOption(raw.encryptedDek)
      nonce <- ZIO.fromOption(raw.nonce)
      decrypted <- ZIO.fromEither(
        encryptionService.decrypt(encryptedText = text, encryptedDek = dek, nonce = nonce)
      )
    yield decrypted).tapError {
      case e: EncryptionError =>
        ZIO.logError(s"Decryption failed for msg ${raw.seq} in room ${raw.roomId}: $e")
      case None => ZIO.unit // Ignore - means no text
    }.option // flattens both to None (missing fields or failed decryption)

    // TODO: The following will also go through encryption/decryption flow once we support image/audio
    val imageData = raw.image.map(_.toVector).getOrElse(Vector.empty)
    val audioData = raw.audio.map(_.toVector).getOrElse(Vector.empty)

    decryptedTextTask.map { maybeText =>
      Message(
        roomId = raw.roomId,
        userId = raw.userId,
        userName = raw.userName,
        seq = raw.seq,
        createdAt = raw.createdAt,
        `type` = raw.`type`,
        text = maybeText,
        imageType = raw.imageType,
        image = imageData,
        audioType = raw.audioType,
        audio = audioData,
        isMe = raw.isMe,
        unreadMarker = raw.unreadMarker
      )
    }

  private def fetchHistoryMessagesInternal(
      connection: Connection,
      roomId: RoomId,
      currentUserId: UserId,
      offset: Long,
      size: Long,
      lastReadSeq: Long
  ): Seq[RawMessage] =
    val sql =
      """
        SELECT * FROM (
          SELECT
              m.room_id,
              m.seq,
              m.message_type,
              m.message_text,
              m.encrypted_dek,
              m.nonce,
              m.created_at,
              CAST(u.user_canonical_id AS text) AS string_user_id,
              u.name,
              i.content_type AS image_type,
              i.data         AS image_data,
              a.content_type AS audio_type,
              a.data         AS audio_data
          FROM happyfarm.messages m
          LEFT JOIN happyfarm.users u ON m.user_canonical_id = u.user_canonical_id
          LEFT JOIN happyfarm.image_blobs i ON m.image_id = i.image_id
          LEFT JOIN happyfarm.audio_blobs a ON m.audio_id = a.audio_id
          WHERE m.room_id = ?
            AND m.seq <= ?
          ORDER BY m.seq DESC -- Get the LATEST (size) messages
          LIMIT ?
        ) AS sub
        ORDER BY seq ASC    -- Re-sort so the UI sees it the correct chronological order
      """.stripMargin

    Using.resource(connection.prepareStatement(sql)) { ps =>
      ps.setObject(1, roomId.toUUID)
      ps.setLong(2, offset)
      ps.setLong(3, size)

      Using.resource(ps.executeQuery()) { rs =>
        val buf = Vector.newBuilder[RawMessage]

        while rs.next() do
          val userId = rs.getString("string_user_id")
          buf += RawMessage(
            roomId = rs.getObject("room_id", classOf[UUID]),
            userId = userId,
            userName = Some(rs.getString("name")),
            seq = rs.getLong("seq"),
            createdAt = formatter.format(rs.getTimestamp("created_at").toInstant),
            `type` = MessageType.valueOf(rs.getString("message_type")),
            encryptedText = Option(rs.getBytes("message_text")),
            encryptedDek = Option(rs.getBytes("encrypted_dek")),
            nonce = Option(rs.getBytes("nonce")),
            imageType = Option(rs.getString("image_type")),
            image = Option(rs.getBytes("image_data")),
            audioType = Option(rs.getString("audio_type")),
            audio = Option(rs.getBytes("audio_data")),
            isMe = currentUserId.toString == userId,
            unreadMarker = rs.getLong("seq") > lastReadSeq
          )

        buf.result()
      }
    }

  private def getLastReadSeq(
      connection: Connection,
      roomId: UUID,
      currentUserId: UUID
  ): Long =
    val sql =
      s"""
         SELECT rm.last_read_seq
         FROM happyfarm.room_members rm
         WHERE rm.room_id = ? AND rm.user_canonical_id = ?
         """.stripMargin

    Using.resource(connection.prepareStatement(sql)) { ps =>
      ps.setObject(1, roomId)
      ps.setObject(2, currentUserId)

      Using.resource(ps.executeQuery()) { rs =>
        if rs.next() then rs.getLong("last_read_seq")
        else 0
      }
    }

  private def resolvePrivateChatTitle(
      connection: Connection,
      roomId: UUID,
      currentUserId: UUID
  ): String =
    val sql =
      s"""
           SELECT u.name
           FROM happyfarm.room_members rm
           JOIN happyfarm.users u
           ON u.user_canonical_id = rm.user_canonical_id
           WHERE rm.room_id = ? AND rm.user_canonical_id <> ?
           LIMIT 1;
           """.stripMargin

    Using.resource(connection.prepareStatement(sql)) { ps =>
      ps.setObject(1, roomId)
      ps.setObject(2, currentUserId)

      Using.resource(ps.executeQuery()) { rs =>
        if rs.next() then rs.getString("name")
        else "<unknown>"
      }
    }

  private def generateToken(userCanonicalId: UUID): AuthIO[Token] =
    val token       = CommonUtils.nextToken()
    val hashedToken = CommonUtils.base64EncodeSHA256(token)
    db { connection =>
      val sql =
        s"""
           INSERT INTO happyfarm.access_tokens
            (user_canonical_id, token_hash, expires_at)
           VALUES (?, ?, now() + interval '3 months')
           """.stripMargin

      Using.resource(connection.prepareStatement(sql)) { preparedStatement =>
        preparedStatement.setObject(1, userCanonicalId)
        preparedStatement.setString(2, hashedToken)

        /*
         * INSERT without RETURNING does not create a 'ResultSet' object. Therefore,
         * it does not have to be guarded by its own resource management.
         */
        preparedStatement.executeUpdate()
      }
    }.flatMap { result =>
      if result == 1 then ZIO.succeed(token)
      else ZIO.fail(TokenInsertionFailed)
    }.mapError(_ => AuthUnknownServerError)

  private def db[RESULT](thunk: Connection => RESULT): Task[RESULT] =
    ZIO.attemptBlocking(
      Using.Manager { use =>
        val connection = use(connectionProvider())
        thunk(connection)
      }.get
    )

  private val formatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

  /*
   * NOTE: We compare UUIDs as STRINGS here to stay consistent with PostgreSQL.
   * Java's UUID.compareTo treats the most significant bits as a SIGNED 128-bit integer.
   * This causes UUIDs starting with 8-F (where the leading bit is 1) to be seen as
   * "negative" and thus "smaller" than UUIDs starting with 0-7.
   *
   * PostgreSQL treats UUIDs as UNSIGNED bytes (lexicographical order), so 'e...' > '7...'.
   * Using .toString.compareTo ensures Scala and the DB's CHECK constraints agree.
   */
  private def orderUUIDs(uuid: UUID, anotherUuid: UUID): (UUID, UUID) =
    if uuid.toString.compareTo(anotherUuid.toString) < 0 then (uuid, anotherUuid)
    else (anotherUuid, uuid)

object PostgresHappyFarmRepository:
  def apply(
      connectionProvider: DataSource,
      encryptionService: EncryptionService
  ): PostgresHappyFarmRepository =
    new PostgresHappyFarmRepository(
      () => connectionProvider.getConnection,
      encryptionService
    )

  case class RawMessage(
      roomId: UUID,
      userId: String,
      userName: Option[String] = None,
      seq: Long,
      createdAt: String,
      `type`: MessageType,
      // Text-specific encrypted data
      encryptedText: Option[Array[Byte]],
      encryptedDek: Option[Array[Byte]],
      nonce: Option[Array[Byte]],
      // Image/Audio blobs
      imageType: Option[String],
      image: Option[Array[Byte]],
      audioType: Option[String],
      audio: Option[Array[Byte]],
      isMe: Boolean = false,
      unreadMarker: Boolean = false
  )

  case class RawChatRoomOverview(
      id: UUID,
      title: String,
      lastReadSeq: Long,
      maybeLatestMessage: Option[RawMessage]
  )
