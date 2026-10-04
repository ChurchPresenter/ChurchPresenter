package org.churchpresenter.presenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import org.churchpresenter.sharedui.composables.OutlinedText
import org.churchpresenter.sharedui.composables.rememberTextBackdropPainter

import org.churchpresenter.sharedui.utils.spacingEm
import org.churchpresenter.sharedui.utils.styledDisplayText
import org.churchpresenter.settings.utils.Constants

/* A song slide's title, number and section label, and the lyrics with what is held on them. */

@Composable
internal fun SongSlide.NumberPart(
    modifier: Modifier = Modifier,
    visibilityAlpha: Float = 1f,
    /** False in a corner, where filling the width would drag the number out of it. */
    fillWidth: Boolean = true,
) {
    val numberPainter = rememberTextBackdropPainter(numberStyleProfile.backdrop)
    OutlinedText(
        modifier = modifier
            .songElementMove(ss, SongStyleElement.NUMBER, isLowerThird, null, scaleFactor)
            .alpha(visibilityAlpha)
            .then(numberPainter.modifier),
        outline = keyedOutline(numberStyleProfile.outline),
        scaleFactor = scaleFactor,
        fillWidth = fillWidth,
        onTextLayout = numberPainter::onTextLayout,
        textAlign = songNumberHorizontalAlignment,
        fontFamily = songNumberFontFamily,
        fontSize = scaledSongNumberFontSize,
        text = styledDisplayText(
            section.songNumber.toString(),
            numberStyleProfile.transform,
            spacingEm(numberStyleProfile.letterSpacing, numberStyleProfile.fontSize),
            spacingEm(numberStyleProfile.wordSpacing, numberStyleProfile.fontSize),
        ),
        color = songNumberColor,
        style = songNumberTextStyleScaled
    )
}

/** The section label, drawn wherever [position] says it sits; nothing elsewhere. */
@Composable
internal fun SongSlide.SectionLabelPart(position: String) {
    if (sectionLabelPosition != position) return
    if (ss.isBoxed(SongStyleElement.SECTION_LABEL, isLowerThird)) return
    val label = sectionLabelText(section, ss.layoutExtras.sectionLabel, isTitleSlide) ?: return
    val profile = sectionLabelProfile
    val labelStyling = songLineStyling(profile, null, scaleFactor, isKey, this::scaleElementShadow)
    val labelPainter = rememberTextBackdropPainter(profile.backdrop)
    OutlinedText(
        modifier = Modifier
            .songElementMove(ss, SongStyleElement.SECTION_LABEL, isLowerThird, null, scaleFactor)
            .then(labelPainter.modifier),
        outline = keyedOutline(profile.outline),
        scaleFactor = scaleFactor,
        onTextLayout = labelPainter::onTextLayout,
        textAlign = getTextAlign(profile.horizontalAlignment),
        fontFamily = labelStyling.fontFamily,
        fontSize = labelStyling.fontSize,
        text = styledDisplayText(
            label,
            profile.transform,
            spacingEm(profile.letterSpacing, profile.fontSize),
            spacingEm(profile.wordSpacing, profile.fontSize),
        ),
        color = labelStyling.color,
        style = labelStyling.textStyle,
    )
}

@Composable
internal fun SongSlide.TitlePart(
    modifier: Modifier = Modifier,
    visibilityAlpha: Float = 1f,
    /** False beside the number in a row, where the two share the width. */
    fillWidth: Boolean = true,
) {
    val titlePainter = rememberTextBackdropPainter(titleProfileHere.backdrop)
    OutlinedText(
        modifier = modifier
            .songElementMove(ss, SongStyleElement.TITLE, isLowerThird, titleLanguage, scaleFactor)
            .alpha(visibilityAlpha)
            .then(titlePainter.modifier),
        outline = keyedOutline(titleProfileHere.outline),
        scaleFactor = scaleFactor,
        fillWidth = fillWidth,
        onTextLayout = titlePainter::onTextLayout,
        textAlign = titleAlignHere,
        fontFamily = titleFontFamilyHere,
        fontSize = titleFontSizeHere,
        text = styledDisplayText(
            effectiveTitle,
            titleProfileHere.transform,
            spacingEm(titleProfileHere.letterSpacing, titleProfileHere.fontSize),
            spacingEm(titleProfileHere.wordSpacing, titleProfileHere.fontSize),
        ),
        color = titleColorHere,
        style = titleTextStyleHere
    )
}

