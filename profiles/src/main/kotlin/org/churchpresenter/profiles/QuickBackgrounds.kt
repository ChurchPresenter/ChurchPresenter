package org.churchpresenter.profiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.KeyEvent
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.utils.ShortcutMap
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.churchpresenter.settings.QuickBackground
import java.util.UUID
import org.churchpresenter.sharedui.composables.matches

/** How many backgrounds the tray will hold. The last of them has no key by default — see below. */
const val QUICK_BACKGROUND_SLOTS = 10

/**
 * What a tile is called — the background's own name, the same one the song panel shows.
 *
 * A quick background carries no label of its own: "Deep Navy" or the file's name is what the
 * operator picked it by in the panel, so naming it twice would only let the two disagree.
 */
@Composable
fun quickBackgroundLabel(entry: QuickBackground): String = songBackgroundName(entry.background)

/**
 * A new tray entry: an opaque black full screen, and a lower third that inherits.
 *
 * The full-screen half is never [SongBackgroundType.INHERIT] — a quick background exists to
 * override, and one that inherited there would be a tile that does nothing when pressed. The
 * lower-third half starts inheriting so that picking a tile leaves each output's own lower-third
 * band as configured; seeding it black as well painted every band solid black the moment a tile
 * was picked.
 */
internal fun newQuickBackground(): QuickBackground = QuickBackground(
    id = UUID.randomUUID().toString(),
    background = SongBackground(type = SongBackgroundType.COLOR, color = "#000000"),
    lowerThirdBackground = SongBackground(),
)

/** Which tray slot [keyEvent] asks for, or null when it asks for none. */
fun quickBackgroundSlotFor(shortcuts: ShortcutMap, keyEvent: KeyEvent): Int? =
    QUICK_BACKGROUND_ACTIONS.indexOfFirst { shortcuts.matches(it, keyEvent) }
        .takeIf { it >= 0 }
        ?.plus(1)

/** The action bound to tray slot [slot] (1-based), or null past the last one that has an action. */
fun quickBackgroundActionFor(slot: Int): ShortcutAction? = QUICK_BACKGROUND_ACTIONS.getOrNull(slot - 1)

private val QUICK_BACKGROUND_ACTIONS = listOf(
    ShortcutAction.QUICK_BACKGROUND_1, ShortcutAction.QUICK_BACKGROUND_2,
    ShortcutAction.QUICK_BACKGROUND_3, ShortcutAction.QUICK_BACKGROUND_4,
    ShortcutAction.QUICK_BACKGROUND_5, ShortcutAction.QUICK_BACKGROUND_6,
    ShortcutAction.QUICK_BACKGROUND_7, ShortcutAction.QUICK_BACKGROUND_8,
    ShortcutAction.QUICK_BACKGROUND_9, ShortcutAction.QUICK_BACKGROUND_10,
)
