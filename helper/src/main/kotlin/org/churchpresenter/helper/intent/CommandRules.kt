package org.churchpresenter.helper.intent

import org.churchpresenter.helper.action.ContentScope
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.action.Persistence
import org.churchpresenter.helper.helperText
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_clarify_scope

// The rules that ask for something to be done. Each returns null when the request is not its own.

internal fun undoRule(r: Request): Resolution? =
    if (r.first in Vocabulary.UNDO || r.says("take that back")) act(HelperAction.UndoLast) else null

internal fun setupWizardRule(r: Request): Resolution? =
    if (r.says("setup wizard", "set up wizard", "getting started", "first run", "start over")) {
        act(HelperAction.OpenSetupWizard)
    } else {
        null
    }

internal fun displaySetupRule(r: Request): Resolution? {
    if (r.has(Vocabulary.SETTINGS) || r.isQuestion && !r.says("set up")) return null
    if (r.says("identify") && r.has(Vocabulary.SCREEN) || r.hasPhrase(Vocabulary.WHICH_SCREEN)) {
        return act(HelperAction.IdentifyScreens)
    }
    val aboutScreens = r.has(Vocabulary.SCREEN) || r.says("second screen")
    val settingUp = r.has(Vocabulary.SETUP) || r.says("set up") || r.first == "help" ||
        r.hasPhrase(Vocabulary.SCREEN_TROUBLE)
    val showOrClear = r.has(Vocabulary.CLEAR) || r.first in setOf("show", "hide")
    return if (aboutScreens && settingUp && !showOrClear) act(HelperAction.StartDisplaySetup) else null
}

internal fun backgroundColorRule(r: Request): Resolution? {
    if (!r.has(Vocabulary.BACKGROUND)) return null
    val (name, hex) = ColorNames.find(r.words, r.text) ?: return null
    val persistence = if (r.hasPhrase(Vocabulary.TEMPORARY)) Persistence.THIS_SERVICE else Persistence.SAVED
    return scoped(r) { HelperAction.SetBackgroundColor(it, hex, name, persistence) }
}

internal fun fontSizeRule(r: Request): Resolution? {
    val direction = when {
        r.has(Vocabulary.BIGGER) || r.hasPhrase(Vocabulary.TOO_SMALL) -> 1
        r.has(Vocabulary.SMALLER) || r.hasPhrase(Vocabulary.TOO_BIG) -> -1
        else -> return null
    }
    val aboutText = r.has(Vocabulary.FONT) || r.has(Vocabulary.SONG) || r.has(Vocabulary.BIBLE)
    return if (aboutText) scoped(r) { HelperAction.ChangeFontSize(it, direction) } else null
}

/** [build] for the scope the request names, else the open tab's, else a question. */
private fun scoped(r: Request, build: (ContentScope) -> HelperAction): Resolution {
    val scope = when {
        r.has(Vocabulary.BOTH) || r.has(Vocabulary.SONG) && r.has(Vocabulary.BIBLE) -> ContentScope.ALL
        r.has(Vocabulary.SONG) -> ContentScope.SONG
        r.has(Vocabulary.BIBLE) -> ContentScope.BIBLE
        r.context.currentTab == Tabs.SONGS -> ContentScope.SONG
        r.context.currentTab == Tabs.BIBLE -> ContentScope.BIBLE
        else -> null
    }
    return if (scope != null) {
        act(build(scope))
    } else {
        Resolution.Clarify(helperText(Res.string.helper_clarify_scope), ContentScope.entries.map(build))
    }
}

internal fun shortcutRule(r: Request): Resolution? {
    if (!r.says("shortcut", "shortcuts", "hotkey", "hotkeys", "keyboard")) return null
    val action = when {
        r.has(Vocabulary.CLEAR) -> ShortcutAction.CLEAR_OUTPUT
        r.says("take", "go live") -> ShortcutAction.TAKE
        r.has(Vocabulary.NEXT) && r.has(Vocabulary.BIBLE) -> ShortcutAction.BIBLE_NEXT_VERSE
        r.has(Vocabulary.NEXT) && r.has(Vocabulary.SONG) -> ShortcutAction.SONGS_NEXT_SECTION
        r.has(Vocabulary.NEXT) -> ShortcutAction.CLICKER_NEXT
        r.has(Vocabulary.PREVIOUS) -> ShortcutAction.CLICKER_PREVIOUS
        r.has(Vocabulary.UNDO) -> ShortcutAction.UNDO
        else -> null
    }
    return act(action?.let { HelperAction.ShowShortcut(it) } ?: HelperAction.OpenKeyboardShortcuts)
}

internal fun clearRule(r: Request): Resolution? =
    if (r.has(Vocabulary.CLEAR) || r.says("black out", "blackout")) act(HelperAction.ClearOutput) else null

internal fun takeRule(r: Request): Resolution? =
    if (r.first == "take" || r.says("go live")) act(HelperAction.Take) else null

internal fun outputsRule(r: Request): Resolution? {
    val toggling = r.first in setOf("show", "hide", "toggle") || r.says("turn on", "turn off")
    return if (toggling && r.has(Vocabulary.SCREEN)) act(HelperAction.ToggleOutputWindows) else null
}
