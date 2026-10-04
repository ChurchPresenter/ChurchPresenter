package org.churchpresenter.sharedui.models

import androidx.compose.ui.input.key.Key
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.shortcut_category_bible
import org.churchpresenter.strings.generated.resources.shortcut_category_canvas
import org.churchpresenter.strings.generated.resources.shortcut_category_global
import org.churchpresenter.strings.generated.resources.shortcut_category_media
import org.churchpresenter.strings.generated.resources.shortcut_category_menus
import org.churchpresenter.strings.generated.resources.shortcut_category_pictures
import org.churchpresenter.strings.generated.resources.shortcut_category_presentation
import org.churchpresenter.strings.generated.resources.shortcut_category_songs
import org.churchpresenter.strings.generated.resources.shortcut_scope_bible_hint
import org.churchpresenter.strings.generated.resources.shortcut_scope_canvas_hint
import org.churchpresenter.strings.generated.resources.shortcut_scope_global_hint
import org.churchpresenter.strings.generated.resources.shortcut_scope_media_hint
import org.churchpresenter.strings.generated.resources.shortcut_scope_menus_hint
import org.churchpresenter.strings.generated.resources.shortcut_scope_pictures_hint
import org.churchpresenter.strings.generated.resources.shortcut_scope_presentation_hint
import org.churchpresenter.strings.generated.resources.shortcut_scope_songs_hint
import org.churchpresenter.strings.generated.resources.shortcut_description_add_to_schedule
import org.churchpresenter.strings.generated.resources.shortcut_description_blank_output
import org.churchpresenter.strings.generated.resources.shortcut_description_clicker_next
import org.churchpresenter.strings.generated.resources.shortcut_description_clicker_prev
import org.churchpresenter.strings.generated.resources.shortcut_description_close_schedule
import org.churchpresenter.strings.generated.resources.shortcut_description_open_calendar_manager
import org.churchpresenter.strings.generated.resources.shortcut_description_open_converter
import org.churchpresenter.strings.generated.resources.shortcut_description_open_song_library
import org.churchpresenter.strings.generated.resources.shortcut_description_delete_source
import org.churchpresenter.strings.generated.resources.shortcut_description_clear_announcement
import org.churchpresenter.strings.generated.resources.shortcut_description_clear_captions
import org.churchpresenter.strings.generated.resources.shortcut_description_clear_lower_third
import org.churchpresenter.strings.generated.resources.shortcut_description_escape
import org.churchpresenter.strings.generated.resources.shortcut_description_exit
import org.churchpresenter.strings.generated.resources.shortcut_description_f1_keyboard_shortcuts
import org.churchpresenter.strings.generated.resources.shortcut_description_f10_media
import org.churchpresenter.strings.generated.resources.shortcut_description_f11_lower_third
import org.churchpresenter.strings.generated.resources.shortcut_description_f12_announcements
import org.churchpresenter.strings.generated.resources.shortcut_description_f6_bible
import org.churchpresenter.strings.generated.resources.shortcut_description_f7_songs
import org.churchpresenter.strings.generated.resources.shortcut_description_f8_pictures
import org.churchpresenter.strings.generated.resources.shortcut_description_f9_presentation
import org.churchpresenter.strings.generated.resources.shortcut_description_media_play_pause
import org.churchpresenter.strings.generated.resources.shortcut_description_mute
import org.churchpresenter.strings.generated.resources.shortcut_description_nav_down
import org.churchpresenter.strings.generated.resources.shortcut_description_nav_up
import org.churchpresenter.strings.generated.resources.shortcut_description_new_schedule
import org.churchpresenter.strings.generated.resources.shortcut_description_next_chapter
import org.churchpresenter.strings.generated.resources.shortcut_description_next_image
import org.churchpresenter.strings.generated.resources.shortcut_description_next_section
import org.churchpresenter.strings.generated.resources.shortcut_description_next_slide
import org.churchpresenter.strings.generated.resources.shortcut_description_next_song
import org.churchpresenter.strings.generated.resources.shortcut_description_next_verse
import org.churchpresenter.strings.generated.resources.shortcut_description_open_schedule
import org.churchpresenter.strings.generated.resources.shortcut_description_play_pause
import org.churchpresenter.strings.generated.resources.shortcut_description_prev_chapter
import org.churchpresenter.strings.generated.resources.shortcut_description_prev_image
import org.churchpresenter.strings.generated.resources.shortcut_description_prev_section
import org.churchpresenter.strings.generated.resources.shortcut_description_prev_slide
import org.churchpresenter.strings.generated.resources.shortcut_description_prev_song
import org.churchpresenter.strings.generated.resources.shortcut_description_prev_verse
import org.churchpresenter.strings.generated.resources.shortcut_description_redo
import org.churchpresenter.strings.generated.resources.shortcut_description_remove_from_schedule
import org.churchpresenter.strings.generated.resources.shortcut_description_save_schedule
import org.churchpresenter.strings.generated.resources.shortcut_description_save_schedule_as
import org.churchpresenter.strings.generated.resources.shortcut_description_settings
import org.churchpresenter.strings.generated.resources.shortcut_description_quick_background_1
import org.churchpresenter.strings.generated.resources.shortcut_description_quick_background_2
import org.churchpresenter.strings.generated.resources.shortcut_description_quick_background_3
import org.churchpresenter.strings.generated.resources.shortcut_description_quick_background_4
import org.churchpresenter.strings.generated.resources.shortcut_description_quick_background_5
import org.churchpresenter.strings.generated.resources.shortcut_description_quick_background_6
import org.churchpresenter.strings.generated.resources.shortcut_description_quick_background_7
import org.churchpresenter.strings.generated.resources.shortcut_description_quick_background_8
import org.churchpresenter.strings.generated.resources.shortcut_description_quick_background_9
import org.churchpresenter.strings.generated.resources.shortcut_description_quick_background_10
import org.churchpresenter.strings.generated.resources.shortcut_description_quick_background_reset
import org.churchpresenter.strings.generated.resources.shortcut_description_undo
import org.churchpresenter.core.models.shortcuts.KeyChord
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
    OPEN_SONG_LIBRARY(ShortcutScope.MENU, Res.string.shortcut_description_open_song_library,
        listOf(KeyChord.of(Key.L, ctrl = true, shift = true))),
    OPEN_CONVERTER(ShortcutScope.MENU, Res.string.shortcut_description_open_converter,
        listOf(KeyChord.of(Key.K, ctrl = true, shift = true))),
    OPEN_CALENDAR_MANAGER(ShortcutScope.MENU, Res.string.shortcut_description_open_calendar_manager,
        listOf(KeyChord.of(Key.D, ctrl = true, shift = true))),
    ADD_TO_SCHEDULE(ShortcutScope.MENU, Res.string.shortcut_description_add_to_schedule,
        listOf(KeyChord.of(Key.F2))),
    REMOVE_FROM_SCHEDULE(ShortcutScope.MENU, Res.string.shortcut_description_remove_from_schedule,
        listOf(KeyChord.of(Key.Delete))),

    // ── Global ───────────────────────────────────────────────────────────────
    CLEAR_OUTPUT(ShortcutScope.GLOBAL, Res.string.shortcut_description_escape,
        listOf(KeyChord.of(Key.Escape))),
    // Take one overlay down, leaving the slide under it. Unbound until someone picks keys for them.
    CLEAR_LOWER_THIRD(ShortcutScope.GLOBAL, Res.string.shortcut_description_clear_lower_third, emptyList()),
    CLEAR_CAPTIONS(ShortcutScope.GLOBAL, Res.string.shortcut_description_clear_captions, emptyList()),
    CLEAR_ANNOUNCEMENT(ShortcutScope.GLOBAL, Res.string.shortcut_description_clear_announcement, emptyList()),
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

    /**
     * The quick-background tray's slots.
     *
     * Ctrl/Cmd rather than a bare digit: nothing in the app binds a digit today, but a bare one
     * would collide with typing in the song and Bible search fields, and a live control should not
     * depend on the focus stand-down behaving.
     */
    QUICK_BACKGROUND_RESET(ShortcutScope.GLOBAL, Res.string.shortcut_description_quick_background_reset,
        listOf(KeyChord.of(Key.Zero, ctrl = true))),
    QUICK_BACKGROUND_1(ShortcutScope.GLOBAL, Res.string.shortcut_description_quick_background_1,
        listOf(KeyChord.of(Key.One, ctrl = true))),
    QUICK_BACKGROUND_2(ShortcutScope.GLOBAL, Res.string.shortcut_description_quick_background_2,
        listOf(KeyChord.of(Key.Two, ctrl = true))),
    QUICK_BACKGROUND_3(ShortcutScope.GLOBAL, Res.string.shortcut_description_quick_background_3,
        listOf(KeyChord.of(Key.Three, ctrl = true))),
    QUICK_BACKGROUND_4(ShortcutScope.GLOBAL, Res.string.shortcut_description_quick_background_4,
        listOf(KeyChord.of(Key.Four, ctrl = true))),
    QUICK_BACKGROUND_5(ShortcutScope.GLOBAL, Res.string.shortcut_description_quick_background_5,
        listOf(KeyChord.of(Key.Five, ctrl = true))),
    QUICK_BACKGROUND_6(ShortcutScope.GLOBAL, Res.string.shortcut_description_quick_background_6,
        listOf(KeyChord.of(Key.Six, ctrl = true))),
    QUICK_BACKGROUND_7(ShortcutScope.GLOBAL, Res.string.shortcut_description_quick_background_7,
        listOf(KeyChord.of(Key.Seven, ctrl = true))),
    QUICK_BACKGROUND_8(ShortcutScope.GLOBAL, Res.string.shortcut_description_quick_background_8,
        listOf(KeyChord.of(Key.Eight, ctrl = true))),
    QUICK_BACKGROUND_9(ShortcutScope.GLOBAL, Res.string.shortcut_description_quick_background_9,
        listOf(KeyChord.of(Key.Nine, ctrl = true))),

    /**
     * The tenth slot ships unbound: the digits are spent — one to eight and nine above, with
     * Ctrl+0 on the reset — and inventing a two-handed chord for it would be worse than letting
     * whoever wants it choose their own in the shortcuts dialog.
     */
    QUICK_BACKGROUND_10(ShortcutScope.GLOBAL, Res.string.shortcut_description_quick_background_10,
        emptyList()),
}
