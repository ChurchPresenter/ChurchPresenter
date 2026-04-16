package org.churchpresenter.app.churchpresenter.server

import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter
import org.churchpresenter.app.churchpresenter.utils.Constants
import java.io.File
import java.io.StringWriter
import java.math.BigInteger
import java.net.InetAddress
import java.security.*
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

/**
 * Manages a two-level Private PKI for the companion server:
 *
 *   1. **CA certificate** (`~/.churchpresenter/ca.jks`) — a self-signed root CA that
 *      lives for 10 years.  Mobile devices install this once via GET /ca.crt and from
 *      that point all server certificates are automatically trusted.
 *
 *   2. **Server certificate** (`~/.churchpresenter/server.jks`) — signed by the CA,
 *      valid for ~2 years, includes an IP SAN matching the current server address.
 *      Regenerated automatically when the host/IP changes or the cert nears expiry.
 *
 * Both CA and server use **ECDSA P-256** keys with SHA-256 signatures — the algorithm
 * Apple requires for ATS forward-secrecy compliance and the preferred choice for Android.
 * Existing keystores that contain old RSA keys are automatically deleted and regenerated
 * on first start after this upgrade.
 *
 * Mobile trust flow:
 *  1. User visits  GET /ca.crt  (DER) or  GET /ca.pem  in a browser / companion app.
 *  2. iOS: Settings ▸ VPN & Device Management ▸ Trust.
 *     Android: companion app adds it as a custom trust anchor via NetworkSecurityConfig.
 *  3. All subsequent HTTPS connections to the server are accepted without errors.
 */
object SslCertificateManager {

    private val baseDir = File(System.getProperty("user.home"), ".churchpresenter").also { it.mkdirs() }
    private val caKeystoreFile     = File(baseDir, "ca.jks")
    private val serverKeystoreFile = File(baseDir, "server.jks")

    /** DER-encoded CA certificate — served at GET /ca.crt for mobile installation. */
    val caCertFile: File = File(baseDir, "ca.crt")

    private val CA_ALIAS     = "church-presenter-ca"
    private val SERVER_ALIAS = Constants.SSL_KEY_ALIAS
    private val PASSWORD     = Constants.SSL_KEYSTORE_PASSWORD.toCharArray()

    init {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns a [KeyStore] ready for Ktor's sslConnector containing the server's
     * ECDSA P-256 private key and full cert chain (server cert → CA cert).
     *
     * @param serverHost IP or hostname clients connect to; used as a SAN so the
     *   cert is valid for that address after the CA is trusted once.
     */
    fun getOrCreateKeyStore(serverHost: String = "127.0.0.1"): KeyStore {
        val (caKey, caCert) = getOrCreateCA()
        return getOrCreateServerKeyStore(caKey, caCert, serverHost)
    }

    /** Raw DER bytes of the CA cert, or null before first server start. */
    fun getCaCertBytes(): ByteArray? =
        if (caCertFile.exists()) caCertFile.readBytes() else null

    /**
     * PEM-encoded CA certificate.
     * Use this for Android Network Security Config (`res/raw/ca.pem`) or any
     * OpenSSL-compatible tool.  Returns null before the first server start.
     */
    fun getCaCertPem(): String? {
        val bytes = getCaCertBytes() ?: return null
        val sw = StringWriter()
        PemWriter(sw).use { writer ->
            writer.writeObject(PemObject("CERTIFICATE", bytes))
        }
        return sw.toString()
    }

    /**
     * SHA-256 fingerprint of the CA cert in `XX:XX:…` notation.
     * Show this in the UI so users can verify they installed the correct cert.
     */
    fun getCaCertFingerprint(): String? {
        val bytes = getCaCertBytes() ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(":") { "%02X".format(it) }
    }

    // ── CA generation ─────────────────────────────────────────────────────────

    private fun getOrCreateCA(): Pair<PrivateKey, X509Certificate> {
        if (caKeystoreFile.exists()) {
            try {
                val ks   = loadKeyStore(caKeystoreFile)
                val key  = ks.getKey(CA_ALIAS, PASSWORD) as? PrivateKey
                val cert = ks.getCertificate(CA_ALIAS) as? X509Certificate
                // Only reuse the CA if it already uses ECDSA — migrate away from old RSA keystores
                if (key != null && cert != null && key.algorithm == "EC" &&
                    cert.notAfter.after(Date.from(Instant.now().plus(30, ChronoUnit.DAYS)))) {
                    return key to cert
                }
            } catch (_: Exception) { }
            caKeystoreFile.delete()
        }

        val caKeyPair = generateEcKeyPair()
        val now       = Instant.now()
        val subject   = X500Name("CN=ChurchPresenter CA, O=ChurchPresenter, C=US")

        val caCert = sign(
            JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(now.toEpochMilli()),
                Date.from(now),
                Date.from(now.plus(3650, ChronoUnit.DAYS)),
                subject,
                caKeyPair.public
            ).apply {
                addExtension(Extension.basicConstraints,      true, BasicConstraints(0))
                addExtension(Extension.keyUsage,              true, KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign))
                addExtension(Extension.subjectKeyIdentifier, false,
                    JcaX509ExtensionUtils().createSubjectKeyIdentifier(caKeyPair.public))
            },
            caKeyPair.private
        )

