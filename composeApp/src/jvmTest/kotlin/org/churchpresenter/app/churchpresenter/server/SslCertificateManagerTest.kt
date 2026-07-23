package org.churchpresenter.app.churchpresenter.server

import org.churchpresenter.app.churchpresenter.utils.Constants
import java.nio.file.Files
import java.security.PrivateKey
import java.security.cert.X509Certificate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The private PKI that lets phones trust the companion server over HTTPS.
 *
 * A self-signed CA is installed on the phone once; every server cert is then signed by that CA and
 * carries a SAN for the address the phone connects to, so the browser accepts the connection. The
 * invariants that keep that chain working are what matters here: the key must be ECDSA (Apple ATS
 * rejects RSA-only forward secrecy), the server cert must actually be signed by the CA and name the
 * host it is served on, and the CA must be stable across calls — regenerating it would silently
 * un-trust every phone that already installed the old one.
 *
 * Real crypto, but fast (P-256) and deterministic in structure — assertions are on algorithm, chain
 * shape, signature validity, SAN membership and fingerprint format, never on exact key bytes.
 *
 * The manager is a JVM singleton that resolves its keystore directory from user.home once, at first
 * class-load. No other test references it, so redirecting user.home to a temp dir before the first
 * call keeps it off the developer's real ~/.churchpresenter. baseDir latches to that first temp dir
 * for the rest of the JVM, which is why these tests don't assume an empty starting state — they
 * assert generation and reuse, both order-independent.
 */
class SslCertificateManagerTest {

    private val alias = Constants.SSL_KEY_ALIAS
    private val password = Constants.SSL_KEYSTORE_PASSWORD.toCharArray()

    private lateinit var originalHome: String

    @BeforeTest
    fun isolateHome() {
        originalHome = System.getProperty("user.home")
        System.setProperty("user.home", Files.createTempDirectory("ssl-home").toString())
    }

    @AfterTest
    fun restoreHome() {
        System.setProperty("user.home", originalHome)
    }

    private fun serverCertFor(host: String): X509Certificate {
        val ks = SslCertificateManager.getOrCreateKeyStore(host)
        return ks.getCertificateChain(alias)[0] as X509Certificate
    }

    private fun sanNamesOf(cert: X509Certificate): Set<String> =
        cert.subjectAlternativeNames.orEmpty().mapNotNull { it.getOrNull(1) as? String }.toSet()

    @Test
    fun `the server keystore holds an EC key and a server-then-CA chain`() {
        val ks = SslCertificateManager.getOrCreateKeyStore("127.0.0.1")
        val key = ks.getKey(alias, password) as PrivateKey
        assertEquals("EC", key.algorithm, "Apple ATS requires ECDSA; an RSA key would be rejected by iOS")
        assertEquals(2, ks.getCertificateChain(alias).size, "the chain must present both server cert and CA")
    }

    @Test
    fun `the server certificate is signed by the CA`() {
        val chain = SslCertificateManager.getOrCreateKeyStore("127.0.0.1").getCertificateChain(alias)
        val server = chain[0] as X509Certificate
        val ca = chain[1] as X509Certificate
        // verify() throws on a bad signature; a passing call is the whole trust chain in one assertion.
        server.verify(ca.publicKey)
    }

    @Test
    fun `the certificate names the requested IP plus the loopback aliases`() {
        val sans = sanNamesOf(serverCertFor("192.168.1.50"))
        assertContains(sans, "192.168.1.50", "the cert must be valid for the address the phone dials")
        assertContains(sans, "127.0.0.1", "loopback must stay valid so the desktop can reach its own server")
        assertContains(sans, "localhost")
    }

    @Test
    fun `a hostname server is carried as a DNS name`() {
        val sans = sanNamesOf(serverCertFor("worship.local"))
        assertContains(sans, "worship.local", "a non-IP host must be a dNSName SAN or TLS validation fails")
    }

    @Test
    fun `the CA is reused across calls so already-trusted phones stay trusted`() {
        SslCertificateManager.getOrCreateKeyStore("127.0.0.1")
        val firstCa = assertNotNull(SslCertificateManager.getCaCertBytes())
        val firstPrint = SslCertificateManager.getCaCertFingerprint()
        SslCertificateManager.getOrCreateKeyStore("10.0.0.9")   // different host, same CA
        val secondCa = assertNotNull(SslCertificateManager.getCaCertBytes())
        assertTrue(firstCa.contentEquals(secondCa), "regenerating the CA would un-trust every installed phone")
        assertEquals(firstPrint, SslCertificateManager.getCaCertFingerprint())
    }

    @Test
    fun `the CA fingerprint is a colon-separated SHA-256 hex string`() {
        SslCertificateManager.getOrCreateKeyStore("127.0.0.1")
        val print = assertNotNull(SslCertificateManager.getCaCertFingerprint())
        // SHA-256 = 32 bytes => 32 hex pairs joined by 31 colons; users read this to verify the cert.
        assertTrue(
            print.matches(Regex("^([0-9A-F]{2}:){31}[0-9A-F]{2}$")),
            "fingerprint must be the readable XX:XX:… form, was: $print"
        )
    }

    @Test
    fun `the CA is exported as a PEM certificate`() {
        SslCertificateManager.getOrCreateKeyStore("127.0.0.1")
        val pem = assertNotNull(SslCertificateManager.getCaCertPem())
        assertTrue(
            pem.trimStart().startsWith("-----BEGIN CERTIFICATE-----"),
            "Android network-security-config needs a standard PEM block"
        )
    }
}
