package org.churchpresenter.presenter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.utils.Utils.parseHexColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

@OptIn(ExperimentalTestApi::class)
class BibleStyleTest {

    private val red = "#FF0000"
    private val green = "#00FF00"
    private val blue = "#0000FF"
    private val yellow = "#FFFF00"

    private val primary = BibleTranslationSettings(
        fileName = "kjv.spb",
        textColor = red,
        lowerThirdTextColor = green,
        referenceColor = blue,
        lowerThirdReferenceColor = yellow,
        textFontSize = 70,
        lowerThirdTextFontSize = 28,
        referenceFontSize = 70,
        lowerThirdReferenceFontSize = 24,
        textLetterSpacing = 7,
        lowerThirdTextLetterSpacing = 14,
        referenceLetterSpacing = 14,
        lowerThirdReferenceLetterSpacing = 12,
        textWordSpacing = 21,
        lowerThirdTextWordSpacing = 7,
        referenceWordSpacing = 35,
        lowerThirdReferenceWordSpacing = 6,
        textShadowColor = red,
        textShadowSize = 50,
        textShadowOpacity = 100,
        lowerThirdTextShadowColor = green,
        lowerThirdTextShadowSize = 200,
        lowerThirdTextShadowOpacity = 50,
        referenceShadowColor = blue,
        referenceShadowSize = 100,
        referenceShadowOpacity = 100,
        lowerThirdReferenceShadowColor = yellow,
        lowerThirdReferenceShadowSize = 150,
        lowerThirdReferenceShadowOpacity = 100,
    )

    private val secondary = primary.copy(
        fileName = "rst.spb",
        textColor = yellow,
        lowerThirdTextColor = blue,
        referenceColor = green,
        lowerThirdReferenceColor = red,
        textLetterSpacing = 35,
        lowerThirdReferenceLetterSpacing = 6,
        textWordSpacing = 14,
        lowerThirdReferenceWordSpacing = 12,
        textShadowSize = 300,
        lowerThirdReferenceShadowSize = 50,
    )

    private val settings = AppSettings(bibleSettings = BibleSettings(translations = listOf(primary, secondary)))
    private val verses = listOf(
        SelectedVerse(translationFileName = "kjv.spb", verseText = "In the beginning"),
        SelectedVerse(translationFileName = "rst.spb", verseText = "В начале"),
    )

    @Test
    fun `colors spacing and shadows follow the full screen or lower third settings and the key`() =
        runComposeUiTest {
            var lowerThird by mutableStateOf(false)
            var role by mutableStateOf(Constants.OUTPUT_ROLE_NORMAL)
            var look: BibleLook? = null
            var style: BibleStyle? = null
            setContent {
                val l = BibleLook(
                    selectedVerses = verses,
                    appSettings = settings,
                    isLowerThird = lowerThird,
                    isLowerThirdVertical = false,
                    outputRole = role,
                    transitionAlpha = 1f,
                    showBackground = true,
                    crossfadeEnabled = false,
                    bibleTranslations = emptyList(),
                )
                look = l
                style = BibleStyle(l, l.rememberBibleFonts(), l.rememberBibleColors(l.isKey), l.rememberBiblePainters())
            }
            fun style(): BibleStyle {
                waitForIdle()
                return checkNotNull(style)
            }

            style().let {
                assertSame(look, it.look)
                assertEquals(parseHexColor(red), it.primaryBibleTextColor)
                assertEquals(parseHexColor(blue), it.primaryBibleReferenceTextColor)
                assertEquals(parseHexColor(yellow), it.secondaryBibleTextColor)
                assertEquals(parseHexColor(green), it.secondaryBibleReferenceTextColor)
                assertEquals(0.1f, it.pLsEm, EPS)
                assertEquals(0.2f, it.prLsEm, EPS)
                assertEquals(0.5f, it.sLsEm, EPS)
                assertEquals(0.2f, it.srLsEm, EPS)
                assertEquals(0.3f, it.pWsEm, EPS)
                assertEquals(0.5f, it.prWsEm, EPS)
                assertEquals(0.2f, it.sWsEm, EPS)
                assertEquals(0.5f, it.srWsEm, EPS)
                assertEquals(Offset(1f, 1f), it.pBibleShadowVal.offset)
                assertEquals(parseHexColor(red).copy(alpha = 0.78f), it.pBibleShadowVal.color)
                assertEquals(Offset(2f, 2f), it.pRefShadowVal.offset)
                assertEquals(Offset(6f, 6f), it.sBibleShadowVal.offset)
                assertEquals(Offset(2f, 2f), it.sRefShadowVal.offset)
            }

            lowerThird = true
            style().let {
                assertEquals(parseHexColor(green), it.primaryBibleTextColor)
                assertEquals(parseHexColor(yellow), it.primaryBibleReferenceTextColor)
                assertEquals(parseHexColor(blue), it.secondaryBibleTextColor)
                assertEquals(parseHexColor(red), it.secondaryBibleReferenceTextColor)
                assertEquals(0.5f, it.pLsEm, EPS)
                assertEquals(0.5f, it.prLsEm, EPS)
                assertEquals(0.5f, it.sLsEm, EPS)
                assertEquals(0.25f, it.srLsEm, EPS)
                assertEquals(0.25f, it.pWsEm, EPS)
                assertEquals(0.25f, it.prWsEm, EPS)
                assertEquals(0.25f, it.sWsEm, EPS)
                assertEquals(0.5f, it.srWsEm, EPS)
                assertEquals(Offset(4f, 4f), it.pBibleShadowVal.offset)
                assertEquals(parseHexColor(green).copy(alpha = 0.5f * 0.78f), it.pBibleShadowVal.color)
                assertEquals(Offset(3f, 3f), it.pRefShadowVal.offset)
                assertEquals(Offset(4f, 4f), it.sBibleShadowVal.offset)
                assertEquals(Offset(1f, 1f), it.sRefShadowVal.offset)
            }

            role = Constants.OUTPUT_ROLE_KEY
            style().let {
                assertEquals(Color.White, it.primaryBibleTextColor)
                assertEquals(Color.White, it.primaryBibleReferenceTextColor)
                assertEquals(Color.White, it.secondaryBibleTextColor)
                assertEquals(Color.White, it.secondaryBibleReferenceTextColor)
            }

            lowerThird = false
            assertEquals(Color.White, style().primaryBibleTextColor)
        }

    @Test
    fun `the style hands an output only the translations it is assigned`() = runComposeUiTest {
        var style: BibleStyle? = null
        setContent {
            val l = BibleLook(
                selectedVerses = verses,
                appSettings = settings,
                isLowerThird = false,
                isLowerThirdVertical = false,
                outputRole = Constants.OUTPUT_ROLE_NORMAL,
                transitionAlpha = 1f,
                showBackground = true,
                crossfadeEnabled = false,
                bibleTranslations = listOf(1),
            )
            style = BibleStyle(l, l.rememberBibleFonts(), l.rememberBibleColors(l.isKey), l.rememberBiblePainters())
        }
        waitForIdle()

        assertEquals(listOf(verses[1]), checkNotNull(style).versesForOutput(verses))
    }

    private companion object {
        const val EPS = 0.0001f
    }
}
