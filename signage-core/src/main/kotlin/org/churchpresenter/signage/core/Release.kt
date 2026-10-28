package org.churchpresenter.signage.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * `signage-release.json`, published with every release beside the bundles: which player version it
 * is, the protocol it speaks, and each platform's bundle. Its Ed25519 signature,
 * `signage-release.json.sig`, is what ChurchPresenter and the player trust; a bundle is trusted
 * because it hashes to its entry here.
 */
@Serializable
data class SignageRelease(
    val version: String,
    val protocolMin: Int,
    val protocolMax: Int,
    val assets: Map<Platform, ReleaseAsset>,
    /**
     * The release's `install.sh`, which a player's machine runs as root (`--system-only`) when Update is
     * pressed, only if it hashes to this; null in releases from before.
     */
    val setup: ReleaseAsset? = null,
) {
    companion object {
        const val FILE = "signage-release.json"
        const val SIGNATURE_FILE = "signage-release.json.sig"
    }
}

@Serializable
data class ReleaseAsset(val name: String, val size: Long, val sha256: String)

/** Checks a release index against the key the release workflow signs with. */
object ReleaseSigning {
    /**
     * The public half of the repo secret `SIGNAGE_SIGNING_KEY`, as X.509 SubjectPublicKeyInfo in
     * base64. ChurchPresenter's copy of this file must carry the same key.
     */
    const val PUBLIC_KEY = "MCowBQYDK2VwAyEA/e/slJSb6siWZ4/nj/yRhgo80F8ZdTyTxBo9VDWf7aA="

    /** The index [text] (exactly as published) if [signatureBase64] is its signature by [publicKey]; else null. */
    fun verify(text: String, signatureBase64: String, publicKey: String = PUBLIC_KEY): SignageRelease? {
        val good = try {
            val key = KeyFactory.getInstance(ALGORITHM).generatePublic(X509EncodedKeySpec(decode(publicKey)))
            Signature.getInstance(ALGORITHM).run {
                initVerify(key)
                update(text.toByteArray(Charsets.UTF_8))
                verify(decode(signatureBase64.trim()))
            }
        } catch (_: GeneralSecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
        if (!good) return null
        return try {
            SignageJson.decodeFromString(SignageRelease.serializer(), text)
        } catch (_: SerializationException) {
            null
        }
    }

    private fun decode(base64: String): ByteArray = Base64.getDecoder().decode(base64)

    private const val ALGORITHM = "Ed25519"
}

/**
 * Orders dotted versions number by number (`0.10.0` after `0.9.1`); anything after a `-` is ignored.
 * Negative when [a] is older than [b].
 */
fun compareVersions(a: String, b: String): Int {
    val left = a.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    val right = b.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(left.size, right.size)) {
        val diff = left.getOrElse(i) { 0 }.compareTo(right.getOrElse(i) { 0 })
        if (diff != 0) return diff
    }
    return 0
}
