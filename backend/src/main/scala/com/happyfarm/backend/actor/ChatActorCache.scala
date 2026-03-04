package com.happyfarm.backend.actor

import com.happyfarm.backend.persistence.HappyFarmRepository
import com.happyfarm.backend.subscriber.UserPresence
import shared.RoomId
import zio.*
import zio.cache.{Cache, Lookup}

class ChatActorCache(val cache: Cache[RoomId, ChatActorCreationError, ChatActor])

object ChatActorCache:
  def make(repo: HappyFarmRepository, userPresenceManager: UserPresence): ZIO[Any, Nothing, ChatActorCache] =
    Cache
      .make(
        capacity =
          100, // We don't expect more than 100 chat rooms for our own internal family use. But this can be configurable.
        timeToLive = 30.minutes,
        lookup = Lookup(id => makeChatActor(id, repo, userPresenceManager))
      )
      .map(actualCache => new ChatActorCache(actualCache))
