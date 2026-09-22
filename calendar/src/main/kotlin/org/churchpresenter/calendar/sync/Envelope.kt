package org.churchpresenter.calendar.sync

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-GCM under the instance key, with the instance id and record id as associated data.
 * Wire form: base64 of `nonce(12) || ciphertext || tag(16)`.
 */
class Envelope(keyBytes: ByteArray) {
    init {
        require(keyBytes.size == KEY_BYTES) { "instance key must be $KEY_BYTES bytes" }
    }

    private val key = SecretKeySpec(keyBytes, "AES")

    fun seal(plaintext: ByteArray, instanceId: String, recordId: String): String {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad(instanceId, recordId))
        val sealed = cipher.doFinal(plaintext)
        return Base64.getEncoder().encodeToString(nonce + sealed)
    }

    /** The plaintext, or null for anything that does not open. Never throws. */
    fun open(box: String, instanceId: String, recordId: String): ByteArray? {
        val bytes = try {
            Base64.getDecoder().decode(box)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (bytes.size < NONCE_BYTES + TAG_BITS / Byte.SIZE_BITS) return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, NONCE_BYTES))
            cipher.updateAAD(aad(instanceId, recordId))
            cipher.doFinal(bytes, NONCE_BYTES, bytes.size - NONCE_BYTES)
        } catch (_: GeneralSecurityException) {
            null
        }
    }

    private fun aad(instanceId: String, recordId: String): ByteArray = "$instanceId/$recordId".toByteArray()

    companion object {
        const val KEY_BYTES = 32
        private const val NONCE_BYTES = 12
        private const val TAG_BITS = 128
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private val random = SecureRandom()

        fun newKey(): ByteArray = ByteArray(KEY_BYTES).also(random::nextBytes)

        /** The key as settings and the QR carry it. URL-safe, no padding, so it survives a query string. */
        fun encodeKey(key: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(key)

        fun decodeKey(text: String): ByteArray? = try {
            Base64.getUrlDecoder().decode(text).takeIf { it.size == KEY_BYTES }
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
