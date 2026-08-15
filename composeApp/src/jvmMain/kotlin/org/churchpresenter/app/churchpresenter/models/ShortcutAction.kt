package org.churchpresenter.app.churchpresenter.models

import androidx.compose.ui.input.key.Key
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.shortcut_category_bible
import churchpresenter.composeapp.generated.resources.shortcut_category_canvas
import churchpresenter.composeapp.generated.resources.shortcut_category_global
import churchpresenter.composeapp.generated.resources.shortcut_category_media
import churchpresenter.composeapp.generated.resources.shortcut_category_menus
import churchpresenter.composeapp.generated.resources.shortcut_category_pictures
import churchpresenter.composeapp.generated.resources.shortcut_category_presentation
import churchpresenter.composeapp.generated.resources.shortcut_category_songs
import churchpresenter.composeapp.generated.resources.shortcut_scope_bible_hint
import churchpresenter.composeapp.generated.resources.shortcut_scope_canvas_hint
import churchpresenter.composeapp.generated.resources.shortcut_scope_global_hint
import churchpresenter.composeapp.generated.resources.shortcut_scope_media_hint
import churchpresenter.composeapp.generated.resources.shortcut_scope_menus_hint
import churchpresenter.composeapp.generated.resources.shortcut_scope_pictures_hint
import churchpresenter.composeapp.generated.resources.shortcut_scope_presentation_hint
import churchpresenter.composeapp.generated.resources.shortcut_scope_songs_hint
import churchpresenter.composeapp.generated.resources.shortcut_description_add_to_schedule
import churchpresenter.composeapp.generated.resources.shortcut_description_blank_output
import churchpresenter.composeapp.generated.resources.shortcut_description_clicker_next
import churchpresenter.composeapp.generated.resources.shortcut_description_clicker_prev
import churchpresenter.composeapp.generated.resources.shortcut_description_close_schedule
import churchpresenter.composeapp.generated.resources.shortcut_description_delete_source
import churchpresenter.composeapp.generated.resources.shortcut_description_escape
import churchpresenter.composeapp.generated.resources.shortcut_description_exit
import churchpresenter.composeapp.generated.resources.shortcut_description_f1_keyboard_shortcuts
import churchpresenter.composeapp.generated.resources.shortcut_description_f10_media
import churchpresenter.composeapp.generated.resources.shortcut_description_f11_lower_third
import churchpresenter.composeapp.generated.resources.shortcut_description_f12_announcements
import churchpresenter.composeapp.generated.resources.shortcut_description_f6_bible
import churchpresenter.composeapp.generated.resources.shortcut_description_f7_songs
import churchpresenter.composeapp.generated.resources.shortcut_description_f8_pictures
import churchpresenter.composeapp.generated.resources.shortcut_description_f9_presentation
import churchpresenter.composeapp.generated.resources.shortcut_description_media_play_pause
import churchpresenter.composeapp.generated.resources.shortcut_description_mute
import churchpresenter.composeapp.generated.resources.shortcut_description_nav_down
import churchpresenter.composeapp.generated.resources.shortcut_description_nav_up
import churchpresenter.composeapp.generated.resources.shortcut_description_new_schedule
import churchpresenter.composeapp.generated.resources.shortcut_description_next_chapter
import churchpresenter.composeapp.generated.resources.shortcut_description_next_image
import churchpresenter.composeapp.generated.resources.shortcut_description_next_section
import churchpresenter.composeapp.generated.resources.shortcut_description_next_slide
import churchpresenter.composeapp.generated.resources.shortcut_description_next_song
import churchpresenter.composeapp.generated.resources.shortcut_description_next_verse
import churchpresenter.composeapp.generated.resources.shortcut_description_open_schedule
import churchpresenter.composeapp.generated.resources.shortcut_description_play_pause
import churchpresenter.composeapp.generated.resources.shortcut_description_prev_chapter
import churchpresenter.composeapp.generated.resources.shortcut_description_prev_image
import churchpresenter.composeapp.generated.resources.shortcut_description_prev_section
import churchpresenter.composeapp.generated.resources.shortcut_description_prev_slide
import churchpresenter.composeapp.generated.resources.shortcut_description_prev_song
import churchpresenter.composeapp.generated.resources.shortcut_description_prev_verse
import churchpresenter.composeapp.generated.resources.shortcut_description_redo
import churchpresenter.composeapp.generated.resources.shortcut_description_remove_from_schedule
import churchpresenter.composeapp.generated.resources.shortcut_description_save_schedule
import churchpresenter.composeapp.generated.resources.shortcut_description_save_schedule_as
import churchpresenter.composeapp.generated.resources.shortcut_description_settings
import churchpresenter.composeapp.generated.resources.shortcut_description_undo
import org.churchpresenter.app.churchpresenter.tabs.Tabs
import org.jetbrains.compose.resources.StringResource

