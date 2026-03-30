package com.happyfarm.backend.persistence

import com.happyfarm.backend.persistence.EncryptionError.{ DecryptionFailed, EncryptionFailed }
import com.happyfarm.backend.setting.EncryptionSettings

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.{ GCMParameterSpec, SecretKeySpec }
import java.util.Base64
import scala.util.Try

class AesGcmEncryptionService(kekBase64: String) extends EncryptionService:
  private val TEXT_ENCRYPTION_ALGORITHM =
    "AES/GCM/NoPadding" // GCM is a "Streaming" mode. It is highly efficient for arbitrary lengths of text
  private val DEK_ENCRYPTION_ALGORITHM =
    "AES/ECB/PKCS5Padding" // ECB is the simplest form of encryption for an already randomized key
  private val KEY_ALGORITHM = "AES"
  private val TAG_BIT_LEN   = 128
  private val NONCE_LEN     = 12 // 96 bits is the standard for GCM
  private val DEK_LEN       = 32 // 256 bits for AES-256

  private val random = new SecureRandom()

  private val kekBytes = Base64.getDecoder.decode(kekBase64)

  private val masterKey = new SecretKeySpec(kekBytes, KEY_ALGORITHM)

  override def encrypt(plaintext: String): Either[EncryptionError, (Array[Byte], Array[Byte], Array[Byte])] =
    Try {
      val data = plaintext.getBytes("UTF-8")

      // Generate a random DEK and a random Nonce
      val dekBytes = new Array[Byte](DEK_LEN)
      val nonce    = new Array[Byte](NONCE_LEN)
      random.nextBytes(dekBytes)
      random.nextBytes(nonce)

      val dekSpec = new SecretKeySpec(dekBytes, KEY_ALGORITHM)

      // Encrypt the plaintext with the DEK
      val cipher = Cipher.getInstance(TEXT_ENCRYPTION_ALGORITHM)
      cipher.init(Cipher.ENCRYPT_MODE, dekSpec, new GCMParameterSpec(TAG_BIT_LEN, nonce))
      val encryptedText = cipher.doFinal(data)

      // Wrap the DEK with the KEK
      val wrappingCipher = Cipher.getInstance(DEK_ENCRYPTION_ALGORITHM)
      wrappingCipher.init(Cipher.ENCRYPT_MODE, masterKey)
      val encryptedDek = wrappingCipher.doFinal(dekBytes)

      (encryptedText, encryptedDek, nonce)
    }.toEither.left.map(throwable => EncryptionFailed(throwable.getMessage))

  override def decrypt(
      encryptedText: Array[Byte],
      encryptedDek: Array[Byte],
      nonce: Array[Byte]
  ): Either[EncryptionError, String] =
    Try {
      // Unwrap the DEK using the KEK
      val wrappingCipher = Cipher.getInstance(DEK_ENCRYPTION_ALGORITHM)
      wrappingCipher.init(Cipher.DECRYPT_MODE, masterKey)
      val dekBytes = wrappingCipher.doFinal(encryptedDek)
      val dekSpec  = new SecretKeySpec(dekBytes, KEY_ALGORITHM)

      // Decrypt the message with the DEK and Nonce
      val cipher = Cipher.getInstance(TEXT_ENCRYPTION_ALGORITHM)
      cipher.init(Cipher.DECRYPT_MODE, dekSpec, new GCMParameterSpec(TAG_BIT_LEN, nonce))

      new String(cipher.doFinal(encryptedText), "UTF-8")
    }.toEither.left.map(throwable => DecryptionFailed(throwable.getMessage))

object AesGcmEncryptionService:
  def apply(encryptionSettings: EncryptionSettings): AesGcmEncryptionService =
    new AesGcmEncryptionService(encryptionSettings.kekBase64)
