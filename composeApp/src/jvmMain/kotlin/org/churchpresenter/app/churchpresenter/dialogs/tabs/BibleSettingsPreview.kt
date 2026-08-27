package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bible_preview_full_screen
import churchpresenter.composeapp.generated.resources.bible_preview_lower_third
import churchpresenter.composeapp.generated.resources.bible_preview_no_translations
import churchpresenter.composeapp.generated.resources.bible_preview_sample_book
import churchpresenter.composeapp.generated.resources.bible_preview_sample_verse
import org.churchpresenter.app.churchpresenter.presenter.BiblePresenter
import org.churchpresenter.bible.PreviewVerse
import org.churchpresenter.bible.defaultTranslationAbbreviation
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.jetbrains.compose.resources.stringResource
import kotlin.math.min

/** The reference the sample verse carries when a module could not be read. */
private const val SAMPLE_CHAPTER = 3
private const val SAMPLE_VERSE = 16

/** Fallback geometry for an output that has not reported its own bounds. */
private const val DEFAULT_OUTPUT_WIDTH = 1920
private const val DEFAULT_OUTPUT_HEIGHT = 1080

/**
 * How tall the preview is allowed to get.
 *
 * The dialog is at most 1400x900 and rather less on a 1366x768 laptop, and the typography panel
 * under the preview needs roughly 250dp of it. Left to fill the pane's width the preview would take
 * more than the whole remainder, so it is capped here and centred in the space instead.
 */
internal val PREVIEW_MAX_HEIGHT = 260.dp

private const val PREVIEW_BACKGROUND = 0xFF08090B
private const val EMPTY_NOTE_ALPHA = 0.45f
private const val BADGE_ALPHA = 0.55f
private const val BADGE_TEXT_ALPHA = 0.75f

/** The band's height is stored as a whole percentage of the output. */
private const val PERCENT = 100f

private const val MARGIN_GUIDE_ALPHA = 0.5f
private const val GUIDE_STROKE_PX = 1f
private const val GUIDE_DASH_PX = 4f

/** The resolution the styling is being designed against, in the output's own pixels. */
internal data class PreviewOutputSize(val width: Int, val height: Int) {
    val aspectRatio: Float get() = width.toFloat() / height.toFloat()
}

/**
 * The screen this styling actually lands on.
 *
 * The first assigned output that has reported its bounds, so a 4:3 projector or a 2560x1080
 * ultrawide is previewed at its own shape rather than assumed to be 16:9. With nothing assigned
 * yet, or nothing that has reported, 1920x1080 stands in.
 */
internal fun previewOutputSize(settings: AppSettings): PreviewOutputSize {
    val assigned = settings.projectionSettings.screenAssignments
        .firstOrNull { it.targetBoundsW > 0 && it.targetBoundsH > 0 }
    return if (assigned != null) {
        PreviewOutputSize(assigned.targetBoundsW, assigned.targetBoundsH)
    } else {
        PreviewOutputSize(DEFAULT_OUTPUT_WIDTH, DEFAULT_OUTPUT_HEIGHT)
    }
}

/**
 * What the configured styling puts on screen -- drawn by [BiblePresenter] itself.
 *
 * This composes the **real presenter** at the output's own logical size and scales the result down,
 * exactly as the live preview panel beside the tabs does. It does not reproduce the presenter's
 * layout, and that is the point: an earlier version of this file recomputed the scale factor, the
 * margins, the auto-fit and the band arithmetic by hand, and every one of them was a separate
 * opportunity to disagree with the output. They all did, in different ways and on different
 * screens. Rendering the presenter means the preview is right by construction, and stays right when
 * the presenter changes.
 *
 * Backgrounds are left off: the presenter would decode an image or start a video for a picture a
 * few hundred dp wide, and what is being previewed here is the type.
 */
