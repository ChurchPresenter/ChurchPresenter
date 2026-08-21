package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.key_alias_down
import churchpresenter.composeapp.generated.resources.key_alias_left
import churchpresenter.composeapp.generated.resources.key_alias_right
import churchpresenter.composeapp.generated.resources.key_alias_up
import churchpresenter.composeapp.generated.resources.key_mod_alt
import churchpresenter.composeapp.generated.resources.key_mod_ctrl
import churchpresenter.composeapp.generated.resources.key_mod_meta
import churchpresenter.composeapp.generated.resources.key_mod_shift
import churchpresenter.composeapp.generated.resources.key_name_backspace
import churchpresenter.composeapp.generated.resources.key_name_delete
import churchpresenter.composeapp.generated.resources.key_name_end
import churchpresenter.composeapp.generated.resources.key_name_enter
import churchpresenter.composeapp.generated.resources.key_name_escape
import churchpresenter.composeapp.generated.resources.key_name_home
import churchpresenter.composeapp.generated.resources.key_name_insert
import churchpresenter.composeapp.generated.resources.key_name_page_down
import churchpresenter.composeapp.generated.resources.key_name_page_up
import churchpresenter.composeapp.generated.resources.key_name_space
import churchpresenter.composeapp.generated.resources.key_name_tab
import churchpresenter.composeapp.generated.resources.shortcut_unbound
import org.churchpresenter.core.models.shortcuts.KeyChord
import org.churchpresenter.app.churchpresenter.models.ShortcutAction
import org.jetbrains.compose.resources.stringResource

/** Chords of a multi-key binding are shown separated by this, e.g. `← / ↑`. */
private const val CHORD_SEPARATOR = " / "

/**
 * Whether to draw modifiers as the Mac symbols.
 *
 * Read once into a `val` rather than per call. `os.name` is also what skiko latches its host OS
 * from, and tests that fake it must go through `TestSingletons.latchSkikoHostOs()` first — see the
 * `os.name` rule in AGENT.md.
 */
private val isMac: Boolean = System.getProperty("os.name").orEmpty().lowercase().contains("mac")

/**
 * The printable name of a key.
 *
 * Only the keys worth naming are listed; anything else falls back to Compose's own `toString`,
 * which yields a usable "Key: F13"-style name rather than a blank. Arrows and punctuation are
 * symbols and identical in every locale, so they are literals, not resources.
 */
@Composable
private fun keyDisplayName(key: Key): String = when (key) {
    Key.Spacebar -> stringResource(Res.string.key_name_space)
    Key.Escape -> stringResource(Res.string.key_name_escape)
    Key.Enter, Key.NumPadEnter -> stringResource(Res.string.key_name_enter)
    Key.Tab -> stringResource(Res.string.key_name_tab)
    Key.Backspace -> stringResource(Res.string.key_name_backspace)
    Key.Delete -> stringResource(Res.string.key_name_delete)
    Key.Insert -> stringResource(Res.string.key_name_insert)
    Key.MoveHome -> stringResource(Res.string.key_name_home)
    Key.MoveEnd -> stringResource(Res.string.key_name_end)
    Key.PageUp -> stringResource(Res.string.key_name_page_up)
    Key.PageDown -> stringResource(Res.string.key_name_page_down)
    Key.DirectionUp -> "↑"
    Key.DirectionDown -> "↓"
    Key.DirectionLeft -> "←"
    Key.DirectionRight -> "→"
    Key.Period -> "."
    Key.Comma -> ","
    Key.Semicolon -> ";"
    Key.Apostrophe -> "'"
    Key.Slash -> "/"
    Key.Backslash -> "\\"
    Key.LeftBracket -> "["
    Key.RightBracket -> "]"
    Key.Minus -> "-"
    Key.Equals -> "="
    Key.Grave -> "`"
    else -> key.toString().substringAfter(": ", key.toString())
}

/** This chord as the user sees it, e.g. `Ctrl+Shift+Z` — or `⇧⌘Z` on macOS. */
@Composable
fun KeyChord.label(): String = label(useSymbols = isMac)

/**
 * This chord rendered with either the Mac modifier symbols or the spelled-out words.
 *
 * The form is a parameter rather than always following the platform because search has to match
 * both: a Mac user sees `⌃⇧N` but will type "ctrl", and someone reading Windows documentation on a
 * Mac will type the symbol. See [KeyChord.searchText].
 */
