package org.churchpresenter.signage.tab

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.churchpresenter.signage.hub.PairedScreen
import org.churchpresenter.signage.hub.PendingScreen
import org.churchpresenter.signage.hub.ScreenLink
import org.churchpresenter.signage.hub.SignageHub
import java.nio.file.Path
import java.time.ZoneId
import kotlin.io.path.Path
import kotlin.io.path.name

/** A playlist as the tab lists it. */
data class PlaylistSummary(val id: String, val name: String, val pictures: Int, val videos: Int)

/** What the Signage tab shows of the folder and the settings. */
data class SignageFolderState(
    val folder: Path,
    val playlists: List<PlaylistSummary> = emptyList(),
    val settings: SignageSettings = SignageSettings(),
    /** False until the folder has been read once. */
    val ready: Boolean = false,
)

/**
 * Keeps every paired screen's manifest in step with the signage folder and the tab's choices, while
 * ChurchPresenter runs in dev mode. The folder is read every [pollMs]; a change is published once it
 * has stood still for one more read, so a copy in progress is not sent half-written. Changes made
 * while ChurchPresenter was off are simply what the first reads find.
 *
 * All folder and store work runs on one IO thread, so it never overlaps itself.
 */
class SignageController(
    private val hub: SignageHub,
    storeDir: Path,
    private val defaultFolder: Path,
    private val scope: CoroutineScope,
    private val pollMs: Long = DEFAULT_POLL_MS,
    /** This ChurchPresenter's analytics setting, sent to every screen as whether it may report crashes. */
    private val diagnostics: StateFlow<Boolean> = MutableStateFlow(true),
) {
    private val store = SignageStore(storeDir)
    private val io = Dispatchers.IO.limitedParallelism(1)
    private val _state = MutableStateFlow(SignageFolderState(defaultFolder))
    val state: StateFlow<SignageFolderState> = _state.asStateFlow()

    /** The paired screens, the players asking to join, and the screens connected now. */
    val screens: StateFlow<List<PairedScreen>> = hub.registry.screens
    val pending: StateFlow<List<PendingScreen>> = hub.pairing.pending
    val links: StateFlow<Map<String, ScreenLink>> = hub.links.links

    /** Approving, declining, renaming, identifying, previewing and updating screens: what needs no folder. */
    val controls = ScreenControls(hub, scope)

    private var job: Job? = null
    private var hashed: List<HashedPlaylist> = emptyList()

    /** Starts keeping the screens in step; does nothing when already started. */
    fun start() {
        if (job != null) return
        job = scope.launch(io) { run() }
    }

    /** Stops reading the folder. The screens keep playing what they last received. */
    fun stop() {
        job?.cancel()
        job = null
    }

    fun remove(screenId: String) {
        hub.revoke(screenId)
        update { it.copy(screens = it.screens - screenId) }
    }

    fun play(screenId: String, playlistId: String) = update { settings ->
        settings.copy(screens = settings.screens + (screenId to settings.plan(screenId).copy(playlistId = playlistId)))
    }

    fun setFolder(folder: Path) = update { it.copy(folder = folder.toString()) }

    fun setPictureSeconds(seconds: Int) = update { it.copy(pictureSeconds = seconds.coerceIn(1, MAX_PICTURE_SECONDS)) }

    /**
     * Empties [screenId]: it plays [ScreenPlan.NOTHING], and once that empty manifest is published
     * it is told to delete what it holds, so the manifest it fetches straight after has nothing in it.
     */
    fun wipe(screenId: String) = update(
        change = { settings ->
            settings.copy(screens = settings.screens + (screenId to ScreenPlan(ScreenPlan.NOTHING)))
        },
        then = { controls.wipe(screenId) },
    )

    private fun update(then: () -> Unit = {}, change: (SignageSettings) -> SignageSettings) {
        scope.launch(io) {
            val settings = change(_state.value.settings)
            store.saveSettings(settings)
            _state.update { it.copy(settings = settings, folder = folderOf(settings, defaultFolder)) }
            publishAll()
            then()
        }
    }

    private suspend fun run() = coroutineScope {
        hub.registry.load()
        store.loadHashes()
        val settings = store.loadSettings()
        _state.update { it.copy(settings = settings, folder = folderOf(settings, defaultFolder)) }
        launch { hub.registry.screens.collect { publishAll() } }
        launch { diagnostics.collect { publishAll() } }
        var lastRead: List<FolderPlaylist>? = null
        var published: List<FolderPlaylist>? = null
        while (true) {
            val folder = _state.value.folder
            val read = SignageFolder.scan(folder, folder.name)
            if (read == lastRead && read != published) {
                hashed = read.map { playlist -> HashedPlaylist(playlist, playlist.files.associateWith(store::hashOf)) }
                store.saveHashes(read.flatMap { playlist -> playlist.files.map { it.path } }.toSet())
                _state.update { it.copy(playlists = read.map { playlist -> playlist.summary() }, ready = true) }
                publishAll()
                published = read
            }
            lastRead = read
            delay(pollMs)
        }
    }

    private fun publishAll() {
        if (hub.registry.ownerId.isEmpty()) return
        val settings = _state.value.settings
        val zone = ZoneId.systemDefault().id
        hub.registry.screens.value.forEach { screen ->
            val (manifest, files) =
                buildManifest(screen, hub.registry.ownerId, settings, hashed, zone, diagnostics.value)
            hub.publish(screen.id, manifest, files)
        }
    }

    private companion object {
        const val DEFAULT_POLL_MS = 2_000L
        const val MAX_PICTURE_SECONDS = 600
    }
}

/** The signage folder [settings] name, or [default] when they name none. */
private fun folderOf(settings: SignageSettings, default: Path): Path =
    settings.folder.takeIf { it.isNotBlank() }?.let(::Path) ?: default
