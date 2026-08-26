package org.churchpresenter.app.churchpresenter

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Format placeholders in the English strings must be positional.
 *
 * Compose Multiplatform substitutes only the `%<n>$s` / `%<n>$d` form. A bare `%s` is passed
 * through untouched, so `stringResource(Res.string.x, arg)` silently renders the literal text
 * `%s` — it does not throw, and nothing fails until someone reads the screen.
 *
 * That is exactly what happened: three strings added with the shortcuts feature used bare `%s`,
 * and the Undo/Redo tooltips shipped reading `Undo (%s)`. This test is the cheap guard that would
 * have caught it, and it covers the other fifty-odd argument-taking strings too.
 *
 * Only the default English file is checked. Translations are managed separately and this suite must
 * not encourage editing them.
 */
class StringResourceFormatTest {

    private val stringsFile = File("src/jvmMain/composeResources/values/strings.xml")

    /** `name="..."` and the element body, which is all this test needs from the XML. */
    private val entryPattern = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

    /**
     * A `%` that starts a conversion but is not positional.
     *
     * `%%` is an escaped literal percent and is skipped by consuming it first. Everything the app
     * actually uses is `s` or `d`; a bare `%` followed by anything else (a percent sign next to a
     * word, as in "100% opacity") is not a conversion and is not flagged.
     */
    private val nonPositional = Regex("""%%|%(?![0-9]+\$)([sd])""")

    private fun entries(): List<Pair<String, String>> {
        assertTrue(stringsFile.isFile, "expected the English strings at ${stringsFile.absolutePath}")
        return entryPattern.findAll(stringsFile.readText())
            .map { it.groupValues[1] to it.groupValues[2] }
            .toList()
    }

    @Test
    fun `every format placeholder is positional`() {
        val offenders = entries().filter { (_, body) ->
            nonPositional.findAll(body).any { it.value != "%%" }
        }.map { it.first }

        assertEquals(
            emptyList(),
            offenders,
            "these strings use a bare %s or %d, which Compose renders literally — use %1\$s instead",
        )
    }

    /**
     * Strings allowed to name a key, and why.
     *
     * Two kinds: pointer gestures, which are not rebindable and so are correctly hand-written; and
     * the label vocabulary the registry itself renders bindings *from* (`utils/ShortcutLabels.kt`),
     * which has to name keys — that is its whole job.
     */
    private val mayNameAKey = setOf(
        // Pointer gestures — no keyboard binding behind them.
        "shortcut_key_double_click", "shortcut_key_right_click", "shortcut_key_shift_drag",
        "shortcut_description_go_live", "shortcut_description_context_menu",
        "shortcut_description_reorder_item", "shortcut_description_reorder_image",
        "bible_verse_selection_hint", "hold_live_modifier_hint", "pictures_reorder_hint",
        // The registry's own vocabulary.
        "key_mod_ctrl", "key_mod_shift", "key_mod_alt", "key_mod_meta",
        "key_name_space", "key_name_escape", "key_name_enter", "key_name_tab",
        "key_name_backspace", "key_name_delete", "key_name_insert", "key_name_home",
        "key_name_end", "key_name_page_up", "key_name_page_down",
        // The capture dialog, which is literally asking for a key press.
        "shortcut_capture_title", "shortcut_capture_prompt",
        // Names an on-screen arrow *button*, not a key.
        "bible_translation_order_hint",
        // Escape dismisses a focusable Compose Popup. That is the toolkit's own behaviour, not an
        // entry in ShortcutMap, so there is no binding to render it from and none to go stale.
        "bible_cross_references_dismiss_hint",
        // Same shape: the font picker's ↑↓/⏎/esc are read by the panel's own `onPreviewKeyEvent`
        // (`FontSettingsDropdown.kt`), which names `Key.DirectionUp`/`Enter`/`Escape` outright.
        // They are the list-box keys any menu answers to rather than an app shortcut, so there is
        // no binding to render them from and none that can go stale under them.
        "font_picker_keys",
    )

    /**
     * The shapes a stale key reference takes.
     *
     * `→` alone is deliberately **not** matched: the app uses it as a menu-path separator
     * ("Settings → General → About") and a direction label ("Slide Along Top (L→R)") far more often
     * than as a key. `←`, `↑` and `↓` are only ever keys, so a right-arrow that means a key is
     * caught by the company it keeps.
     */
    private val namesAKey = Regex("""[←↑↓]|\b(Ctrl|Cmd|Shift|Alt|Esc|Spacebar|PgUp|PgDn)\b|[⌘⌃⌥⇧]""")

    /**
     * No string may name a keyboard key unless it is on the allow-list.
     *
     * This is the guard for the miss that prompted it: `line_navigation_hint` read
     * "Use ← → to navigate lines, ↑ ↓ for verses" as a plain literal and went on saying that after
     * the keys became rebindable. It was missed by eye because the two hints already converted are
     * named `*_arrow_key_hint` and this one is not — a name-shaped search could never have found it,
     * but a content-shaped one does.
     *
     * Adding a string here is a deliberate act: either render it from `ShortcutMap`, or add it to
     * [mayNameAKey] with a reason.
     */
    @Test
    fun `no string hardcodes a key that the user can rebind`() {
        val offenders = entries()
            .filter { (name, _) -> name !in mayNameAKey }
            .filter { (_, body) -> namesAKey.containsMatchIn(body) }
            .map { (name, body) -> "$name = \"$body\"" }

        assertEquals(
            emptyList(),
            offenders,
            "render these from ShortcutMap, or allow-list them with a reason",
        )
    }

    @Test
    fun `the allow-list has no stale entries`() {
        // A name left behind after its string was deleted would silently widen the guard.
        val known = entries().map { it.first }.toSet()

        assertEquals(emptyList(), (mayNameAKey - known).sorted())
    }

    @Test
    fun `the file was actually read, so a bad path cannot make this suite vacuous`() {
        val found = entries()

        assertTrue(found.size > 500, "only ${found.size} strings parsed — the regex or path is wrong")
        assertTrue(found.any { it.first == "tooltip_undo" }, "expected a known string to be present")
    }

    @Test
    fun `a positional placeholder is accepted and a bare one is rejected`() {
        // Pins the matcher itself: a test that silently matched nothing would pass for ever.
        assertTrue(nonPositional.findAll("Undo (%s)").any { it.value != "%%" })
        assertTrue(nonPositional.findAll("Screen %1\$d").none { it.value != "%%" })
        assertTrue(nonPositional.findAll("100%% brightness").none { it.value != "%%" })
        assertTrue(nonPositional.findAll("Zoom 100% now").none { it.value != "%%" })
    }
}
