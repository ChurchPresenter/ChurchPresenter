package org.churchpresenter.app.churchpresenter.dialogs.tabs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The preview's shape: the presets, a ratio typed as Custom, and the label beside the picture.
 *
 * Stored as a size on the profile, which is also what makes a band vertical, so every one of these
 * is a statement about what gets written -- a portrait preset must stay portrait, and nothing typed
 * may produce a zero or a sliver.
 */
class PreviewShapeTest {

    @Test
    fun `each preset is recognised by the size it stores`() {
        for (preset in PreviewShapePreset.entries) {
            assertEquals(preset, PreviewShapePreset.matching(preset.width, preset.height))
        }
    }

    @Test
    fun `a size no preset stores is custom`() {
        assertNull(PreviewShapePreset.matching(1280, 1024))
    }

    @Test
    fun `the portrait preset is taller than it is wide`() {
        assertEquals(true, PreviewShapePreset.PORTRAIT.height > PreviewShapePreset.PORTRAIT.width)
    }

    @Test
    fun `a ratio is stored at the stored height`() {
        assertEquals(1350 to 1080, sizeForRatio(5, 4))
        assertEquals(3840 to 1080, sizeForRatio(32, 9))
    }

    @Test
    fun `a ratio outside its range is clamped rather than stored as typed`() {
        assertEquals(sizeForRatio(1, 1), sizeForRatio(0, 1), "0 wide is taken as 1")
        assertEquals(sizeForRatio(100, 1), sizeForRatio(500, 1), "past 100 is taken as 100")
    }

    @Test
    fun `no typed ratio can produce a side outside the allowed range`() {
        // 1:100 at 1080 tall is 10.8 wide -- a sliver -- so it is held at the floor.
        assertEquals(PREVIEW_SIDE_RANGE.first, sizeForRatio(1, 100).first)
        // 100:1 would be 108,000 wide, so it is held at the ceiling.
        assertEquals(PREVIEW_SIDE_RANGE.last, sizeForRatio(100, 1).first)
    }

    @Test
    fun `a size reduces to its smallest whole ratio`() {
        assertEquals(5 to 4, reducedRatio(1350, 1080))
        assertEquals(16 to 9, reducedRatio(1920, 1080))
    }

    @Test
    fun `a size with no area reads as the default shape`() {
        assertEquals(DEFAULT_RATIO, reducedRatio(0, 1080))
    }

    @Test
    fun `a 1080-tall size with no small ratio is labelled by its size`() {
        // 1921:1080 does not reduce below 100, so it cannot have been typed as a ratio.
        assertEquals("1921×1080", previewShapeLabel(1921, 1080))
    }

    @Test
    fun `the label names a preset, a typed ratio, or the exact size`() {
        assertEquals("16:9", previewShapeLabel(1920, 1080))
        assertEquals("5:4", previewShapeLabel(1350, 1080), "typed as a ratio, shown as one")
        assertEquals("1280×1024", previewShapeLabel(1280, 1024), "typed as a size, shown as one")
    }
}
