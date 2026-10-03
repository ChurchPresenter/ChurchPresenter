package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.songchords.SongSectionWordGroup
import org.churchpresenter.songchords.SongSectionWords
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.semantic

/** How a section reads in the preview — the colour tells verses from choruses at a glance. */
enum class SongSectionKind { VERSE, CHORUS, BRIDGE, TAG }

/**
 * Which kind of section a header names.
 *
 * Matched on the section words the song format itself uses, in every language at once — see
 * [SongSectionWords], which the wrapping and importing sides read too, so a song written with
 * Polish or Russian markers colours like the English one it would import to.
 *
 * Only the four kinds that have an ink of their own are distinguished. Everything else — an intro,
 * an instrumental, a pre-chorus, an unrecognised name — reads as a verse, which is what an
 * unlabelled block is anyway.
 */
fun sectionKindOf(label: String): SongSectionKind = when (SongSectionWords.groupOf(label)) {
    SongSectionWordGroup.CHORUS -> SongSectionKind.CHORUS
    SongSectionWordGroup.BRIDGE -> SongSectionKind.BRIDGE
    SongSectionWordGroup.TAG -> SongSectionKind.TAG
    else -> SongSectionKind.VERSE
}

/**
 * Ink for each section kind.
 *
 * A legend — verse, chorus, bridge, tag — so these do not follow the theme *accent*; a legend whose
 * colours move with the accent stops being one. They do follow light and dark, which is why they are
 * theme tokens rather than literals here: the pair that holds its contrast on a light ground is not
 * the pair that holds it on a dark one, and the theme is the one place that knows which is in force.
 */
object SectionInk {
    @Composable
    fun of(kind: SongSectionKind): Color = with(MaterialTheme.semantic) {
        when (kind) {
            SongSectionKind.VERSE -> chordVerse
            SongSectionKind.CHORUS -> chordChorus
            SongSectionKind.BRIDGE -> chordBridge
            SongSectionKind.TAG -> chordTag
        }
    }
}

/**
 * A section's name as a coloured chip with a rule running off it — verse amber, chorus purple,
 * bridge green, tag red.
 *
 * Shared by the song editor's preview and the Songs tab's list so a section is recognised the same
 * way in both; [label] is the bare name, with any `[]`/`{}` already off it.
 */
@Composable
fun SectionLabelRow(
    label: String,
    modifier: Modifier = Modifier,
    slideIndex: Int = 0,
    slideCount: Int = 1,
) {
    val ink = SectionInk.of(sectionKindOf(label))
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ZoneLabel(
            text = label,
            color = ink,
            modifier = Modifier
                .background(ink.copy(alpha = 0.16f), AppShape(6.dp))
                .padding(horizontal = 9.dp, vertical = 3.dp),
        )
        // Which slide of the section this is, shown only when there is more than one — otherwise
        // every unsplit verse in the library would carry a "1/1" that tells nobody anything. Digits
        // and a slash, so there is nothing here to translate.
        if (slideCount > 1) {
            ZoneLabel(text = "${slideIndex + 1}/$slideCount")
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun ZoneLabel(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    Text(
        text = text.uppercase(),
        fontSize = 9.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.0.sp,
        color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
