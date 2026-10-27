package org.churchpresenter.signage.core

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseTest {
    private val index = checkNotNull(javaClass.getResource("/fixtures/signage-release-v1.json")).readText()
    private val keys: KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val publicKey = Base64.getEncoder().encodeToString(keys.public.encoded)

    private fun sign(text: String, pair: KeyPair = keys): String = Base64.getEncoder().encodeToString(
        Signature.getInstance("Ed25519").run {
            initSign(pair.private)
            update(text.toByteArray())
            sign()
        },
    )

    @Test
    fun aSignedIndexIsBelieved() {
        val release = assertNotNull(ReleaseSigning.verify(index, sign(index), publicKey))
        assertEquals("0.2.0", release.version)
        assertEquals("2".repeat(64), release.assets.getValue(Platform.LINUX_X64).sha256)
    }

    @Test
    fun aChangedIndexIsNot() {
        val signature = sign(index)
        assertNull(ReleaseSigning.verify(index.replace("2222", "3333"), signature, publicKey))
    }

    @Test
    fun anotherKeysSignatureIsNot() {
        val other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        assertNull(ReleaseSigning.verify(index, sign(index, other), publicKey))
        assertNull(ReleaseSigning.verify(index, "not base64!", publicKey))
        assertNull(ReleaseSigning.verify(index, sign(index), "not a key"))
    }

    @Test
    fun theBuiltInKeyIsAnEd25519Key() {
        // A well-formed key: verifying merely fails, rather than the key failing to load.
        assertNull(ReleaseSigning.verify(index, sign(index)))
        assertTrue(Base64.getDecoder().decode(ReleaseSigning.PUBLIC_KEY).size == ED25519_SPKI_BYTES)
    }

    @Test
    fun versionsCompareNumberByNumber() {
        assertTrue(compareVersions("0.10.0", "0.9.1") > 0)
        assertTrue(compareVersions("0.1.0", "0.2.0") < 0)
        assertEquals(0, compareVersions("1.0", "1.0.0"))
        assertEquals(0, compareVersions("1.2.0-rc1", "1.2.0"))
    }

    private companion object {
        const val ED25519_SPKI_BYTES = 44
    }
}
