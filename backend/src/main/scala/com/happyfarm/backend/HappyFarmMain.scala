package com.happyfarm.backend

import com.happyfarm.backend.actor.ChatActorCache
import com.happyfarm.backend.handler.{ AuthHandler, ChatHandler }
import com.happyfarm.backend.persistence.PostgresHappyFarmRepository
import com.happyfarm.backend.setting.{ DatabaseSettings, HikariConnectionSettings }
import com.happyfarm.backend.subscriber.UserPresenceManager
import com.typesafe.config.{ Config, ConfigFactory }
import com.zaxxer.hikari.HikariDataSource
import zio.http.*
import zio.{ ZIO, ZIOAppDefault }

object HappyFarmMain extends ZIOAppDefault:
  private val rootConfig: Config = ConfigFactory.load()

  private val hikariSettings: HikariConnectionSettings = HikariConnectionSettings(
    rootConfig.getConfig(CONNECTION_POOL_CONFIG_PATH)
  )
  private val databaseSettings: DatabaseSettings = DatabaseSettings(
    rootConfig.getConfig(DATABASE_CONFIG_PATH)
  )
  private val hikariDatasource: HikariDataSource =
    HikariConnectionSettings.mkHikariDataSource(hikariSettings, databaseSettings)
  private val repository: PostgresHappyFarmRepository = PostgresHappyFarmRepository(hikariDatasource)

  private def staticRoutes: Routes[Any, Nothing] = Routes(
    Method.GET / "" -> Handler.fromResource("public/index.html"),

    Method.GET / "public" / trailing -> Handler.fromFunctionHandler[(Path, Request)] {
      case (path, _) =>
        Handler.fromResource(s"public/${path.encode}")
    }
  ).sandbox @@ HandlerAspect.requestLogging()

  override def run: ZIO[Any, Throwable, Nothing] =
    ZIO.scoped {
      for
        userPresenceManager <- UserPresenceManager.make
        chatActorCache      <- ChatActorCache.make(repository, userPresenceManager)

        authRoutes = AuthHandler(repository).routes
        chatRoutes = ChatHandler(repository, chatActorCache, userPresenceManager).routes

        allRoutes = authRoutes ++ chatRoutes ++ staticRoutes

        server <- Server.serve(allRoutes).provide(Server.default)
      yield server
    }
