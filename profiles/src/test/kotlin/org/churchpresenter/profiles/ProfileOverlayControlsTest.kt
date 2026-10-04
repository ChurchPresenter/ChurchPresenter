package org.churchpresenter.profiles

import androidx.compose.ui.test.ExperimentalTestApi
import org.churchpresenter.settings.MediaSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.QASettings
import org.churchpresenter.settings.STTSettings
import org.churchpresenter.settings.StageMonitorSettings
import org.churchpresenter.settings.StageMonitorZoneStyle
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ProfileOverlayControlsTest {

    @Test
    fun `subtitles write their box color and their text box`() = profilesTab(
        profileDocument(profile = OutputProfile(mediaSettings = MediaSettings(backgroundColor = "#101010"))),
    ) { get ->
        openCustomizePane(CustomizePane.SUBTITLES)
        recolor("#101010", "#202020")
        toggleCheckbox("Text box")
        toggleCheckbox("Keep clear of other boxes")
        val media = get().profile().mediaSettings
        assertEquals("#202020", media.backgroundColor)
        assertTrue(media.textBoxes.values.single().enabled)
        assertNotEquals(MediaSettings().textBoxOptions, media.textBoxOptions)
    }

    @Test
    fun `Q&A writes its box and code colors and its text boxes`() = profilesTab(
        profileDocument(
            profile = OutputProfile(
                qaSettings = QASettings(
                    backgroundColor = "#101010",
                    qrForegroundColor = "#111111",
                    qrBackgroundColor = "#EEEEEE",
                ),
            ),
        ),
    ) { get ->
        openCustomizePane(CustomizePane.QA)
        recolor("#101010", "#202020")
        recolor("#111111", "#222222")
        recolor("#EEEEEE", "#DDDDDD")
        toggleCheckbox("Text box")
        toggleCheckbox("Keep clear of other boxes")
        val qa = get().profile().qaSettings
        assertEquals("#202020", qa.backgroundColor)
        assertEquals("#222222", qa.qrForegroundColor)
        assertEquals("#DDDDDD", qa.qrBackgroundColor)
        assertTrue(qa.textBoxes.values.any { it.enabled })
        assertNotEquals(QASettings().textBoxOptions, qa.textBoxOptions)
    }

    @Test
    fun `captions write their box color, translation color and text boxes`() = profilesTab(
        profileDocument(
            profile = OutputProfile(
                sttSettings = STTSettings(
                    displayMode = "both",
                    backgroundColor = "#101010",
                    translationTextColor = "#ABABAB",
                ),
            ),
        ),
    ) { get ->
        openCustomizePane(CustomizePane.CAPTIONS)
        recolor("#ABABAB", "#BCBCBC")
        recolor("#101010", "#202020")
        toggleCheckbox("Text box")
        toggleCheckbox("Keep clear of other boxes")
        val stt = get().profile().sttSettings
        assertEquals("#BCBCBC", stt.translationTextColor)
        assertEquals("#202020", stt.backgroundColor)
        assertTrue(stt.textBoxes.values.any { it.enabled })
        assertNotEquals(STTSettings().textBoxOptions, stt.textBoxOptions)
    }

    private val zone = StageMonitorSettings().layout.slots.first()

    private fun stageDoc() = profileDocument(
        mode = Constants.DISPLAY_MODE_STAGE_MONITOR,
        profile = OutputProfile(
            stageMonitorSettings = StageMonitorSettings(
                zoneStyles = mapOf(
                    zone to StageMonitorZoneStyle(
                        fontType = SENTINEL_FONT,
                        color = "#ABCDEF",
                        chordColor = "#4FD3E8",
                        shadow = true,
                        shadowColor = "#0A0B0C",
                        shadowSize = 77,
                        shadowOpacity = 66,
                    ),
                ),
            ),
        ),
    )

    @Test
    fun `a stage zone writes its face, colors and shadow`() {
        val family = uniquelyNamedFont()
        profilesTab(stageDoc()) { get ->
            openCustomizePane(CustomizePane.STAGE_MONITOR)
            pickFont(SENTINEL_FONT, family)
            recolor("#ABCDEF", "#123123")
            recolor("#4FD3E8", "#321321")
            recolor("#0A0B0C", "#0C0B0A")
            retypeNumberField(77, 88)
            retypeNumberField(66, 55)
            val style = get().profile().stageMonitorSettings.styleFor(zone)
            assertEquals(family, style.fontType)
            assertEquals("#123123", style.color)
            assertEquals("#321321", style.chordColor)
            assertEquals("#0C0B0A", style.shadowColor)
            assertEquals(88, style.shadowSize)
            assertEquals(55, style.shadowOpacity)
        }
    }

    @Test
    fun `a stage zone moved into a box, and the box options, are written`() = profilesTab(stageDoc()) { get ->
        openCustomizePane(CustomizePane.STAGE_MONITOR)
        toggleCheckbox("Text box")
        toggleCheckbox("Keep clear of other boxes")
        val sm = get().profile().stageMonitorSettings
        assertTrue(sm.textBoxes.values.any { it.enabled })
        assertNotEquals(StageMonitorSettings().textBoxOptions, sm.textBoxOptions)
    }
}
