package org.churchpresenter.helper.suggest

import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.settings.HelperSettings
import org.churchpresenter.settings.dismissing
import org.churchpresenter.settings.snoozing
import org.churchpresenter.sharedui.utils.ShortcutMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SuggestionsTest {

    private val everything = HelperSignals(
        screenCount = 2,
        hasAudienceOutput = false,
        outputWindowsShown = false,
        primaryBibleMissing = true,
        songLibraryEmpty = true,
        scheduleEmpty = true,
    )

    private fun ids(signals: HelperSignals, settings: HelperSettings = HelperSettings(), now: Long = 0L) =
        suggestionsFor(signals, settings, now).map { it.id }

    @Test
    fun `a quiet app suggests nothing`() = assertEquals(emptyList(), ids(HelperSignals()))

    @Test
    fun `each thing noticed has its suggestion, most useful first`() {
        assertEquals(
            listOf(
                SuggestionIds.NO_AUDIENCE,
                SuggestionIds.BIBLE_NONE,
                SuggestionIds.SONGS_EMPTY,
                SuggestionIds.SCHEDULE_EMPTY,
            ),
            ids(everything),
        )
        assertEquals(
            listOf(SuggestionIds.OUTPUTS_HIDDEN),
            ids(HelperSignals(outputWindowsShown = false)),
        )
        val suggestions = suggestionsFor(everything, HelperSettings(), 0L)
        assertIs<HelperAction.StartDisplaySetup>(suggestions.first().action)
        assertTrue(suggestions.all { it.topic != null })
    }

    @Test
    fun `nothing is suggested while busy or switched off`() {
        assertEquals(emptyList(), ids(everything.copy(anythingLive = true)))
        assertEquals(emptyList(), ids(everything.copy(settingsOpen = true)))
        assertEquals(emptyList(), ids(everything.copy(firstRunDone = false)))
        assertEquals(emptyList(), ids(everything, HelperSettings(enabled = false)))
    }

    @Test
    fun `a dismissed or snoozed suggestion stays away`() {
        val settings = HelperSettings().dismissing(SuggestionIds.BIBLE_NONE).snoozing(SuggestionIds.SONGS_EMPTY, 100L)
        val now = ids(everything, settings, now = 50L)
        assertTrue(SuggestionIds.BIBLE_NONE !in now && SuggestionIds.SONGS_EMPTY !in now)
        assertTrue(SuggestionIds.SONGS_EMPTY in ids(everything, settings, now = 200L))
    }

    @Test
    fun `tips rotate through every one, either way`() {
        val tips = allTips(ShortcutMap.DEFAULT)
        assertTrue(tips.size > 5)
        assertEquals(tips[0], tipAt(tips, tips.size))
        assertEquals(tips.last(), tipAt(tips, -1))
        assertNull(tipAt(emptyList(), 3))
        assertNotNull(tips.first().text)
    }
}