@Composable
internal fun BiblePreviewPanel(
    settings: AppSettings,
    translations: List<BibleTranslationSettings>,
    target: BibleStyleTarget,
    /** A verse read out of each module, keyed by file name; a missing one falls back to the sample. */
    verses: Map<String, PreviewVerse>,
    modifier: Modifier = Modifier,
) {
    val output = previewOutputSize(settings)
    val sampleVerse = stringResource(Res.string.bible_preview_sample_verse)
    val sampleBook = stringResource(Res.string.bible_preview_sample_book)
    val selectedVerses = translations.map { translation ->
        val verse = verses[translation.fileName]
        SelectedVerse(
            translationFileName = translation.fileName,
            bibleAbbreviation = translation.customAbbreviation.ifBlank {
                defaultTranslationAbbreviation(translation.customName, translation.fileName)
            },
            bookName = verse?.bookName?.takeIf { it.isNotBlank() } ?: sampleBook,
            chapter = verse?.chapter ?: SAMPLE_CHAPTER,
            verseNumber = verse?.verseNumber ?: SAMPLE_VERSE,
            verseText = verse?.text ?: sampleVerse,
        )
    }
    // Which lower third the outputs are actually set up for: the bottom band and the right-hand
    // strip are different shapes, and previewing the wrong one misreports where the text sits.
    val vertical = settings.projectionSettings.screenAssignments.any { it.isLowerThirdVertical }

    Box(
        modifier = modifier
            .aspectRatio(output.aspectRatio)
            .clipToBounds()
            .background(Color(PREVIEW_BACKGROUND), RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
    ) {
        if (selectedVerses.isEmpty()) {
            Text(
                text = stringResource(Res.string.bible_preview_no_translations),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = EMPTY_NOTE_ALPHA),
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            ScaledPresenter(output) {
                BiblePresenter(
                    selectedVerses = selectedVerses,
                    appSettings = settings,
                    isLowerThird = target.isLowerThird,
                    isLowerThirdVertical = target.isLowerThird && vertical,
                    showBackground = false,
                )
            }
        }
        // Drawn outside the scaled layer, not inside it: within the layer a 1dp line is scaled
        // down with everything else and comes out a quarter of a pixel wide, which is to say
        // invisible. Expressed as fractions of the output instead, which the box matches exactly
        // because it carries the output's own aspect ratio.
        MarginGuide(
            output = output,
            settings = settings,
            lowerThird = target.isLowerThird,
            color = MaterialTheme.colorScheme.primary.copy(alpha = MARGIN_GUIDE_ALPHA),
        )
        PreviewBadge(
            label = stringResource(
                if (target.isLowerThird) {
                    Res.string.bible_preview_lower_third
                } else {
                    Res.string.bible_preview_full_screen
                },
            ),
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
        )
    }
}

/**
 * Lays [content] out at [output]'s own size and scales the drawn result into the space available.
 *
 * The same trick `LivePreviewPanel` uses, and for the same reason. Density is pinned to 1 so that a
 * dp is an output pixel: [BiblePresenter] derives its own scale factor from `maxWidth.toPx()`, so
 * without this it would see the *dialog's* density and size everything against that rather than
 * against the output. The scaling happens in a graphics layer afterwards, so every proportion the
 * presenter chose -- type, margins, the lower-third band, the auto-fit -- survives untouched.
 */
@Composable
private fun ScaledPresenter(output: PreviewOutputSize, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val scale = min(
                    constraints.maxWidth.toFloat() / output.width,
                    constraints.maxHeight.toFloat() / output.height,
                )
                val placeable = measurable.measure(
                    constraints.copy(
                        minWidth = output.width,
                        maxWidth = output.width,
                        minHeight = output.height,
                        maxHeight = output.height,
                    ),
                )
                layout((output.width * scale).toInt(), (output.height * scale).toInt()) {
                    placeable.placeWithLayer(0, 0) {
                        this.scaleX = scale
                        this.scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                }
            },
    ) {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
            content()
        }
    }
}

/**
 * The dashed rectangle marking where text is actually allowed to go.
 *
 * The presenter respects the projection window insets and the Bible margins but draws nothing to
 * show them, so the guide is the preview's own -- and it has to agree with the presenter's geometry
 * exactly or it marks a boundary nothing observes. On the lower third that boundary is the band:
 * the presenter takes [ProjectionSettings.lowerThirdHeightPercent] of the *inset* height and sits it
 * on the floor of that area, so the guide follows the band rather than the whole screen.
 */
@Composable
private fun MarginGuide(
    output: PreviewOutputSize,
    settings: AppSettings,
    lowerThird: Boolean,
    color: Color,
) {
    val projection = settings.projectionSettings
    val bible = settings.bibleSettings
    val leftFraction = (projection.windowLeft + bible.marginLeft).toFloat() / output.width
    val rightFraction = (projection.windowRight + bible.marginRight).toFloat() / output.width
    val topFraction = (projection.windowTop + bible.marginTop).toFloat() / output.height
    val bottomFraction = (projection.windowBottom + bible.marginBottom).toFloat() / output.height
    val insetHeightFraction = (1f - topFraction - bottomFraction).coerceAtLeast(0f)
    val guideTopFraction = if (lowerThird) {
        1f - bottomFraction - insetHeightFraction * projection.lowerThirdHeightPercent / PERCENT
    } else {
        topFraction
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val left = size.width * leftFraction
        val top = size.height * guideTopFraction
        val width = size.width * (1f - leftFraction - rightFraction)
        val height = size.height * (1f - guideTopFraction - bottomFraction)
        if (width <= 0f || height <= 0f) return@Canvas
        drawRect(
            color = color,
            topLeft = Offset(left, top),
            size = Size(width, height),
            style = Stroke(
                width = GUIDE_STROKE_PX,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(GUIDE_DASH_PX, GUIDE_DASH_PX)),
            ),
        )
    }
}

/** Which of the two outputs this picture is of, said in the corner rather than only in the switch. */
@Composable
private fun PreviewBadge(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = BADGE_ALPHA), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = BADGE_TEXT_ALPHA),
        )
    }
}