/**
 * Where a shortcut is dispatched from, which is what decides whether two bindings can collide.
 *
 * [MENU] is deliberately separate from [GLOBAL]. Menu accelerators are dispatched by the Compose
 * `MenuBar` before focus-based handlers ever see the event, so `Delete` can mean *Remove from
 * Schedule* in the Edit menu and *Delete Selected Source* in the Canvas tab at the same time —
 * which is exactly what the app does today. Folding the two scopes together would report that
 * shipped pair as a conflict.
 *
 * [hintRes] is the one-line answer to "when does this apply?", shown under the heading in the
 * shortcuts dialog. It belongs to the scope rather than to the dialog because it *describes the
 * dispatch rule above* — the same rule that decides what collides with what.
 */
enum class ShortcutScope(val titleRes: StringResource, val hintRes: StringResource) {
    MENU(Res.string.shortcut_category_menus, Res.string.shortcut_scope_menus_hint),
    GLOBAL(Res.string.shortcut_category_global, Res.string.shortcut_scope_global_hint),
    BIBLE(Res.string.shortcut_category_bible, Res.string.shortcut_scope_bible_hint),
    SONGS(Res.string.shortcut_category_songs, Res.string.shortcut_scope_songs_hint),
    PICTURES(Res.string.shortcut_category_pictures, Res.string.shortcut_scope_pictures_hint),
    PRESENTATION(Res.string.shortcut_category_presentation, Res.string.shortcut_scope_presentation_hint),
    MEDIA(Res.string.shortcut_category_media, Res.string.shortcut_scope_media_hint),
    CANVAS(Res.string.shortcut_category_canvas, Res.string.shortcut_scope_canvas_hint);

    /**
     * Whether a binding in this scope competes with one in [other].
     *
     * A tab scope only competes with itself and with [GLOBAL], because a tab handler and the root
     * handler both see the event while that tab has focus. Two different tab scopes never do —
     * `Space` means play/pause in both Media and Pictures and always has.
     */
    fun overlaps(other: ShortcutScope): Boolean =
        this == other || this == GLOBAL || other == GLOBAL
}

/**
 * Every rebindable action, with the binding it ships with.
 *
 * The defaults here are the single source of truth for what the app responds to. Before this
 * existed each binding was a literal `Key.X` comparison at its handler and a separate hand-written
 * string in the shortcuts dialog, and the two drifted — the dialog never mentioned Page Up/Down,
 * `B`, or `.` at all.
 *
 * [defaults] is a list because several actions genuinely have more than one key: next-slide is `→`
 * *and* `↓`, delete-source is `Delete` *and* `Backspace`. An empty override list means the user
 * unbound the action, which is distinct from having no override.
 *
 * Sequences that are not shortcuts stay out of here on purpose: the easter eggs, Enter-to-commit in
 * text fields, Crossword letter entry, and the Web tab's key forwarding to the embedded browser.
 */
