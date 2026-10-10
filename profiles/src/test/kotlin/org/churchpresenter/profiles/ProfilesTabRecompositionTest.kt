@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfilesTabRecompositionTest {

    private fun swappableTab(
        initial: AppSettings,
        block: SkikoComposeUiTest.(get: () -> AppSettings, set: (AppSettings) -> Unit) -> Unit,
    ) = runSkikoComposeUiTest(size = Size(1400f, 900f), density = Density(1f)) {
        var state by mutableStateOf(initial)
        setContent {
            ProfilesSettingsTab(settings = state, onSettingsChange = { state = it(state) })
        }
        block({ state }, { next -> state = next; waitForIdle() })
    }

    private fun SkikoComposeUiTest.visitEveryPage(
        mode: String,
        get: () -> AppSettings,
        set: (AppSettings) -> Unit,
    ): List<String> {
        val pages = listOf(ProfilePage.General, ProfilePage.Outputs, ProfilePage.Content) +
            customizePanes(mode).map { ProfilePage.Appearance(it) }
        val visited = mutableListOf<String>()
        pages.forEach { page ->
            val tag = page.navTag()
            if (onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) return@forEach
            onNodeWithTag(tag).performScrollTo().performClick()
            waitForIdle()
            val before = get()
            set(before.copy())
            set(before.copy(schedulePanelWidthDp = before.schedulePanelWidthDp + 1))
            set(before)
            visited += tag
        }
        return visited
    }

    private fun document(mode: String, advanced: Boolean) = profileDocument(mode = mode).let { doc ->
        doc.copy(profilesAdvanced = advanced)
    }

    @Test
    fun `every full screen page survives recompositions that change none of its inputs`() =
        swappableTab(document(Constants.DISPLAY_MODE_FULLSCREEN, advanced = true)) { get, set ->
            val before = get()
            val visited = visitEveryPage(Constants.DISPLAY_MODE_FULLSCREEN, get, set)
            assertEquals(10, visited.size)
            assertEquals(before.projectionSettings, get().projectionSettings)
        }

    @Test
    fun `every lower third page survives recompositions in Basic`() =
        swappableTab(document(Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL, advanced = false)) { get, set ->
            val before = get()
            val visited = visitEveryPage(Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL, get, set)
            assertTrue(visited.size >= 3)
            assertEquals(before.projectionSettings, get().projectionSettings)
        }

    @Test
    fun `every stage monitor page survives recompositions`() =
        swappableTab(document(Constants.DISPLAY_MODE_STAGE_MONITOR, advanced = true)) { get, set ->
            val before = get()
            val visited = visitEveryPage(Constants.DISPLAY_MODE_STAGE_MONITOR, get, set)
            assertEquals(6, visited.size)
            assertEquals(before.projectionSettings, get().projectionSettings)
        }

    @Test
    fun `a linked profile's pages survive recompositions`() {
        val master = OutputProfile(id = "master", name = "Master")
        val doc = profileDocument().let { d ->
            val proj = d.projectionSettings
            d.copy(
                profilesAdvanced = true,
                projectionSettings = proj.copy(
                    outputProfiles = listOf(
                        master,
                        proj.outputProfiles.first().copy(parentId = "master"),
                    ),
                ),
            )
        }
        swappableTab(doc) { get, set ->
            onNodeWithTag(profileRowTag(PROFILE_ID)).performClick()
            waitForIdle()
            val visited = visitEveryPage(Constants.DISPLAY_MODE_FULLSCREEN, get, set)
            assertEquals(10, visited.size)
        }
    }

    private fun SkikoComposeUiTest.openAndEscapeLargePreview(mode: String): Int {
        var opened = 0
        customizePanes(mode).forEach { pane ->
            onNodeWithTag(railTag(pane.name)).performScrollTo().performClick()
            waitForIdle()
            if (onAllNodesWithTag(PREVIEW_LARGER_TAG).fetchSemanticsNodes().isEmpty()) return@forEach
            tap(PREVIEW_LARGER_TAG)
            assertEquals(1, onAllNodesWithTag(LARGE_PREVIEW_TAG).fetchSemanticsNodes().size, pane.name)
            val large = onAllNodes(hasTestTag(LARGE_PREVIEW_TAG), useUnmergedTree = true)[0]
            large.performKeyInput { pressKey(Key.A) }
            waitForIdle()
            assertEquals(1, onAllNodesWithTag(LARGE_PREVIEW_TAG).fetchSemanticsNodes().size, "a key that is not Esc")
            large.performKeyInput { pressKey(Key.Escape) }
            waitForIdle()
            assertEquals(0, onAllNodesWithTag(LARGE_PREVIEW_TAG).fetchSemanticsNodes().size, pane.name)
            opened++
        }
        return opened
    }

    @Test
    fun `every page's large preview opens and Esc closes it`() {
        listOf(
            Constants.DISPLAY_MODE_FULLSCREEN,
            Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
            Constants.DISPLAY_MODE_STAGE_MONITOR,
        ).forEach { mode ->
            swappableTab(document(mode, advanced = true)) { _, _ ->
                val expected = customizePanes(mode).size - if (mode == Constants.DISPLAY_MODE_STAGE_MONITOR) 1 else 0
                assertEquals(expected, openAndEscapeLargePreview(mode), mode)
            }
        }
    }
}
