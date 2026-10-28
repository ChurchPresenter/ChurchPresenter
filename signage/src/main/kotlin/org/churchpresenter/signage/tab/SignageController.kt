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
import org.churchpresenter.signage.core.Orientation
import org.churchpresenter.signage.core.ScheduleBlock
import org.churchpresenter.signage.hub.PairedScreen
import org.churchpresenter.signage.hub.PendingScreen
import org.churchpresenter.signage.hub.ScreenLink
import org.churchpresenter.signage.hub.SignageHub
import java.nio.file.Path
import java.time.ZoneId
import kotlin.io.path.Path
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

/**
 * A playlist as the tab lists it: a folder with its counts, or an added playlist with its [detail]
 * (the file's name or the link's address); [missing] when its file is gone.
 */
data class PlaylistSummary(
    val id: String,
    val name: String,
    val pictures: Int = 0,
    val videos: Int = 0,
    val kind: SourceKind = SourceKind.FOLDER,
    val detail: String? = null,
    val missing: Boolean = false,
    /** A rotation's steps, and their seconds added up (a folder or a video without a time adds none). */
    val steps: Int = 0,
    val seconds: Int = 0,
) {
    /** Added in the tab, so it can be renamed, and removed without touching any file. */
    val added: Boolean get() = kind != SourceKind.FOLDER
}

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
 * the Signage tab is shown in the tab bar. The folder is read every [pollMs]; a change is published once it
 * has stood still for one more read, so a copy in progress is not sent half-written. Changes made
 * while ChurchPresenter was off are simply what the first reads find.
 *
 * The settings may sit in a folder another computer shares: what it writes there (its edits, the
 * screens it pairs) is read again on the next poll, and the screens are sent what changed.
 *
 * All folder and store work runs on one IO thread, so it never overlaps itself.
 */
