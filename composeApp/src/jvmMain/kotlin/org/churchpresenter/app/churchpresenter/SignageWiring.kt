package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.settings.utils.AppDataDir
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.SIGNAGE_DIR
import org.churchpresenter.settings.signageFolder
import org.churchpresenter.diagnostics.Log
import java.nio.file.Files
import org.churchpresenter.signage.hub.AgentUpdates
import org.churchpresenter.signage.hub.SignageHub
import org.churchpresenter.signage.tab.SignageController
import org.churchpresenter.sharedui.models.Tabs
import java.io.IOException
import java.net.InetAddress
import java.nio.file.Path
import kotlin.io.path.Path

/*
 * Signage: lobby screens that keep playing after hours. The tab starts hidden, as STT does, and
 * signage runs while it is shown: the hub of paired screens is made the first time, off the event
 * thread, and shared by the companion server and the Signage tab. Hiding the tab stops the folder
 * sync and the mDNS advert, and the server refuses the players; the screens keep playing what they
 * have.
 */

/** Keeps the companion server's dev mode current, and starts or stops signage with its tab. */
@Composable
internal fun AppRootState.SignageEffect() {
    val devMode = devMode
    LaunchedEffect(devMode) { companionServer.devMode = devMode }
    // The screens follow this ChurchPresenter's analytics setting for their own crash reports.
    val analytics = appSettings.analyticsReportingEnabled
    LaunchedEffect(analytics) { signageDiagnostics.value = analytics }
    val shown = signageTabShown()
    val folderSetting = appSettings.signageStorageDirectory
    LaunchedEffect(shown, folderSetting) {
        companionServer.signageEnabled = shown
        if (!shown) {
            signageController?.stop()
            return@LaunchedEffect
        }
        val current = signageController?.takeIf { signageFolderInUse == folderSetting }
        val controller = current ?: withContext(Dispatchers.IO) {
            signageController?.stop()
            // The setup (screens, playlists, schedules) in the signage folder, which other computers may
            // share; this computer's own downloads and caches in its app data folder.
            val local = AppDataDir.resolve().toPath().resolve(SIGNAGE_DIR)
            val shared = appSettings.signageFolder().toPath()
            if (shared != local) adoptSignageSetup(from = local, to = shared)
            val releases = System.getProperty(AgentUpdates.RELEASE_URL_PROPERTY) ?: AgentUpdates.RELEASE_URL
            val hub = SignageHub(
                shared, computerName(), coroutineScope,
                updates = AgentUpdates(local.resolve(PLAYER_BUILDS_DIR), baseUrl = releases),
            )
            companionServer.signage = hub
            SignageController(
                hub, shared, defaultSignageFolder(), coroutineScope,
                diagnostics = signageDiagnostics, cacheDir = local,
            )
        }.also {
            signageController = it
            signageFolderInUse = folderSetting
        }
        controller.start()
    }
}

/** What the Signage tab gets: null while its tab is hidden, so signage is off. */
@Composable
internal fun AppRootState.signageTabInput(): SignageTabInput? {
    val controller = signageController?.takeIf { signageTabShown() } ?: return null
    val running = companionServer.isRunning.collectAsState().value
    return SignageTabInput(
        controller = controller,
        serverRunning = running,
        onTurnOnServer = {
            companionServer.start(appSettings.serverSettings.port, appSettings.serverSettings.serverHost)
            appSettings = appSettings.copy(serverSettings = appSettings.serverSettings.copy(enabled = true))
            settingsManager.saveSettings(appSettings)
        },
    )
}

private fun AppRootState.signageTabShown(): Boolean = Tabs.SIGNAGE.name !in appSettings.hiddenTabs

private fun computerName(): String = try {
    InetAddress.getLocalHost().hostName.substringBefore('.')
} catch (_: IOException) {
    Constants.SERVER_APP_NAME
}

private fun defaultSignageFolder(): Path =
    Path(System.getProperty(Constants.SystemProperties.USER_HOME), "Documents", Constants.SERVER_APP_NAME, "Signage")

private const val PLAYER_BUILDS_DIR = "player-builds"

/**
 * Brings this computer's signage setup into the folder it is moving to, the first time: the paired
 * screens and the tab's settings. A folder that already holds a setup is another computer's, and is
 * joined as it stands — this one's own is left where it was. Blocking: run it on `Dispatchers.IO`.
 */
internal fun adoptSignageSetup(from: Path, to: Path) {
    val files = listOf(SignageHub.REGISTRY_FILE, SIGNAGE_SETTINGS_FILE)
    if (files.any { Files.exists(to.resolve(it)) }) return
    try {
        Files.createDirectories(to)
        files.map(from::resolve).filter(Files::exists).forEach { Files.copy(it, to.resolve(it.fileName)) }
    } catch (e: IOException) {
        Log.warn("Signage", "Could not copy the signage setup to $to: ${e.message}")
    }
}

private const val SIGNAGE_SETTINGS_FILE = "signage.json"
