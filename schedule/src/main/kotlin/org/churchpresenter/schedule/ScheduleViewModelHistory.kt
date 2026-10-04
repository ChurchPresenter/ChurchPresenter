package org.churchpresenter.schedule

import org.churchpresenter.schedule.ScheduleViewModel.ScheduleSnapshot
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem

fun ScheduleViewModel.undo() {
    if (_isFollowingRemote.value || undoStack.isEmpty()) return
    redoStack.addLast(ScheduleSnapshot(_scheduleItems.toList(), _notes.toMap(), _timing.toMap()))
    val snapshot = undoStack.removeLast()
    _scheduleItems.clear()
    _scheduleItems.addAll(snapshot.items)
    _notes.clear()
    _notes.putAll(snapshot.notes)
    _timing.clear()
    _timing.putAll(snapshot.timing)
    _canUndo.value = undoStack.isNotEmpty()
    _canRedo.value = true
    notifyChanged()
}

fun ScheduleViewModel.redo() {
    if (_isFollowingRemote.value || redoStack.isEmpty()) return
    undoStack.addLast(ScheduleSnapshot(_scheduleItems.toList(), _notes.toMap(), _timing.toMap()))
    val snapshot = redoStack.removeLast()
    _scheduleItems.clear()
    _scheduleItems.addAll(snapshot.items)
    _notes.clear()
    _notes.putAll(snapshot.notes)
    _timing.clear()
    _timing.putAll(snapshot.timing)
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