enum class ShortcutAction(
    val scope: ShortcutScope,
    val descriptionRes: StringResource,
    val defaults: List<KeyChord>,
    val targetTab: Tabs? = null,
) {
    // ── Menu accelerators ────────────────────────────────────────────────────
    NEW_SCHEDULE(ShortcutScope.MENU, Res.string.shortcut_description_new_schedule,
        listOf(KeyChord.of(Key.N, ctrl = true, shift = true))),
    OPEN_SCHEDULE(ShortcutScope.MENU, Res.string.shortcut_description_open_schedule,
        listOf(KeyChord.of(Key.O, ctrl = true))),
    SAVE_SCHEDULE(ShortcutScope.MENU, Res.string.shortcut_description_save_schedule,
        listOf(KeyChord.of(Key.S, ctrl = true))),
    SAVE_SCHEDULE_AS(ShortcutScope.MENU, Res.string.shortcut_description_save_schedule_as,
        emptyList()),
    CLOSE_SCHEDULE(ShortcutScope.MENU, Res.string.shortcut_description_close_schedule,
        listOf(KeyChord.of(Key.W, ctrl = true))),
    EXIT(ShortcutScope.MENU, Res.string.shortcut_description_exit,
        listOf(KeyChord.of(Key.Q, ctrl = true))),
    OPEN_SETTINGS(ShortcutScope.MENU, Res.string.shortcut_description_settings,
        listOf(KeyChord.of(Key.T, ctrl = true))),
    KEYBOARD_SHORTCUTS(ShortcutScope.MENU, Res.string.shortcut_description_f1_keyboard_shortcuts,
        listOf(KeyChord.of(Key.F1))),
    ADD_TO_SCHEDULE(ShortcutScope.MENU, Res.string.shortcut_description_add_to_schedule,
        listOf(KeyChord.of(Key.F2))),
    REMOVE_FROM_SCHEDULE(ShortcutScope.MENU, Res.string.shortcut_description_remove_from_schedule,
        listOf(KeyChord.of(Key.Delete))),

    // ── Global ───────────────────────────────────────────────────────────────
    CLEAR_OUTPUT(ShortcutScope.GLOBAL, Res.string.shortcut_description_escape,
        listOf(KeyChord.of(Key.Escape))),
    UNDO(ShortcutScope.GLOBAL, Res.string.shortcut_description_undo,
        listOf(KeyChord.of(Key.Z, ctrl = true))),
    REDO(ShortcutScope.GLOBAL, Res.string.shortcut_description_redo,
        listOf(KeyChord.of(Key.Z, ctrl = true, shift = true))),
    CLICKER_NEXT(ShortcutScope.GLOBAL, Res.string.shortcut_description_clicker_next,
        listOf(KeyChord.of(Key.PageDown))),
    CLICKER_PREVIOUS(ShortcutScope.GLOBAL, Res.string.shortcut_description_clicker_prev,
        listOf(KeyChord.of(Key.PageUp))),
    SWITCH_TO_BIBLE(ShortcutScope.GLOBAL, Res.string.shortcut_description_f6_bible,
        listOf(KeyChord.of(Key.F6)), Tabs.BIBLE),
    SWITCH_TO_SONGS(ShortcutScope.GLOBAL, Res.string.shortcut_description_f7_songs,
        listOf(KeyChord.of(Key.F7)), Tabs.SONGS),
    SWITCH_TO_PICTURES(ShortcutScope.GLOBAL, Res.string.shortcut_description_f8_pictures,
        listOf(KeyChord.of(Key.F8)), Tabs.PICTURES),
    SWITCH_TO_PRESENTATION(ShortcutScope.GLOBAL, Res.string.shortcut_description_f9_presentation,
        listOf(KeyChord.of(Key.F9)), Tabs.PRESENTATION),
    SWITCH_TO_MEDIA(ShortcutScope.GLOBAL, Res.string.shortcut_description_f10_media,
        listOf(KeyChord.of(Key.F10)), Tabs.MEDIA),
    SWITCH_TO_LOWER_THIRD(ShortcutScope.GLOBAL, Res.string.shortcut_description_f11_lower_third,
        listOf(KeyChord.of(Key.F11)), Tabs.LOWER_THIRD),
    SWITCH_TO_ANNOUNCEMENTS(ShortcutScope.GLOBAL, Res.string.shortcut_description_f12_announcements,
        listOf(KeyChord.of(Key.F12)), Tabs.ANNOUNCEMENTS),

    // ── Bible tab ────────────────────────────────────────────────────────────
    BIBLE_PREVIOUS_VERSE(ShortcutScope.BIBLE, Res.string.shortcut_description_prev_verse,
        listOf(KeyChord.of(Key.DirectionUp))),
    BIBLE_NEXT_VERSE(ShortcutScope.BIBLE, Res.string.shortcut_description_next_verse,
        listOf(KeyChord.of(Key.DirectionDown))),
    BIBLE_PREVIOUS_CHAPTER(ShortcutScope.BIBLE, Res.string.shortcut_description_prev_chapter,
        listOf(KeyChord.of(Key.DirectionLeft))),
    BIBLE_NEXT_CHAPTER(ShortcutScope.BIBLE, Res.string.shortcut_description_next_chapter,
        listOf(KeyChord.of(Key.DirectionRight))),

    // ── Songs tab ────────────────────────────────────────────────────────────
    SONGS_PREVIOUS_SECTION(ShortcutScope.SONGS, Res.string.shortcut_description_prev_section,
        listOf(KeyChord.of(Key.DirectionUp))),
    SONGS_NEXT_SECTION(ShortcutScope.SONGS, Res.string.shortcut_description_next_section,
        listOf(KeyChord.of(Key.DirectionDown))),
    SONGS_PREVIOUS(ShortcutScope.SONGS, Res.string.shortcut_description_prev_song,
        listOf(KeyChord.of(Key.DirectionLeft))),
    SONGS_NEXT(ShortcutScope.SONGS, Res.string.shortcut_description_next_song,
        listOf(KeyChord.of(Key.DirectionRight))),

    // ── Pictures tab ─────────────────────────────────────────────────────────
    PICTURES_PREVIOUS(ShortcutScope.PICTURES, Res.string.shortcut_description_prev_image,
        listOf(KeyChord.of(Key.DirectionLeft))),
    PICTURES_NEXT(ShortcutScope.PICTURES, Res.string.shortcut_description_next_image,
        listOf(KeyChord.of(Key.DirectionRight))),
    PICTURES_ROW_UP(ShortcutScope.PICTURES, Res.string.shortcut_description_nav_up,
        listOf(KeyChord.of(Key.DirectionUp))),
    PICTURES_ROW_DOWN(ShortcutScope.PICTURES, Res.string.shortcut_description_nav_down,
        listOf(KeyChord.of(Key.DirectionDown))),
    PICTURES_PLAY_PAUSE(ShortcutScope.PICTURES, Res.string.shortcut_description_play_pause,
        listOf(KeyChord.of(Key.Spacebar))),

    // ── Presentation tab ─────────────────────────────────────────────────────
    PRESENTATION_PREVIOUS(ShortcutScope.PRESENTATION, Res.string.shortcut_description_prev_slide,
        listOf(KeyChord.of(Key.DirectionLeft), KeyChord.of(Key.DirectionUp))),
    PRESENTATION_NEXT(ShortcutScope.PRESENTATION, Res.string.shortcut_description_next_slide,
        listOf(KeyChord.of(Key.DirectionRight), KeyChord.of(Key.DirectionDown))),
    PRESENTATION_PLAY_PAUSE(ShortcutScope.PRESENTATION, Res.string.shortcut_description_play_pause,
        listOf(KeyChord.of(Key.Spacebar))),
    PRESENTATION_BLANK(ShortcutScope.PRESENTATION, Res.string.shortcut_description_blank_output,
        listOf(KeyChord.of(Key.B), KeyChord.of(Key.Period))),

    // ── Media tab ────────────────────────────────────────────────────────────
    MEDIA_PLAY_PAUSE(ShortcutScope.MEDIA, Res.string.shortcut_description_media_play_pause,
        listOf(KeyChord.of(Key.Spacebar))),
    MEDIA_MUTE(ShortcutScope.MEDIA, Res.string.shortcut_description_mute,
        listOf(KeyChord.of(Key.M))),

    // ── Canvas tab ───────────────────────────────────────────────────────────
    CANVAS_DELETE_SOURCE(ShortcutScope.CANVAS, Res.string.shortcut_description_delete_source,
        listOf(KeyChord.of(Key.Delete), KeyChord.of(Key.Backspace))),
}
