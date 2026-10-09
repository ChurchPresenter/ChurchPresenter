package org.churchpresenter.updater

import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The update window's download sequence, [UpdateDownloadFlow]: Download counts the download and
 * fetches the release's installer, each stage lands in the window's state, Install Now runs the file,
 * and a failure to start it is put where the window shows it.
 *
 * Every step that reaches past the window comes in through [UpdateSteps] as a fake, so nothing is
 * fetched, launched or counted for real. The fake download holds at half way on a gate the test
 * opens, which is how the in-progress state is seen without waiting on a clock; stages are applied
 * unconfined, so each is in [UpdateDownloadFlow.state] the moment it is reported.
 */
class UpdateDownloadSequenceTest {

    private val scope = CoroutineScope(SupervisorJob())

    @AfterTest
    fun cancelScope() {
        scope.cancel()
    }

    private val installer = File("ChurchPresenter-update-test.dmg")

    private class Calls {
        val counted = CopyOnWriteArrayList<String>()
        val downloaded = CopyOnWriteArrayList<String>()
        val digests = CopyOnWriteArrayList<String?>()
        val installed = CopyOnWriteArrayList<File>()
        val halfWay = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
    }

    private fun info(downloadUrl: String? = "https://example.invalid/ChurchPresenter-2.5.0.dmg") = UpdateInfo(
        latestVersion = "2.5.0",
        releaseUrl = "https://example.invalid/releases/2.5.0",
        releaseNotes = "notes",
        downloadUrl = downloadUrl,
        downloadSha256 = "ab12",
    )

    private fun flow(
        info: UpdateInfo?,
        calls: Calls,
        installError: DownloadState.Error? = null,
    ) = UpdateDownloadFlow(
        info,
        scope,
        UpdateSteps(
            reportDownloadStarted = { calls.counted += it },
            download = { url, sha256, report ->
                calls.downloaded += url
                calls.digests += sha256
                report(DownloadState.Downloading(0.5f))
                calls.halfWay.complete(Unit)
                calls.finish.await()
                report(DownloadState.Done(installer))
            },
            install = { file ->
                calls.installed += file
                installError
            },
        ),
        ui = Dispatchers.Unconfined,
    )

    @Test
    fun `download counts the download, fetches the release's installer and reports its progress`() {
        val calls = Calls()
        val flow = flow(info(), calls)

        val job = assertNotNull(flow.download())
        runBlocking { calls.halfWay.await() }

        assertEquals(DownloadState.Downloading(0.5f), flow.state)
        assertEquals(listOf("2.5.0"), calls.counted.toList())
        assertEquals(listOf("https://example.invalid/ChurchPresenter-2.5.0.dmg"), calls.downloaded.toList())
        assertEquals(listOf<String?>("ab12"), calls.digests.toList(), "the release's digest goes with the url")

        calls.finish.complete(Unit)
        runBlocking { job.join() }
        assertEquals(DownloadState.Done(installer), flow.state)
    }

    @Test
    fun `install runs the downloaded installer and leaves the state alone when it starts`() {
        val calls = Calls()
        val flow = flow(info(), calls)
        calls.finish.complete(Unit)
        runBlocking { assertNotNull(flow.download()).join() }

        flow.install(installer)

        assertEquals(listOf(installer), calls.installed.toList())
        assertEquals(DownloadState.Done(installer), flow.state)
    }

    @Test
    fun `an installer that cannot be started puts the reason where the window shows it`() {
        val calls = Calls()
        val flow = flow(info(), calls, installError = DownloadState.Error("Installer blocked"))

        flow.install(installer)

        assertEquals(DownloadState.Error("Installer blocked"), flow.state)
    }

    @Test
    fun `with no update, or no installer for this machine, there is nothing to download`() {
        val calls = Calls()

        assertNull(flow(null, calls).download())
        assertNull(flow(info(downloadUrl = null), calls).download())
        assertEquals(0, calls.counted.size, "nothing fetched is nothing counted")
    }

    @Test
    fun `the window's own flow, with the real steps, starts idle and has nothing to fetch without an update`() {
        val flow = UpdateDownloadFlow(info = null, scope = scope)

        assertEquals(DownloadState.Idle, flow.state)
        assertNull(flow.download())
    }

    @Test
    fun `the window is taller with an update to show and taller again on a manual check`() {
        assertEquals(548.dp, updateDialogHeight(hasUpdate = true, isManualCheck = true))
        assertEquals(500.dp, updateDialogHeight(hasUpdate = true, isManualCheck = false))
        assertEquals(468.dp, updateDialogHeight(hasUpdate = false, isManualCheck = true))
        assertEquals(420.dp, updateDialogHeight(hasUpdate = false, isManualCheck = false))
    }
}
