package app.cipherly.crypto

/**
 * Raised when a cryptographic operation fails. The message never contains key
 * material, plaintext or ciphertext.
 */
class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)
