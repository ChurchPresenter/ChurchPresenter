package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.song_preview_full_screen
import churchpresenter.composeapp.generated.resources.song_preview_lower_third
import churchpresenter.composeapp.generated.resources.song_preview_sample_title
import org.churchpresenter.app.churchpresenter.presenter.SongPresenter
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SectionTranslation
import org.churchpresenter.core.models.songs.withSecondaryLines
import org.churchpresenter.settings.AppSettings
import org.jetbrains.compose.resources.stringResource

/** The song number the sample slide carries, chosen to be three digits like a real songbook. */
private const val SAMPLE_SONG_NUMBER = 427

/**
 * What the configured styling puts on screen -- drawn by [SongPresenter] itself.
 *
 * This composes the **real presenter** at the output's own size and scales the result down, exactly
 * as the live preview panel beside the tabs does, rather than reproducing its layout. Every rule the
 * presenter applies -- the auto-fit, the bilingual split, the look-ahead band, the end-of-song
 * marker, the margins -- therefore lands here without being written twice.
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
    /** Likewise the chord chart, which the output decides per screen and the styling has to fit. */
    showChords: Boolean,
    /** The two sections the preview draws -- see [songSampleSections]. */
    sections: List<LyricSection>,
    modifier: Modifier = Modifier,
) {
    val output = previewOutputSize(settings)
    val song = settings.songSettings
    val vertical = settings.projectionSettings.screenAssignments.any { it.isLowerThirdVertical }

    Box(
        modifier = modifier
            .aspectRatio(output.aspectRatio)
            .clipToBounds()
            .background(Color(PREVIEW_BACKGROUND), RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
    ) {
        ScaledPresenterBox(output) {
            SongPresenter(
                lyricSection = sections.first(),
                appSettings = settings,
                isLowerThird = target.isLowerThird,
                isLowerThirdVertical = target.isLowerThird && vertical,
                lookAheadEnabled = showLookAhead,
                allLyricSections = sections,
                displaySectionIndex = 0,
                showBackground = false,
                showChords = showChords,
                // The one thing every other caller of SongPresenter passes and this did not. The
                // output's own song mode overrides the song-level language setting wherever it is
                // set -- and it always is -- so without this the preview showed a language the
                // screen would not.
                languageOverride = settings.songLanguageFor(target),
            )
        }
        MarginGuide(
            output = output,
            settings = settings,
            margins = PreviewMargins(
                left = song.marginLeft,
                right = song.marginRight,
                top = song.marginTop,
                bottom = song.marginBottom,
            ),
            bandPercent = song.lowerThirdHeightPercent,
            lowerThird = target.isLowerThird,
        )
        PreviewBadge(
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
 * A verse and the chorus behind it, bilingual and with chords, at [slot]'s length.
 *
 * Two sections rather than one at every length, because the look-ahead line and the next-section
 * marker have nothing to show without something to look ahead *to* -- with a single section those
 * two element tabs would preview a blank. Both languages at every length too, so the bilingual
 * layout controls keep showing what they do.
 *
 * The three lengths are what makes auto-fit visible: a one-line slide and a six-line one are the
 * two ends the same font size has to serve, and a preview that only ever draws the middle case
 * answers neither question.
 *
 * `internal` because the on-screen preview pushes exactly these sections at the real outputs -- one
 * definition of "the sample", so the two pictures cannot drift apart.
 */
@Composable
internal fun songSampleSections(slot: PreviewSampleSlot): List<LyricSection> {
    val title = stringResource(Res.string.song_preview_sample_title)
    val verse = LyricSection(
        title = title,
        translations = listOf(SectionTranslation(title = title)),
        songNumber = SAMPLE_SONG_NUMBER,
        type = "verse",
        labelName = "Verse 1",
    )
    val chorus = verse.copy(type = "chorus", labelName = "Chorus", isLastSection = true)
    return when (slot) {
        PreviewSampleSlot.SHORT -> shortSample(verse, chorus)
        PreviewSampleSlot.MEDIUM -> mediumSample(verse, chorus)
        PreviewSampleSlot.LONG -> longSample(verse, chorus)
    }
}

/**
 * One sample slide: the primary's lines, the second language's, and the chart the band would read.
 *
 * A helper rather than three `copy` calls because the second language is no longer a field on
 * [LyricSection] -- it is one entry of its translation list, and filling it by hand at six call
 * sites would say six times what this says once.
 *
 * The samples stay bilingual even though a song may now carry four languages: they exist to show
 * what the styling controls do, and a third and fourth column of the same hymn would shrink the
 * type without demonstrating anything the second does not.
 */
private fun LyricSection.sample(
    lines: List<String>,
    secondary: List<String>,
    chords: List<String>,
): LyricSection = copy(lines = lines, chordLines = chords).withSecondaryLines(secondary)

/**
 * One short line a side.
 *
 * Short in **both** senses, and it has to be: with the display mode set to line -- which is the
 * lower third's default -- only the first line of a section is ever drawn, so three samples that
 * differed only in how many lines they had were byte-identical on that output and the selector
 * appeared to do nothing at all. Each slot therefore changes the length of the first line as well
 * as the number of them.
 */
private fun shortSample(verse: LyricSection, chorus: LyricSection) = listOf(
    verse.sample(
        lines = listOf("How sweet the sound"),
        secondary = listOf("О, благодать!"),
        chords = listOf("[C]How sweet the [G]sound"),
    ),
    chorus.sample(
        lines = listOf("That saved a wretch"),
        secondary = listOf("Спасён я Богом"),
        chords = listOf("[Em]That saved a [D]wretch"),
    ),
)

/** Two lines a side, of ordinary length -- a normal slide, and what the preview opens on. */
private fun mediumSample(verse: LyricSection, chorus: LyricSection) = listOf(
    verse.sample(
        lines = listOf(
            "Amazing grace! How sweet the sound",
            "That saved a wretch like me",
        ),
        secondary = listOf(
            "О, благодать! Спасён я",
            "Тобой из бездны зла",
        ),
        chords = listOf(
            "[G]Amazing [G7]grace! How [C]sweet the [G]sound",
            "[G]That saved a [Em]wretch [D]like [G]me",
        ),
    ),
    chorus.sample(
        lines = listOf(
            "Twas grace that taught my heart to fear",
            "And grace my fears relieved",
        ),
        secondary = listOf(
            "И благодать научит нас",
            "Страх сердца побеждать",
        ),
        chords = listOf(
            "[C]Twas grace that [G]taught my heart to [D]fear",
            "[G]And grace my [D]fears re[G]lieved",
        ),
    ),
)

/**
 * Six long lines a side: what auto-fit has to shrink, and what finds a margin that is too generous.
 *
 * The first line is the longest of the three slots as well, so this slot still says something on a
 * line-mode output where the other five lines are never drawn.
 */
private fun longSample(verse: LyricSection, chorus: LyricSection) = listOf(
    verse.sample(
        lines = listOf(
            "Through many dangers, toils and snares I have already come",
            "Tis grace hath brought me safe thus far, and grace will lead me home",
            "Amazing grace! How sweet the sound that saved a wretch like me",
            "I once was lost, but now am found, was blind but now I see",
            "Twas grace that taught my heart to fear, and grace my fears relieved",
            "How precious did that grace appear the hour I first believed",
        ),
        secondary = listOf(
            "Сквозь много бед, трудов и сетей я уже прошёл",
            "Меня хранила благодать, она домой введёт",
            "О, благодать! Спасён я был Тобой из бездны зла",
            "Был мёртв и чудом ожил я, был слеп и вижу свет",
            "И благодать научит нас страх сердца побеждать",
            "Как драгоценна благодать в тот час, когда я верил",
        ),
        chords = listOf(
            "[G]Through many [G7]dangers, [C]toils and snares I have al[G]ready come",
            "[G]Tis grace hath [Em]brought me safe thus far, and [D]grace will lead me [G]home",
            "[G]Amazing [G7]grace! How [C]sweet the sound that saved a [G]wretch like me",
            "[G]I once was [Em]lost, but now am found, was [D]blind but now I [G]see",
            "[C]Twas grace that [G]taught my heart to fear, and [D]grace my fears re[G]lieved",
            "[G]How precious [G7]did that [C]grace appear the hour I [G]first believed",
        ),
    ),
    chorus.sample(
        lines = listOf(
            "Tis grace hath brought me safe thus far, and grace will lead me home",
            "Through many dangers, toils and snares I have already come",
            "When we've been there ten thousand years, bright shining as the sun",
            "We've no less days to sing God's praise than when we first begun",
            "My chains are gone, I've been set free, my God my Saviour has ransomed me",
            "And like a flood His mercy reigns, unending love, amazing grace",
        ),
        secondary = listOf(
            "Меня хранила благодать, она домой введёт",
            "Сквозь много бед, трудов и сетей я уже прошёл",
            "Когда пройдёт десять тысяч лет, сияющих как солнце",
            "У нас не меньше дней хвалить, чем в самый первый день",
            "Оковы пали, я свободен, мой Бог, Спаситель, искупил",
            "И как поток течёт Его любовь, о, благодать!",
        ),
        chords = listOf(
            "[G]Tis grace hath [Em]brought me safe thus far, and [D]grace will lead me [G]home",
            "[G]Through many [G7]dangers, [C]toils and snares I have al[G]ready come",
            "[G]When we've been [G7]there ten [C]thousand years, bright shining as the [G]sun",
            "[G]We've no less [Em]days to sing God's praise than [D]when we first be[G]gun",
            "[C]My chains are [G]gone, I've been set free, my [D]God my Saviour has ransomed [G]me",
            "[G]And like a [G7]flood His [C]mercy reigns, unending love, a[G]mazing grace",
        ),
    ),
)
