package org.churchpresenter.app.churchpresenter.remote

import kotlinx.coroutines.runBlocking
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.server.InstanceLinkViewModel
import org.churchpresenter.server.LiveStateDto
import org.churchpresenter.settings.LinkLayers
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A follower mirroring the primary's layers: what goes live, what comes down, and the layers it
 * chose not to follow left as they are.
 */
class RemoteLayersTest {

    @BeforeTest
    fun setUp() {
        TestSingletons.latchToTestHome()
    }

    private val follower = PresenterManager(showPresenterWindowInitially = false)
    private val everything = { _: Presenting -> true }

    private fun state(mode: Presenting, slide: Presenting? = null, overlays: List<Presenting>? = null) = LiveStateDto(
        contentType = mode.name,
        liveSlide = slide?.name,
        overlays = overlays?.map { it.name },
    )

    @Test
    fun `an older primary's change goes live as it always did`() {
        followAir(state(Presenting.BIBLE), Presenting.BIBLE, follower, everything)
        assertEquals(Presenting.BIBLE, follower.presentingMode.value)
    }

    @Test
    fun `an older primary's change on a layer not followed is left alone`() {
        followAir(state(Presenting.BIBLE), Presenting.BIBLE, follower) { it != Presenting.BIBLE }
        assertEquals(Presenting.NONE, follower.presentingMode.value)
    }

    @Test
    fun `the primary's slide goes live when this change is the slide's`() {
        followAir(state(Presenting.LYRICS, Presenting.LYRICS, emptyList()), Presenting.LYRICS, follower, everything)
        assertEquals(Presenting.LYRICS, follower.presentingMode.value)
    }

    @Test
    fun `content the primary is not showing does not go live`() {
        follower.setPresentingMode(Presenting.BIBLE)
        followAir(state(Presenting.LYRICS, Presenting.BIBLE, emptyList()), Presenting.LYRICS, follower, everything)
        assertEquals(Presenting.BIBLE, follower.presentingMode.value)
    }

    @Test
    fun `an overlay goes up over the slide when this change is that overlay's`() {
        follower.setPresentingMode(Presenting.BIBLE)
        val up = state(Presenting.LOWER_THIRD, Presenting.BIBLE, listOf(Presenting.LOWER_THIRD))
        followAir(up, Presenting.LOWER_THIRD, follower, everything)
        assertEquals(Presenting.BIBLE, follower.presentingMode.value)
        assertEquals(setOf(Presenting.LOWER_THIRD), follower.overlays.value)
    }

    @Test
    fun `one overlay taken down on the primary comes down here, and only that one`() {
        follower.setPresentingMode(Presenting.BIBLE)
        follower.setPresentingMode(Presenting.LOWER_THIRD)
        follower.setPresentingMode(Presenting.STT)
        val after = state(Presenting.BIBLE, Presenting.BIBLE, listOf(Presenting.STT))
        followAir(after, Presenting.BIBLE, follower, everything)
        assertEquals(setOf(Presenting.STT), follower.overlays.value)
        assertEquals(Presenting.BIBLE, follower.presentingMode.value)
    }

    @Test
    fun `an overlay of this follower's own is not taken down for the primary`() {
        follower.setPresentingMode(Presenting.LOWER_THIRD)
        val nothing = state(Presenting.ANNOUNCEMENTS, Presenting.NONE, emptyList())
        followAir(nothing, Presenting.ANNOUNCEMENTS, follower) { it != Presenting.LOWER_THIRD }
        assertEquals(setOf(Presenting.LOWER_THIRD), follower.overlays.value)
    }

    @Test
    fun `an overlay on a layer not followed does not go up`() {
        val up = state(Presenting.LOWER_THIRD, Presenting.NONE, listOf(Presenting.LOWER_THIRD))
        followAir(up, Presenting.LOWER_THIRD, follower) { it != Presenting.LOWER_THIRD }
        assertTrue(follower.overlays.value.isEmpty())
    }

    @Test
    fun `each content type is on its layer`() {
        assertEquals(LinkLayers.MEDIA, linkLayerOf(Presenting.MEDIA))
        assertEquals(LinkLayers.LOWER_THIRD, linkLayerOf(Presenting.LOWER_THIRD))
        assertEquals(LinkLayers.CAPTIONS, linkLayerOf(Presenting.STT))
        assertEquals(LinkLayers.ANNOUNCEMENTS, linkLayerOf(Presenting.ANNOUNCEMENTS))
        listOf(Presenting.BIBLE, Presenting.LYRICS, Presenting.PICTURES, Presenting.WEBSITE).forEach {
            assertEquals(LinkLayers.SLIDE, linkLayerOf(it), it.name)
        }
    }

    @Test
    fun `a change on a layer not followed neither changes content nor goes live`() {
        val announcement = LiveStateDto(
            contentType = Presenting.ANNOUNCEMENTS.name,
            announcementText = "From the primary",
            liveSlide = Presenting.NONE.name,
            overlays = listOf(Presenting.ANNOUNCEMENTS.name),
        )
        runBlocking {
            applyRemoteLiveState(
                state = announcement,
                presenterManager = follower,
                instanceLinkViewModel = InstanceLinkViewModel(),
                followedLayers = LinkLayers.ALL - LinkLayers.ANNOUNCEMENTS,
            )
        }
        assertEquals("", follower.announcementText.value)
        assertTrue(follower.overlays.value.isEmpty())
    }

    @Test
    fun `a change on a followed layer is applied and goes live`() {
        val announcement = LiveStateDto(
            contentType = Presenting.ANNOUNCEMENTS.name,
            announcementText = "From the primary",
            liveSlide = Presenting.NONE.name,
            overlays = listOf(Presenting.ANNOUNCEMENTS.name),
        )
        runBlocking { applyRemoteLiveState(announcement, follower, InstanceLinkViewModel()) }
        assertEquals("From the primary", follower.announcementText.value)
        assertEquals(setOf(Presenting.ANNOUNCEMENTS), follower.overlays.value)
    }
}
