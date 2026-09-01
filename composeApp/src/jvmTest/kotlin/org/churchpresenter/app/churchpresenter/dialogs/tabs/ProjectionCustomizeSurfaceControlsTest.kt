@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.DictionarySettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Driving the Dictionary and Background panes, and reading back what each control stored.
 *
 * These two categories are not a pair of style profiles the way Bible and Songs are — the
 * dictionary card is one set of values whatever shape the output is, and a background surface is
 * picked by the output's shape before the pane is ever drawn, so a Song surface edited on a band
 * must leave the full-screen one alone.
 *
 * The shadow rows under a text colour exist only while the box above them is ticked, so they are
 * driven from that box rather than from a fixture that starts with shadow on.
 */
class ProjectionCustomizeSurfaceControlsTest {

    private fun dictionaryOutput(mode: String = Constants.DISPLAY_MODE_FULLSCREEN) = AppSettings(
        dictionarySettings = DictionarySettings(
            wordFontSize = 61,
            wordColor = "#AABBCC",
            wordShadowColor = "#778899",
            wordShadowSize = 41,
            wordShadowOpacity = 63,
            referenceFontSize = 37,
            referenceColor = "#445566",
            definitionFontSize = 47,
            definitionColor = "#556677",
            kjvUsageFontSize = 51,
            kjvUsageColor = "#667788",
            cardBackgroundColor = "#889900",
            cardBackgroundOpacity = 0.42f,
            transitionDuration = 555f,
        ),
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(ScreenAssignment(displayMode = mode)),
        ),
    )

    private fun backgroundOutput(mode: String = Constants.DISPLAY_MODE_FULLSCREEN) = AppSettings(
        backgroundSettings = BackgroundSettings(defaultBackgroundColor = "#AABBCC"),
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(ScreenAssignment(displayMode = mode)),
        ),
    )

    private fun AppSettings.storedDictionary(): DictionarySettings = assertNotNull(
        projectionSettings.screenAssignments[0].dictionaryOverride,
        "the output must have its own dictionary",
    )

    private fun AppSettings.storedBackground(): BackgroundSettings = assertNotNull(
        projectionSettings.screenAssignments[0].backgroundOverride,
        "the output must have its own background",
    )

    // ── The dictionary ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `the word element writes its size, colour and whether it shows`() {
        projectionTab(dictionaryOutput()) { get ->
            openCustomizePane(CustomizePane.DICTIONARY)
            retypeNumberField(61, 55)
            recolor("#AABBCC", "#112233")
            toggleCheckbox("Show")

            val stored = get().storedDictionary()
            assertEquals(55, stored.wordFontSize)
            assertEquals("#112233", stored.wordColor)
            assertFalse(stored.showWord, "the word was showing and must have been switched off")
        }
    }

    @Test
    fun `switching the word's shadow on reveals the three rows that configure it`() {
        projectionTab(dictionaryOutput()) { get ->
            openCustomizePane(CustomizePane.DICTIONARY)
            toggleCheckbox("Shadow")
            recolor("#778899", "#332211")
            retypeNumberField(41, 55)
            retypeNumberField(63, 77)

            val stored = get().storedDictionary()
            assertTrue(stored.wordShadow)
            assertEquals("#332211", stored.wordShadowColor)
            assertEquals(55, stored.wordShadowSize)
            assertEquals(77, stored.wordShadowOpacity)
        }
    }

    @Test
    fun `the definition element writes its own size, colour and whether it shows`() {
        projectionTab(dictionaryOutput()) { get ->
            openCustomizePane(CustomizePane.DICTIONARY, CustomizeElement.DICTIONARY_DEFINITION)
            retypeNumberField(47, 39)
            recolor("#556677", "#223344")
            toggleCheckbox("Show")

            val stored = get().storedDictionary()
            assertEquals(39, stored.definitionFontSize)
            assertEquals("#223344", stored.definitionColor)
            assertFalse(stored.showDefinition)
        }
    }

    @Test
    fun `the KJV usage element writes its own size and colour`() {
        projectionTab(dictionaryOutput()) { get ->
            openCustomizePane(CustomizePane.DICTIONARY, CustomizeElement.DICTIONARY_KJV)
            retypeNumberField(51, 46)
            recolor("#667788", "#334455")

            val stored = get().storedDictionary()
            assertEquals(46, stored.kjvUsageFontSize)
            assertEquals("#334455", stored.kjvUsageColor)
        }
    }

    @Test
    fun `the reference element writes its own size and colour`() {
        projectionTab(dictionaryOutput()) { get ->
            openCustomizePane(CustomizePane.DICTIONARY, CustomizeElement.DICTIONARY_REFERENCE)
            retypeNumberField(37, 31)
            recolor("#445566", "#556644")

            val stored = get().storedDictionary()
            assertEquals(31, stored.referenceFontSize)
            assertEquals("#556644", stored.referenceColor)
        }
    }

    @Test
    fun `the card element writes its colour and its opacity as a percentage`() {
        projectionTab(dictionaryOutput()) { get ->
            openCustomizePane(CustomizePane.DICTIONARY, CustomizeElement.DICTIONARY_CARD)
            recolor("#889900", "#004488")
            retypeNumberField(42, 60)

            val stored = get().storedDictionary()
            assertEquals("#004488", stored.cardBackgroundColor)
            assertEquals(0.6f, stored.cardBackgroundOpacity, absoluteTolerance = 0.001f)
        }
    }

    @Test
    fun `the dictionary strip writes the fades and their duration`() {
        projectionTab(dictionaryOutput()) { get ->
            openCustomizePane(CustomizePane.DICTIONARY)
            toggleCheckbox("Fade In", scroll = false)
            toggleCheckbox("Fade Out", scroll = false)
            retypeNumberField(555, 620)

            val stored = get().storedDictionary()
            assertFalse(stored.fadeIn)
            assertFalse(stored.fadeOut)
            assertEquals(620f, stored.transitionDuration)
        }
    }

    // ── The background ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `the default surface writes its colour and then its type`() {
        projectionTab(backgroundOutput()) { get ->
            openCustomizePane(CustomizePane.BACKGROUND)
            recolor("#AABBCC", "#112233")
            chooseSegment("Transparent")

            val stored = get().storedBackground()
            assertEquals("#112233", stored.defaultBackgroundColor)
            assertEquals(Constants.BACKGROUND_TRANSPARENT, stored.defaultBackgroundType)
        }
    }

    @Test
    fun `a band's Song surface writes the band's own config, not the full screen's`() {
        projectionTab(backgroundOutput(Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)) { get ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_SONG)
            chooseSegment("Video")

            val stored = get().storedBackground()
            assertEquals(
                Constants.BACKGROUND_VIDEO,
                stored.configFor(BackgroundScope.SONG_LOWER_THIRD).backgroundType,
            )
            assertEquals(
                Constants.BACKGROUND_COLOR,
                stored.configFor(BackgroundScope.SONG).backgroundType,
                "the full screen's Song surface must be untouched",
            )
        }
    }
}
