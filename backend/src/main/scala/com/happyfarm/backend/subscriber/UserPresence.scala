package com.happyfarm.backend.subscriber

import shared.{ChatResponse, UserId}
import zio.{Dequeue, Hub, Ref, Scope, UIO, ZIO}

class UserPresence(val userHubs: Ref[Map[UserId, Hub[ChatResponse]]]):
  // Returns a subscription (Dequeue) if the user is online.
  def subscribe(userId: UserId): ZIO[Scope, Nothing, Option[Dequeue[ChatResponse]]] =
    userHubs.get.flatMap { hubs =>
      hubs.get(userId) match
        case Some(hub) => hub.subscribe.map(Some(_))
        case None      => ZIO.succeed(None)
    }

  def register(userId: UserId): ZIO[Any, Nothing, Unit] =
    for
      newHub <- Hub.unbounded[ChatResponse]
      _      <- userHubs.update(map => if map.contains(userId) then map else map + (userId -> newHub))
    yield ()

  def remove(userId: UserId): UIO[Unit] =
    userHubs.update(_ - userId)

  def publishToUser(userId: UserId, response: ChatResponse): ZIO[Any, Nothing, AnyVal] =
    userHubs.get.flatMap { hubs =>
      hubs.get(userId) match
        case Some(hub) => hub.publish(response)
        case None      =>
          // User is offline, nothing happens
          ZIO.unit
    }

object UserPresenceManager:
  def make: ZIO[Any, Nothing, UserPresence] =
    for
      ref          <- Ref.make(Map.empty[UserId, Hub[ChatResponse]])
      userPresence <- ZIO.succeed(new UserPresence(ref))
    yield userPresence
