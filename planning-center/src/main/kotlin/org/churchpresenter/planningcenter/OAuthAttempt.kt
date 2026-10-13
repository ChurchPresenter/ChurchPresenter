package org.churchpresenter.planningcenter

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

private const val VERIFIER_BYTES = 32
private const val STATE_BYTES = 16

/**
 * The two secrets of one sign-in attempt. [state] goes out on the consent URL and must come back on
 * the callback unchanged, so a redirect the app did not start is refused (RFC 6749 §10.12).
 * [codeVerifier] stays in the app; only its S256 [codeChallenge] goes out, and the token request
 * proves it with the verifier, so a code caught on its way to the loopback callback is useless to
 * anyone else (RFC 7636). PCO lists `S256` in its `code_challenge_methods_supported`.
 */
class OAuthAttempt internal constructor(val state: String, val codeVerifier: String) {

    /** The S256 challenge for [codeVerifier]: base64url of its SHA-256, without padding. */
    val codeChallenge: String = base64Url(
        MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII)),
    )

    /** Whether a callback's [returned] state is this attempt's, compared in constant time. */
    fun matches(returned: String?): Boolean =
        returned != null && MessageDigest.isEqual(returned.toByteArray(), state.toByteArray())

    companion object {
        /** A fresh attempt: a 43-character verifier and a 22-character state, both from [random]. */
        fun create(random: SecureRandom = SecureRandom()): OAuthAttempt =
            OAuthAttempt(state = randomToken(random, STATE_BYTES), codeVerifier = randomToken(random, VERIFIER_BYTES))

        private fun randomToken(random: SecureRandom, bytes: Int): String =
            base64Url(ByteArray(bytes).also(random::nextBytes))

        private fun base64Url(bytes: ByteArray): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
