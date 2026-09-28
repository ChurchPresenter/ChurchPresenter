package org.churchpresenter.settings

import kotlinx.serialization.json.Json
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The OMT output list, and the fields an OMT output keeps: the same four operations the NDI list
 * has, and the defaults a document written before OMT existed must load with.
 */
class OmtOutputsTest {

    private fun defaultAssignment() = ScreenAssignment(activeProfileId = DEFAULT_OUTPUT_PROFILE_ID)

    @Test
    fun `an OMT output is added at the end`() {
        assertEquals(2, ProjectionSettings().addOmtOutput().addOmtOutput().omtOutputs.size)
    }

    @Test
    fun `an OMT output is configured by index, and past the end fills the gap`() {
        val settings = ProjectionSettings().withOmtOutput(2, ScreenAssignment(omtName = "Stage"))
        assertEquals(3, settings.omtOutputs.size)
        assertEquals("Stage", settings.getOmtOutput(2).omtName)
        assertEquals(defaultAssignment(), settings.getOmtOutput(0))
    }

    @Test
    fun `reading an OMT output that is not there gives a default rather than throwing`() {
        assertEquals(defaultAssignment(), ProjectionSettings().getOmtOutput(4))
    }

    @Test
    fun `removing an OMT output shifts the ones after it down, and the preview groups with it`() {
        val grouped = ProjectionSettings(
            previewGroups = listOf(
                PreviewGroup(
                    id = "g",
                    members = listOf(
                        Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_OMT, 0),
                        Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_OMT, 2),
                    ),
                ),
            ),
        )
            .withOmtOutput(0, ScreenAssignment(omtName = "One"))
            .withOmtOutput(1, ScreenAssignment(omtName = "Two"))
            .withOmtOutput(2, ScreenAssignment(omtName = "Three"))

        val after = grouped.removeOmtOutput(1)

        assertEquals(listOf("One", "Three"), after.omtOutputs.map { it.omtName })
        assertEquals(
            listOf(
                Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_OMT, 0),
                Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_OMT, 1),
            ),
            after.previewGroups.single().members,
            "the third output is the second now, and its tile follows it",
        )
    }

    @Test
    fun `OMT outputs are kept apart from NDI outputs`() {
        val settings = ProjectionSettings().addNdiOutput().addOmtOutput().addOmtOutput()
        assertEquals(1, settings.ndiOutputs.size)
        assertEquals(2, settings.omtOutputs.size)
    }

    @Test
    fun `an OMT output counts as a user of its profile`() {
        val settings = ProjectionSettings()
            .withOmtOutput(0, ScreenAssignment(activeProfileId = "p9", omtName = "Stage"))
        assertEquals(1, settings.outputProfileUsageCount("p9"))
        assertEquals(listOf("Stage"), settings.outputProfileUsers("p9") { it.omtName })
    }

    @Test
    fun `a new OMT output is enabled, 1080p30, alpha, at automatic quality`() {
        val output = ScreenAssignment()
        assertTrue(output.omtEnabled)
        assertEquals(1920, output.omtWidth)
        assertEquals(1080, output.omtHeight)
        assertEquals(30, output.omtFps)
        assertEquals(Constants.OMT_MODE_ALPHA, output.omtMode)
        assertEquals(Constants.OMT_QUALITY_DEFAULT, output.omtQuality)
    }

    @Test
    fun `the network name falls back to the numbered default and is trimmed`() {
        assertEquals("OMT Output 2", ScreenAssignment().omtLabelOr("OMT Output 2"))
        assertEquals("OMT Output 1", ScreenAssignment(omtName = "   ").omtLabelOr("OMT Output 1"))
        assertEquals("Lyrics", ScreenAssignment(omtName = "  Lyrics ").omtLabelOr("OMT Output 1"))
    }

    @Test
    fun `a document written before OMT loads with none, the bundled library and automatic discovery`() {
        val json = Json { ignoreUnknownKeys = true }
        val old = json.decodeFromString(ProjectionSettings.serializer(), """{"ndiOutputs":[{"ndiName":"Old"}]}""")
        assertTrue(old.omtOutputs.isEmpty())
        assertEquals("", old.omtLibraryPath)
        assertEquals("", old.omtDiscoveryServer)
        assertEquals("Old", old.ndiOutputs.single().ndiName)
    }

    @Test
    fun `OMT settings survive a round trip`() {
        val json = Json { ignoreUnknownKeys = true }
        val settings = ProjectionSettings(
            omtOutputs = listOf(
                ScreenAssignment(
                    omtName = "Stage", omtEnabled = false, omtWidth = 1280, omtHeight = 720, omtFps = 60,
                    omtMode = Constants.OMT_MODE_FILL, omtQuality = Constants.OMT_QUALITY_HIGH,
                ),
            ),
            omtLibraryPath = "/opt/omt",
            omtDiscoveryServer = "omt://server:6400",
        )
        val back = json.decodeFromString(
            ProjectionSettings.serializer(), json.encodeToString(ProjectionSettings.serializer(), settings),
        )
        assertEquals(settings, back)
    }
}
