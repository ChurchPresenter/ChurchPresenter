package org.churchpresenter.schedule

import org.churchpresenter.showcontrol.Action
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.churchpresenter.sharedui.utils.addGuardedShutdownHook
import org.churchpresenter.core.models.io.writeTextAtomically
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.io.File
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private const val AUTOSAVE_INTERVAL_MS = 60_000L
private const val MAX_UNDO_DEPTH = 50

class ScheduleViewModel(
    private val onScheduleChanged: ((List<ScheduleItem>) -> Unit)? = null,
    /**
     * The wall clock the live row is timed against -- see [markLive] and `planDrift`. Defaulted
     * so the app never passes it; a test or a screenshot pins it, since a "3:40 behind" reckoned
     * from the real time is a different picture every second.
     */
    val clock: () -> LocalTime = { LocalTime.now() },
    /** How often unsaved changes are written to the autosave; shortened only by a test. */
    private val autoSaveIntervalMs: Long = AUTOSAVE_INTERVAL_MS,
) {
    internal val _scheduleItems: SnapshotStateList<ScheduleItem> = mutableStateListOf()
    val scheduleItems: List<ScheduleItem> get() = _scheduleItems

    // ── Instance Link — remote schedule mirroring ────────────────────────────
    // True whenever this instance is following another instance's schedule via Instance Link.
    // While true, local mutation methods below are no-ops — the schedule is driven entirely by
    // followRemoteSchedule(), called by the app whenever InstanceLinkViewModel.remoteSchedule updates.
    internal val _isFollowingRemote = mutableStateOf(false)
    val isFollowingRemote: Boolean get() = _isFollowingRemote.value

    /** Replaces the schedule with [items], the primary's `schedule_updated` broadcast mapped by the app. */
    fun followRemoteSchedule(items: List<ScheduleItem>) {
        _isFollowingRemote.value = true
        _scheduleItems.clear()
        _scheduleItems.addAll(items)
        notifyChanged()
    }

    /** Called on Instance Link disconnect — hands local editing back to the operator. */
    fun stopFollowingRemote() {
        _isFollowingRemote.value = false
    }

    /** Wired to InstanceLinkViewModel.sendAddToSchedule when the operator has enabled pushing items
     *  to the primary's schedule; left null otherwise, in which case [addOrPush] below is a no-op
     *  while following — same behavior as before this existed. */
    var onPushToRemoteSchedule: ((ScheduleItem) -> Unit)? = null

    /** Adds [item] locally, or pushes it to the primary (subject to its own approval dialog) while
     *  following a remote schedule — the single entry point every add* method below funnels through. */
    internal fun addOrPush(item: ScheduleItem) {
        if (_isFollowingRemote.value) {
            onPushToRemoteSchedule?.invoke(item)
            return
        }
        pushUndoSnapshot()
        _scheduleItems.add(item)
        notifyChanged()
    }

    // ── Auto-save ─────────────────────────────────────────────────────────────

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    internal val autoSaveFile = File(System.getProperty("user.home"), ".churchpresenter/autosave_schedule.tmp")

    @Volatile private var isDirty = false

    init {
        // An autosave the restore prompt can no longer offer — see autoSaveAvailable()'s same-day
        // and four-hour gate — is unreachable for ever, so it is deleted rather than left to sit
        // in the data folder. This runs before anything can ask for the prompt.
        discardUnreachableAutoSave()
        // Compose's DisposableEffect-driven dispose() isn't guaranteed to run on a raw
        // exitProcess/System.exit shutdown (e.g. the self-updater relaunching an installer),
        // so this loop could otherwise wake up mid-shutdown and try to classload ScheduleFileV2
        // after the installer has started overwriting the app's own files.
        addGuardedShutdownHook("schedule") { scope.cancel() }
        scope.launch {
            while (true) {
                delay(autoSaveIntervalMs)
                if (isDirty && _scheduleItems.isNotEmpty()) {
                    try {
                        autoSaveFile.parentFile?.mkdirs()
                        autoSaveFile.writeTextAtomically(encodedSchedule())
                        isDirty = false
                    } catch (_: Exception) {
                    } catch (_: LinkageError) {
                    }
                }
            }
        }
    }

    @Volatile internal var autoRestorePrompted = false

    fun dispose() {
        scope.cancel()
    }

    internal fun notifyChanged() {
        isDirty = true
        onScheduleChanged?.invoke(_scheduleItems.toList())
    }

    internal val _selectedItemId = mutableStateOf<String?>(null)
    val selectedItemId get() = _selectedItemId.value

    /**
     * The row most recently put on screen, and when -- what "behind plan" is reckoned from.
     *
     * Not the selection: a row can be selected without going live, and the automation puts rows
     * live without a click. Set by every path that presents a row, cleared with the schedule.
     */
    internal val _liveRowId = mutableStateOf<String?>(null)
    val liveRowId: String? get() = _liveRowId.value
    internal val _liveSince = mutableStateOf<LocalTime?>(null)
    val liveSince: LocalTime? get() = _liveSince.value

    /** Records that [id] went live at [at]. Presenting the same row again restarts its clock. */
    fun markLive(id: String, at: LocalTime = clock()) {
        _liveRowId.value = id
        _liveSince.value = at
    }

    // Fields a newer build adds are skipped, not a reason to read the file as something else.
    internal val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }
    internal var currentFilePath: String? = null

    // ── Undo / Redo ───────────────────────────────────────────────────────────

    internal data class ScheduleSnapshot(
        val items: List<ScheduleItem>,
        val notes: Map<String, String>,
        val timing: Map<String, RowTiming>,
        val actions: Map<String, List<Action>> = emptyMap(),
    )

    /** The rows and everything kept beside them, as they are now -- what undo goes back to. */
    internal fun snapshot() =
        ScheduleSnapshot(_scheduleItems.toList(), _notes.toMap(), _timing.toMap(), _actions.toMap())

    /** Puts the rows and everything kept beside them back as [snapshot] has them. */
    internal fun restore(snapshot: ScheduleSnapshot) {
        _scheduleItems.clear()
        _scheduleItems.addAll(snapshot.items)
        _notes.clear()
        _notes.putAll(snapshot.notes)
        _timing.clear()
        _timing.putAll(snapshot.timing)
        _actions.clear()
        _actions.putAll(snapshot.actions)
    }

    internal val undoStack = ArrayDeque<ScheduleSnapshot>()
    internal val redoStack = ArrayDeque<ScheduleSnapshot>()

    internal val _canUndo = mutableStateOf(false)
    internal val _canRedo = mutableStateOf(false)
    val canUndo: Boolean get() = _canUndo.value
    val canRedo: Boolean get() = _canRedo.value

    /**
     * Replaces the rows, notes and timing wholesale -- an Open, a restored autosave or a new
     * schedule -- with nothing to undo back to, and remembers [filePath] for the next Save.
     */
    internal fun replaceSchedule(decoded: DecodedSchedule, filePath: String?) {
        restore(ScheduleSnapshot(decoded.items, decoded.notes, decoded.timing, decoded.actions))
        currentFilePath = filePath
        undoStack.clear()
        redoStack.clear()
        _canUndo.value = false
        _canRedo.value = false
        clearAutoSave()
        notifyChanged()
    }

    internal fun pushUndoSnapshot() {
        undoStack.addLast(snapshot())
        if (undoStack.size > MAX_UNDO_DEPTH) undoStack.removeFirst()
        redoStack.clear()
        _canUndo.value = true
        _canRedo.value = false
    }

    // ── Notes ─────────────────────────────────────────────────────────────────

    internal val _notes = mutableStateMapOf<String, String>()

    // ── Timing ────────────────────────────────────────────────────────────────

    /**
     * How each row runs on its own, keyed by row id -- what the Calendar Manager planned for it.
     * A row with no entry is cued by hand and runs its own length once, which is every row that
     * was not loaded from a plan.
     */
    internal val _timing = mutableStateMapOf<String, RowTiming>()
    val timing: Map<String, RowTiming> get() = _timing

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * What each row does when it goes live, keyed by row id (`docs/SHOW_CONTROL.md`, Cue actions):
     * show-control actions run in order, beside the content the row puts up.
     */
    internal val _actions = mutableStateMapOf<String, List<Action>>()
    val actions: Map<String, List<Action>> get() = _actions

    /**
     * The `HH:mm` start of the planned service this schedule was loaded from, or null.
     *
     * The anchor the clock column falls back to when no row is pinned -- see `scheduleClocks`.
     * Not saved with the schedule: it is a fact about the service that was loaded, and a file
     * reopened next month is not that service. Cleared with the rows.
     */
    internal val _serviceStartTime = mutableStateOf<String?>(null)
    val serviceStartTime: String? get() = _serviceStartTime.value

    /** The file the last Open could not use, and why; the Schedule tab says so until dismissed. */
    var openFailure by mutableStateOf<ScheduleOpenFailure?>(null)

    /** Wired to InstanceLinkViewModel.sendRemoveFromSchedule when the operator has enabled pushing
     *  items to the primary's schedule (same allowPushToSchedule gate as [onPushToRemoteSchedule]) —
     *  left null otherwise, in which case [removeItem] below is a no-op while following, same as
     *  before this existed. */
    var onRemoveFromRemoteSchedule: ((id: String) -> Unit)? = null

    /**
     * Told whenever a row is put on screen from here -- how long each thing takes is measured from
     * this, and from the cue path's own equivalent. A lambda, not a listener object: nothing here
     * should know what is doing the measuring.
     */
    var onItemPresented: ((ScheduleItem) -> Unit)? = null

    /**
     * Called once a row has been put on screen, with the actions it runs when it goes live -- after
     * its content, so anything that waits for that content to reach the air sees it there.
     */
    var onRowActions: ((ScheduleItem, List<Action>) -> Unit)? = null
}
