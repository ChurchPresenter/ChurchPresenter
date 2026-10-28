@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import org.churchpresenter.dialogs.KeyboardShortcutsDialog
import kotlin.test.Test

/** Running a service from the keyboard, then Help → Keyboard Shortcuts: finding, changing, resetting. */
class ShortcutsDemoTour {
    @Test
    fun shortcuts() = recordTour(
        "shortcuts",
        tab = "Keyboard Shortcuts",
        liveLabel = "From the keys",
        steps = 12,
        heading = "" to "",
        panel = { settings, change ->
            Box(Modifier.size(DIALOG_W.dp, DIALOG_H.dp)) {
                KeyboardShortcutsDialog(
                    isVisible = true,
                    settings = settings,
                    onSave = { saved -> change { saved } },
                    devMode = false,
                    onDismiss = {},
                    frame = { _, content -> content() },
                )
            }
        },
    ) { shortcutsTour() }

    private fun DemoTour.shortcutsTour() {
        say("intro")
        intro("Your whole service, right from the keys.")
        say("why")
        caption("Hands on the keys", "No hunting for buttons")
        // The keys go to whatever holds focus; a click in the tab gives the window the keyboard.
        quietClick(searchField())
        songs()
        bible()
        say("clicker")
        caption("Page Down / Page Up", "A live presentation, from any tab")
        say("escape")
        val clear = find(hasContentDescription("Clear Display") or hasText("Clear Display"))
        press("Clear the screen", "Nothing on the projector", clear, Key.Escape, "Esc", live = true) {
            // Under the recorder the key does not reach the window's handler; cleared as it would.
            runCatching { clear.node.performClick() }
            ui.waitForIdle()
            settle()
            refocus()
        }
        dialog()
        say("outro")
        outro()
    }

    private fun DemoTour.songs() {
        say("f-keys")
        caption(
            "F6 Bible · F7 Songs · F8 Pictures · F9 Presentation",
            "F10 Media · F11 Lower Third · F12 Announcements",
        )
        switchTab(Key.F7, "Songs")
        say("search")
        type("Type the song", "The search box is ready", searchField(), "amazing grace")
        say("enter")
        press("Go live", "The highlighted song", searchField(), Key.Enter, "Enter ⏎", live = true) {
            quietGoLive()
        }
        say("down")
        val verse2 = text("'Twas grace that taught")
        press("Next verse", "While it's live", verse2, Key.DirectionDown, "↓", live = true) { settle() }
        say("up")
        val verse1 = text("Amazing grace! how sweet")
        press("Previous verse", "Back one", verse1, Key.DirectionUp, "↑", live = true) { settle() }
        say("f2")
        press("Add to schedule", "For later in the service", find(hasText("Schedule")), Key.F2, "F2") {
            // F2 is the menu bar's accelerator, and the recording has no menu bar: added as it would.
            schedule().addSong(AMAZING_GRACE, "Amazing Grace", "Hymnal", "Hymnal::$AMAZING_GRACE")
        }
    }

    private fun DemoTour.bible() {
        switchTab(Key.F6, "Bible")
        say("reference")
        type("Type a reference", "Book, chapter and verse", searchField(), "John 3:16")
        say("bible-enter")
        press("Go live", "The verse you typed", searchField(), Key.Enter, "Enter ⏎", live = true) { quietGoLive() }
        say("bible-down")
        press("Next verse", "While it's live", searchField(), Key.DirectionDown, "↓", live = true) { settle() }
    }

    private fun DemoTour.dialog() {
        say("open-dialog")
        // The recording has no menu bar to point at: the key and the menu are named instead.
        caption("F1", "Or Help → Keyboard Shortcuts")
        openPanel("Keyboard Shortcuts", DIALOG_W, DIALOG_H)
        say("categories")
        val songsTab = rail("Songs Tab")
        panelNote("Grouped by where they work", "Menus, everywhere, each tab", songsTab, click = true, numbered = true)
        say("managers")
        quietPanelClick(hasText("Menus"))
        point("The managers", "Song Library, Calendar, Converter", hasText("Open Song Library Manager"))
        say("search-actions")
        val search = seek(hasSetTextAction())
        panelEdit("Search", "By name", search, "live")
        pause(0.8)
        search.node.performTextReplacement("")
        say("press-key")
        panelNote("Press key", "Then press a combination", seek(hasText("Press key")), click = true, numbered = true)
        runCatching {
            panelItem(hasTestTag(PRESS_PANEL)).node.performKeyInput { pressKey(Key.F7) }
            ui.waitForIdle()
        }
        pause(1.2)
        quietPanelClick(hasText("Press key"))
        rebind()
        say("save")
        point("OK", "Saves your changes", hasText(" OK", substring = true))
        closePanel()
    }

