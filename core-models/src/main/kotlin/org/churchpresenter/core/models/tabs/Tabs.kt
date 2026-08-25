package org.churchpresenter.core.models.tabs

/**
 * The tabs the app can show.
 *
 * Here rather than in `:composeApp` because `ShortcutAction` names one as its `targetTab` — the
 * F-keys that jump straight to a tab — and `:shortcuts` cannot depend on the app. Every module that
 * needs to say "this belongs to the Bible tab" refers to this one list.
 */
enum class Tabs {
    BIBLE,
    SONGS,
    PICTURES,
    PRESENTATION,
    MEDIA,
    LOWER_THIRD,
    ANNOUNCEMENTS,
    WEB,
    CANVAS,
    QA,
    STT,
    CROSSWORD,
    DICTIONARY,
    COMPANION_SURFACE,
}
