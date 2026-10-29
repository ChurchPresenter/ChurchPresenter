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
import org.churchpresenter.signage.core.KioskPin
import org.churchpresenter.signage.core.Orientation
import org.churchpresenter.signage.core.ScheduleBlock
import org.churchpresenter.signage.hub.PairedScreen
import org.churchpresenter.signage.hub.PendingScreen
import org.churchpresenter.signage.hub.ScreenLink
import org.churchpresenter.signage.hub.SignageHub
import java.nio.file.Path
import java.time.ZoneId
import kotlin.io.path.name

/**
 * A playlist as the tab lists it, with its [detail] (the folder's path, the file's name or the link's
 * address) and a folder's counts; [missing] when its file or folder is gone.
 */
data class PlaylistSummary(
    val id: String,
    val name: String,
    val pictures: Int = 0,
    val videos: Int = 0,
    val kind: SourceKind = SourceKind.FOLDER,
    val detail: String? = null,
    val missing: Boolean = false,
    /**
     * A rotation's steps, and their seconds added up (a folder or a video without a time adds none);
     * for a folder, the seconds each picture stays up.
     */
    val steps: Int = 0,
    val seconds: Int = 0,
)

/** What the Signage tab shows of the playlists and the settings. */
data class SignageFolderState(
    val playlists: List<PlaylistSummary> = emptyList(),
    val settings: SignageSettings = SignageSettings(),
    /** False until the playlists' folders and files have been read once. */
    val ready: Boolean = false,
    /** What is being copied into the signage folder now, by name. */
    val copying: List<String> = emptyList(),
    /** The name of the last copy that failed; cleared when another starts. */
    val copyFailed: String? = null,
)

/**
 * Keeps every paired screen's manifest in step with the playlists and the tab's choices, while the
 * Signage tab is shown in the tab bar. The playlists' folders and files are read every [pollMs]; a
 * change is published once it has stood still for one more read, so a copy in progress is not sent
 * half-written. Changes made while ChurchPresenter was off are simply what the first reads find.
 *
 * The settings may sit in a folder another computer shares: what it writes there (its edits, the
 * screens it pairs) is read again on the next poll, and the screens are sent what changed.
 *
 * All folder and store work runs on one IO thread, so it never overlaps itself.
 */
class SignageController(
    private val hub: SignageHub,
    /** Where the settings are kept: the signage folder, which other computers may share. */
    private val storeDir: Path,
    private val scope: CoroutineScope,
    private val pollMs: Long = DEFAULT_POLL_MS,
    /** This ChurchPresenter's analytics setting, sent to every screen as whether it may report crashes. */
    private val diagnostics: StateFlow<Boolean> = MutableStateFlow(true),
    /** This computer's own cache (file hashes), kept out of the shared folder. */
    cacheDir: Path = storeDir,
) {
    private val store = SignageStore(storeDir, cacheDir)
    private val io = Dispatchers.IO.limitedParallelism(1)
    private val _state = MutableStateFlow(SignageFolderState())
    val state: StateFlow<SignageFolderState> = _state.asStateFlow()

    /** The paired screens, the players asking to join, and the screens connected now. */
    val screens: StateFlow<List<PairedScreen>> = hub.registry.screens
    val pending: StateFlow<List<PendingScreen>> = hub.pairing.pending
    val links: StateFlow<Map<String, ScreenLink>> = hub.links.links

    /** Approving, declining, renaming, identifying, previewing and updating screens: what needs no folder. */
    val controls = ScreenControls(hub, scope)
    val power = ScreenPower(hub)

    private var job: Job? = null

    /** Whether the saved settings have been read; used on the IO thread only. */
    private var loaded = false

    private val folders = StableFolders(store)

    private val features = KnownFeatures()

    /** Starts keeping the screens in step; does nothing when already started. */
    fun start() {
        if (job != null) return
        job = scope.launch(io) { run() }
    }

    /** Stops reading the playlists. The screens keep playing what they last received. */
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

    /** Adding, renaming and removing the playlists, copying what they play into the signage folder. */
    val added = AddedPlaylistEdits(::update, MediaCopies(storeDir, scope, _state))

    /** Each screen's weekly schedule, and how it is mounted. */
    val plans = ScreenPlans(::update)

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
            // An edit made before the first read changes what was saved, not the defaults.
            if (!loaded) loadSettings()
            val settings = change(_state.value.settings)
            store.saveSettings(settings)
            _state.update { it.copy(settings = settings) }
            publishAll()
            then()
        }
    }

    private fun loadSettings() {
        val settings = store.loadSettings()
        _state.update { it.copy(settings = settings) }
        loaded = true
    }

    private suspend fun run() = coroutineScope {
        hub.registry.load()
        store.loadHashes()
        loadSettings()
        launch { hub.registry.screens.collect { publishAll() } }
        launch { diagnostics.collect { publishAll() } }
        launch { hub.links.links.collect { links -> if (features.remember(links)) publishAll() } }
        var lastRead: Any? = null
        var published: Any? = null
        while (true) {
            val added = _state.value.settings.added
            val read = added.filterIsInstance<PickedFolderPlaylist>().map { SignageFolder.read(it, storeDir) }
            val addedFiles = added.filterIsInstance<FilePlaylist>().map { it.currentFile(storeDir) }
            val snapshot = read to addedFiles
            if (snapshot == lastRead && snapshot != published) {
                folders.settle(read)
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
                _state.update { it.copy(settings = settings) }
                publishAll()
            }
            hub.registry.reloadIfChanged()
            delay(pollMs)
        }
    }

    /**
     * Lists the playlists, each folder as it was last read, and sends every screen its manifest; one
     * that did not change is not sent.
     */
    private fun publishAll() {
        val hashed = _state.value.settings.added.map(folders::hashedOf)
        _state.update { it.copy(playlists = hashed.map { it.addedSummary() }) }
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
    }
}

