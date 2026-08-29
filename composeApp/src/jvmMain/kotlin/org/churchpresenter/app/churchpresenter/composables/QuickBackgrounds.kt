package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.KeyEvent
import org.churchpresenter.app.churchpresenter.dialogs.songBackgroundName
import org.churchpresenter.app.churchpresenter.models.ShortcutAction
import org.churchpresenter.app.churchpresenter.utils.ShortcutMap
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.churchpresenter.settings.QuickBackground
import java.util.UUID

/** How many backgrounds the tray will hold. The last of them has no key by default — see below. */
internal const val QUICK_BACKGROUND_SLOTS = 10

/**
 * What a tile is called — the background's own name, the same one the song panel shows.
 *
 * A quick background carries no label of its own: "Deep Navy" or the file's name is what the
 * operator picked it by in the panel, so naming it twice would only let the two disagree.
 */
@Composable
internal fun quickBackgroundLabel(entry: QuickBackground): String = songBackgroundName(entry.background)

/**
 * A new tray entry, opaque black until the panel says otherwise.
 *
 * Never [SongBackgroundType.INHERIT]: a quick background exists to override, and one that inherited
 * would be a tile that does nothing when pressed.
 */
internal fun newQuickBackground(): QuickBackground {
    val black = SongBackground(type = SongBackgroundType.COLOR, color = "#000000")
    return QuickBackground(id = UUID.randomUUID().toString(), background = black, lowerThirdBackground = black)
}

/** Which tray slot [keyEvent] asks for, or null when it asks for none. */
internal fun quickBackgroundSlotFor(shortcuts: ShortcutMap, keyEvent: KeyEvent): Int? =
    QUICK_BACKGROUND_ACTIONS.indexOfFirst { shortcuts.matches(it, keyEvent) }
        .takeIf { it >= 0 }
        ?.plus(1)

/** The action bound to tray slot [slot] (1-based), or null past the last one that has an action. */
internal fun quickBackgroundActionFor(slot: Int): ShortcutAction? = QUICK_BACKGROUND_ACTIONS.getOrNull(slot - 1)

private val QUICK_BACKGROUND_ACTIONS = listOf(
    ShortcutAction.QUICK_BACKGROUND_1, ShortcutAction.QUICK_BACKGROUND_2,
    ShortcutAction.QUICK_BACKGROUND_3, ShortcutAction.QUICK_BACKGROUND_4,
    ShortcutAction.QUICK_BACKGROUND_5, ShortcutAction.QUICK_BACKGROUND_6,
    ShortcutAction.QUICK_BACKGROUND_7, ShortcutAction.QUICK_BACKGROUND_8,
    ShortcutAction.QUICK_BACKGROUND_9, ShortcutAction.QUICK_BACKGROUND_10,
)
