package org.churchpresenter.helper.intent

import org.churchpresenter.calendar.model.parseReference
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.models.Tabs

// The rules that go somewhere: a question about where something is, a page, a verse, a tab, a slide.

internal fun navigationRule(r: Request): Resolution? {
    if (!r.isQuestion) return null
    val tour = NavigationTopics.find(r.text) ?: return null
    val hiddenTab = NavigationTopics.tabNamed(r.text)?.takeIf { it !in r.context.visibleTabs }
    return act(hiddenTab?.let { HelperAction.ShowTab(it) } ?: HelperAction.Highlight(tour))
}

/**
 * A song in another language, asked or told — "add a translation to this song" is as much a
 * "how do I" as the question, since the lyrics are the operator's to type.
 */
internal fun songTranslationRule(r: Request): Resolution? =
    if (NavigationTopics.isSongTranslation(r.text, r.context.currentTab)) {
        act(HelperAction.Highlight(NavigationTopics.songTranslation()))
    } else {
        null
    }

internal fun openSettingsRule(r: Request): Resolution? {
    if (!r.has(Vocabulary.SETTINGS)) return null
    val page = when {
        r.has(Vocabulary.SCREEN) || r.text.containsWordPrefix("projection") -> SettingsPage.PROJECTION
        r.has(Vocabulary.BACKGROUND) -> SettingsPage.BACKGROUND
        r.has(Vocabulary.BIBLE) -> SettingsPage.BIBLE
        r.text.containsWordPrefix("profile") || r.text.containsWordPrefix("look") -> SettingsPage.PROFILES
        r.says("server", "remote", "companion", "phone", "tablet") -> SettingsPage.SERVER
        r.says("atem") -> SettingsPage.ATEM
        r.says("obs", "planning center", "integration", "integrations") -> SettingsPage.INTEGRATIONS
        else -> SettingsPage.SYSTEM
    }
    return act(HelperAction.OpenSettings(page))
}

/** "show John 3:16", "go to psalm 23", "john chapter 3 verse 16", "jn 3 16". */
internal fun verseRule(r: Request): Resolution? {
    val verb = Vocabulary.VERSE_VERBS.firstOrNull { r.text.startsWith("$it ") }
    val rest = (verb?.let { r.text.removePrefix("$it ") } ?: r.text)
        .replace(Regex("""\bchapter (\d+) verses? (\d+)"""), "$1:$2")
        .replace(Regex("""^(.*\p{L}) (\d{1,3}) (\d{1,3})$"""), "$1 $2:$3")
        .replace(Regex("""\s*:\s*"""), ":")
        .replace(Regex("""(\d) ?- ?(\d)"""), "$1-$2")
    val ref = parseReference(rest) ?: return null
    val lastWord = ref.bookName.split(' ').last()
    if (lastWord in Vocabulary.NOT_A_BOOK || lastWord in Vocabulary.SETTINGS) return null
    val book = ref.bookName.split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
    val first = maxOf(1, ref.firstVerse)
    val display = ref.copy(bookName = book).display
    return act(HelperAction.ShowBibleVerse(book, ref.chapter, first, maxOf(first, ref.lastVerse), display))
}

internal fun switchTabRule(r: Request): Resolution? {
    if (!r.says("go to", "switch to", "open", "show", "tab")) return null
    val tab = NavigationTopics.tabNamed(r.text) ?: return null
    return act(if (tab in r.context.visibleTabs) HelperAction.SelectTab(tab) else HelperAction.ShowTab(tab))
}

/**
 * Songs and the Bible step by the keys of their own tab, which the helper cannot press — so there it
 * answers with the key to use, and only steps slides and pictures itself.
 */
internal fun nextOrPreviousRule(r: Request): Resolution? {
    val forward = when {
        r.has(Vocabulary.NEXT) -> true
        r.has(Vocabulary.PREVIOUS) || r.says("go back") -> false
        else -> return null
    }
    val key = when (r.context.currentTab) {
        Tabs.SONGS -> if (forward) ShortcutAction.SONGS_NEXT_SECTION else ShortcutAction.SONGS_PREVIOUS_SECTION
        Tabs.BIBLE -> if (forward) ShortcutAction.BIBLE_NEXT_VERSE else ShortcutAction.BIBLE_PREVIOUS_VERSE
        else -> null
    }
    val step = if (forward) HelperAction.NextSlide else HelperAction.PreviousSlide
    return act(key?.let { HelperAction.ShowShortcut(it) } ?: step)
}
