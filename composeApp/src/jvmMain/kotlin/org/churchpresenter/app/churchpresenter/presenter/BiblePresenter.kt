package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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

    val primaryBibleHorizontalAlignment = when (appSettings.bibleSettings.primaryBibleHorizontalAlignment) {
        Constants.LEFT -> Arrangement.Start
        Constants.RIGHT -> Arrangement.End
        else -> Arrangement.Center
    }

    val primaryBibleReferenceHorizontalAlignment =
        when (appSettings.bibleSettings.primaryReferenceHorizontalAlignment) {
            Constants.LEFT -> Arrangement.Start
            Constants.RIGHT -> Arrangement.End
            else -> Arrangement.Center
        }

    val secondaryBibleHorizontalAlignment = when (appSettings.bibleSettings.secondaryBibleHorizontalAlignment) {
        Constants.LEFT -> Arrangement.Start
        Constants.RIGHT -> Arrangement.End
        else -> Arrangement.Center
    }

    val secondaryBibleReferenceHorizontalAlignment =
        when (appSettings.bibleSettings.secondaryReferenceHorizontalAlignment) {
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
        val scaledPrimaryBibleSize = (appSettings.bibleSettings.primaryBibleFontSize * scaleFactor).sp
        val scaledPrimaryReferenceSize = (appSettings.bibleSettings.primaryReferenceFontSize * scaleFactor).sp
        val scaledSecondaryBibleSize = (appSettings.bibleSettings.secondaryBibleFontSize * scaleFactor).sp
        val scaledSecondaryReferenceSize = (appSettings.bibleSettings.secondaryReferenceFontSize * scaleFactor).sp
        val leftOffSet = (appSettings.projectionSettings.windowLeft * scaleFactor).dp
        val rightOffSet = (appSettings.projectionSettings.windowRight * scaleFactor).dp
        val topOffSet = (appSettings.projectionSettings.windowTop * scaleFactor).dp
        val bottomOffSet = (appSettings.projectionSettings.windowBottom * scaleFactor).dp

        Box(
            modifier
                .fillMaxSize()
                .padding(start = leftOffSet, end = rightOffSet, top = topOffSet, bottom = bottomOffSet),
            contentAlignment = contentAlignment
        ) {
            Column {
                // Primary Bible
                if (primaryBibleReferencePosition == Constants.POSITION_ABOVE) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = primaryBibleReferenceHorizontalAlignment
                    ) {
                        val bookNameOrAbbr =
                            if (appSettings.bibleSettings.primaryShowAbbreviation && primaryBible.bibleAbbreviation.isNotEmpty()) {
                                primaryBible.bibleAbbreviation
                            } else {
                                ""
                            }
                        Text(
                            fontFamily = primaryBibleReferenceFontStyle,
                            fontSize = scaledPrimaryReferenceSize,
                            text = "$bookNameOrAbbr ${primaryBible.bookName} ${primaryBible.chapter}:${primaryBible.verseNumber}",
                            color = primaryBibleReferenceTextColor
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = primaryBibleHorizontalAlignment
                ) {
                    Text(
                        fontFamily = primaryBibleFontStyle,
                        fontSize = scaledPrimaryBibleSize,
                        text = primaryBible.verseText,
                        color = primaryBibleTextColor
                    )
                }
                if (primaryBibleReferencePosition == Constants.POSITION_BELOW) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = primaryBibleReferenceHorizontalAlignment
                    ) {
                        val bookNameOrAbbr =
                            if (appSettings.bibleSettings.primaryShowAbbreviation && primaryBible.bibleAbbreviation.isNotEmpty()) {
                                primaryBible.bibleAbbreviation
                            } else {
                                ""
                            }
                        Text(
                            fontFamily = primaryBibleReferenceFontStyle,
                            fontSize = scaledPrimaryReferenceSize,
                            text = "$bookNameOrAbbr ${primaryBible.bookName} ${primaryBible.chapter}:${primaryBible.verseNumber}",
                            color = primaryBibleReferenceTextColor
                        )
                    }
                }

                // Secondary Bible
                if (secondaryBible != null) {
                    if (secondaryBibleReferencePosition == Constants.POSITION_ABOVE) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = secondaryBibleReferenceHorizontalAlignment
                        ) {
                            val bookNameOrAbbr =
                                if (appSettings.bibleSettings.secondaryShowAbbreviation && secondaryBible.bibleAbbreviation.isNotEmpty()) {
                                    secondaryBible.bibleAbbreviation
                                } else {
                                    ""
                                }
                            Text(
                                fontFamily = secondaryBibleReferenceFontStyle,
                                fontSize = scaledSecondaryReferenceSize,
                                text = "$bookNameOrAbbr ${secondaryBible.bookName} ${secondaryBible.chapter}:${secondaryBible.verseNumber}",
                                color = secondaryBibleTextColor
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = secondaryBibleHorizontalAlignment
                    ) {
                        Text(
                            fontFamily = secondaryBibleFontStyle,
                            fontSize = scaledSecondaryBibleSize,
                            text = secondaryBible.verseText,
                            color = secondaryBibleTextColor
                        )
                    }
                    if (secondaryBibleReferencePosition == Constants.POSITION_BELOW) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = secondaryBibleReferenceHorizontalAlignment
                        ) {
                            val bookNameOrAbbr =
                                if (appSettings.bibleSettings.secondaryShowAbbreviation && secondaryBible.bibleAbbreviation.isNotEmpty()) {
                                    secondaryBible.bibleAbbreviation
                                } else {
                                    ""
                                }
                            Text(
                                fontFamily = secondaryBibleReferenceFontStyle,
                                fontSize = scaledSecondaryReferenceSize,
                                text = "$bookNameOrAbbr ${secondaryBible.bookName} ${secondaryBible.chapter}:${secondaryBible.verseNumber}",
                                color = secondaryBibleTextColor
                            )
                        }
                    }
                }
            }
        }
    }
}