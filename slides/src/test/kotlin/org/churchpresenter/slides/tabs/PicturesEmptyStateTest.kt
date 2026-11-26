@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.slides.tabs

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.sharedui.testing.renderedText
import org.churchpresenter.sharedui.testing.showsExactly
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PicturesEmptyStateTest {

    @Test
    fun `with no folder chosen the tab offers to choose one, or take a dragged one`() =
        picturesTab(folder = null) { _, _ ->
            assertTrue(showsExactly("Choose folder"), renderedText().toString())
            assertTrue(showsExactly("or drag a folder here"))
        }

    @Test
    fun `a folder with no pictures is named, with a way to pick another`() {
        val empty = Files.createTempDirectory("cp-pictures-empty").toFile()
        try {
            picturesTab(folder = empty) { vm, _ ->
                assertTrue(vm.images.isEmpty())
                assertTrue(showsExactly("No pictures in “${empty.name}”"), renderedText().toString())
                assertTrue(showsExactly("Choose another folder"))
                assertFalse(showsExactly("Choose folder"))
            }
        } finally {
            empty.deleteRecursively()
        }
    }

    @Test
    fun `a folder that cannot be read says so, and Try again reads it once it can be`() {
        val locked = pictureFolder()
        try {
            locked.setReadable(false)
            picturesTab(folder = locked) { vm, _ ->
                assertTrue(vm.folderUnreadable)
                assertTrue(showsExactly("Can’t open this folder"), renderedText().toString())
                assertTrue(showsExactly("Choose another"))

                locked.setReadable(true)
                onNodeWithText("Try again").performClick()
                waitUntil(timeoutMillis = 5_000) { vm.images.size == 3 }
                waitForIdle()

                assertFalse(vm.folderUnreadable)
                assertFalse(showsExactly("Can’t open this folder"))
            }
        } finally {
            locked.setReadable(true)
            locked.deleteRecursively()
        }
    }
}