    /** Songs' F7 moved onto the Bible's F6: the clash, the list of clashes, and the way back. */
    private fun DemoTour.rebind() {
        quietPanelClick(hasText("Global"))
        val chip = seek(hasTestTag("shortcut_chip_SWITCH_TO_SONGS"))
        say("rebind")
        panelNote("Click its keys", "Then press the new ones", chip, click = true, numbered = true)
        record("SWITCH_TO_SONGS", Key.F6)
        say("conflict")
        point("Already used", "Can't be saved until it's sorted out", hasText("Already used by", substring = true))
        say("conflicts-list")
        val conflicts = seek(hasTestTag(CONFLICTS_FILTER))
        panelNote("Conflicts", "Every clash in one list", conflicts, click = true, numbered = true)
        pause(0.8)
        say("fix")
        val bible = seek(hasTestTag("shortcut_chip_SWITCH_TO_BIBLE"))
        panelNote("Give it a new key", "F5 for the Bible tab", bible, click = true, numbered = true)
        record("SWITCH_TO_BIBLE", Key.F5)
        say("fixed")
        point("No conflicts", "OK works again", hasText(" OK", substring = true))
        // The conflicts view holds the rail until it is left; Press key, on and off, leaves it.
        quietPanelClick(hasText("Press key"))
        quietPanelClick(hasText("Press key"))
        quietPanelClick(hasText("Global"))
        say("clear")
        point("Clear", "No keys at all", hasTestTag("shortcut_revert_SWITCH_TO_ANNOUNCEMENTS"))
        say("reset")
        val revert = seek(hasTestTag("shortcut_revert_SWITCH_TO_SONGS"))
        panelNote("Reset", "Back the way it was", revert, click = true, numbered = true)
        say("reset-all")
        panelNote("Reset All to Defaults", "Every shortcut back", seek(hasTestTag(RESET_ALL)), click = true)
    }

    /** Presses [key] into [action]'s row while it is listening for a new combination. */
    private fun DemoTour.record(action: String, key: Key) {
        runCatching {
            panelItem(hasTestTag("shortcut_recording_$action")).node.performKeyInput { pressKey(key) }
            ui.waitForIdle()
        }
        pause(0.4)
    }

    /** A click in the Clear button leaves the tab without the keyboard, and the app says so in a banner. */
    private fun DemoTour.refocus() {
        if (hasNode(hasText("Keyboard focus lost", substring = true))) {
            quietClick(find(hasText("Keyboard focus lost", substring = true)))
        }
    }

    /** [key] pressed off camera; the tab it opens is named in the F-key list, not shown again. */
    private fun DemoTour.switchTab(key: Key, label: String) {
        runCatching { searchField().node.performKeyInput { pressKey(key) } }
        ensureTab(label)
        pause(0.3)
    }

    /** What a key press should have opened, opened anyway if the key went astray. */
    private fun DemoTour.ensureTab(label: String) {
        if (!hasNode(hasText(label) and isSelected()) { it.top < dp(MENU_TOP_DP) }) {
            runCatching { tab(label).node.performClick() }
        }
        ui.waitForIdle()
        settle()
    }

    /** Go Live, pressed off camera after Enter, so the keyboard sits in what is live as it would. */
    private fun DemoTour.quietGoLive() {
        runCatching { goLiveButton().node.performClick() }
        ui.waitForIdle()
        settle()
    }
}

private const val AMAZING_GRACE = 12
private const val DIALOG_W = 900f
private const val DIALOG_H = 720f
private const val MENU_TOP_DP = 60f
private const val PRESS_PANEL = "shortcut_press_panel"
private const val CONFLICTS_FILTER = "shortcut_conflicts_filter"
private const val RESET_ALL = "shortcut_reset_all"
