package org.churchpresenter.updater

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
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

    private fun download(url: String): List<DownloadState> {
        val states = mutableListOf<DownloadState>()
        runBlocking { downloadInstaller(url) { states += it } }
        states.filterIsInstance<DownloadState.Done>().forEach { downloaded += it.file }
        return states
    }

    private val installer = ByteArray(40_000) { (it % 251).toByte() }

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
    fun `a download cancelled part way deletes what it had written and stays cancelled`() {
        val url = serve("ChurchPresenter-2.5.0.dmg", installer)
        val tempDir = File(System.getProperty("java.io.tmpdir"))
        fun installers() = tempDir.listFiles { f -> f.name.startsWith(UPDATE_INSTALLER_PREFIX) }.orEmpty().toSet()
        val before = installers()

        val cancelled = runCatching {
            runBlocking {
                downloadInstaller(url) { state ->
                    // The window's report hops to the UI dispatcher, which is where a cancel lands.
                    if (state is DownloadState.Downloading) throw CancellationException("cancelled from the window")
                }
            }
        }

        assertIs<CancellationException>(cancelled.exceptionOrNull(), "the cancel is passed on, not swallowed")
        assertEquals(before, installers(), "no half-written installer is left for the next launch")
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
}
