package org.churchpresenter.schedule

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** The cipher a `.cps` file and the autosave are written under. */
internal object ScheduleCipher {
    private const val PASS = "ChurchPresenter-Schedule-Key-2024"
    private const val ALGO = "AES/CBC/PKCS5Padding"
    private const val KEY_ALGO = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 65536
    private const val KEY_LEN = 256
    private const val SALT = "CPScheduleSalt01" // 16-byte fixed salt

    private fun deriveKey(): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(KEY_ALGO)
        val spec = PBEKeySpec(PASS.toCharArray(), SALT.toByteArray(Charsets.UTF_8), ITERATIONS, KEY_LEN)
        val secret = factory.generateSecret(spec)
        return SecretKeySpec(secret.encoded, "AES")
    }

    fun encrypt(plainText: String): String {
        val key = deriveKey()
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(ALGO)
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        // Prepend IV to cipher bytes, then Base64-encode the whole thing
        val combined = iv + encrypted
        return Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(cipherText: String): String {
        val key = deriveKey()
        val combined = Base64.getDecoder().decode(cipherText)
        val iv = combined.copyOfRange(0, 16)
        val encrypted = combined.copyOfRange(16, combined.size)
        val cipher = Cipher.getInstance(ALGO)
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }
}
