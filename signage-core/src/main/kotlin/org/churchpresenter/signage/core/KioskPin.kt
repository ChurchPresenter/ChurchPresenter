package org.churchpresenter.signage.core

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The PIN a screen's touch gesture asks for, as the manifest carries it: salted and stretched, so the
 * PIN itself is never stored on the player or sent to it. ChurchPresenter makes it; the player checks
 * against it.
 *
 * Format: `pbkdf2:<iterations>:<salt hex>:<hash hex>`.
 */
object KioskPin {
    const val MIN_DIGITS = 4
    const val MAX_DIGITS = 8
    private const val PREFIX = "pbkdf2"
    private const val ITERATIONS = 100_000
    private const val SALT_BYTES = 16
    private const val HASH_BITS = 256
    private const val PARTS = 4
    private const val HEX = 16

    /** Whether [pin] is one a screen can take: 4 to 8 digits. */
    fun isValid(pin: String): Boolean = pin.length in MIN_DIGITS..MAX_DIGITS && pin.all { it in '0'..'9' }

    fun hash(pin: String, salt: ByteArray = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)): String {
        require(isValid(pin)) { "a PIN is $MIN_DIGITS to $MAX_DIGITS digits" }
        return "$PREFIX:$ITERATIONS:${salt.hex()}:${derive(pin, salt, ITERATIONS).hex()}"
    }

    /** Whether [pin] is the one [stored] was made from; false for anything that is not a hash of ours. */
    fun matches(pin: String, stored: String): Boolean {
        val parts = stored.split(':')
        val iterations = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 }
        val salt = parts.getOrNull(2)?.unhex()
        val expected = parts.getOrNull(3)?.unhex()
        val ours = parts.size == PARTS && parts[0] == PREFIX && isValid(pin)
        return ours && iterations != null && salt != null && expected != null &&
            MessageDigest.isEqual(derive(pin, salt, iterations), expected)
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, iterations, HASH_BITS)).encoded

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

    private fun String.unhex(): ByteArray? =
        if (length % 2 != 0 || any { it !in '0'..'9' && it !in 'a'..'f' }) null
        else chunked(2).map { it.toInt(HEX).toByte() }.toByteArray()
}
