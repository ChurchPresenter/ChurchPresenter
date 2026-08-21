package org.churchpresenter.app.churchpresenter.data.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.app.churchpresenter.models.shortcuts.KeyChord

/**
 * The user's keyboard rebindings.
 *
 * **Only what the user changed is stored, never the full table.** Two things follow from that, and
 * both are the reason for it: a release that improves a default reaches everyone who never touched
 * that action, and an action dropped from `ShortcutAction` decodes to an ignored map entry instead
 * of failing the whole settings load.
 *
 * Keyed by `ShortcutAction.name`. An entry mapped to an **empty list** is meaningful — it is the
 * user unbinding the action, which is not the same as having no entry at all.
 */
@Serializable
data class KeyboardShortcutSettings(
    val overrides: Map<String, List<KeyChord>> = emptyMap(),
)
