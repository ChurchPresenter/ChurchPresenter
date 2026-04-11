package org.churchpresenter.app.churchpresenter.server

import org.churchpresenter.app.churchpresenter.utils.Constants
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore

/**
 * Generates and persists a self-signed TLS certificate for the companion server.
 *
 * The keystore is stored at ~/.churchpresenter/server.jks and is created
 * automatically on first launch. Subsequent launches reuse the same cert so
 * that mobile clients only need to trust it once.
 *
 * Strategy:
 *  1. Use `keytool -genkeypair` to generate the full keystore (Java-version agnostic).
 *  2. Fall back to sun.security.x509 internals if keytool is unavailable.
 *
 * This avoids relying on sun.security.x509 which is restricted in Java 17+ and
 * completely inaccessible in Java 24 without --add-exports flags.
 */
object SslCertificateManager {

    private val keystoreFile = File(
        System.getProperty("user.home"),
        ".churchpresenter${File.separator}server.jks"
    )

    fun getOrCreateKeyStore(): KeyStore {
        // If a valid keystore already exists on disk, just load it.
        if (keystoreFile.exists()) {
            try {
                val ks = KeyStore.getInstance(Constants.SSL_KEYSTORE_TYPE)
                FileInputStream(keystoreFile).use { fis ->
                    ks.load(fis, Constants.SSL_KEYSTORE_PASSWORD.toCharArray())
                }
                if (ks.containsAlias(Constants.SSL_KEY_ALIAS)) {
                    return ks
                }
            } catch (_: Exception) {
                // Corrupted keystore — regenerate below
                keystoreFile.delete()
            }
        }

        // Generate a new keystore using keytool (works on all JDK versions).
        return generateViaKeytool()
    }

    /**
     * Uses `keytool -genkeypair` to generate a fresh self-signed cert + private key
     * into a JKS keystore file, then loads and returns that keystore.
     * This approach works on all JDK versions including Java 24.
     */
    private fun generateViaKeytool(): KeyStore {
        keystoreFile.parentFile?.mkdirs()

        val javaHome = System.getProperty("java.home") ?: ""
        // keytool lives in $JAVA_HOME/bin on all platforms
        // Find keytool — prefer the one from the running JDK, fall back to PATH
        val keytool: String = run {
            val candidates = listOf(
                "$javaHome/bin/keytool",
                "$javaHome/../bin/keytool"
            )
            candidates.firstOrNull { path ->
                try { File(path).exists() } catch (_: Exception) { false }
            } ?: "keytool"  // rely on PATH as last resort
        }

        val process = ProcessBuilder(
            keytool,
            "-genkeypair",
            "-alias", Constants.SSL_KEY_ALIAS,
            "-keyalg", Constants.SSL_KEY_ALGORITHM,
            "-keysize", "2048",
            // Explicitly use SHA256withRSA — SHA384withRSA (default in JDK 21+) triggers
            // RSA-PSS which macOS LibreSSL 3.3 rejects with "first octet invalid".
            "-sigalg", "SHA256withRSA",
            "-validity", "3650",
            "-dname", "CN=ChurchPresenter, O=ChurchPresenter, C=US",
            "-keystore", keystoreFile.absolutePath,
            "-storepass", Constants.SSL_KEYSTORE_PASSWORD,
            "-keypass", Constants.SSL_KEYSTORE_PASSWORD,
            "-storetype", Constants.SSL_KEYSTORE_TYPE
        ).redirectErrorStream(true).start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0 || !keystoreFile.exists()) {
            throw RuntimeException("keytool failed (exit $exitCode): $output")
        }

        // Load and return the newly created keystore
        val ks = KeyStore.getInstance(Constants.SSL_KEYSTORE_TYPE)
        FileInputStream(keystoreFile).use { fis ->
            ks.load(fis, Constants.SSL_KEYSTORE_PASSWORD.toCharArray())
        }
        return ks
    }
}
