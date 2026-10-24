package org.churchpresenter.signage.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.churchpresenter.signage.hub.LatestRelease
import org.churchpresenter.signage.hub.PairedScreen
import org.churchpresenter.signage.hub.ScreenLink
import org.churchpresenter.sharedui.composables.ZoneLabel
import org.churchpresenter.sharedui.composables.bibleListCard
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.signage_default_name
import org.churchpresenter.strings.generated.resources.signage_no_screens
import org.churchpresenter.strings.generated.resources.signage_no_screens_hint
import org.churchpresenter.strings.generated.resources.signage_screens
import org.churchpresenter.strings.generated.resources.signage_server_off
import org.churchpresenter.strings.generated.resources.signage_turn_on_server
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.sharedui.filechooser.FileChooser
import org.churchpresenter.sharedui.utils.UrlOpener
import java.nio.file.Files
import java.nio.file.Path

/**
 * The Signage tab: the lobby screens this ChurchPresenter owns, the ones asking to
 * join, and the signage folder their playlists come from. Everything it changes is kept by
 * [controller], which goes on running while another tab is selected.
 *
 * @param serverRunning whether the companion server is on; the screens reach ChurchPresenter only
 *   through it.
 * @param onTurnOnServer turns the companion server on.
 * @param desktop the file manager, folder chooser and Trash the tab uses.
 */
@Composable
fun SignageTab(
    controller: SignageController,
    serverRunning: Boolean,
    onTurnOnServer: () -> Unit,
    modifier: Modifier = Modifier,
    desktop: SignageDesktop = remember { SignageDesktop() },
) {
    val folder by controller.state.collectAsState()
    val screens by controller.screens.collectAsState()
    val pending by controller.pending.collectAsState()
    val links by controller.links.collectAsState()
    // Read so the cards offer an update once a newer release is found.
    val latest by controller.controls.latestRelease.collectAsState()
    var largePreview by remember { mutableStateOf<String?>(null) }
    // One set for the tab's life, so a card whose screen did not change is skipped on recomposition.
    val actions = remember(controller) {
        ScreenActions(
            onPlay = controller::play,
            onIdentify = { controller.controls.identify(it) },
            onRename = controller.controls::rename,
            onRemove = controller::remove,
            onWipe = controller::wipe,
            onUpdate = controller.controls::update,
            onPreviewOpen = { largePreview = it },
        )
    }
    // Only while the tab is showing: look for a player update now and daily, and ask for pictures.
    LaunchedEffect(controller) {
        while (true) {
            controller.controls.checkForUpdates()
            delay(UPDATE_CHECK_MS)
        }
    }
    LaunchedEffect(controller, largePreview) {
        while (true) {
            controller.controls.requestPreviews(largePreview)
            delay(PREVIEW_EVERY_MS)
        }
    }
    // No Surface sits under the tab, so text that names no colour would otherwise be drawn black.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(modifier = modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 8.dp)) {
            if (!serverRunning) ServerOffBanner(onTurnOnServer)
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val defaultName = stringResource(Res.string.signage_default_name, screens.size + 1)
                ScreensColumn(
                    screens = screens.map { screen ->
                        val link = links[screen.id]
                        val offer = latest?.let { controller.controls.offerFor(screen, link) }
                        ScreenRowState(screen, link, folder, offer)
                    },
                    pendingCards = {
                        pending.forEach { player ->
                            PendingScreenCard(
                                player = player,
                                onApprove = { controller.controls.approve(player.playerId, defaultName) },
                                onDecline = { controller.controls.reject(player.playerId) },
                            )
                        }
                    },
                    actions = actions,
                    modifier = Modifier.weight(SCREENS_WEIGHT).fillMaxSize(),
                )
                SignageFolderPanel(
                    state = folder,
                    actions = FolderActions(
                        onChangeFolder = controller::setFolder,
                        onPictureSeconds = controller::setPictureSeconds,
                        desktop = desktop,
                    ),
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
            }
        }
    }
}

/** One screen as its card shows it: what it is, its connection, and what the folder offers it. */
internal data class ScreenRowState(
    val screen: PairedScreen,
    val link: ScreenLink?,
    val folder: SignageFolderState,
    /** The player release this screen can be updated to now, if any. */
    val offer: LatestRelease? = null,
)

/** What the folder panel's controls do. */
internal class FolderActions(
    val onChangeFolder: (Path) -> Unit,
    val onPictureSeconds: (Int) -> Unit,
    val desktop: SignageDesktop,
)

/**
 * What the tab asks of the computer itself: open a folder in the file manager, choose one, and
 * delete a playlist (to the Trash where there is one). Kept apart so a test can answer instead.
 * Each blocking call is made off the event thread.
 */
class SignageDesktop(
    val fileChooser: FileChooser = FileChooser.platformInstance,
    val openFolder: (Path) -> Unit = { folder ->
        Files.createDirectories(folder)
        UrlOpener.open(folder.toString())
    },
    /** Deletes playlist `id` from the signage folder; false when something was left. */
    val deletePlaylist: (Path, String) -> Boolean = { root, id -> SignageFolder.delete(root, id) },
    val canTrash: () -> Boolean = SignageFolder::canTrash,
)

/** What a screen card's controls do, each given the screen's id. */
internal class ScreenActions(
    val onPlay: (String, String) -> Unit,
    val onIdentify: (String) -> Unit,
    val onRename: (String, String) -> Unit,
    /** Factory reset: the screen deletes everything, forgets this ChurchPresenter and leaves the list. */
    val onRemove: (String) -> Unit,
    val onWipe: (String) -> Unit = {},
    val onUpdate: (String) -> Unit = {},
    /** A screen's picture opened big, or null when it is closed: that screen is asked for a sharper one. */
    val onPreviewOpen: (String?) -> Unit = {},
)

@Composable
private fun ScreensColumn(
    screens: List<ScreenRowState>,
    pendingCards: @Composable () -> Unit,
    actions: ScreenActions,
    modifier: Modifier,
) {
    Column(modifier = modifier) {
        ZoneLabel(stringResource(Res.string.signage_screens), modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { pendingCards() } }
            items(screens, key = { it.screen.id }) { row -> ScreenCard(row, actions) }
            if (screens.isEmpty()) item { NoScreens() }
        }
    }
}

@Composable
private fun NoScreens() {
    Column(
        modifier = Modifier.fillMaxWidth().bibleListCard().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Filled.Tv,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Text(stringResource(Res.string.signage_no_screens), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(Res.string.signage_no_screens_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ServerOffBanner(onTurnOnServer: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).bibleListCard().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.CloudOff, contentDescription = null, tint = MaterialTheme.semantic.warning)
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(Res.string.signage_server_off),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Box { Button(onClick = onTurnOnServer) { Text(stringResource(Res.string.signage_turn_on_server)) } }
    }
}

private const val SCREENS_WEIGHT = 1.4f
private const val PREVIEW_EVERY_MS = 5_000L
private const val UPDATE_CHECK_MS = 24 * 60 * 60 * 1000L
