package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Saves the settings dialog's draft and pushes it to the live outputs -- what its Apply button does
 * -- for a nested dialog that wants an Apply of its own.
 *
 * The Options dialog provides it; `OutputCustomizeDialog` reads it. That dialog edits the Options
 * draft, so its changes reached the projector only after it was closed and the Options dialog's
 * Apply pressed -- two steps away from the preview that was meant to make the change visible. Null
 * where there is no draft to apply, and the nested dialog then shows no such button.
 */
internal val LocalApplySettings = staticCompositionLocalOf<(() -> Unit)?> { null }
