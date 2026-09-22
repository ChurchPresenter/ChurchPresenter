package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.app.churchpresenter.composables.BottomAlignedText
import org.churchpresenter.app.churchpresenter.subtitles.SubtitleCue
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault
import org.churchpresenter.settings.MediaSettings
import org.churchpresenter.settings.utils.Constants

/**
 * Draws the app-parsed subtitle cue for a video, styled per [mediaSettings].
 *
 * Only ever shown for a cue [SubtitleCueParser][org.churchpresenter.app.churchpresenter.subtitles.SubtitleCueParser]
 * produced (SRT/WebVTT) -- an embedded track or an `.ass`/`.ssa`/`.sub` file is still baked into
 * the shared frame by VLC and never reaches here. Styling reuses `STTPresenter`'s exact model
 * (`BottomAlignedText`, `TextBackdrop`, `TextOutline`) so subtitles and live captions look and
 * behave the same way to an operator configuring either.
 */
@Composable
fun SubtitleOverlay(
    cue: SubtitleCue,
    mediaSettings: MediaSettings,
    outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
) {
    val isKey = outputRole == Constants.OUTPUT_ROLE_KEY
    val textColor = if (isKey) Color.White else parseHexColor(mediaSettings.textColor)
    val bgOpacity = (mediaSettings.backgroundOpacity / 100f).coerceIn(0f, 1f)
    val cardBg = if (isKey) Color.White
                 else parseHexColor(
                     if (mediaSettings.backgroundColor == "transparent") "#1E1E2E" else mediaSettings.backgroundColor
                 ).copy(alpha = bgOpacity)
    val fontFamily = systemFontFamilyOrDefault(mediaSettings.fontType)
    val boxAlignment = sttPositionToAlignment(mediaSettings.position)

    // presenterScale is the same reference-resolution factor BiblePresenter/SongPresenter scale
    // their authored sizes by: `mediaSettings.fontSize` is authored at 1920x1080 like theirs, so
    // without it the same pixel size reads correctly there and wrong everywhere else -- notably a
    // narrow portrait output, where an unscaled size can dwarf or vanish against the frame.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val scaleFactor = presenterScale(maxWidth, maxHeight)
        val scaledFontSize = mediaSettings.fontSize * scaleFactor

        val shadowColorBase = parseHexColor(mediaSettings.shadowColor)
        val shadowSizeMul = mediaSettings.shadowSize / 100f
        val shadowAlpha = (mediaSettings.shadowOpacity / 100f).coerceIn(0f, 1f)
        val shadow = Shadow(
            color = shadowColorBase.copy(alpha = shadowAlpha),
            offset = Offset(
                SHADOW_OFFSET_PX * scaleFactor * shadowSizeMul,
                SHADOW_OFFSET_PX * scaleFactor * shadowSizeMul,
            ),
            blurRadius = SHADOW_BLUR_PX * scaleFactor * shadowSizeMul
        )

        val lineHeightSp = (scaledFontSize * mediaSettings.lineSpacing / 100f).sp

        val textStyle = TextStyle(
            fontFamily = fontFamily,
            fontWeight = if (mediaSettings.bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (mediaSettings.italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = if (mediaSettings.underline) TextDecoration.Underline else TextDecoration.None,
            shadow = if (mediaSettings.shadow) shadow else null,
            textAlign = when {
                mediaSettings.position.contains("Left") -> TextAlign.Left
                mediaSettings.position.contains("Right") -> TextAlign.Right
                else -> TextAlign.Center
            },
            color = textColor,
            fontSize = scaledFontSize.sp,
            lineHeight = lineHeightSp
        )

        Box(
            modifier = Modifier.fillMaxSize().padding((OUTER_MARGIN_DP * scaleFactor).dp),
            contentAlignment = boxAlignment
        ) {
            if (cue.text.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape((CARD_CORNER_RADIUS_DP * scaleFactor).dp))
                        .background(cardBg)
                        .padding((CARD_PADDING_DP * scaleFactor).dp)
                ) {
                    BottomAlignedText(
                        text = AnnotatedString(cue.text),
                        style = textStyle,
                        maxLines = mediaSettings.maxLines,
                        modifier = Modifier.fillMaxWidth(),
                        backdrop = mediaSettings.backdrop,
                        outline = mediaSettings.outline,
                        scaleFactor = scaleFactor,
                    )
                }
            }
        }
    }
}

private const val SHADOW_OFFSET_PX = 4f
private const val SHADOW_BLUR_PX = 8f
private const val OUTER_MARGIN_DP = 32f
private const val CARD_CORNER_RADIUS_DP = 16f
private const val CARD_PADDING_DP = 24f
