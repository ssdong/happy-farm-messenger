package com.happyfarm.backend.setting

import com.typesafe.config.Config
import pureconfig.{ ConfigReader, ConfigSource }

case class EncryptionSettings(
    kekBase64: String
)

object EncryptionSettings:
  given ConfigReader[EncryptionSettings] = ConfigReader.derived

  def apply(config: Config): EncryptionSettings =
    ConfigSource.fromConfig(config).loadOrThrow[EncryptionSettings]
