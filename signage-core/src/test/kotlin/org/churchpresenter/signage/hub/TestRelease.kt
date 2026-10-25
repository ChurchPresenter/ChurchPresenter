package org.churchpresenter.signage.hub

import org.churchpresenter.signage.core.sha256Hex
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

/** A release signed with a key made for the test, served from memory. */
class TestRelease(val version: String = "26.0.9", val build: ByteArray = "the linux build".toByteArray()) {
    val keys: KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    val publicKey: String = Base64.getEncoder().encodeToString(keys.public.encoded)
    val assetName = "ChurchPresenter-Signage-$version-LINUX-x64.tar.gz"

    fun index(sha256: String = sha256Hex(build), size: Long = build.size.toLong()) =
        """{"version":"$version","protocolMin":1,"protocolMax":1,"assets":""" +
            """{"linux_x64":{"name":"$assetName","size":$size,"sha256":"$sha256"}}}"""

    fun sign(text: String, pair: KeyPair = keys): String = Base64.getEncoder().encodeToString(
        Signature.getInstance("Ed25519").run {
            initSign(pair.private)
            update(text.toByteArray())
            sign()
        },
    )

    /** The files at the release's base URL, as GitHub serves them. */
    fun files(index: String = index(), signature: String = sign(index)): Map<String, ByteArray> = mapOf(
        "signage-release.json" to index.toByteArray(),
        "signage-release.json.sig" to signature.toByteArray(),
        assetName to build,
    )
}
