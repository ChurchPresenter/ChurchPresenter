package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.settings.utils.AppDataDir
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.signage.hub.AgentUpdates
import org.churchpresenter.signage.hub.SignageHub
import org.churchpresenter.signage.tab.SignageController
import java.io.IOException
import java.net.InetAddress
import java.nio.file.Path
import kotlin.io.path.Path

/*
 * Signage (dev mode only): lobby screens that keep playing after hours. The hub of paired screens is
 * made the first time dev mode is on, off the event thread, and shared by the companion server and
 * the Signage tab. Dev mode off stops the folder sync and the mDNS advert, and the server refuses the
 * players; the screens keep playing what they have.
 */

/** Keeps the companion server's dev mode current, and starts or stops signage with it. */
@Composable
internal fun AppRootState.SignageEffect() {
    val devMode = devMode
    // The screens follow this ChurchPresenter's analytics setting for their own crash reports.
    val analytics = appSettings.analyticsReportingEnabled
    LaunchedEffect(analytics) { signageDiagnostics.value = analytics }
    LaunchedEffect(devMode) {
        companionServer.devMode = devMode
        if (!devMode) {
            signageController?.stop()
            return@LaunchedEffect
        }
        val controller = signageController ?: withContext(Dispatchers.IO) {
            val dir = AppDataDir.resolve().toPath().resolve(SIGNAGE_DIR)
            val releases = System.getProperty(AgentUpdates.RELEASE_URL_PROPERTY) ?: AgentUpdates.RELEASE_URL
            val hub = SignageHub(
                dir, computerName(), coroutineScope,
                updates = AgentUpdates(dir.resolve(PLAYER_BUILDS_DIR), baseUrl = releases),
            )
            companionServer.signage = hub
            SignageController(hub, dir, defaultSignageFolder(), coroutineScope, diagnostics = signageDiagnostics)
        }.also { signageController = it }
        controller.start()
    }
}

/** What the Signage tab gets: null while signage is off, so the tab is not offered. */
@Composable
internal fun AppRootState.signageTabInput(): SignageTabInput? {
    val controller = signageController?.takeIf { devMode } ?: return null
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

private fun computerName(): String = try {
    InetAddress.getLocalHost().hostName.substringBefore('.')
} catch (_: IOException) {
    Constants.SERVER_APP_NAME
}

private fun defaultSignageFolder(): Path =
    Path(System.getProperty(Constants.SystemProperties.USER_HOME), "Documents", Constants.SERVER_APP_NAME, "Signage")

private const val SIGNAGE_DIR = "signage"
private const val PLAYER_BUILDS_DIR = "player-builds"
