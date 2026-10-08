package org.churchpresenter.schedule

import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.showcontrol.Action

fun ScheduleViewModel.undo() {
    if (_isFollowingRemote.value || undoStack.isEmpty()) return
    redoStack.addLast(snapshot())
    restore(undoStack.removeLast())
    _canUndo.value = undoStack.isNotEmpty()
    _canRedo.value = true
    notifyChanged()
}

fun ScheduleViewModel.redo() {
    if (_isFollowingRemote.value || redoStack.isEmpty()) return
    undoStack.addLast(snapshot())
    restore(redoStack.removeLast())
    _canUndo.value = true
    _canRedo.value = redoStack.isNotEmpty()
    notifyChanged()
}

fun ScheduleViewModel.getNote(itemId: String): String = _notes[itemId] ?: ""

fun ScheduleViewModel.setNote(itemId: String, note: String) {
    // A note is an edit like any other: it has to be snapshotted so it can be undone on its own,
    // and notified so autosave knows the schedule changed. Without the notify a note-only change
    // was never written; without the snapshot, undoing an unrelated edit restored a notes map
    // that predated the note and silently discarded it.
    val next = if (note.isBlank()) "" else note
    // The ✓ that commits a note is also how its editor is closed, so pressing it unchanged must
    // not push an undo step that appears to do nothing.
    if ((_notes[itemId] ?: "") == next) return
    pushUndoSnapshot()
    if (next.isEmpty()) _notes.remove(itemId) else _notes[itemId] = next
    notifyChanged()
}

fun ScheduleViewModel.timingFor(itemId: String): RowTiming = _timing[itemId] ?: RowTiming.DEFAULT

/** What the row [itemId] does when it goes live -- none for most rows. */
fun ScheduleViewModel.actionsFor(itemId: String): List<Action> = _actions[itemId].orEmpty()

/** Sets what the row [itemId] does when it goes live; an empty list takes its actions away. */
fun ScheduleViewModel.setActions(itemId: String, actions: List<Action>) {
    if (actionsFor(itemId) == actions) return
    pushUndoSnapshot()
    if (actions.isEmpty()) _actions.remove(itemId) else _actions[itemId] = actions
    notifyChanged()
}

fun ScheduleViewModel.setServiceStart(startTime: String?) {
    _serviceStartTime.value = startTime
}

/** Adds a planned row whole -- its id kept, so [timing] and any cue payload still point at it. */
fun ScheduleViewModel.addRow(item: ScheduleItem, timing: RowTiming?) {
    if (_isFollowingRemote.value) {
        onPushToRemoteSchedule?.invoke(item)
        return
    }
    pushUndoSnapshot()
    _scheduleItems.add(item)
    if (timing != null && !timing.isDefault()) _timing[item.id] = timing
    notifyChanged()
}
