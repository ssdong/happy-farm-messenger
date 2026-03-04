package com.happyfarm.backend.setting

import com.typesafe.config.Config
import pureconfig.{ ConfigReader, ConfigSource }

case class DatabaseSettings(
    host: String,
    port: Int,
    dbname: String,
    schema: String,
    user: String,
    password: String
):
  def jdbcUrl: String = s"jdbc:postgresql://$host:$port/$dbname?currentSchema=$schema"

object DatabaseSettings:
  given ConfigReader[DatabaseSettings] = ConfigReader.derived

  def apply(config: Config): DatabaseSettings =
    ConfigSource.fromConfig(config).loadOrThrow[DatabaseSettings]