@Composable
internal fun SongSlide.TitleAndNumberRow(position: String, invisible: Boolean = false) {
    // "configured" = setting is not None (could appear on some slides)
    val hasTitleHere = titleConfigured && effectiveTitlePosition == position
    val hasNumberHere = numberConfigured && !numberInCorner &&
            effectiveSongNumberPosition == position
    if (!hasTitleHere && !hasNumberHere) return

    // Alpha: fully invisible when used as a balancing spacer,
    // otherwise visible on this slide or invisible (reserving space)
    val titleAlpha = if (invisible) 0f else if (shouldShowTitle) 1f else 0f
    val numberAlpha = if (invisible) 0f else if (shouldShowSongNumber) 1f else 0f

    if (hasTitleHere && hasNumberHere && samePosition) {
        if (sameHorizontal) {
            val sharedHAlign =
                if (isLowerThird) ss.songNumberLowerThirdHorizontalAlignment else ss.songNumberHorizontalAlignment
            val arrangement = when (sharedHAlign) {
                Constants.LEFT -> Arrangement.Start
                Constants.CENTER -> Arrangement.Center
                else -> Arrangement.End
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = arrangement) {
                if (numberBeforeTitle) {
                    NumberPart(visibilityAlpha = numberAlpha, fillWidth = false)
                    Spacer(Modifier.padding(horizontal = (4 * scaleFactor).dp))
                    TitlePart(visibilityAlpha = titleAlpha, fillWidth = false)
                } else {
                    TitlePart(visibilityAlpha = titleAlpha, fillWidth = false)
                    Spacer(Modifier.padding(horizontal = (4 * scaleFactor).dp))
                    NumberPart(visibilityAlpha = numberAlpha, fillWidth = false)
                }
            }
        } else {
            NumberPart(modifier = Modifier.fillMaxWidth(), visibilityAlpha = numberAlpha)
            TitlePart(modifier = Modifier.fillMaxWidth(), visibilityAlpha = titleAlpha)
        }
    } else if (hasNumberHere) {
        NumberPart(modifier = Modifier.fillMaxWidth(), visibilityAlpha = numberAlpha)
    } else if (hasTitleHere) {
        TitlePart(modifier = Modifier.fillMaxWidth(), visibilityAlpha = titleAlpha)
    }
}

/**
 * The lyrics with whatever is held on them drawn directly above and below, so
 * the alignment and offset that place the lyrics carry those along. [fillHeight]
 * where the lyrics share out the whole height between languages.
 */
@Composable
internal fun SongSlide.HeldOnLyrics(fillHeight: Boolean, lyrics: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().then(
            if (fillHeight) Modifier.fillMaxHeight() else Modifier.wrapContentHeight(),
        ),
    ) {
        SectionLabelPart(Constants.ABOVE_LYRICS)
        TitleAndNumberRow(Constants.ABOVE_LYRICS)
        Box(
            modifier = Modifier.fillMaxWidth()
                .then(if (fillHeight) Modifier.weight(1f) else Modifier),
            contentAlignment = if (isLowerThird) Alignment.BottomCenter else contentAlignment,
        ) {
            lyrics()
        }
        TitleAndNumberRow(Constants.BELOW_LYRICS)
        SectionLabelPart(Constants.BELOW_LYRICS)
        EndOfSongIndicator(afterHeld = true)
    }
}
