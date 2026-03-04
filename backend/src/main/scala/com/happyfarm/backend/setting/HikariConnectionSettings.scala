package com.happyfarm.backend.setting

import com.typesafe.config.Config
import com.zaxxer.hikari.{ HikariConfig, HikariDataSource }
import pureconfig.{ ConfigReader, ConfigSource }

import scala.concurrent.duration.FiniteDuration

case class HikariConnectionSettings(
    minConnections: Int,
    maxConnections: Int,
    timeout: FiniteDuration
)

object HikariConnectionSettings:
  given ConfigReader[HikariConnectionSettings] = ConfigReader.derived

  def apply(config: Config): HikariConnectionSettings =
    ConfigSource.fromConfig(config).loadOrThrow[HikariConnectionSettings]

  def mkHikariDataSource(
      hikariConnectionSettings: HikariConnectionSettings,
      databaseSettings: DatabaseSettings
  ): HikariDataSource =
    val config = new HikariConfig
    config.setJdbcUrl(databaseSettings.jdbcUrl)
    config.setUsername(databaseSettings.user)
    config.setPassword(databaseSettings.password)
    config.setMinimumIdle(hikariConnectionSettings.minConnections)
    config.setMaximumPoolSize(hikariConnectionSettings.maxConnections)
    config.setConnectionTimeout(hikariConnectionSettings.timeout.toMillis)
    new HikariDataSource(config)
