package org.churchpresenter.server

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.churchpresenter.settings.utils.Constants
import java.io.File
import java.math.BigInteger
import java.nio.file.Files
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class SslKeystoreMigrationTest {

    private val password = Constants.SSL_KEYSTORE_PASSWORD.toCharArray()
    private val serverAlias = Constants.SSL_KEY_ALIAS
    private val caAlias = "church-presenter-ca"
    private lateinit var originalHome: String

    private val baseDir get() = SslCertificateManager.caCertFile.parentFile

    @BeforeTest
    fun isolateHome() {
        originalHome = System.getProperty("user.home")
        System.setProperty("user.home", Files.createTempDirectory("ssl-migrate").toString())
    }

    @AfterTest
    fun restoreHome() {
        System.setProperty("user.home", originalHome)
    }

    private fun rsaKeyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.genKeyPair()

    private fun ecKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.genKeyPair()

    private fun selfSigned(keys: KeyPair, notAfter: Instant, algorithm: String): X509Certificate {
        val name = X500Name("CN=Old CA")
        val now = Instant.now()
        val holder = JcaX509v3CertificateBuilder(
            name, BigInteger.valueOf(now.toEpochMilli()), Date.from(now.minus(1, ChronoUnit.DAYS)),
            Date.from(notAfter), name, keys.public,
        ).build(JcaContentSignerBuilder(algorithm).build(keys.private))
        return JcaX509CertificateConverter().getCertificate(holder)
    }

    private fun writeKeyStore(file: File, alias: String, keys: KeyPair?, cert: X509Certificate) {
        val ks = KeyStore.getInstance("JKS").apply { load(null, password) }
        if (keys != null) ks.setKeyEntry(alias, keys.private, password, arrayOf(cert))
        else ks.setCertificateEntry(alias, cert)
        file.outputStream().use { ks.store(it, password) }
    }

    private fun caKeyAlgorithm(): String {
        val ks = KeyStore.getInstance("JKS").apply { File(baseDir, "ca.jks").inputStream().use { load(it, password) } }
        return ks.getKey(caAlias, password).algorithm
    }

    private fun serverChain(host: String) =
        SslCertificateManager.getOrCreateKeyStore(host).getCertificateChain(serverAlias).map { it as X509Certificate }

    @Test
    fun `an RSA CA from an older release is replaced by an EC one`() {
        val rsa = rsaKeyPair()
        writeKeyStore(
            File(baseDir, "ca.jks"), caAlias, rsa,
            selfSigned(rsa, Instant.now().plus(900, ChronoUnit.DAYS), "SHA256withRSA"),
        )

        val chain = serverChain("127.0.0.1")

        assertEquals("EC", caKeyAlgorithm())
        chain[0].verify(chain[1].publicKey)
    }

    @Test
    fun `a CA about to expire is renewed before phones stop trusting it`() {
        val ec = ecKeyPair()
        val expiring = selfSigned(ec, Instant.now().plus(5, ChronoUnit.DAYS), "SHA256withECDSA")
        writeKeyStore(File(baseDir, "ca.jks"), caAlias, ec, expiring)

        val chain = serverChain("127.0.0.1")

        assertNotEquals(expiring.serialNumber, chain[1].serialNumber)
    }

    @Test
    fun `a CA keystore holding only a certificate is regenerated`() {
        val ec = ecKeyPair()
        writeKeyStore(
            File(baseDir, "ca.jks"), caAlias, null,
            selfSigned(ec, Instant.now().plus(900, ChronoUnit.DAYS), "SHA256withECDSA"),
        )

        val chain = serverChain("127.0.0.1")

        chain[0].verify(chain[1].publicKey)
        assertEquals("EC", caKeyAlgorithm())
    }

    @Test
    fun `an RSA server certificate is reissued as EC for the same host`() {
        serverChain("10.0.0.7")
        val rsa = rsaKeyPair()
        writeKeyStore(
            File(baseDir, "server.jks"), serverAlias, rsa,
            selfSigned(rsa, Instant.now().plus(900, ChronoUnit.DAYS), "SHA256withRSA"),
        )

        val chain = serverChain("10.0.0.7")

        assertEquals("EC", chain[0].publicKey.algorithm)
        assertContains(chain[0].subjectAlternativeNames.orEmpty().mapNotNull { it[1] as? String }, "10.0.0.7")
    }

    @Test
    fun `a server keystore without the server entry is reissued`() {
        serverChain("10.0.0.8")
        val ec = ecKeyPair()
        writeKeyStore(
            File(baseDir, "server.jks"), "someone-else", ec,
            selfSigned(ec, Instant.now().plus(900, ChronoUnit.DAYS), "SHA256withECDSA"),
        )

        val chain = serverChain("10.0.0.8")

        assertNotNull(chain.firstOrNull())
        chain[0].verify(chain[1].publicKey)
    }
}
