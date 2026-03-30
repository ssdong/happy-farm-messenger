package com.happyfarm.backend.persistence

enum EncryptionError:
  case DecryptionFailed(msg: String)
  case EncryptionFailed(msg: String)

trait EncryptionService:
  // Encrypts plaintext and returns (EncryptedText, EncryptedDEK, Nonce)
  def encrypt(plaintext: String): Either[EncryptionError, (Array[Byte], Array[Byte], Array[Byte])]

  def decrypt(
      encryptedText: Array[Byte],
      encryptedDek: Array[Byte],
      nonce: Array[Byte]
  ): Either[EncryptionError, String]
