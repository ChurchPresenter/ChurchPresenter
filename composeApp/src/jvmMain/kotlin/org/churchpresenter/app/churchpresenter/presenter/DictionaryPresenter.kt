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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.app.churchpresenter.data.StrongsEntry
import org.churchpresenter.app.churchpresenter.data.settings.DictionarySettings
import org.churchpresenter.app.churchpresenter.utils.Constants
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

            Column(
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
                if (ds.showReference) {
                    Text(
                        text = entry.number,
                        color = referenceColor,
                        fontSize = ds.referenceFontSize.sp,
                        fontFamily = refFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        style = TextStyle(shadow = refShadow()),
                    )
                }

                // Original word
                if (ds.showWord && entry.word.isNotBlank()) {
                    Text(
                        text = entry.word,
                        color = wordColor,
                        fontSize = ds.wordFontSize.sp,
                        fontFamily = wordFontFamily,
                        fontWeight = if (ds.wordBold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (ds.wordItalic) FontStyle.Italic else FontStyle.Normal,
                        textAlign = TextAlign.Center,
                        style = TextStyle(shadow = wordShadow()),
                    )
                }

                // Transliteration · pronunciation (part of Reference section)
                if (ds.showReference) {
                    val translit = buildString {
                        if (entry.transliteration.isNotBlank()) append(entry.transliteration)
                        if (entry.pronunciation.isNotBlank() && entry.pronunciation != entry.transliteration) {
                            if (isNotEmpty()) append("  •  ")
                            append(entry.pronunciation)
                        }
                    }
                    if (translit.isNotBlank()) {
                        Text(
                            text = translit,
                            color = referenceColor,
                            fontSize = (ds.referenceFontSize * 0.85f).sp,
                            fontFamily = refFontFamily,
                            fontStyle = FontStyle.Italic,
                            textAlign = TextAlign.Center,
                            style = TextStyle(shadow = refShadow()),
                        )
                    }
                }

                // Definition
                if (ds.showDefinition && entry.definition.isNotBlank()) {
                    Spacer(Modifier.height((itemSpacing.value / 2).coerceAtLeast(2f).dp))
                    Text(
                        text = entry.definition,
                        color = definitionColor,
                        fontSize = ds.definitionFontSize.sp,
                        fontFamily = wordFontFamily,
                        textAlign = TextAlign.Center,
                        lineHeight = (ds.definitionFontSize * 1.4f).sp,
                    )
                }

                // KJV usage
                if (ds.showKjvUsage && entry.kjvUsage.isNotBlank()) {
                    Text(
                        text = entry.kjvUsage,
                        color = kjvColor,
                        fontSize = ds.kjvUsageFontSize.sp,
                        fontFamily = refFontFamily,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center,
                        lineHeight = (ds.kjvUsageFontSize * 1.4f).sp,
                    )
                }
            }
        }
    }
}
