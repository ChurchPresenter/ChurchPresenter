package org.churchpresenter.companionserver

import org.churchpresenter.settings.utils.Constants
import java.io.File
import java.nio.file.Files
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.security.PrivateKey
import java.security.cert.X509Certificate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotEquals
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

    private val baseDir get() = SslCertificateManager.caCertFile.parentFile

    @Test
    fun `the server certificate is reused for a host already issued one`() {
        val first = serverCertFor("192.168.1.60")
        val second = serverCertFor("192.168.1.60")

        assertEquals(first.serialNumber, second.serialNumber, "reissuing on every start would churn the keystore")
    }

    @Test
    fun `a new address gets a certificate naming it`() {
        val first = serverCertFor("192.168.1.61")
        val second = serverCertFor("192.168.1.62")

        assertNotEquals(first.serialNumber, second.serialNumber)
        assertContains(sanNamesOf(second), "192.168.1.62")
    }

    @Test
    fun `moving back to a previous address issues a certificate for it again`() {
        serverCertFor("192.168.1.63")
        serverCertFor("192.168.1.64")

        assertContains(sanNamesOf(serverCertFor("192.168.1.63")), "192.168.1.63")
    }

    @Test
    fun `an unreadable CA keystore is regenerated rather than locking the server out`() {
        SslCertificateManager.getOrCreateKeyStore("127.0.0.1")
        val before = assertNotNull(SslCertificateManager.getCaCertFingerprint())
        File(baseDir, "ca.jks").writeBytes(byteArrayOf(0, 1, 2, 3))

        val chain = SslCertificateManager.getOrCreateKeyStore("127.0.0.1").getCertificateChain(alias)

        val after = assertNotNull(SslCertificateManager.getCaCertFingerprint())
        assertNotEquals(before, after, "a corrupt CA has to be replaced, not reused")
        (chain[0] as X509Certificate).verify((chain[1] as X509Certificate).publicKey)
    }

    @Test
    fun `an unreadable server keystore is regenerated under the same CA`() {
        SslCertificateManager.getOrCreateKeyStore("192.168.1.65")
        val caBefore = assertNotNull(SslCertificateManager.getCaCertBytes())
        File(baseDir, "server.jks").writeBytes(byteArrayOf(0, 1, 2, 3))

        val sans = sanNamesOf(serverCertFor("192.168.1.65"))

        assertContains(sans, "192.168.1.65")
        assertTrue(
            caBefore.contentEquals(assertNotNull(SslCertificateManager.getCaCertBytes())),
            "only the server cert was damaged; the phones' trust anchor must survive",
        )
    }

    @Test
    fun `regenerating the CA also replaces the server certificate it signed`() {
        serverCertFor("192.168.1.66")
        File(baseDir, "ca.jks").delete()

        val chain = SslCertificateManager.getOrCreateKeyStore("192.168.1.66").getCertificateChain(alias)

        (chain[0] as X509Certificate).verify((chain[1] as X509Certificate).publicKey)
    }

    // ── Migration away from what an older build left behind ─────────────────────
    //
    // These reach the two branches the tests above never do: a keystore that is readable, holds the
    // right alias, and is still in date — but is RSA, or is about to expire. Both must be replaced,
    // and the reasons are different. RSA is replaced because Apple's App Transport Security refuses
    // an RSA-only chain, so a phone that trusted the old CA still cannot open the page. A cert
    // inside its renewal margin is replaced so it never expires mid-service.

    /** Writes a JKS at [file] holding a self-signed cert under [alias], with the given key and life. */
    private fun writeSelfSignedKeyStore(
        file: File,
        alias: String,
        algorithm: String,
        validForDays: Long,
        host: String? = null,
    ) {
        val gen = KeyPairGenerator.getInstance(algorithm)
        if (algorithm == "RSA") gen.initialize(RSA_KEY_BITS) else gen.initialize(ECGenParameterSpec("secp256r1"))
        val pair = gen.generateKeyPair()
        val now = Instant.now()
        val name = X500Name("CN=Stale ChurchPresenter, O=ChurchPresenter, C=US")
        val builder = JcaX509v3CertificateBuilder(
            name,
            BigInteger.valueOf(1),
            Date.from(now.minus(1, ChronoUnit.DAYS)),
            Date.from(now.plus(validForDays, ChronoUnit.DAYS)),
            name,
            pair.public,
        )
        host?.let {
            builder.addExtension(
                Extension.subjectAlternativeName, false,
                GeneralNames(GeneralName(GeneralName.iPAddress, it)),
            )
        }
        val signerAlg = if (algorithm == "RSA") "SHA256withRSA" else "SHA256withECDSA"
        val holder = builder.build(JcaContentSignerBuilder(signerAlg).build(pair.private))
        val cert = JcaX509CertificateConverter().getCertificate(holder)
        val ks = KeyStore.getInstance("JKS").apply { load(null, null) }
        ks.setKeyEntry(alias, pair.private, password, arrayOf<java.security.cert.Certificate>(cert))
        file.outputStream().use { ks.store(it, password) }
    }

    @Test
    fun `an RSA CA left by an older build is replaced with an EC one`() {
        SslCertificateManager.getOrCreateKeyStore("127.0.0.1")
        writeSelfSignedKeyStore(File(baseDir, "ca.jks"), CA_ALIAS, "RSA", validForDays = 3650)

        val chain = SslCertificateManager.getOrCreateKeyStore("127.0.0.1").getCertificateChain(alias)

        assertEquals(
            "EC", (chain[1] as X509Certificate).publicKey.algorithm,
            "an RSA-only chain is refused by Apple ATS, so the CA has to be reissued even though it is readable",
        )
        (chain[0] as X509Certificate).verify((chain[1] as X509Certificate).publicKey)
    }

    @Test
    fun `a CA about to expire is reissued rather than served until it dies`() {
        SslCertificateManager.getOrCreateKeyStore("127.0.0.1")
        writeSelfSignedKeyStore(File(baseDir, "ca.jks"), CA_ALIAS, "EC", validForDays = 5)
        val stale = assertNotNull(SslCertificateManager.getCaCertFingerprint())

        SslCertificateManager.getOrCreateKeyStore("127.0.0.1")

        assertNotEquals(
            stale, assertNotNull(SslCertificateManager.getCaCertFingerprint()),
            "five days is inside the 30-day renewal margin, so it must not be reused",
        )
    }

    @Test
    fun `an RSA server keystore left by an older build is replaced with an EC one`() {
        SslCertificateManager.getOrCreateKeyStore("10.0.0.9")
        writeSelfSignedKeyStore(File(baseDir, "server.jks"), alias, "RSA", validForDays = 3650, host = "10.0.0.9")

        val chain = SslCertificateManager.getOrCreateKeyStore("10.0.0.9").getCertificateChain(alias)

        assertEquals("EC", (chain[0] as X509Certificate).publicKey.algorithm)
        assertContains(sanNamesOf(chain[0] as X509Certificate), "10.0.0.9")
    }

    @Test
    fun `a server certificate about to expire is reissued`() {
        SslCertificateManager.getOrCreateKeyStore("10.0.0.10")
        writeSelfSignedKeyStore(File(baseDir, "server.jks"), alias, "EC", validForDays = 5, host = "10.0.0.10")

        val cert = serverCertFor("10.0.0.10")

        assertTrue(
            cert.notAfter.after(Date.from(Instant.now().plus(31, ChronoUnit.DAYS))),
            "the reissued certificate has to outlive the renewal margin, or every call would reissue",
        )
    }

    @Test
    fun `a keystore holding the wrong alias is treated as unusable`() {
        SslCertificateManager.getOrCreateKeyStore("10.0.0.11")
        writeSelfSignedKeyStore(File(baseDir, "ca.jks"), "some-other-alias", "EC", validForDays = 3650)

        val chain = SslCertificateManager.getOrCreateKeyStore("10.0.0.11").getCertificateChain(alias)

        (chain[0] as X509Certificate).verify((chain[1] as X509Certificate).publicKey)
    }

    private companion object {
        const val CA_ALIAS = "church-presenter-ca"
        const val RSA_KEY_BITS = 2048
    }
}
