package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.churchpresenter.dialogs.AddLabelDialog
import org.churchpresenter.dialogs.CrashFeedbackDialog
import org.churchpresenter.dialogs.KonamiEasterEggDialog
import org.churchpresenter.dialogs.SavePresetDialog
import org.churchpresenter.calendar.PresetStore
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.settings.calendarFolder

/** The dialogs the main screen opens itself: presets, labels, websites, and the two it offers unasked. */
@Composable
internal fun MainDesktopScope.MainDesktopDialogs() {
    val presetStore = remember(appSettings.calendarStorageDirectory) { PresetStore(appSettings.calendarFolder()) }
    SavePresetDialog(
        item = state.presetToSave,
        existingNames = remember(state.presetToSave) {
            if (state.presetToSave == null) emptyList() else presetStore.load().presets.map { it.name }
        },
        onConfirm = { name -> state.presetToSave?.let { presetStore.add(name, it) } },
        onDismiss = { state.presetToSave = null },
    )

    val editingLabelItem = state.editingLabelItem
    AddLabelDialog(
        isVisible = state.showAddLabelDialog,
        onDismiss = this::closeLabelDialog,
        onConfirm = this::confirmLabel,
        existingText = editingLabelItem?.text ?: "",
        // Empty, not a hardcoded pair: a new label picks its colours up from the active theme.
        existingTextColor = editingLabelItem?.textColor.orEmpty(),
        existingBackgroundColor = editingLabelItem?.backgroundColor.orEmpty(),
        isEdit = editingLabelItem != null
    )

    KonamiEasterEggDialog(
        isVisible = state.showKonamiEasterEgg,
        onDismiss = { state.showKonamiEasterEgg = false },
    )

    if (state.showCrashFeedback) {
        CrashFeedbackDialog(
            onDismiss = { state.showCrashFeedback = false },
            onSend = { comment, email ->
                CrashReporter.sendUserFeedback(comment, email = email)
                state.showCrashFeedback = false
            }
        )
    }
}

internal fun MainDesktopScope.closeLabelDialog() {
    state.showAddLabelDialog = false
    state.editingLabelItem = null
}

internal fun MainDesktopScope.confirmLabel(text: String, textColor: String, backgroundColor: String) {
    val editing = state.editingLabelItem
    if (editing != null) {
        currentScheduleActions.updateLabel(editing.id, text, textColor, backgroundColor)
    } else {
        currentScheduleActions.addLabel(text, textColor, backgroundColor)
    }
    closeLabelDialog()
}
