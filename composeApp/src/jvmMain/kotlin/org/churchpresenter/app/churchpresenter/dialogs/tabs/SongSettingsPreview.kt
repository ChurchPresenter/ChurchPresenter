package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import churchpresenter.composeapp.generated.resources.song_preview_full_screen
import churchpresenter.composeapp.generated.resources.song_preview_lower_third
import churchpresenter.composeapp.generated.resources.song_preview_sample_title
import org.churchpresenter.app.churchpresenter.presenter.SongPresenter
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.AppSettings
import org.jetbrains.compose.resources.stringResource
import kotlin.math.min

/** The band's height is stored as a whole percentage of the output. */
private const val PERCENT = 100f

private const val PREVIEW_BACKGROUND = 0xFF08090B
private const val MARGIN_GUIDE_ALPHA = 0.5f
private const val GUIDE_STROKE_PX = 1f
private const val GUIDE_DASH_PX = 4f
private const val BADGE_ALPHA = 0.55f
private const val BADGE_TEXT_ALPHA = 0.75f

/**
 * How tall the preview is allowed to get.
 *
 * The dialog is at most 1400x900 and rather less on a 1366x768 laptop, and the controls under the
 * preview need roughly 250dp of it. Left to fill the pane's width the preview would take more than
 * the whole remainder, so it is capped here and centred in the space instead.
 */
internal val SONG_PREVIEW_MAX_HEIGHT = 260.dp

/** The song number the sample slide carries, chosen to be four digits like a real songbook. */
private const val SAMPLE_SONG_NUMBER = 427

/**
 * What the configured styling puts on screen -- drawn by [SongPresenter] itself.
 *
 * This composes the **real presenter** at the output's own logical size and scales the result down,
 * exactly as the live preview panel beside the tabs does, rather than reproducing its layout. Every
 * rule the presenter applies -- the auto-fit, the bilingual split, the look-ahead band, the
 * end-of-song marker, the margins -- therefore lands here without being written twice.
 *
 * Backgrounds are left off: the presenter would decode an image or start a video for a picture a
 * few hundred dp wide, and what is being previewed here is the type.
 */
@Composable
internal fun SongPreviewPanel(
    settings: AppSettings,
    target: SongStyleTarget,
    /** The look-ahead is shown on demand: a preview switch, not a setting. */
    showLookAhead: Boolean,
    modifier: Modifier = Modifier,
) {
    val output = previewOutputSize(settings)
    val sections = sampleSections()
    val vertical = settings.projectionSettings.screenAssignments.any { it.isLowerThirdVertical }

    Box(
        modifier = modifier
            .aspectRatio(output.aspectRatio)
            .clipToBounds()
            .background(Color(PREVIEW_BACKGROUND), RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
    ) {
        ScaledSongPresenter(output) {
            SongPresenter(
                lyricSection = sections.first(),
                appSettings = settings,
                isLowerThird = target.isLowerThird,
                isLowerThirdVertical = target.isLowerThird && vertical,
                lookAheadEnabled = showLookAhead,
                allLyricSections = sections,
                displaySectionIndex = 0,
                showBackground = false,
                // Chords belong to the stage monitor, not to what the congregation reads. The
                // presenter can be asked for them -- `ScreenAssignment.showChords` -- but neither
                // of the outputs this tab styles is where a chart goes, so the preview never
                // pretends otherwise.
                showChords = false,
                // The one thing every other caller of SongPresenter passes and this did not. The
                // output's own song mode overrides the song-level language setting wherever it is
                // set -- and it always is -- so without this the preview showed a language the
                // screen would not.
                languageOverride = settings.songLanguageFor(target),
            )
        }
        SongMarginGuide(
            output = output,
            settings = settings,
            lowerThird = target.isLowerThird,
            color = MaterialTheme.colorScheme.primary.copy(alpha = MARGIN_GUIDE_ALPHA),
        )
        SongPreviewBadge(
            label = stringResource(
                if (target.isLowerThird) {
                    Res.string.song_preview_lower_third
                } else {
                    Res.string.song_preview_full_screen
                },
            ),
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
        )
    }
}

/**
 * A verse and the chorus behind it, bilingual and with chords.
 *
 * Two sections rather than one, because the look-ahead line and the next-section marker have
 * nothing to show without something to look ahead *to* -- with a single section those two element
 * tabs would preview a blank.
 * two languages so the bilingual layout controls show what they do.
 */
@Composable
private fun sampleSections(): List<LyricSection> {
    val title = stringResource(Res.string.song_preview_sample_title)
    val verse = LyricSection(
        title = title,
        secondaryTitle = title,
        songNumber = SAMPLE_SONG_NUMBER,
        type = "verse",
        labelName = "Verse 1",
        lines = listOf(
            "Amazing grace! How sweet the sound",
            "That saved a wretch like me",
        ),
        secondaryLines = listOf(
            "О, благодать! Спасён я",
            "Тобой из бездны зла",
        ),
        chordLines = listOf(
            "[G]Amazing [G7]grace! How [C]sweet the [G]sound",
            "[G]That saved a [Em]wretch [D]like [G]me",
        ),
    )
    return listOf(
        verse,
        verse.copy(
            type = "chorus",
            labelName = "Chorus",
            lines = listOf("Twas grace that taught my heart to fear"),
            secondaryLines = listOf("И благодать научит нас"),
            chordLines = listOf("[C]Twas grace that [G]taught my heart to [D]fear"),
            isLastSection = true,
        ),
    )
}

/**
 * Lays [content] out at [output]'s own size and scales the drawn result into the space available.
 *
 * Density is pinned to 1 so a dp is an output pixel: [SongPresenter] derives its own scale factor
 * from `maxWidth.toPx()`, so without this it would see the *dialog's* density and size everything
 * against that instead of against the output. Scaling happens in a graphics layer afterwards, so
 * every proportion the presenter chose survives untouched.
 */
@Composable
private fun ScaledSongPresenter(output: PreviewOutputSize, content: @Composable () -> Unit) {
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
 * Drawn outside the scaled layer: inside it a 1dp line is scaled down with everything else and
 * comes out a quarter of a pixel wide. Expressed as fractions of the output instead, which the box
 * matches exactly because it carries the output's own aspect ratio.
 */
@Composable
private fun SongMarginGuide(
    output: PreviewOutputSize,
    settings: AppSettings,
    lowerThird: Boolean,
    color: Color,
) {
    val projection = settings.projectionSettings
    val song = settings.songSettings
    val leftFraction = (projection.windowLeft + song.marginLeft).toFloat() / output.width
    val rightFraction = (projection.windowRight + song.marginRight).toFloat() / output.width
    val topFraction = (projection.windowTop + song.marginTop).toFloat() / output.height
    val bottomFraction = (projection.windowBottom + song.marginBottom).toFloat() / output.height
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
private fun SongPreviewBadge(label: String, modifier: Modifier = Modifier) {
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
