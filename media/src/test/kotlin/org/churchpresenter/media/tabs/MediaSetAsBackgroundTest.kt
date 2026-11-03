@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.media.tabs

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.BackgroundContent
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.composables.BackgroundTarget
import org.churchpresenter.sharedui.composables.setAsBackgroundTag
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Media tab's Set as Background: a clip on this computer, set behind the text. */
class MediaSetAsBackgroundTest {

    private val targets = listOf(BackgroundTarget("main", "Screen 1"))

    @Test
    fun `without targets the tab offers no Set as Background`() = mediaTab { vm, _ ->
        vm.loadMedia("/media/waves.mp4", Constants.MEDIA_TYPE_LOCAL)
        waitForIdle()
        assertEquals(0, onAllNodesWithTag(MEDIA_SET_AS_BACKGROUND_TAG).fetchSemanticsNodes().size)
    }

    @Test
    fun `nothing loaded, nothing to set`() = mediaTab(backgroundTargets = targets) { _, _ ->
        onNodeWithTag(MEDIA_SET_AS_BACKGROUND_TAG).assertIsNotEnabled()
    }

    @Test
    fun `a stream or a sound cannot be a background`() = mediaTab(backgroundTargets = targets) { vm, _ ->
        vm.loadMedia("https://example.org/clip.mp4", Constants.MEDIA_TYPE_URL)
        waitForIdle()
        onNodeWithTag(MEDIA_SET_AS_BACKGROUND_TAG).assertIsNotEnabled()
        vm.loadMedia("/media/hymn.mp3", Constants.MEDIA_TYPE_LOCAL)
        waitForIdle()
        onNodeWithTag(MEDIA_SET_AS_BACKGROUND_TAG).assertIsNotEnabled()
    }

    @Test
    fun `a clip on this computer is set where it is asked for`() =
        mediaTab(backgroundTargets = targets) { vm, reports ->
        vm.loadMedia("/media/waves.mp4", Constants.MEDIA_TYPE_LOCAL)
        waitForIdle()
        onNodeWithTag(MEDIA_SET_AS_BACKGROUND_TAG).assertIsEnabled().performClick()
        waitForIdle()
        onNodeWithTag(setAsBackgroundTag("main", BackgroundContent.BIBLE)).performClick()
        waitForIdle()
        val (file, id, content) = reports.backgrounds.single()
        assertEquals("waves.mp4", file.name)
        assertEquals("main", id)
        assertEquals(BackgroundContent.BIBLE, content)
        val closed = onAllNodesWithTag(setAsBackgroundTag("main", BackgroundContent.BIBLE)).fetchSemanticsNodes()
        assertEquals(0, closed.size, "the menu closes")
    }

    @Test
    fun `the background the clip already is is marked`() = mediaTab(
        backgroundTargets = targets,
        isBackground = { file, id, content ->
            file.name == "waves.mp4" && id == null && content == BackgroundContent.SONG
        },
    ) { vm, _ ->
        vm.loadMedia("/media/waves.mp4", Constants.MEDIA_TYPE_LOCAL)
        waitForIdle()
        onNodeWithTag(MEDIA_SET_AS_BACKGROUND_TAG).performClick()
        waitForIdle()
        val selected = onNodeWithTag(setAsBackgroundTag(null, BackgroundContent.SONG))
            .fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected)
        assertEquals(true, selected)
    }
}
