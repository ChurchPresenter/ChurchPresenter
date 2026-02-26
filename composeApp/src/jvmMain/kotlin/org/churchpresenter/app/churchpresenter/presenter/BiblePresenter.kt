package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.extensions.conditional
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault
import org.jetbrains.skia.Image
import java.io.File
import kotlin.math.min

@Composable
fun BiblePresenter(
    modifier: Modifier = Modifier,
    selectedVerses: List<SelectedVerse>,
    appSettings: AppSettings,
    isLowerThird: Boolean = false,
) {
    val primaryBibleFontStyle = remember(appSettings.bibleSettings.primaryBibleFontType) {
        systemFontFamilyOrDefault(appSettings.bibleSettings.primaryBibleFontType)
    }
    val primaryBibleReferenceFontStyle = remember(appSettings.bibleSettings.primaryReferenceFontType) {
        systemFontFamilyOrDefault(appSettings.bibleSettings.primaryReferenceFontType)
    }

    val secondaryBibleFontStyle = remember(appSettings.bibleSettings.secondaryBibleFontType) {
        systemFontFamilyOrDefault(appSettings.bibleSettings.secondaryBibleFontType)
    }
    val secondaryBibleReferenceFontStyle = remember(appSettings.bibleSettings.secondaryReferenceFontType) {
        systemFontFamilyOrDefault(appSettings.bibleSettings.secondaryReferenceFontType)
    }

    val primaryBible = selectedVerses.first()
    val secondaryBible = selectedVerses.getOrNull(1)

    val primaryBibleTextColor = remember(appSettings.bibleSettings.primaryBibleColor) {
        parseHexColor(appSettings.bibleSettings.primaryBibleColor)
    }
    val primaryBibleReferenceTextColor = remember(appSettings.bibleSettings.primaryReferenceColor) {
        parseHexColor(appSettings.bibleSettings.primaryReferenceColor)
    }
    val secondaryBibleTextColor = remember(appSettings.bibleSettings.secondaryBibleColor) {
        parseHexColor(appSettings.bibleSettings.secondaryBibleColor)
    }
    val secondaryBibleReferenceTextColor = remember(appSettings.bibleSettings.secondaryReferenceColor) {
        parseHexColor(appSettings.bibleSettings.secondaryReferenceColor)
    }

    // Text styles from settings
    val primaryBibleTextStyle = TextStyle(
        fontWeight = if (appSettings.bibleSettings.primaryBibleBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (appSettings.bibleSettings.primaryBibleItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (appSettings.bibleSettings.primaryBibleUnderline) TextDecoration.Underline else TextDecoration.None,
        shadow = if (appSettings.bibleSettings.primaryBibleShadow) Shadow(color = Color.Black.copy(alpha = 0.7f), offset = Offset(2f, 2f), blurRadius = 4f) else null
    )
    val primaryReferenceTextStyle = TextStyle(
        fontWeight = if (appSettings.bibleSettings.primaryReferenceBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (appSettings.bibleSettings.primaryReferenceItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (appSettings.bibleSettings.primaryReferenceUnderline) TextDecoration.Underline else TextDecoration.None,
        shadow = if (appSettings.bibleSettings.primaryReferenceShadow) Shadow(color = Color.Black.copy(alpha = 0.7f), offset = Offset(2f, 2f), blurRadius = 4f) else null
    )
    val secondaryBibleTextStyle = TextStyle(
        fontWeight = if (appSettings.bibleSettings.secondaryBibleBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (appSettings.bibleSettings.secondaryBibleItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (appSettings.bibleSettings.secondaryBibleUnderline) TextDecoration.Underline else TextDecoration.None,
        shadow = if (appSettings.bibleSettings.secondaryBibleShadow) Shadow(color = Color.Black.copy(alpha = 0.7f), offset = Offset(2f, 2f), blurRadius = 4f) else null
    )
    val secondaryReferenceTextStyle = TextStyle(
        fontWeight = if (appSettings.bibleSettings.secondaryReferenceBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (appSettings.bibleSettings.secondaryReferenceItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (appSettings.bibleSettings.secondaryReferenceUnderline) TextDecoration.Underline else TextDecoration.None,
        shadow = if (appSettings.bibleSettings.secondaryReferenceShadow) Shadow(color = Color.Black.copy(alpha = 0.7f), offset = Offset(2f, 2f), blurRadius = 4f) else null
    )

    val primaryBibleHorizontalAlignment = when (
        if (isLowerThird) appSettings.bibleSettings.primaryBibleLowerThirdHorizontalAlignment
        else appSettings.bibleSettings.primaryBibleHorizontalAlignment
    ) {
        Constants.LEFT -> Arrangement.Start
        Constants.RIGHT -> Arrangement.End
        else -> Arrangement.Center
    }

    val primaryBibleReferenceHorizontalAlignment = when (
        if (isLowerThird) appSettings.bibleSettings.primaryReferenceLowerThirdHorizontalAlignment
        else appSettings.bibleSettings.primaryReferenceHorizontalAlignment
    ) {
        Constants.LEFT -> Arrangement.Start
        Constants.RIGHT -> Arrangement.End
        else -> Arrangement.Center
    }

    val secondaryBibleHorizontalAlignment = when (
        if (isLowerThird) appSettings.bibleSettings.secondaryBibleLowerThirdHorizontalAlignment
        else appSettings.bibleSettings.secondaryBibleHorizontalAlignment
    ) {
        Constants.LEFT -> Arrangement.Start
        Constants.RIGHT -> Arrangement.End
        else -> Arrangement.Center
    }

    val secondaryBibleReferenceHorizontalAlignment = when (
        if (isLowerThird) appSettings.bibleSettings.secondaryReferenceLowerThirdHorizontalAlignment
        else appSettings.bibleSettings.secondaryReferenceHorizontalAlignment
    ) {
        Constants.LEFT -> Arrangement.Start
        Constants.RIGHT -> Arrangement.End
        else -> Arrangement.Center
    }

    val primaryBibleReferencePosition = appSettings.bibleSettings.primaryReferencePosition
    val secondaryBibleReferencePosition = appSettings.bibleSettings.secondaryReferencePosition

    // Combine vertical alignment with horizontal center
    val contentAlignment = when (appSettings.bibleSettings.verticalAlignment) {
        Constants.TOP -> Alignment.TopCenter
        Constants.BOTTOM -> Alignment.BottomCenter
        else -> Alignment.Center  // MIDDLE or default
    }

    var backgroundColor: Color = parseHexColor(appSettings.backgroundSettings.bibleBackground.backgroundColor)
    val backgroundType = appSettings.backgroundSettings.bibleBackground.backgroundType
    val backgroundImagePath = appSettings.backgroundSettings.bibleBackground.backgroundImage

    if (backgroundType == Constants.BACKGROUND_DEFAULT) {
        backgroundColor = parseHexColor(appSettings.backgroundSettings.defaultBackgroundColor)
    }

    // Load background image if type is IMAGE and path is not empty
    val backgroundImageBitmap = remember(backgroundImagePath) {
        if (backgroundType == Constants.BACKGROUND_IMAGE && backgroundImagePath.isNotEmpty()) {
            try {
                val file = File(backgroundImagePath)
                if (file.exists()) {
                    val bytes = file.readBytes()
                    Image.makeFromEncoded(bytes).toComposeImageBitmap()
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .conditional(backgroundType == Constants.BACKGROUND_DEFAULT || backgroundType == Constants.BACKGROUND_COLOR) {
                background(color = backgroundColor)
            }
            .conditional(backgroundType == Constants.BACKGROUND_IMAGE && backgroundImageBitmap != null) {
                paint(
                    painter = BitmapPainter(backgroundImageBitmap!!),
                    contentScale = ContentScale.Crop
                )
            }
            .conditional(backgroundType == Constants.BACKGROUND_IMAGE && backgroundImageBitmap == null) {
                background(color = Color.Black) // Fallback if image fails to load
            }
    ) {
        val density = LocalDensity.current

        // Calculate scale factor based on available width and height
        // Using a reference size of 1920x1080 as base
        val widthScale = with(density) { maxWidth.toPx() / 1920f }
        val heightScale = with(density) { maxHeight.toPx() / 1080f }
        val scaleFactor = min(widthScale, heightScale).coerceIn(0.5f, 3.0f)

        // Scale font sizes based on window size
        val effectivePrimaryBibleSize =
            if (isLowerThird) appSettings.bibleSettings.primaryBibleLowerThirdFontSize else appSettings.bibleSettings.primaryBibleFontSize
        val effectivePrimaryReferenceSize =
            if (isLowerThird) appSettings.bibleSettings.primaryReferenceLowerThirdFontSize else appSettings.bibleSettings.primaryReferenceFontSize
        val effectiveSecondaryBibleSize =
            if (isLowerThird) appSettings.bibleSettings.secondaryBibleLowerThirdFontSize else appSettings.bibleSettings.secondaryBibleFontSize
        val effectiveSecondaryReferenceSize =
            if (isLowerThird) appSettings.bibleSettings.secondaryReferenceLowerThirdFontSize else appSettings.bibleSettings.secondaryReferenceFontSize
        val scaledPrimaryBibleSize = (effectivePrimaryBibleSize * scaleFactor).sp
        val scaledPrimaryReferenceSize = (effectivePrimaryReferenceSize * scaleFactor).sp
        val scaledSecondaryBibleSize = (effectiveSecondaryBibleSize * scaleFactor).sp
        val scaledSecondaryReferenceSize = (effectiveSecondaryReferenceSize * scaleFactor).sp
        val leftOffSet = (appSettings.projectionSettings.windowLeft * scaleFactor).dp
        val rightOffSet = (appSettings.projectionSettings.windowRight * scaleFactor).dp
        val topOffSet = (appSettings.projectionSettings.windowTop * scaleFactor).dp
        val bottomOffSet = (appSettings.projectionSettings.windowBottom * scaleFactor).dp

        Box(
            modifier
                .fillMaxSize()
                .padding(start = leftOffSet, end = rightOffSet, top = topOffSet, bottom = bottomOffSet),
            contentAlignment = if (isLowerThird) Alignment.BottomCenter else contentAlignment
        ) {
            val innerModifier = if (isLowerThird)
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.333f)
                    .align(Alignment.BottomCenter)
            else
                Modifier.align(Alignment.Center)

            Box(
                modifier = innerModifier,
                contentAlignment = if (isLowerThird) Alignment.BottomCenter else Alignment.TopStart
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                    verticalArrangement = if (isLowerThird) Arrangement.Bottom else Arrangement.Top
                ) {
                    // Primary Bible
                    if (primaryBibleReferencePosition == Constants.POSITION_ABOVE) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = primaryBibleReferenceHorizontalAlignment) {
                            val bookNameOrAbbr = if (appSettings.bibleSettings.primaryShowAbbreviation && primaryBible.bibleAbbreviation.isNotEmpty()) primaryBible.bibleAbbreviation else ""
                            Text(
                                fontFamily = primaryBibleReferenceFontStyle,
                                fontSize = scaledPrimaryReferenceSize,
                                text = "$bookNameOrAbbr ${primaryBible.bookName} ${primaryBible.chapter}:${primaryBible.verseNumber}",
                                color = primaryBibleReferenceTextColor,
                                style = primaryReferenceTextStyle
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = primaryBibleHorizontalAlignment) {
                        Text(
                            fontFamily = primaryBibleFontStyle,
                            fontSize = scaledPrimaryBibleSize,
                            text = primaryBible.verseText,
                            color = primaryBibleTextColor,
                            style = primaryBibleTextStyle
                        )
                    }
                    if (primaryBibleReferencePosition == Constants.POSITION_BELOW) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = primaryBibleReferenceHorizontalAlignment) {
                            val bookNameOrAbbr = if (appSettings.bibleSettings.primaryShowAbbreviation && primaryBible.bibleAbbreviation.isNotEmpty()) primaryBible.bibleAbbreviation else ""
                            Text(
                                fontFamily = primaryBibleReferenceFontStyle,
                                fontSize = scaledPrimaryReferenceSize,
                                text = "$bookNameOrAbbr ${primaryBible.bookName} ${primaryBible.chapter}:${primaryBible.verseNumber}",
                                color = primaryBibleReferenceTextColor,
                                style = primaryReferenceTextStyle
                            )
                        }
                    }

                    // Secondary Bible
                    val showSecondary = secondaryBible != null &&
                        (!isLowerThird || appSettings.bibleSettings.secondaryBibleLowerThirdEnabled)
                    if (showSecondary) {
                        if (secondaryBibleReferencePosition == Constants.POSITION_ABOVE) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = secondaryBibleReferenceHorizontalAlignment) {
                                val bookNameOrAbbr = if (appSettings.bibleSettings.secondaryShowAbbreviation && secondaryBible!!.bibleAbbreviation.isNotEmpty()) secondaryBible.bibleAbbreviation else ""
                                Text(
                                    fontFamily = secondaryBibleReferenceFontStyle,
                                    fontSize = scaledSecondaryReferenceSize,
                                    text = "$bookNameOrAbbr ${secondaryBible!!.bookName} ${secondaryBible.chapter}:${secondaryBible.verseNumber}",
                                    color = secondaryBibleReferenceTextColor,
                                    style = secondaryReferenceTextStyle
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = secondaryBibleHorizontalAlignment) {
                            Text(
                                fontFamily = secondaryBibleFontStyle,
                                fontSize = scaledSecondaryBibleSize,
                                text = secondaryBible!!.verseText,
                                color = secondaryBibleTextColor,
                                style = secondaryBibleTextStyle
                            )
                        }
                        if (secondaryBibleReferencePosition == Constants.POSITION_BELOW) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = secondaryBibleReferenceHorizontalAlignment) {
                                val bookNameOrAbbr = if (appSettings.bibleSettings.secondaryShowAbbreviation && secondaryBible!!.bibleAbbreviation.isNotEmpty()) secondaryBible.bibleAbbreviation else ""
                                Text(
                                    fontFamily = secondaryBibleReferenceFontStyle,
                                    fontSize = scaledSecondaryReferenceSize,
                                    text = "$bookNameOrAbbr ${secondaryBible!!.bookName} ${secondaryBible.chapter}:${secondaryBible.verseNumber}",
                                    color = secondaryBibleReferenceTextColor,
                                    style = secondaryReferenceTextStyle
                                )
                            }
                        }
                    }
                }
            } // end inner Box
        }
    }
}