class SignageController(
    private val hub: SignageHub,
    /** Where the settings are kept: the signage folder, which other computers may share. */
    storeDir: Path,
    private val defaultFolder: Path,
    private val scope: CoroutineScope,
    private val pollMs: Long = DEFAULT_POLL_MS,
    /** This ChurchPresenter's analytics setting, sent to every screen as whether it may report crashes. */
    private val diagnostics: StateFlow<Boolean> = MutableStateFlow(true),
    /** This computer's own cache (file hashes), kept out of the shared folder. */
    cacheDir: Path = storeDir,
) {
    private val store = SignageStore(storeDir, cacheDir)
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
    private var folders: List<HashedPlaylist> = emptyList()

    private val features = KnownFeatures()

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

    /** Adding, renaming and removing the playlists added by hand. */
    val added = AddedPlaylistEdits(::update)

    /** Each screen's weekly schedule, and how it is mounted. */
    val plans = ScreenPlans(::update)

    fun setPictureSeconds(seconds: Int) = update { it.copy(pictureSeconds = seconds.coerceIn(1, MAX_PICTURE_SECONDS)) }

    /**
     * Empties [screenId]: it plays [ScreenPlan.NOTHING], and once that empty manifest is published
     * it is told to delete what it holds, so the manifest it fetches straight after has nothing in it.
     */
    fun wipe(screenId: String) = update(
        change = { settings ->
            val emptied = ScreenPlan(ScreenPlan.NOTHING, orientation = settings.plan(screenId).orientation)
            settings.copy(screens = settings.screens + (screenId to emptied))
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
        launch { hub.links.links.collect { links -> if (features.remember(links)) publishAll() } }
        var lastRead: Any? = null
        var published: Any? = null
        while (true) {
            val folder = _state.value.folder
            val read = SignageFolder.scan(folder, folder.name)
            val addedFiles = _state.value.settings.added.filterIsInstance<FilePlaylist>().map { it.currentFile() }
            val snapshot = read to addedFiles
            if (snapshot == lastRead && snapshot != published) {
                folders = read.map { playlist -> HashedPlaylist(playlist, playlist.files.associateWith(store::hashOf)) }
                val kept = read.flatMap { playlist -> playlist.files.map { it.path } } +
                    addedFiles.mapNotNull { it?.path }
                store.saveHashes(kept.toSet())
                // Listed by publishAll() first: whoever waits for ready finds the playlists there.
                publishAll()
                _state.update { it.copy(ready = true) }
                published = snapshot
            }
            lastRead = snapshot
            // What another computer sharing the signage folder changed: its settings, and the
            // screens it paired, renamed or removed (the screens flow republishes those on its own).
            store.settingsChangedElsewhere()?.let { settings ->
                _state.update { it.copy(settings = settings, folder = folderOf(settings, defaultFolder)) }
                publishAll()
            }
            hub.registry.reloadIfChanged()
            delay(pollMs)
        }
    }

    /**
     * Lists the playlists from the last folder read and the added ones, and sends every screen its
     * manifest; one that did not change is not sent.
     */
    private fun publishAll() {
        val added = store.hashAdded(_state.value.settings.added)
        hashed = folders + added
        val listed = folders.map { (it.playlist as FolderPlaylist).summary() } + added.map { it.addedSummary() }
        _state.update { it.copy(playlists = listed) }
        if (hub.registry.ownerId.isEmpty()) return
        val settings = _state.value.settings
        val zone = ZoneId.systemDefault().id
        hub.registry.screens.value.forEach { screen ->
            val (manifest, files) = buildManifest(
                screen, hub.registry.ownerId, settings, hashed, zone, diagnostics.value,
                features = features.of(screen.id),
            )
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

/**
 * What each screen's player said it can do, kept after it disconnects, so a screen that is away for a
 * moment is not sent a manifest without its links. Used on the controller's one IO thread.
 */
internal class KnownFeatures {
    private val known = mutableMapOf<String, List<String>>()

    fun of(screenId: String): List<String> = known[screenId].orEmpty()

    /** Keeps what each connected screen can do; true when that changed anything. */
    fun remember(links: Map<String, ScreenLink>): Boolean {
        var changed = false
        links.forEach { (id, link) ->
            if (known[id] != link.features) {
                known[id] = link.features
                changed = true
            }
        }
        return changed
    }
}

/** The tab's edits to the playlists added by hand, each saved and published by [update]. */
class AddedPlaylistEdits internal constructor(
    private val update: (then: () -> Unit, change: (SignageSettings) -> SignageSettings) -> Unit,
) {
    /** Adds [link] as a playlist. */
    fun addLink(link: NewLink) = update({}) { settings ->
        val playlist = with(link) { LinkPlaylist.create(name.trim(), url.trim(), kind, refreshMinutes, smooth) }
        settings.copy(added = settings.added + playlist)
    }

    /** Replaces link [id]'s name, address, kind, refresh and smoothness with [link]'s; it keeps its id. */
    fun updateLink(id: String, link: NewLink) = update({}) { settings ->
        settings.copy(
            added = settings.added.map { entry ->
                if (entry is LinkPlaylist && entry.id == id) {
                    entry.copy(
                        name = link.name.trim(),
                        url = link.url.trim(),
                        kind = link.kind,
                        refreshMinutes = link.refreshMinutes,
                        smooth = link.smooth,
                    )
                } else {
                    entry
                }
            },
        )
    }

    /** Renames file playlist [id] and points it at [file]; a file that is no picture or video is not taken. */
    fun updateFile(id: String, name: String, file: Path) = update({}) { settings ->
        val kind = SignageFolder.kindOf(file)
        settings.copy(
            added = settings.added.map { entry ->
                if (entry is FilePlaylist && entry.id == id) {
                    kind?.let { entry.copy(name = name.trim(), path = file.toString(), kind = it) }
                        ?: entry.copy(name = name.trim())
                } else {
                    entry
                }
            },
        )
    }

    /** Adds [file] as a playlist of its own, named after it; nothing when it is no picture or video. */
    fun addFile(file: Path) {
        val kind = SignageFolder.kindOf(file) ?: return
        update({}) { settings ->
            val playlist = FilePlaylist.create(file.nameWithoutExtension, file.toString(), kind)
            settings.copy(added = settings.added + playlist)
        }
    }

    /** Adds a rotation of [steps], other entries by id. */
    fun addRotation(name: String, steps: List<RotationStep>) = update({}) { settings ->
        settings.copy(added = settings.added + RotationPlaylist.create(name.trim(), steps))
    }

    /** Replaces rotation [id]'s name and steps. */
    fun updateRotation(id: String, name: String, steps: List<RotationStep>) = update({}) { settings ->
        settings.copy(
            added = settings.added.map { entry ->
                if (entry is RotationPlaylist && entry.id == id) {
                    entry.copy(name = name.trim(), steps = steps)
                } else {
                    entry
                }
            },
        )
    }

    /** Removes the added playlist [id], leaving its file alone; a screen that played it plays the first. */
    fun remove(id: String) = update({}) { it.withoutAdded(id) }
}

/** The tab's edits to each screen's plan, saved and published by [update]. */
class ScreenPlans internal constructor(
    private val update: (then: () -> Unit, change: (SignageSettings) -> SignageSettings) -> Unit,
) {
    /** Replaces [screenId]'s schedule with [blocks]; what it plays outside them stays. */
    fun setSchedule(screenId: String, blocks: List<ScheduleBlock>) = change(screenId) { it.copy(schedule = blocks) }

    /** How [screenId] is mounted; it turns everything it shows to match. */
    fun setOrientation(screenId: String, orientation: Orientation) =
        change(screenId) { it.copy(orientation = orientation) }

    private fun change(screenId: String, edit: (ScreenPlan) -> ScreenPlan) = update({}) { settings ->
        settings.copy(screens = settings.screens + (screenId to edit(settings.plan(screenId))))
    }
}
