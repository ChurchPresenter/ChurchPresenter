package org.churchpresenter.schedule


fun ScheduleViewModel.moveItemUp(id: String): Int {
    if (_isFollowingRemote.value) return -1
    val index = _scheduleItems.indexOfFirst { it.id == id }
    if (index > 0) {
        pushUndoSnapshot()
        val item = _scheduleItems.removeAt(index)
        _scheduleItems.add(index - 1, item)
        notifyChanged()
        return index - 1
    }
    return index
}

fun ScheduleViewModel.moveItemDown(id: String): Int {
    if (_isFollowingRemote.value) return -1
    val index = _scheduleItems.indexOfFirst { it.id == id }
    if (index >= 0 && index < _scheduleItems.size - 1) {
        pushUndoSnapshot()
        val item = _scheduleItems.removeAt(index)
        _scheduleItems.add(index + 1, item)
        notifyChanged()
        return index + 1
    }
    return index
}

fun ScheduleViewModel.moveItemToTop(id: String): Int {
    if (_isFollowingRemote.value) return -1
    val index = _scheduleItems.indexOfFirst { it.id == id }
    if (index > 0) {
        pushUndoSnapshot()
        val item = _scheduleItems.removeAt(index)
        _scheduleItems.add(0, item)
        notifyChanged()
        return 0
    }
    return index
}

fun ScheduleViewModel.moveItemToBottom(id: String): Int {
    if (_isFollowingRemote.value) return -1
    val index = _scheduleItems.indexOfFirst { it.id == id }
    if (index >= 0 && index < _scheduleItems.size - 1) {
        pushUndoSnapshot()
        val item = _scheduleItems.removeAt(index)
        _scheduleItems.add(item)
        notifyChanged()
        return _scheduleItems.size - 1
    }
    return index
}

fun ScheduleViewModel.moveItem(from: Int, to: Int) {
    if (_isFollowingRemote.value) return
    val fromValid = from in _scheduleItems.indices
    val toValid = to in _scheduleItems.indices
    if (!fromValid || !toValid || from == to) return
    pushUndoSnapshot()
    val item = _scheduleItems.removeAt(from)
    _scheduleItems.add(to, item)
    notifyChanged()
}

/**
 * Puts down row [index] after a drag: into the delete zone removes it (and its selection), else
 * it moves to [targetIndex], the slot it was over.
 */
internal fun ScheduleViewModel.finishRowDrag(index: Int, overDeleteZone: Boolean, targetIndex: Int?) {
    val droppedId = scheduleItems.getOrNull(index)?.id
    if (overDeleteZone && droppedId != null) {
        removeItem(droppedId)
        if (selectedItemId == droppedId) clearSelection()
    } else {
        val to = targetIndex ?: index
        if (index != to) moveItem(index, to)
    }
}
