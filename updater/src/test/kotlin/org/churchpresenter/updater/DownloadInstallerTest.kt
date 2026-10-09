package org.churchpresenter.updater

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The update download itself, against a local JDK [HttpServer]: what arrives is the whole installer
 * under the release asset's extension, the progress it reports climbs to complete -- or stays
 * indeterminate when the server sends no length -- and every way it can fail ends in
 * [DownloadState.Error] rather than an exception out of the dialog's coroutine.
 */
class DownloadInstallerTest {

    private var server: HttpServer? = null
    private val downloaded = mutableListOf<File>()

    @AfterTest
    fun stop() {
        server?.stop(0)
        downloaded.forEach { it.delete() }
    }

    /** Serves [body] at `/<name>`; [sendLength] false streams it chunked, with no content length. */
    private fun serve(name: String, body: ByteArray, status: Int = 200, sendLength: Boolean = true): String {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/$name") { ex: HttpExchange ->
            ex.sendResponseHeaders(status, if (sendLength) body.size.toLong() else 0L)
            ex.responseBody.use { it.write(body) }
        }
        s.start()
        server = s
        return "http://127.0.0.1:${s.address.port}/$name"
    }

    private fun download(url: String, sha256: String? = installerSha256): List<DownloadState> {
        val states = mutableListOf<DownloadState>()
        runBlocking { downloadInstaller(url, sha256) { states += it } }
        states.filterIsInstance<DownloadState.Done>().forEach { downloaded += it.file }
        return states
    }

    private val installer = ByteArray(40_000) { (it % 251).toByte() }
    private val installerSha256 =
        MessageDigest.getInstance("SHA-256").digest(installer).joinToString("") { "%02x".format(it) }

    /** Every file the update download created in the temp directory. */
    private fun installersOnDisk(): Set<File> =
        File(System.getProperty("java.io.tmpdir")).listFiles { f -> f.name.startsWith(UPDATE_INSTALLER_PREFIX) }
            .orEmpty().toSet()

    @Test
    fun `the whole installer arrives under its own extension, with progress climbing to complete`() {
        val states = download(serve("ChurchPresenter-9.dmg", installer))

        val done = assertIs<DownloadState.Done>(states.last())
        assertTrue(done.file.name.endsWith(".dmg"), done.file.name)
        assertTrue(done.file.readBytes().contentEquals(installer), "a short copy is an installer that will not run")
        val progress = states.dropLast(1).map { assertIs<DownloadState.Downloading>(it).progress }
        assertTrue(progress.size > 1, "the bar moves as chunks land, not only at the end")
        assertEquals(progress.sorted(), progress, "progress never goes backwards")
        assertEquals(1f, progress.last())
    }

    @Test
    fun `a server that sends no length leaves the progress indeterminate`() {
        val states = download(serve("ChurchPresenter-9.msi", installer, sendLength = false))

        val done = assertIs<DownloadState.Done>(states.last())
        assertTrue(done.file.name.endsWith(".msi"))
        assertTrue(states.dropLast(1).all { it == DownloadState.Downloading(-1f) })
    }

    @Test
    fun `an asset the server does not have is reported as an error`() {
        val states = download(serve("ChurchPresenter-9.deb", "gone".toByteArray(), status = 404))

        val error = assertIs<DownloadState.Error>(states.single())
        assertTrue(error.message.isNotBlank())
    }

    @Test
    fun `a server that cannot be reached is reported as an error`() {
        val url = serve("x.dmg", installer)
        server?.stop(0)
        server = null

        assertIs<DownloadState.Error>(download(url).single())
    }

    @Test
    fun `an address that is not a full url is reported as an error`() {
        val error = assertIs<DownloadState.Error>(download("releases/ChurchPresenter-9.dmg").single())

        assertTrue(error.message.isNotBlank())
    }

    @Test
    fun `an installer that does not match the published digest is refused and deleted`() {
        val before = installersOnDisk()

        val states = download(serve("ChurchPresenter-9.dmg", installer), sha256 = "0".repeat(64))

        val error = assertIs<DownloadState.Error>(states.last())
        assertTrue(error.unverified, "the window says why, rather than showing a raw error")
        assertEquals(before, installersOnDisk(), "a file that failed verification is not left to run")
    }

    @Test
    fun `a release with no published digest is not downloaded at all`() {
        val states = download(serve("ChurchPresenter-9.dmg", installer), sha256 = null)

        assertTrue(assertIs<DownloadState.Error>(states.single()).unverified)
    }

    @Test
    fun `the digest check ignores hex case and refuses a blank pin`() {
        assertTrue(installerDigestMatches("abcd", "ABCD"))
        assertFalse(installerDigestMatches("abcd", "abce"))
        assertFalse(installerDigestMatches("abcd", " "))
    }
}
