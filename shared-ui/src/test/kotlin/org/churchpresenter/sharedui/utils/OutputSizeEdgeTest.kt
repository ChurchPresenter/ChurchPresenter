package org.churchpresenter.sharedui.utils

import org.churchpresenter.settings.ScreenAssignment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class OutputSizeEdgeTest {

    @Test
    fun `a screen with a width but no height is treated as having no bounds`() {
        val slot = ScreenAssignment(
            targetBoundsW = 1920, targetBoundsH = 0,
            devWindowWidth = 800, devWindowHeight = 600,
        )
        assertEquals(OutputSize(800, 600), outputSizeOf(slot, OutputKind.SCREEN))
    }

    @Test
    fun `a configured size with a width but no height falls back to 1080p`() {
        val output = ScreenAssignment(ndiWidth = 1280, ndiHeight = 0)
        assertEquals(FallbackOutputSize, outputSizeOf(output, OutputKind.NDI))
    }

    @Test
    fun `a preview output carries what it was built from`() {
        val assignment = ScreenAssignment(targetBoundsW = 1024, targetBoundsH = 768)
        val output = PreviewOutput(
            "screen-1", "Projector", OutputSize(1024, 768), showsMode = true, assignment = assignment,
        )
        assertEquals("screen-1", output.component1())
        assertEquals("Projector", output.component2())
        assertEquals(OutputSize(1024, 768), output.component3())
        assertEquals(true, output.component4())
        assertEquals(assignment, output.component5())
        assertEquals(output, output.copy())
        assertEquals(output.hashCode(), output.copy().hashCode())
        assertNotEquals(output, output.copy(showsMode = false))
        assertEquals(true, output.toString().contains("Projector"))
    }

    @Test
    fun `a preview output's fields read back as they were given`() {
        val assignment = ScreenAssignment(ndiWidth = 1280, ndiHeight = 720)
        val output = PreviewOutput("ndi-1", "Stream", OutputSize(1280, 720), showsMode = false, assignment = assignment)
        assertEquals("ndi-1", output.key)
        assertEquals("Stream", output.label)
        assertEquals(1280, output.size.width)
        assertEquals(720, output.size.height)
        assertEquals(false, output.showsMode)
        assertEquals(assignment, output.assignment)
    }

    @Test
    fun `the fallback stage is shaped like a 1080p output`() {
        assertEquals(1920f / 1080f, FALLBACK_STAGE_ASPECT, 0.0001f)
    }
}
