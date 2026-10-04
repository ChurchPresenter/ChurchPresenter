package org.churchpresenter.schedule

import java.util.UUID
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.schedule.websiteDisplayText

fun ScheduleViewModel.addWebsite(url: String, title: String) {
    addOrPush(ScheduleItem.WebsiteItem(id = UUID.randomUUID().toString(), url = url, title = title.ifBlank { url }))
}

fun ScheduleViewModel.addScene(sceneId: String, sceneName: String) {
    addOrPush(ScheduleItem.SceneItem(id = UUID.randomUUID().toString(), sceneId = sceneId, sceneName = sceneName))
}

/** Adds a cue row whole -- its payload, time and play count come with it -- under a fresh id. */
fun ScheduleViewModel.addCue(item: ScheduleItem.CueItem) {
    addOrPush(item.copy(id = UUID.randomUUID().toString()))
}

/** Ticks or unticks one cue row -- "skip this one" -- in place. */
fun ScheduleViewModel.setCueEnabled(id: String, enabled: Boolean) {
    val index = _scheduleItems.indexOfFirst { it.id == id }
    val cue = _scheduleItems.getOrNull(index) as? ScheduleItem.CueItem ?: return
    pushUndoSnapshot()
    _scheduleItems[index] = cue.copy(enabled = enabled)
    notifyChanged()
}

fun ScheduleViewModel.updateWebsiteTitle(url: String, title: String) {
    if (_isFollowingRemote.value || title.isBlank()) return
    val index = _scheduleItems.indexOfFirst { it is ScheduleItem.WebsiteItem && it.url == url }
    if (index >= 0) {
        val existing = _scheduleItems[index] as ScheduleItem.WebsiteItem
        // Only update if the current title is still the URL (i.e. no real title was set yet)
        if (existing.title == existing.url || existing.title.isBlank()) {
            pushUndoSnapshot()
            // displayText must be passed explicitly: copy() does not re-apply constructor
            // defaults, so without this the row keeps showing the URL the item was added with.
            _scheduleItems[index] = existing.copy(title = title, displayText = websiteDisplayText(title))
            notifyChanged()
        }
    }
}

fun ScheduleViewModel.updateLabel(id: String, text: String, textColor: String, backgroundColor: String) {
    if (_isFollowingRemote.value) return
    val index = _scheduleItems.indexOfFirst { it.id == id }
    if (index >= 0 && _scheduleItems[index] is ScheduleItem.LabelItem) {
        pushUndoSnapshot()
        _scheduleItems[index] = ScheduleItem.LabelItem(
            id = id,
            text = text,
            textColor = textColor,
            backgroundColor = backgroundColor
        )
        notifyChanged()
    }
}

fun ScheduleViewModel.removeItem(id: String) {
    if (_isFollowingRemote.value) {
        onRemoveFromRemoteSchedule?.invoke(id)
        return
    }
    pushUndoSnapshot()
    _scheduleItems.removeAll { it.id == id }
    _notes.remove(id)
    _timing.remove(id)
    notifyChanged()
}

fun ScheduleViewModel.clearSchedule() {
    if (_isFollowingRemote.value) return
    pushUndoSnapshot()
    _scheduleItems.clear()
    _notes.clear()
    _timing.clear()
    _serviceStartTime.value = null
    _liveRowId.value = null
    _liveSince.value = null
    notifyChanged()
}