        val ks = newKeyStore()
        ks.setKeyEntry(CA_ALIAS, caKeyPair.private, PASSWORD, arrayOf(caCert))
        caKeystoreFile.outputStream().use { ks.store(it, PASSWORD) }
        caCertFile.writeBytes(caCert.encoded)   // DER for GET /ca.crt
        serverKeystoreFile.delete()             // old cert was signed by previous CA

        return caKeyPair.private to caCert
    }

    // ── Server cert generation ────────────────────────────────────────────────

    private fun getOrCreateServerKeyStore(
        caKey: PrivateKey, caCert: X509Certificate, serverHost: String
    ): KeyStore {
        if (serverKeystoreFile.exists()) {
            try {
                val ks   = loadKeyStore(serverKeystoreFile)
                val key  = ks.getKey(SERVER_ALIAS, PASSWORD) as? PrivateKey
                val cert = ks.getCertificate(SERVER_ALIAS) as? X509Certificate
                // Only reuse the server cert if it already uses ECDSA — migrate RSA keystores
                if (key != null && cert != null && key.algorithm == "EC" &&
                    cert.notAfter.after(Date.from(Instant.now().plus(30, ChronoUnit.DAYS))) &&
                    serverHost in extractSanNames(cert)) return ks
            } catch (_: Exception) { /* fall through */ }
            serverKeystoreFile.delete()
        }
        return generateServerKeyStore(caKey, caCert, serverHost)
    }

    private fun generateServerKeyStore(
        caKey: PrivateKey, caCert: X509Certificate, serverHost: String
    ): KeyStore {
        val serverKeyPair = generateEcKeyPair()
        val now           = Instant.now()

        val sanList = mutableListOf(
            GeneralName(GeneralName.dNSName, "localhost"),
            ipSan("127.0.0.1")
        )
        if (isIpAddress(serverHost)) sanList += ipSan(serverHost)
        else                          sanList += GeneralName(GeneralName.dNSName, serverHost)

        val serverCert = sign(
            JcaX509v3CertificateBuilder(
                X500Name("CN=ChurchPresenter CA, O=ChurchPresenter, C=US"),
                BigInteger.valueOf(now.toEpochMilli()),
                Date.from(now),
                Date.from(now.plus(825, ChronoUnit.DAYS)),   // ~2 years (Apple max)
                X500Name("CN=ChurchPresenter, O=ChurchPresenter, C=US"),
                serverKeyPair.public
            ).apply {
                addExtension(Extension.basicConstraints,       false, BasicConstraints(false))
                // ECDSA uses digitalSignature only; keyEncipherment is only for RSA key-exchange
                // which is non-forward-secret and disabled on modern TLS stacks.
                addExtension(Extension.keyUsage,               true,
                    KeyUsage(KeyUsage.digitalSignature))
                addExtension(Extension.extendedKeyUsage,       false,
                    ExtendedKeyUsage(arrayOf(KeyPurposeId.id_kp_serverAuth)))
                addExtension(Extension.subjectAlternativeName, false,
                    GeneralNames(sanList.toTypedArray()))
                addExtension(Extension.authorityKeyIdentifier, false,
                    JcaX509ExtensionUtils().createAuthorityKeyIdentifier(caCert))
                addExtension(Extension.subjectKeyIdentifier,   false,
                    JcaX509ExtensionUtils().createSubjectKeyIdentifier(serverKeyPair.public))
            },
            caKey
        )

        val ks = newKeyStore()
        ks.setKeyEntry(SERVER_ALIAS, serverKeyPair.private, PASSWORD, arrayOf(serverCert, caCert))
        serverKeystoreFile.outputStream().use { ks.store(it, PASSWORD) }
        return ks
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Generates an ECDSA P-256 key pair.
     * P-256 is Apple's recommended curve for ATS forward-secrecy compliance and is
     * universally supported by iOS (7.0+) and Android (4.0+).
     */
    private fun generateEcKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC", "BC")
            .also { it.initialize(ECGenParameterSpec("P-256"), SecureRandom()) }
            .generateKeyPair()

    private fun sign(builder: JcaX509v3CertificateBuilder, key: PrivateKey): X509Certificate =
        JcaX509CertificateConverter().setProvider("BC")
            .getCertificate(
                builder.build(JcaContentSignerBuilder("SHA256withECDSA").setProvider("BC").build(key))
            )

    private fun newKeyStore()              = KeyStore.getInstance("JKS").also { it.load(null, PASSWORD) }
    private fun loadKeyStore(f: File)      = KeyStore.getInstance("JKS").also { ks -> f.inputStream().use { ks.load(it, PASSWORD) } }
    private fun ipSan(ip: String)          = GeneralName(GeneralName.iPAddress, DEROctetString(InetAddress.getByName(ip).address))
    private fun isIpAddress(host: String)  = host.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$""")) || host.contains(":")

    private fun extractSanNames(cert: X509Certificate): Set<String> = try {
        cert.subjectAlternativeNames?.mapNotNull { it.getOrNull(1) as? String }?.toSet() ?: emptySet()
    } catch (_: Exception) { emptySet() }
}
