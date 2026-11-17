@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.schedule

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.util.Collections
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Thumbnails in place of the type chip (#1050): only with the option on, only where the app has a
 * picture, the chip everywhere else, and every title starting at the same place either way.
 */
class ScheduleThumbnailsTest {

    /** A picture for each picture, deck and video row -- as the app's provider has -- recording what it was asked. */
    private class FakeThumbnails : ScheduleThumbnails {
        val asked: MutableList<ScheduleItem> = Collections.synchronizedList(mutableListOf())

        override suspend fun thumbnail(item: ScheduleItem): ImageBitmap? {
            asked += item
            return when (item) {
                is ScheduleItem.PictureItem, is ScheduleItem.PresentationItem, is ScheduleItem.MediaItem ->
                    ImageBitmap(16, 9)
                else -> null
            }
        }
    }

    /** A label, three rows with a picture and two without -- few enough that every row is composed. */
    private fun ScheduleViewModel.seedThumbnailRows() {
        addLabel("Welcome", "#FFFFFF", "#203040")
        addSong(songNumber = 42, title = "Amazing Grace", songbook = "Hymnal")
        addPresentation(filePath = "/decks/Sermon.pptx", fileName = "Sermon.pptx", slideCount = 24, fileType = "pptx")
        addPicture(folderPath = "/pictures/Gallery", folderName = "Gallery", imageCount = 12)
        addMedia(mediaUrl = "/media/Welcome Loop.mp4", mediaTitle = "Welcome Loop", mediaType = "local")
        addAnnouncement(text = "Fellowship lunch")
    }

    private fun ComposeUiTest.count(tag: String): Int =
        onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired = false).size

    private fun ComposeUiTest.awaitTiles(n: Int) {
        waitUntil(timeoutMillis = WAIT_MS) { count(SCHEDULE_ROW_THUMBNAIL_TAG) == n }
    }

    private fun ComposeUiTest.titleLeft(text: String): Float =
        onNodeWithText(text).fetchSemanticsNode().boundsInRoot.left

    @Test
    fun `off, no row asks for a thumbnail and every row keeps its chip`() {
        val fake = FakeThumbnails()
        scheduleTab(seed = { seedThumbnailRows() }, thumbnails = fake) { vm, _ ->
            waitForIdle()
            assertEquals(0, count(SCHEDULE_ROW_THUMBNAIL_TAG))
            assertEquals(vm.scheduleItems.count { it !is ScheduleItem.LabelItem }, count(SCHEDULE_ROW_TYPE_CHIP_TAG))
            assertTrue(fake.asked.isEmpty(), "nothing is decoded while the option is off: ${fake.asked}")
        }
    }

    @Test
    fun `on, the picture, deck and video rows draw a tile and the rest keep the chip`() {
        val fake = FakeThumbnails()
        scheduleTab(seed = { seedThumbnailRows() }, showThumbnails = true, thumbnails = fake) { vm, _ ->
            awaitTiles(3)
            val rows = vm.scheduleItems.count { it !is ScheduleItem.LabelItem }
            assertEquals(rows - 3, count(SCHEDULE_ROW_TYPE_CHIP_TAG), "a tile stands in for its chip, not beside it")
            assertTrue(fake.asked.none { it is ScheduleItem.LabelItem }, "a label is never asked for a picture")
        }
    }

    @Test
    fun `on, a row the app has no picture for keeps its chip`() =
        scheduleTab(
            seed = { seedThumbnailRows() },
            showThumbnails = true,
            thumbnails = ScheduleThumbnails { null },
        ) { vm, _ ->
            waitForIdle()
            assertEquals(0, count(SCHEDULE_ROW_THUMBNAIL_TAG))
            assertEquals(vm.scheduleItems.count { it !is ScheduleItem.LabelItem }, count(SCHEDULE_ROW_TYPE_CHIP_TAG))
        }

    @Test
    fun `on, a chip row's title starts where a tile row's does`() =
        scheduleTab(seed = { seedThumbnailRows() }, showThumbnails = true, thumbnails = FakeThumbnails()) { _, _ ->
            awaitTiles(3)
            val tile = titleLeft("Sermon.pptx (24 slides)")
            val chip = titleLeft("Fellowship lunch")
            assertTrue(abs(tile - chip) < 1f, "tile row title at $tile, chip row title at $chip")
        }

    @Test
    fun `the options menu turns thumbnails on`() =
        scheduleTab(seed = { seedService() }) { _, reports ->
            taggedButton(ScheduleToolbarTags.OPTIONS).performClick()
            waitForIdle()
            taggedButton(ScheduleToolbarTags.OPTIONS_THUMBNAILS).performClick()
            waitForIdle()

            assertEquals(listOf(true), reports.thumbnailChanges)
        }

    @Test
    fun `the options menu turns thumbnails back off`() =
        scheduleTab(seed = { seedService() }, showThumbnails = true) { _, reports ->
            taggedButton(ScheduleToolbarTags.OPTIONS).performClick()
            waitForIdle()
            taggedButton(ScheduleToolbarTags.OPTIONS_THUMBNAILS).performClick()
            waitForIdle()

            assertEquals(listOf(false), reports.thumbnailChanges)
        }

    private companion object {
        /** Only the failure path: the fake answers at once. */
        const val WAIT_MS = 5_000L
    }
}
