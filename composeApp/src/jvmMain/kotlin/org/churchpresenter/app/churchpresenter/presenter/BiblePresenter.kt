package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import org.churchpresenter.app.churchpresenter.models.AnimationType
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.composables.LoopingVideoBackground
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
        shadow = if (appSettings.bibleSettings.primaryBibleShadow) Shadow(
            color = Color.Black.copy(alpha = 0.7f),
            offset = Offset(2f, 2f),
            blurRadius = 4f
        ) else null
    )
    val primaryReferenceTextStyle = TextStyle(
        fontWeight = if (appSettings.bibleSettings.primaryReferenceBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (appSettings.bibleSettings.primaryReferenceItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (appSettings.bibleSettings.primaryReferenceUnderline) TextDecoration.Underline else TextDecoration.None,
        shadow = if (appSettings.bibleSettings.primaryReferenceShadow) Shadow(
            color = Color.Black.copy(alpha = 0.7f),
            offset = Offset(2f, 2f),
            blurRadius = 4f
        ) else null
    )
    val secondaryBibleTextStyle = TextStyle(
        fontWeight = if (appSettings.bibleSettings.secondaryBibleBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (appSettings.bibleSettings.secondaryBibleItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (appSettings.bibleSettings.secondaryBibleUnderline) TextDecoration.Underline else TextDecoration.None,
        shadow = if (appSettings.bibleSettings.secondaryBibleShadow) Shadow(
            color = Color.Black.copy(alpha = 0.7f),
            offset = Offset(2f, 2f),
            blurRadius = 4f
        ) else null
    )
    val secondaryReferenceTextStyle = TextStyle(
        fontWeight = if (appSettings.bibleSettings.secondaryReferenceBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (appSettings.bibleSettings.secondaryReferenceItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (appSettings.bibleSettings.secondaryReferenceUnderline) TextDecoration.Underline else TextDecoration.None,
        shadow = if (appSettings.bibleSettings.secondaryReferenceShadow) Shadow(
            color = Color.Black.copy(alpha = 0.7f),
            offset = Offset(2f, 2f),
            blurRadius = 4f
        ) else null
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

    val bgConfig = if (isLowerThird) appSettings.backgroundSettings.bibleLowerThirdBackground
    else appSettings.backgroundSettings.bibleBackground

    // Resolve effective background type/paths (handle Default → inherit from global)
    val effectiveType: String
    val effectiveImagePath: String
    val effectiveVideoPath: String
    var backgroundColor: Color

    if (bgConfig.backgroundType == Constants.BACKGROUND_DEFAULT) {
        val defaults = appSettings.backgroundSettings
        effectiveType = defaults.defaultBackgroundType
        effectiveImagePath = defaults.defaultBackgroundImage
        effectiveVideoPath = defaults.defaultBackgroundVideo
        backgroundColor = parseHexColor(defaults.defaultBackgroundColor)
    } else {
        effectiveType = bgConfig.backgroundType
        effectiveImagePath = bgConfig.backgroundImage
        effectiveVideoPath = bgConfig.backgroundVideo
        backgroundColor = parseHexColor(bgConfig.backgroundColor)
    }

    val backgroundImageBitmap = remember(effectiveType, effectiveImagePath, isLowerThird) {
        if (effectiveType == Constants.BACKGROUND_IMAGE && effectiveImagePath.isNotEmpty()) {
            try {
                val file = File(effectiveImagePath)
                if (file.exists()) org.jetbrains.skia.Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
                else null
            } catch (_: Exception) {
                null
            }
        } else null
    }

    val useVideoBackground = effectiveType == Constants.BACKGROUND_VIDEO && effectiveVideoPath.isNotEmpty()

    val bgModifier: Modifier = when {
        useVideoBackground -> Modifier.background(Color.Black) // video rendered as overlay
        effectiveType == Constants.BACKGROUND_IMAGE && backgroundImageBitmap != null ->
            Modifier.paint(painter = BitmapPainter(backgroundImageBitmap), contentScale = ContentScale.Crop)

        effectiveType == Constants.BACKGROUND_IMAGE ->
            Modifier.background(Color.Black)

        else ->
            Modifier.background(backgroundColor)
    }

    BoxWithConstraints(
        modifier.fillMaxSize()
            .then(if (!isLowerThird) bgModifier else Modifier)
    ) {
        if (useVideoBackground && !isLowerThird) {
            LoopingVideoBackground(
                videoPath = effectiveVideoPath,
                modifier = Modifier.fillMaxSize()
            )
        }
        val density = LocalDensity.current
        val widthScale = with(density) { maxWidth.toPx() / 1920f }
        val heightScale = with(density) { maxHeight.toPx() / 1080f }
        val scaleFactor = min(widthScale, heightScale).coerceIn(0.5f, 3.0f)

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

        if (isLowerThird) {
            // Background stretches full width at bottom third, text respects padding on top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.333f)
                    .align(Alignment.BottomCenter)
                    .then(bgModifier)
            )
        }

        // Outer box for padding/alignment — not animated
        Box(
            modifier = Modifier
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

            // Only animate the text content — background is never inside this block
            @Composable
            fun TextContent(verses: List<SelectedVerse>) {
                val primary = verses.first()
                val secondary = verses.getOrNull(1)
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
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = primaryBibleReferenceHorizontalAlignment
                            ) {
                                val bookNameOrAbbr =
                                    if (appSettings.bibleSettings.primaryShowAbbreviation && primary.bibleAbbreviation.isNotEmpty()) primary.bibleAbbreviation else ""
                                Text(
                                    fontFamily = primaryBibleReferenceFontStyle,
                                    fontSize = scaledPrimaryReferenceSize,
                                    text = "$bookNameOrAbbr ${primary.bookName} ${primary.chapter}:${primary.verseNumber}",
                                    color = primaryBibleReferenceTextColor,
                                    style = primaryReferenceTextStyle
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = primaryBibleHorizontalAlignment) {
                            Text(
                                fontFamily = primaryBibleFontStyle,
                                fontSize = scaledPrimaryBibleSize,
                                text = primary.verseText,
                                color = primaryBibleTextColor,
                                style = primaryBibleTextStyle
                            )
                        }
                        if (primaryBibleReferencePosition == Constants.POSITION_BELOW) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = primaryBibleReferenceHorizontalAlignment
                            ) {
                                val bookNameOrAbbr =
                                    if (appSettings.bibleSettings.primaryShowAbbreviation && primary.bibleAbbreviation.isNotEmpty()) primary.bibleAbbreviation else ""
                                Text(
                                    fontFamily = primaryBibleReferenceFontStyle,
                                    fontSize = scaledPrimaryReferenceSize,
                                    text = "$bookNameOrAbbr ${primary.bookName} ${primary.chapter}:${primary.verseNumber}",
                                    color = primaryBibleReferenceTextColor,
                                    style = primaryReferenceTextStyle
                                )
                            }
                        }
                        val showSecondary =
                            secondary != null && (!isLowerThird || appSettings.bibleSettings.secondaryBibleLowerThirdEnabled)
                        if (showSecondary && secondary != null) {
                            if (secondaryBibleReferencePosition == Constants.POSITION_ABOVE) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = secondaryBibleReferenceHorizontalAlignment
                                ) {
                                    val bookNameOrAbbr =
                                        if (appSettings.bibleSettings.secondaryShowAbbreviation && secondary.bibleAbbreviation.isNotEmpty()) secondary.bibleAbbreviation else ""
                                    Text(
                                        fontFamily = secondaryBibleReferenceFontStyle,
                                        fontSize = scaledSecondaryReferenceSize,
                                        text = "$bookNameOrAbbr ${secondary.bookName} ${secondary.chapter}:${secondary.verseNumber}",
                                        color = secondaryBibleReferenceTextColor,
                                        style = secondaryReferenceTextStyle
                                    )
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = secondaryBibleHorizontalAlignment) {
                                Text(
                                    fontFamily = secondaryBibleFontStyle,
                                    fontSize = scaledSecondaryBibleSize,
                                    text = secondary.verseText,
                                    color = secondaryBibleTextColor,
                                    style = secondaryBibleTextStyle
                                )
                            }
                            if (secondaryBibleReferencePosition == Constants.POSITION_BELOW) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = secondaryBibleReferenceHorizontalAlignment
                                ) {
                                    val bookNameOrAbbr =
                                        if (appSettings.bibleSettings.secondaryShowAbbreviation && secondary.bibleAbbreviation.isNotEmpty()) secondary.bibleAbbreviation else ""
                                    Text(
                                        fontFamily = secondaryBibleReferenceFontStyle,
                                        fontSize = scaledSecondaryReferenceSize,
                                        text = "$bookNameOrAbbr ${secondary.bookName} ${secondary.chapter}:${secondary.verseNumber}",
                                        color = secondaryBibleReferenceTextColor,
                                        style = secondaryReferenceTextStyle
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Resolve animation from appSettings — no params needed from caller
            val animationType = when (appSettings.bibleSettings.animationType) {
                Constants.ANIMATION_FADE -> AnimationType.FADE
                Constants.ANIMATION_SLIDE_LEFT -> AnimationType.SLIDE_LEFT
                Constants.ANIMATION_SLIDE_RIGHT -> AnimationType.SLIDE_RIGHT
                Constants.ANIMATION_NONE -> AnimationType.NONE
                else -> AnimationType.CROSSFADE
            }
            val transitionDuration = appSettings.bibleSettings.transitionDuration.toInt()

            when (animationType) {
                AnimationType.CROSSFADE -> Crossfade(
                    targetState = selectedVerses,
                    animationSpec = tween(transitionDuration),
                    label = "BibleCrossfade"
                ) { TextContent(it) }

                AnimationType.FADE -> AnimatedContent(
                    targetState = selectedVerses,
                    transitionSpec = { fadeIn(tween(transitionDuration)) togetherWith fadeOut(tween(transitionDuration)) },
                    label = "BibleFade"
                ) { TextContent(it) }

                AnimationType.SLIDE_LEFT -> AnimatedContent(
                    targetState = selectedVerses,
                    transitionSpec = {
                        slideInHorizontally(tween(transitionDuration)) { it } togetherWith slideOutHorizontally(
                            tween(
                                transitionDuration
                            )
                        ) { -it }
                    },
                    label = "BibleSlideLeft"
                ) { TextContent(it) }

                AnimationType.SLIDE_RIGHT -> AnimatedContent(
                    targetState = selectedVerses,
                    transitionSpec = {
                        slideInHorizontally(tween(transitionDuration)) { -it } togetherWith slideOutHorizontally(
                            tween(
                                transitionDuration
                            )
                        ) { it }
                    },
                    label = "BibleSlideRight"
                ) { TextContent(it) }

                AnimationType.NONE -> TextContent(selectedVerses)
            }
        }
    }
}