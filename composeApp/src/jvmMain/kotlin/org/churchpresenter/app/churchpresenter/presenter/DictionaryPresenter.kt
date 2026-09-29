package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.app.churchpresenter.composables.OutlinedText
import org.churchpresenter.app.churchpresenter.composables.backdropRoom
import org.churchpresenter.app.churchpresenter.composables.rememberTextBackdropPainter
import org.churchpresenter.app.churchpresenter.data.StrongsEntry
import org.churchpresenter.settings.DICTIONARY_DEFINITION_BOX
import org.churchpresenter.settings.DICTIONARY_KJV_BOX
import org.churchpresenter.settings.DICTIONARY_REFERENCE_BOX
import org.churchpresenter.settings.DICTIONARY_WORD_BOX
import org.churchpresenter.settings.DictionarySettings
import org.churchpresenter.settings.boxAt
import org.churchpresenter.settings.textBoxKey
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault

@Composable
fun DictionaryPresenter(
    modifier: Modifier = Modifier,
    entry: StrongsEntry?,
    dictionarySettings: DictionarySettings,
    outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
    transitionAlpha: Float = 1f,
) {
    val isKey = outputRole == Constants.OUTPUT_ROLE_KEY
    val ds = dictionarySettings

    val wordFontFamily = remember(ds.wordFontType) { systemFontFamilyOrDefault(ds.wordFontType) }
    val refFontFamily = remember(ds.referenceFontType) { systemFontFamilyOrDefault(ds.referenceFontType) }

    val wordColor = remember(ds.wordColor, isKey) {
        if (isKey) Color.White else parseHexColor(ds.wordColor)
    }
    val referenceColor = remember(ds.referenceColor, isKey) {
        if (isKey) Color.White else parseHexColor(ds.referenceColor)
    }
    val definitionColor = remember(ds.definitionColor, isKey) {
        if (isKey) Color.White else parseHexColor(ds.definitionColor)
    }
    val kjvColor = remember(ds.kjvUsageColor, isKey) {
        if (isKey) Color.White else parseHexColor(ds.kjvUsageColor)
    }
    val cardBgColor = remember(ds.cardBackgroundColor, isKey) {
        if (isKey) Color.Black else parseHexColor(ds.cardBackgroundColor)
    }

    val density = LocalDensity.current

    fun wordShadow(): Shadow? {
        if (!ds.wordShadow) return null
        val shadowColor = parseHexColor(ds.wordShadowColor).copy(alpha = ds.wordShadowOpacity / 100f)
        val shadowPx = with(density) { (ds.wordShadowSize / 10f).dp.toPx() }
        return Shadow(color = shadowColor, offset = Offset(shadowPx, shadowPx), blurRadius = shadowPx * 1.5f)
    }

    fun refShadow(): Shadow? {
        if (!ds.referenceShadow) return null
        val shadowColor = parseHexColor(ds.referenceShadowColor).copy(alpha = ds.referenceShadowOpacity / 100f)
        val shadowPx = with(density) { (ds.referenceShadowSize / 10f).dp.toPx() }
        return Shadow(color = shadowColor, offset = Offset(shadowPx, shadowPx), blurRadius = shadowPx * 1.5f)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = transitionAlpha },
        contentAlignment = Alignment.BottomCenter
    ) {
        if (entry != null) {
            val isSmall = maxHeight < 500.dp
            val outerPaddingH = if (isSmall) 16.dp else 40.dp
            val outerPaddingV = if (isSmall) 12.dp else 32.dp
            val innerPaddingH = if (isSmall) 24.dp else 48.dp
            val innerPaddingV = if (isSmall) 16.dp else 32.dp
            val itemSpacing = if (isSmall) 8.dp else 14.dp

            // Three painters, one per element the settings tab styles. The number and the
            // transliteration are both "reference", so they share one -- only one of them is
            // ever the last to lay out, and both draw the same band.
            val numberPainter = rememberTextBackdropPainter(ds.referenceBackdrop)
            val wordPainter = rememberTextBackdropPainter(ds.wordBackdrop)
            val translitPainter = rememberTextBackdropPainter(ds.referenceBackdrop)
            val definitionPainter = rememberTextBackdropPainter(ds.definitionBackdrop)
            val translit = buildString {
                if (entry.transliteration.isNotBlank()) append(entry.transliteration)
                if (entry.pronunciation.isNotBlank() && entry.pronunciation != entry.transliteration) {
                    if (isNotEmpty()) append("  •  ")
                    append(entry.pronunciation)
                }
            }
            // Each part as its own composable, drawn at [scale] of its configured size: 1 in the
            // card, less where a box it has been given shrinks it to fit.
            @Composable
            fun NumberPart(scale: Float) {
                OutlinedText(
                    modifier = Modifier.backdropRoom(ds.referenceBackdrop).then(numberPainter.modifier),
                    onTextLayout = numberPainter::onTextLayout,
                    outline = ds.referenceOutline,
                    scaleFactor = 1f,
                    fillWidth = false,
                    text = entry.number,
                    color = referenceColor,
                    fontSize = (ds.referenceFontSize * scale).sp,
                    fontFamily = refFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    style = TextStyle(shadow = refShadow()),
                )
            }
            @Composable
            fun WordPart(scale: Float) {
                OutlinedText(
                    modifier = Modifier.backdropRoom(ds.wordBackdrop).then(wordPainter.modifier),
                    onTextLayout = wordPainter::onTextLayout,
                    outline = ds.wordOutline,
                    scaleFactor = 1f,
                    fillWidth = false,
                    text = entry.word,
                    color = wordColor,
                    fontSize = (ds.wordFontSize * scale).sp,
                    fontFamily = wordFontFamily,
                    fontWeight = if (ds.wordBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (ds.wordItalic) FontStyle.Italic else FontStyle.Normal,
                    textAlign = TextAlign.Center,
                    style = TextStyle(shadow = wordShadow()),
                )
            }
            @Composable
            fun TranslitPart(scale: Float) {
                OutlinedText(
                    modifier = Modifier.backdropRoom(ds.referenceBackdrop).then(translitPainter.modifier),
                    onTextLayout = translitPainter::onTextLayout,
                    outline = ds.referenceOutline,
                    scaleFactor = 1f,
                    fillWidth = false,
                    text = translit,
                    color = referenceColor,
                    fontSize = (ds.referenceFontSize * TRANSLIT_SHARE * scale).sp,
                    fontFamily = refFontFamily,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center,
                    style = TextStyle(shadow = refShadow()),
                )
            }
            @Composable
            fun DefinitionPart(scale: Float) {
                OutlinedText(
                    modifier = Modifier.backdropRoom(ds.definitionBackdrop).then(definitionPainter.modifier),
                    onTextLayout = definitionPainter::onTextLayout,
                    outline = ds.definitionOutline,
                    scaleFactor = 1f,
                    fillWidth = false,
                    text = entry.definition,
                    color = definitionColor,
                    fontSize = (ds.definitionFontSize * scale).sp,
                    fontFamily = wordFontFamily,
                    textAlign = TextAlign.Center,
                    lineHeight = (ds.definitionFontSize * LINE_HEIGHT_SHARE * scale).sp,
                    style = TextStyle(),
                )
            }
            @Composable
            fun KjvPart(scale: Float) {
                OutlinedText(
                    outline = ds.definitionOutline,
                    scaleFactor = 1f,
                    fillWidth = false,
                    text = entry.kjvUsage,
                    color = kjvColor,
                    fontSize = (ds.kjvUsageFontSize * scale).sp,
                    fontFamily = refFontFamily,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center,
                    lineHeight = (ds.kjvUsageFontSize * LINE_HEIGHT_SHARE * scale).sp,
                    style = TextStyle(),
                )
            }
            fun boxOf(part: String) = ds.textBoxes.boxAt(textBoxKey(part, lowerThird = false))
            val showsReference = ds.showReference
            val showsWord = ds.showWord && entry.word.isNotBlank()
            val showsDefinition = ds.showDefinition && entry.definition.isNotBlank()
            val showsKjv = ds.showKjvUsage && entry.kjvUsage.isNotBlank()
            val referenceInCard = showsReference && !boxOf(DICTIONARY_REFERENCE_BOX).enabled
            val wordInCard = showsWord && !boxOf(DICTIONARY_WORD_BOX).enabled
            val definitionInCard = showsDefinition && !boxOf(DICTIONARY_DEFINITION_BOX).enabled
            val kjvInCard = showsKjv && !boxOf(DICTIONARY_KJV_BOX).enabled

            if (listOf(referenceInCard, wordInCard, definitionInCard, kjvInCard).any { it }) Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = outerPaddingH, vertical = outerPaddingV)
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardBgColor.copy(alpha = ds.cardBackgroundOpacity))
                    .padding(horizontal = innerPaddingH, vertical = innerPaddingV)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(itemSpacing)
            ) {
                // Strong's number badge (part of Reference section)
                if (referenceInCard) NumberPart(1f)
                // Original word
                if (wordInCard) WordPart(1f)
                // Transliteration · pronunciation (part of Reference section)
                if (referenceInCard && translit.isNotBlank()) TranslitPart(1f)
                // Definition
                if (definitionInCard) {
                    Spacer(Modifier.height((itemSpacing.value / HALF).coerceAtLeast(MIN_DEFINITION_GAP).dp))
                    DefinitionPart(1f)
                }
                // KJV usage
                if (kjvInCard) KjvPart(1f)
            }

            // Each boxed part in its own box, shrunk to fit it where the box says so.
            val area = Rect(0f, 0f, maxWidth.value, maxHeight.value)
            val measurer = rememberTextMeasurer()
            @Composable
            fun Boxed(part: String, shows: Boolean, text: String, size: Int, content: @Composable (Float) -> Unit) {
                val box = boxOf(part)
                if (!shows || !box.enabled) return
                val rect = box.rectIn(area)
                val fitted = remember(text, rect, box, size) {
                    fitInBox(
                        measurer,
                        BoxFitText(AnnotatedString(text), TextStyle(lineHeight = (size * LINE_HEIGHT_SHARE).sp), size),
                        box,
                        IntSize(rect.width.toInt(), rect.height.toInt()),
                    )
                }
                val scale = fitted.toFloat() / size.coerceAtLeast(1)
                BoxedItem(rect, box, Constants.CENTER, textBoxKey(part, false)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { content(scale) }
                }
            }
            val referenceText = entry.number + "\n" + translit
            Boxed(DICTIONARY_REFERENCE_BOX, showsReference, referenceText, ds.referenceFontSize) { scale ->
                NumberPart(scale)
                if (translit.isNotBlank()) TranslitPart(scale)
            }
            Boxed(DICTIONARY_WORD_BOX, showsWord, entry.word, ds.wordFontSize) { WordPart(it) }
            Boxed(DICTIONARY_DEFINITION_BOX, showsDefinition, entry.definition, ds.definitionFontSize) {
                DefinitionPart(it)
            }
            Boxed(DICTIONARY_KJV_BOX, showsKjv, entry.kjvUsage, ds.kjvUsageFontSize) { KjvPart(it) }
        }
    }
}

/** The gap above the definition: half the card's spacing, and never less than [MIN_DEFINITION_GAP] dp. */
private const val HALF = 2f
private const val MIN_DEFINITION_GAP = 2f

/** The transliteration's size, as a share of the reference's. */
private const val TRANSLIT_SHARE = 0.85f

/** The definition's and KJV usage's line height, as a share of their size. */
private const val LINE_HEIGHT_SHARE = 1.4f