@Composable
fun KeyChord.label(useSymbols: Boolean): String {
    val name = keyDisplayName(key)
    return if (useSymbols) {
        // Mac convention: symbols, no separator, and a fixed order regardless of press order.
        buildString {
            if (ctrl) append("⌃")
            if (alt) append("⌥")
            if (shift) append("⇧")
            if (meta) append("⌘")
            append(name)
        }
    } else {
        buildList {
            if (ctrl) add(stringResource(Res.string.key_mod_ctrl))
            if (meta) add(stringResource(Res.string.key_mod_meta))
            if (alt) add(stringResource(Res.string.key_mod_alt))
            if (shift) add(stringResource(Res.string.key_mod_shift))
            add(name)
        }.joinToString("+")
    }
}

/**
 * This chord split into the parts it is drawn as: one per modifier held, then the key itself.
 *
 * The shortcuts dialog draws each part as its own keycap, and that split cannot be recovered from
 * [label] — the macOS form has no separator at all (`⌃⇧N`), so slicing the rendered string would
 * work on Windows and Linux and produce one wide cap on a Mac.
 */
@Composable
fun KeyChord.keyCaps(): List<String> = buildList {
    if (isMac) {
        // Same fixed order as the label, which is the Mac convention regardless of press order.
        if (ctrl) add("⌃")
        if (alt) add("⌥")
        if (shift) add("⇧")
        if (meta) add("⌘")
    } else {
        if (ctrl) add(stringResource(Res.string.key_mod_ctrl))
        if (meta) add(stringResource(Res.string.key_mod_meta))
        if (alt) add(stringResource(Res.string.key_mod_alt))
        if (shift) add(stringResource(Res.string.key_mod_shift))
    }
    add(keyDisplayName(key))
}

/**
 * A typeable name for a key that is otherwise drawn as an un-typeable glyph.
 *
 * Only the four arrows need this. Every other symbol the app binds — `.` `,` `/` `[` and the rest —
 * is a key you can actually press into a search box, so it needs no alias; the arrows are the sole
 * bindings that could not be searched for at all.
 */
@Composable
private fun keySearchAlias(key: Key): String = when (key) {
    Key.DirectionUp -> stringResource(Res.string.key_alias_up)
    Key.DirectionDown -> stringResource(Res.string.key_alias_down)
    Key.DirectionLeft -> stringResource(Res.string.key_alias_left)
    Key.DirectionRight -> stringResource(Res.string.key_alias_right)
    else -> ""
}

/**
 * Everything a user might type to find this chord, lower-cased.
 *
 * Both renderings, a typeable alias for the arrows, and the common names for each modifier —
 * because what is on screen is only one of several things someone will reach for. Without the
 * modifier names the key search would work on Windows and Linux and match nothing on macOS, where
 * every modifier is a symbol; without the arrow aliases an arrow binding could not be found by key
 * on any platform, since `←` is not on the keyboard.
 */
@Composable
fun KeyChord.searchText(): String = buildString {
    append(label(useSymbols = true)).append(' ')
    append(label(useSymbols = false)).append(' ')
    append(keySearchAlias(key)).append(' ')
    if (ctrl) append("ctrl control ⌃ ")
    if (meta) append("meta cmd command ⌘ ")
    if (alt) append("alt option ⌥ ")
    if (shift) append("shift ⇧ ")
}.lowercase()

/** Every chord bound to [action], as searchable text. Empty when the action is unbound. */
@Composable
fun ShortcutMap.searchText(action: ShortcutAction): String {
    val parts = mutableListOf<String>()
    chordsFor(action).forEach { parts.add(it.searchText()) }
    return parts.joinToString(" ")
}

/**
 * Every chord bound to [action], joined — or an empty string when it is unbound.
 *
 * Empty rather than a placeholder so callers can decide: the settings tab shows "Not set", while an
 * inline hint hides itself entirely rather than describing a key that does nothing.
 */
@Composable
fun ShortcutMap.label(action: ShortcutAction): String {
    // Built with an explicit loop, not joinToString: its transform lambda is not a composable
    // context, and KeyChord.label() reads string resources.
    val parts = mutableListOf<String>()
    chordsFor(action).forEach { parts.add(it.label()) }
    return parts.joinToString(CHORD_SEPARATOR)
}

/** [label] with "Not set" substituted, for places that must render something. */
@Composable
fun ShortcutMap.labelOrUnbound(action: ShortcutAction): String =
    label(action).ifEmpty { stringResource(Res.string.shortcut_unbound) }

/**
 * The combined label for a pair of opposed actions, e.g. `←  →` for prev/next.
 *
 * The inline tab hints describe the pair as one phrase ("next/prev image"), so they need the two
 * bindings side by side rather than two separate rows. Returns empty when **both** are unbound, so
 * the caller can drop the phrase.
 */
@Composable
fun ShortcutMap.pairLabel(first: ShortcutAction, second: ShortcutAction): String =
    listOf(label(first), label(second)).filter { it.isNotEmpty() }.joinToString("  ")
