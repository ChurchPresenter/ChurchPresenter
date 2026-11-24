package org.churchpresenter.songs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.chip.Tag
import org.churchpresenter.hosanna.composables.list.ListDivider
import org.churchpresenter.hosanna.composables.model.ChipColor
import org.churchpresenter.sharedui.composables.SongSectionKind
import org.churchpresenter.sharedui.composables.sectionKindOf

/**
 * A section's name as a tag in its section's color, which slide of it this is when it is split,
 * and a rule running off to the edge.
 */
@Composable
internal fun SectionTagRow(
    label: String,
    modifier: Modifier = Modifier,
    slideIndex: Int = 0,
    slideCount: Int = 1,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Tag(label, color = sectionChipColor(sectionKindOf(label)))
        // Digits and a slash only, so nothing here to translate; a lone slide shows no "1/1".
        if (slideCount > 1) Tag("${slideIndex + 1}/$slideCount")
        ListDivider(Modifier.weight(1f))
    }
}

/** The chip color for a section kind. */
internal fun sectionChipColor(kind: SongSectionKind): ChipColor = when (kind) {
    SongSectionKind.VERSE -> ChipColor.Verse
    SongSectionKind.CHORUS -> ChipColor.Chorus
    SongSectionKind.BRIDGE -> ChipColor.Bridge
    SongSectionKind.TAG -> ChipColor.OtherSection
}
