package org.churchpresenter.helper.ui

import org.churchpresenter.helper.suggest.SuggestedRequest
import org.churchpresenter.helper.suggest.SuggestionIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class IconsTest {

    @Test
    fun `every suggested request has an icon`() {
        val icons = SuggestedRequest.entries.map(::requestIcon)
        assertEquals(SuggestedRequest.entries.size, icons.size)
        assertEquals(requestIcon(SuggestedRequest.SONG_SEARCH), requestIcon(SuggestedRequest.BIBLE_SEARCH))
    }

    @Test
    fun `each topic has its own icon, anything else a bulb`() {
        val topics = listOf(
            SuggestionIds.SCHEDULE_EMPTY, SuggestionIds.SONGS_EMPTY, SuggestionIds.BIBLE_NONE,
            SuggestionIds.NO_AUDIENCE, SuggestionIds.OUTPUTS_HIDDEN,
        ).map(::topicIcon)
        assertEquals(topics.size, topics.toSet().size)
        topics.forEach { assertNotEquals(topicIcon(null), it) }
        assertEquals(topicIcon("tip"), topicIcon(null))
    }
}
