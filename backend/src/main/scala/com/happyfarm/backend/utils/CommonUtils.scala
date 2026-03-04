package com.happyfarm.backend.utils

import java.nio.charset.StandardCharsets
import java.security.{ MessageDigest, SecureRandom }
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object CommonUtils:
  // https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html#pbkdf2
  private val iterations = 600000
  private val saltBytes  = 16
  private val keyLenBits = 256
  private val algorithm  = "PBKDF2WithHmacSHA256"

  private val randomGenerator  = new SecureRandom()
  private val base64UrlEncoder = Base64.getUrlEncoder
  private val base64Encoder    = Base64.getEncoder
  private val base64Decoder    = Base64.getDecoder

  def nextToken(): String =
    val bytes = new Array[Byte](32)
    randomGenerator.nextBytes(bytes)
    base64UrlEncoder.withoutPadding().encodeToString(bytes)

  def base64EncodeSHA256(input: String): String =
    val sha256Hash = MessageDigest
      .getInstance("SHA256")
      .digest(input.getBytes(StandardCharsets.UTF_8))

    base64UrlEncoder.withoutPadding().encodeToString(sha256Hash)

  // https://www.baeldung.com/java-password-hashing
  def hashPassword(password: String): String =
    val salt = new Array[Byte](saltBytes)
    randomGenerator.nextBytes(salt)

    // Password-Based Key Derivation Function 2
    val spec    = new PBEKeySpec(password.toCharArray, salt, iterations, keyLenBits)
    val factory = SecretKeyFactory.getInstance(algorithm)
    val key     = factory.generateSecret(spec).getEncoded
    spec.clearPassword()

    s"$algorithm$$$iterations$$${base64Encoder.encodeToString(salt)}$$${base64Encoder.encodeToString(key)}"

  def verifyPassword(password: String, storedHashedPassword: String): Boolean =
    val parts = storedHashedPassword.split("\\$", 4)
    require(parts.size == 4, "malformed password hash format")

    val alg        = parts(0)
    val iterations = parts(1).toInt
    val salt       = base64Decoder.decode(parts(2))
    val expected   = base64Decoder.decode(parts(3))

    val spec    = new PBEKeySpec(password.toCharArray, salt, iterations, keyLenBits)
    val factory = SecretKeyFactory.getInstance(alg)
    val actual  = factory.generateSecret(spec).getEncoded
    spec.clearPassword()

    MessageDigest.isEqual(actual, expected)
