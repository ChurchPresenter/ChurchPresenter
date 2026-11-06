package org.churchpresenter.signage.tab

import org.churchpresenter.signage.core.BlockAction
import org.churchpresenter.signage.core.ClockTime
import org.churchpresenter.signage.core.Day
import org.churchpresenter.signage.core.Features
import org.churchpresenter.signage.core.ScheduleBlock
import kotlin.test.Test
import kotlin.test.assertEquals

/** Which of a screen's links its player will skip: the manifest leaves them out, so the card has to say. */
class UnplayableLinksTest {
    private val page = LinkPlaylist("link:w", "Weather", "https://weather.example.org", LinkKind.WEB)
    private val stream = LinkPlaylist("link:l", "Live", "https://example.org/live.m3u8", LinkKind.STREAM)
    private val rotation = RotationPlaylist("rotation:r", "Lobby loop", listOf(RotationStep(page.id, 10)))
    private val added = listOf(page, stream, rotation)

    private fun block(playlistId: String) = ScheduleBlock(
        id = "b",
        days = setOf(Day.MON),
        start = ClockTime.of(9, 0),
        end = ClockTime.of(10, 0),
        action = BlockAction.Play(playlistId),
    )

    @Test
    fun aWebPageOnAPlayerWithoutABrowserIsNamed() {
        val skipped = unplayableLinks(ScreenPlan(page.id), added, listOf(Features.STREAM))

        assertEquals(listOf("Weather"), skipped)
    }

    @Test
    fun aLinkThePlayerCanShowIsNotNamed() {
        assertEquals(emptyList(), unplayableLinks(ScreenPlan(stream.id), added, listOf(Features.STREAM)))
    }

    @Test
    fun aLinkInTheScheduleCounts() {
        val plan = ScreenPlan(stream.id, schedule = listOf(block(page.id)))

        assertEquals(listOf("Weather"), unplayableLinks(plan, added, listOf(Features.STREAM)))
    }

    @Test
    fun aLinkInARotationNeedsRotationToo() {
        val plan = ScreenPlan(rotation.id)

        assertEquals(listOf("Weather"), unplayableLinks(plan, added, listOf(Features.WEB)))
        assertEquals(emptyList(), unplayableLinks(plan, added, listOf(Features.WEB, Features.ROTATION)))
    }

    @Test
    fun aLinkNamedTwiceIsListedOnce() {
        val plan = ScreenPlan(page.id, schedule = listOf(block(rotation.id)))

        assertEquals(listOf("Weather"), unplayableLinks(plan, added, emptyList()))
    }

    @Test
    fun aScreenWithNoPlanSkipsNothing() {
        assertEquals(emptyList(), unplayableLinks(ScreenPlan(), added, emptyList()))
    }
}
