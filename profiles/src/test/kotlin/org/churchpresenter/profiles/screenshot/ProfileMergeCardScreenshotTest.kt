@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.profiles.OutputKind
import org.churchpresenter.profiles.OutputTile
import org.churchpresenter.profiles.ProfileMergeCard
import org.churchpresenter.settings.MergeTile
import org.churchpresenter.settings.OutputMerge
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.sharedui.screenshot.captureComponent
import kotlin.test.Test

class ProfileMergeCardScreenshotTest {

    private fun shoot(name: String, width: Dp, merge: OutputMerge) {
        val profile = OutputProfile(id = "wall", name = "Wall", merge = merge)
        val ndi = ScreenAssignment(ndiWidth = 1920, ndiHeight = 1080, activeProfileId = "wall")
        val proj = ProjectionSettings(ndiOutputs = List(3) { ndi }, outputProfiles = listOf(profile))
        val tiles = List(3) { OutputTile(OutputKind.NDI, it, "Display ${it + 1}", size = null, profileId = "wall") }
        captureComponent(SECTION, name) {
            Column(Modifier.width(width)) {
                ProfileMergeCard(profile, proj, tiles, onMergeChange = {}, onMac = false, deckLinkSize = { null })
            }
        }
    }

    private val sideBySide = OutputMerge(List(3) { MergeTile("ndi:$it", it * 1920, 0) })

    @Test
    fun `three outputs merged side by side`() = shoot("three_side_by_side", 460.dp, sideBySide)

    @Test
    fun `three outputs merged in a narrow pane`() = shoot("three_narrow", 320.dp, sideBySide)

    @Test
    fun `one output left out`() = shoot(
        "one_left_out",
        460.dp,
        OutputMerge(listOf(MergeTile("ndi:0", 0, 0), MergeTile("ndi:2", 3840, 0))),
    )

    private companion object {
        const val SECTION = "profileMergeCard"
    }
}