/**
 * Each picked folder as last read once it stood still, by playlist id, with its files' hashes. Used
 * on the controller's one IO thread.
 */
internal class StableFolders(private val store: SignageStore) {
    private var read: Map<String, HashedPlaylist> = emptyMap()

    /** Keeps [folders] as they stand now, each having stood still since the read before. */
    fun settle(folders: List<FolderPlaylist>) {
        read = folders.associate { it.id to store.hashFolder(it) }
    }

    /** [entry] with its files' hashes; a folder as last read, under the name it has now. */
    fun hashedOf(entry: AddedPlaylist): HashedPlaylist {
        if (entry !is PickedFolderPlaylist) return store.hashAdded(entry)
        val last = read[entry.id]?.takeIf { (it.playlist as FolderPlaylist).path == entry.path }
        // A folder just picked, or pointed somewhere else, is read now rather than polls later.
        val hashed = last ?: store.hashAdded(entry).also { read = read + (entry.id to it) }
        val folder = (hashed.playlist as FolderPlaylist).copy(name = entry.name, pictureSeconds = entry.pictureSeconds)
        return hashed.copy(playlist = folder)
    }
}

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

/** The tab's edits to each screen's plan, saved and published by [update]. */
class ScreenPlans internal constructor(
    private val update: (then: () -> Unit, change: (SignageSettings) -> SignageSettings) -> Unit,
) {
    /** Replaces [screenId]'s schedule with [blocks]; what it plays outside them stays. */
    fun setSchedule(screenId: String, blocks: List<ScheduleBlock>) = change(screenId) { it.copy(schedule = blocks) }

    /** How [screenId] is mounted; it turns everything it shows to match. */
    fun setOrientation(screenId: String, orientation: Orientation) =
        change(screenId) { it.copy(orientation = orientation) }

    /** Whether holding [screenId]'s corner switches to a touch page, and to which playlist (null: the first). */
    fun setGesture(screenId: String, on: Boolean, playlistId: String?) =
        change(screenId) { it.copy(kioskGesture = on, kioskGesturePlaylistId = playlistId) }

    /**
     * The PIN [screenId]'s touch gesture asks for, or none when [pin] is null. Only its hash is kept
     * and sent to the screen; the hashing is slow on purpose, so it happens with the save, off the UI.
     */
    fun setKioskPin(screenId: String, pin: String?) =
        change(screenId) { it.copy(kioskPinHash = pin?.takeIf(KioskPin::isValid)?.let(KioskPin::hash)) }

    private fun change(screenId: String, edit: (ScreenPlan) -> ScreenPlan) = update({}) { settings ->
        settings.copy(screens = settings.screens + (screenId to edit(settings.plan(screenId))))
    }
}
