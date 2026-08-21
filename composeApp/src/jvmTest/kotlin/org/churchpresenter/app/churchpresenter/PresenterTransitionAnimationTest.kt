package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.data.settings.AnnouncementsSettings
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleSettings
import org.churchpresenter.app.churchpresenter.data.settings.SongSettings
import org.churchpresenter.core.models.presentation.AnimationType
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PresenterTransitionAnimationTest {

    private fun ComposeUiTest.effects(
        manager: PresenterManager,
        settings: AppSettings = AppSettings(),
    ) = setContent { PresenterTransitionEffects(manager, settings) }

    private fun ComposeUiTest.managerShowing(path: String, type: AnimationType): PresenterManager {
        val manager = PresenterManager()
        manager.setAnimationType(AnimationType.NONE)
        manager.setTransitionDuration(SHORT_TRANSITION_MS)
        effects(manager)
        manager.setSelectedImagePath(path)
        waitUntil("the first picture is live") { manager.displayedImagePath.value == path }
        manager.setAnimationType(type)
        return manager
    }

    private fun ComposeUiTest.managerShowingSlide(first: ImageBitmap, type: AnimationType): PresenterManager {
        val manager = PresenterManager()
        manager.setAnimationType(AnimationType.NONE)
        manager.setTransitionDuration(SHORT_TRANSITION_MS)
        effects(manager)
        manager.setSelectedSlide(first)
        waitUntil("the first slide is live") { manager.displayedSlide.value === first }
        manager.setAnimationType(type)
        return manager
    }

    @Test
    fun `a faded picture swap ends fully opaque on the new picture`() = runComposeUiTest {
        val manager = managerShowing("/tmp/first.jpg", AnimationType.FADE)

        manager.setSelectedImagePath("/tmp/second.jpg")

        waitUntil("the fade back in finished") {
            manager.displayedImagePath.value == "/tmp/second.jpg" &&
                manager.pictureTransitionAlpha.value == 1f
        }
        assertNull(manager.previousDisplayedImagePath.value)
    }

    @Test
    fun `a crossfaded picture swap releases the outgoing picture when it ends`() = runComposeUiTest {
        val manager = managerShowing("/tmp/first.jpg", AnimationType.CROSSFADE)

        manager.setSelectedImagePath("/tmp/second.jpg")

        waitUntil("the crossfade finished") {
            manager.displayedImagePath.value == "/tmp/second.jpg" &&
                manager.pictureTransitionAlpha.value == 1f &&
                manager.previousDisplayedImagePath.value == null
        }
        assertEquals("/tmp/second.jpg", manager.displayedImagePath.value)
    }

    @Test
    fun `a slid picture ends with the offset run all the way out`() = runComposeUiTest {
        val manager = managerShowing("/tmp/first.jpg", AnimationType.SLIDE_LEFT)

        manager.setSelectedImagePath("/tmp/second.jpg")

        waitUntil("the slide finished") {
            manager.displayedImagePath.value == "/tmp/second.jpg" &&
                manager.pictureSlideOffset.value == 1f &&
                manager.previousDisplayedImagePath.value == null
        }
        assertEquals("/tmp/second.jpg", manager.displayedImagePath.value)
        assertNull(manager.previousDisplayedImagePath.value)
        assertEquals(1f, manager.pictureTransitionAlpha.value)
    }

    @Test
    fun `sliding right ends in the same resting state as sliding left`() = runComposeUiTest {
        val manager = managerShowing("/tmp/first.jpg", AnimationType.SLIDE_RIGHT)

        manager.setSelectedImagePath("/tmp/second.jpg")

        waitUntil("the slide finished") {
            manager.displayedImagePath.value == "/tmp/second.jpg" &&
                manager.pictureSlideOffset.value == 1f &&
                manager.previousDisplayedImagePath.value == null
        }
        assertEquals("/tmp/second.jpg", manager.displayedImagePath.value)
        assertNull(manager.previousDisplayedImagePath.value)
    }

    @Test
    fun `the first picture of the service appears at once whatever the animation`() = runComposeUiTest {
        val manager = PresenterManager()
        manager.setAnimationType(AnimationType.CROSSFADE)
        effects(manager)

        manager.setSelectedImagePath("/tmp/only.jpg")

        waitUntil("the picture is live") { manager.displayedImagePath.value == "/tmp/only.jpg" }
        assertEquals(1f, manager.pictureTransitionAlpha.value)
        assertNull(manager.previousDisplayedImagePath.value)
    }

    @Test
    fun `a faded slide swap ends fully opaque on the new slide`() = runComposeUiTest {
        val first = ImageBitmap(2, 2)
        val second = ImageBitmap(2, 2)
        val manager = managerShowingSlide(first, AnimationType.FADE)

        manager.setSelectedSlide(second)

        waitUntil("the fade back in finished") {
            manager.displayedSlide.value === second && manager.slideTransitionAlpha.value == 1f
        }
        assertNull(manager.previousDisplayedSlide.value)
    }

    @Test
    fun `a crossfaded slide swap releases the outgoing slide when it ends`() = runComposeUiTest {
        val first = ImageBitmap(2, 2)
        val second = ImageBitmap(2, 2)
        val manager = managerShowingSlide(first, AnimationType.CROSSFADE)

        manager.setSelectedSlide(second)

        waitUntil("the crossfade finished") {
            manager.displayedSlide.value === second &&
                manager.slideTransitionAlpha.value == 1f &&
                manager.previousDisplayedSlide.value == null
        }
        assertTrue(manager.displayedSlide.value === second)
    }

    @Test
    fun `a slid slide ends with the offset run all the way out`() = runComposeUiTest {
        val first = ImageBitmap(2, 2)
        val second = ImageBitmap(2, 2)
        val manager = managerShowingSlide(first, AnimationType.SLIDE_LEFT)

        manager.setSelectedSlide(second)

        waitUntil("the slide finished") {
            manager.displayedSlide.value === second &&
                manager.slideSlideOffset.value == 1f &&
                manager.previousDisplayedSlide.value == null
        }
        assertTrue(manager.displayedSlide.value === second)
        assertNull(manager.previousDisplayedSlide.value)
    }

    @Test
    fun `the first slide of a deck appears at once whatever the animation`() = runComposeUiTest {
        val manager = PresenterManager()
        manager.setAnimationType(AnimationType.SLIDE_LEFT)
        effects(manager)
        val only = ImageBitmap(2, 2)

        manager.setSelectedSlide(only)

        waitUntil("the slide is live") { manager.displayedSlide.value === only }
        assertEquals(1f, manager.slideTransitionAlpha.value)
        assertNull(manager.previousDisplayedSlide.value)
    }

    private fun announcementSettings(
        animationType: String,
        loopCount: Int = 0,
        animationDuration: Int = 12_000,
    ) = AppSettings(
        announcementsSettings = AnnouncementsSettings(
            animationType = animationType,
            animationDuration = animationDuration,
            loopCount = loopCount,
        )
    )

    @Test
    fun `a sliding announcement is swapped in without a fade`() = runComposeUiTest {
        val manager = PresenterManager()
        effects(manager, announcementSettings(Constants.ANIMATION_SLIDE_FROM_BOTTOM))

        manager.setAnnouncementText("Service starts at 10")

        waitUntil("the announcement reached the output") {
            manager.displayedAnnouncementText.value == "Service starts at 10"
        }
        assertEquals(1f, manager.announcementTransitionAlpha.value)
    }

    @Test
    fun `a fading announcement arrives fully opaque`() = runComposeUiTest {
        val manager = PresenterManager()
        effects(manager, announcementSettings(Constants.ANIMATION_FADE))

        manager.setAnnouncementText("Welcome")

        waitUntil("the fade in finished") {
            manager.displayedAnnouncementText.value == "Welcome" &&
                manager.announcementTransitionAlpha.value == 1f
        }
        assertEquals("Welcome", manager.displayedAnnouncementText.value)
    }

    @Test
    fun `clearing a faded announcement ends with an empty output at full opacity`() = runComposeUiTest {
        val manager = PresenterManager()
        effects(manager, announcementSettings(Constants.ANIMATION_FADE))
        manager.setAnnouncementText("Welcome")
        waitUntil("it is up") { manager.displayedAnnouncementText.value == "Welcome" }

        manager.setAnnouncementText("")

        waitUntil("the fade out finished") {
            manager.displayedAnnouncementText.value.isEmpty() &&
                manager.announcementTransitionAlpha.value == 1f
        }
        assertEquals("", manager.displayedAnnouncementText.value)
    }

    @Test
    fun `an announcement with a loop count takes itself off screen`() = runComposeUiTest {
        val manager = PresenterManager()
        effects(
            manager,
            announcementSettings(Constants.ANIMATION_NONE, loopCount = 1, animationDuration = 30_000),
        )

        manager.setPresentingMode(Presenting.ANNOUNCEMENTS)
        manager.setAnnouncementText("One time only")
        waitUntil("it is up") { manager.displayedAnnouncementText.value == "One time only" }

        waitUntil("the loop ended and took the announcement down", timeoutMillis = 10_000) {
            manager.announcementText.value.isEmpty()
        }
        assertEquals("", manager.displayedAnnouncementText.value)
    }

    @Test
    fun `an announcement with no loop count stays up`() = runComposeUiTest {
        val manager = PresenterManager()
        effects(manager, announcementSettings(Constants.ANIMATION_NONE, loopCount = 0))

        manager.setAnnouncementText("Until I say otherwise")

        waitUntil("it is up") { manager.displayedAnnouncementText.value == "Until I say otherwise" }
        assertTrue(manager.announcementText.value.isNotEmpty())
    }

    @Test
    fun `clearing a verse does not fade when a screen is locked to scripture`() = runComposeUiTest {
        val manager = PresenterManager()
        effects(manager)
        manager.setSelectedVerses(listOf(VERSE))
        waitUntil("the verse is live") { manager.bibleTransitionAlpha.value == 1f }
        manager.setScreenLock(0, Presenting.BIBLE)
        manager.setPresentingMode(Presenting.BIBLE)

        manager.requestClearDisplay()

        waitUntil("the clear ran") { manager.presentingMode.value == Presenting.NONE }
        assertEquals(1f, manager.bibleTransitionAlpha.value)
    }

    @Test
    fun `clearing a verse cuts instead of fading when fade-out is off`() = runComposeUiTest {
        val manager = PresenterManager()
        effects(manager, AppSettings(bibleSettings = BibleSettings(fadeOut = false)))
        manager.setSelectedVerses(listOf(VERSE))
        waitUntil("the verse is live") { manager.bibleTransitionAlpha.value == 1f }
        manager.setPresentingMode(Presenting.BIBLE)

        manager.requestClearDisplay()

        waitUntil("the clear ran") { manager.presentingMode.value == Presenting.NONE }
        assertEquals(1f, manager.bibleTransitionAlpha.value)
    }

    @Test
    fun `clearing lyrics fades the song alpha away`() = runComposeUiTest {
        val manager = PresenterManager()
        effects(manager, AppSettings(songSettings = SongSettings(fadeOut = true)))
        manager.setPresentingMode(Presenting.LYRICS)

        manager.requestClearDisplay()

        waitUntil("the song fade-out ran") { manager.songTransitionAlpha.value < 1f }
        assertTrue(manager.songTransitionAlpha.value < 1f)
    }

    @Test
    fun `clearing a mode that never fades goes straight to nothing`() = runComposeUiTest {
        val manager = PresenterManager()
        effects(manager)
        manager.setPresentingMode(Presenting.PICTURES)

        manager.requestClearDisplay()

        waitUntil("the clear ran") { manager.presentingMode.value == Presenting.NONE }
        assertEquals(1f, manager.pictureTransitionAlpha.value)
    }

    @Test
    fun `a held bible keeps the previous verse on the output`() = runComposeUiTest {
        val manager = PresenterManager()
        effects(manager)
        manager.setSelectedVerses(listOf(VERSE))
        waitUntil("the first verse is live") { manager.displayedVerses.value == listOf(VERSE) }

        manager.setBibleHold(true)
        val next = VERSE.copy(verseNumber = 17, verseText = "For God did not send his Son")
        manager.setSelectedVerses(listOf(next))
        waitForIdle()
        assertEquals(listOf(VERSE), manager.displayedVerses.value)

        manager.setBibleHold(false)

        waitUntil("the held verse was released") { manager.displayedVerses.value == listOf(next) }
        assertEquals(listOf(next), manager.displayedVerses.value)
    }

    private companion object {
        const val SHORT_TRANSITION_MS = 30

        val VERSE = SelectedVerse(
            bookName = "John", chapter = 3, verseNumber = 16, verseText = "For God so loved the world"
        )
    }
}
