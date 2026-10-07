package org.churchpresenter.helper.suggest

import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.helperText
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.utils.ShortcutMap
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_tip_auto_fit
import org.churchpresenter.strings.generated.resources.helper_tip_identify
import org.churchpresenter.strings.generated.resources.helper_tip_profiles
import org.churchpresenter.strings.generated.resources.helper_tip_quick_background
import org.churchpresenter.strings.generated.resources.helper_tip_remote
import org.churchpresenter.strings.generated.resources.helper_tip_schedule_drag
import org.churchpresenter.strings.generated.resources.helper_tip_shortcut
import org.churchpresenter.strings.generated.resources.helper_tip_tell_me
import org.churchpresenter.strings.generated.resources.helper_tip_verse_typing

/** A tip of the day, with what the helper can open to show it. */
data class Tip(val text: HelperText, val action: HelperAction? = null)

/** The shortcuts worth a tip — the everyday ones, not the whole list. */
private val TIP_SHORTCUTS = listOf(
    ShortcutAction.CLEAR_OUTPUT,
    ShortcutAction.TAKE,
    ShortcutAction.SONGS_NEXT_SECTION,
    ShortcutAction.BIBLE_NEXT_VERSE,
    ShortcutAction.SWITCH_TO_BIBLE,
    ShortcutAction.SWITCH_TO_SONGS,
    ShortcutAction.ADD_TO_SCHEDULE,
    ShortcutAction.UNDO,
    ShortcutAction.QUICK_BACKGROUND_RESET,
)

private val FEATURE_TIPS = listOf(
    Tip(helperText(Res.string.helper_tip_tell_me)),
    Tip(helperText(Res.string.helper_tip_identify), HelperAction.StartDisplaySetup),
    Tip(helperText(Res.string.helper_tip_quick_background)),
    Tip(helperText(Res.string.helper_tip_profiles), HelperAction.OpenSettings(SettingsPage.PROFILES)),
    Tip(helperText(Res.string.helper_tip_auto_fit)),
    Tip(helperText(Res.string.helper_tip_schedule_drag)),
    Tip(helperText(Res.string.helper_tip_verse_typing)),
    Tip(helperText(Res.string.helper_tip_remote), HelperAction.OpenSettings(SettingsPage.SERVER)),
)

/**
 * Every tip, feature tips and shortcut tips taking turns. A shortcut tip names the key the operator
 * actually has bound, and a shortcut with none bound is left out rather than shown as nothing.
 */
fun allTips(shortcuts: ShortcutMap): List<Tip> {
    val shortcutTips = TIP_SHORTCUTS.mapNotNull { action ->
        if (shortcuts.chordsFor(action).isEmpty()) return@mapNotNull null
        val what = helperText(action.descriptionRes)
        val text = helperText(Res.string.helper_tip_shortcut, what, HelperText.KeyFor(action))
        Tip(text, HelperAction.ShowShortcut(action))
    }
    val interleaved = mutableListOf<Tip>()
    val longest = maxOf(FEATURE_TIPS.size, shortcutTips.size)
    for (i in 0 until longest) {
        FEATURE_TIPS.getOrNull(i)?.let(interleaved::add)
        shortcutTips.getOrNull(i)?.let(interleaved::add)
    }
    return interleaved
}

/** The tip at rotation position [index], wrapping round. */
fun tipAt(tips: List<Tip>, index: Int): Tip? = if (tips.isEmpty()) null else tips[Math.floorMod(index, tips.size)]
