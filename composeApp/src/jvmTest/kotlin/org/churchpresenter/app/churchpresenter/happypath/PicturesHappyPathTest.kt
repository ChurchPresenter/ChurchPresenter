@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.happypath

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.PictureSettings
import org.churchpresenter.sharedui.composables.SLIDESHOW_HIDE_TOGGLE_TAG
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.models.Tabs
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

private val IMAGES = listOf("image-0.png", "image-1.png", "image-2.png")

class PicturesHappyPathTest : HappyPathHarness() {

    private fun library(pictureSettings: PictureSettings = PictureSettings()): AppSettings {
        val folder = File(dir, "Sunday Photos").apply { mkdirs() }
        IMAGES.forEach { name ->
            ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", File(folder, name))
        }
        return settings(Tabs.PICTURES)
            .copy(pictureSettings = pictureSettings.copy(storageDirectory = folder.absolutePath))
    }

    private fun ComposeUiTest.thumbnails(): List<String> =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() }
            .filter { it.endsWith(".png") }

    /** The tab opens on the folder it last showed, as the settings remember it. */
    private fun ComposeUiTest.openFolder() {
        await("every thumbnail is drawn") { thumbnails().containsAll(IMAGES) }
    }

    private fun ComposeUiTest.awaitPicture(app: HappyApp, name: String) {
        await("$name is on air") {
            app.presenter.slideContent.value == Presenting.PICTURES &&
                app.presenter.displayedImagePath.value?.endsWith(name) == true
        }
    }

    private fun ComposeUiTest.clickThumbnail(name: String) {
        onAllNodes(hasContentDescription(name))[0].performClick()
        waitForIdle()
    }

    @Test
    fun `opening a folder shows every picture`() = happyPath(library()) { app ->
        openFolder()

        assertEquals(IMAGES, thumbnails().distinct().sorted())
    }

    @Test
    fun `a picture goes live`() = happyPath(library()) { app ->
        openFolder()
        clickThumbnail("image-1.png")
        goLive()

        awaitPicture(app, "image-1.png")
    }

    @Test
    fun `the live picture steps forward and back`() = happyPath(library()) { app ->
        openFolder()
        clickThumbnail("image-0.png")
        goLive()
        awaitPicture(app, "image-0.png")

        clickTabControl("Next Image")
        awaitPicture(app, "image-1.png")

        clickTabControl("Previous Image")
        awaitPicture(app, "image-0.png")
    }

    @Test
    fun `the slideshow moves on by itself`() =
        happyPath(library(PictureSettings(autoScrollInterval = 0.2f))) { app ->
            openFolder()
            clickThumbnail("image-0.png")
            goLive()
            awaitPicture(app, "image-0.png")

            clickTabControl("Play")

            awaitPicture(app, "image-1.png")
        }

    @Test
    fun `next passes over a hidden picture`() = happyPath(library()) { app ->
        openFolder()
        onNodeWithTag(SLIDESHOW_HIDE_TOGGLE_TAG + 1).performClick()
        waitForIdle()
        clickThumbnail("image-0.png")
        goLive()
        awaitPicture(app, "image-0.png")

        clickTabControl("Next Image")

        awaitPicture(app, "image-2.png")
    }

    @Test
    fun `a folder added to the schedule becomes a row`() = happyPath(library()) { app ->
        openFolder()
        clickTabControl("Add to Schedule")

        await("the schedule holds the folder") { scheduleShows("Sunday Photos") }
        assertEquals(1, app.schedule().size)
    }
}
