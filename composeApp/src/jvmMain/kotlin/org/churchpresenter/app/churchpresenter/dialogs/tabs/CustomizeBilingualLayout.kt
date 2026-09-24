package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bilingual_grid_1x3
import churchpresenter.composeapp.generated.resources.bilingual_grid_1x4
import churchpresenter.composeapp.generated.resources.bilingual_grid_2x2
import churchpresenter.composeapp.generated.resources.bilingual_grid_3x1
import churchpresenter.composeapp.generated.resources.bilingual_grid_4x1
import churchpresenter.composeapp.generated.resources.bilingual_left_right
import churchpresenter.composeapp.generated.resources.bilingual_top_bottom
import churchpresenter.composeapp.generated.resources.customize_bilingual
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

/**
 * How two-to-four parallel translations are arranged against each other.
 *
 * One control shared by [BibleCustomizePane] and [SongCustomizePane], so the Bible's arrangement
 * and a song's are picked from one list in one shape. It sits in those panes rather than in the
 * strip under the preview, where it first landed: the strip holds what *frames* the picture --
 * margins, fades, the band's height -- while this is a property of the block itself, and it belongs
 * beside the choice of which languages are shown rather than a column away from it.
 *
 * All seven options, not the two the strip offered when this pane was first built: `bilingualGrid()`
 * resolves the five grid values and both presenters honour them, so a stack of three or four
 * translations had no way to ask for the shape it needed. It was `BilingualLayoutButtons` in the old
 * Song tab's rail.
 */
@Composable
internal fun BilingualLayoutRow(selected: String, onSelect: (String) -> Unit) {
    CustomizeRow(stringResource(Res.string.customize_bilingual)) {
        ChoiceControl(
            options = bilingualLayoutOptions(),
            selected = selected,
            maxPerRow = BILINGUAL_LAYOUTS_PER_ROW,
            onSelect = onSelect,
        )
    }
}

@Composable
private fun bilingualLayoutOptions(): List<Pair<String, String>> = listOf(
    Constants.BILINGUAL_SIDE_BY_SIDE to stringResource(Res.string.bilingual_left_right),
    Constants.BILINGUAL_TOP_BOTTOM to stringResource(Res.string.bilingual_top_bottom),
    Constants.BILINGUAL_GRID_1X3 to stringResource(Res.string.bilingual_grid_1x3),
    Constants.BILINGUAL_GRID_3X1 to stringResource(Res.string.bilingual_grid_3x1),
    Constants.BILINGUAL_GRID_1X4 to stringResource(Res.string.bilingual_grid_1x4),
    Constants.BILINGUAL_GRID_4X1 to stringResource(Res.string.bilingual_grid_4x1),
    Constants.BILINGUAL_GRID_2X2 to stringResource(Res.string.bilingual_grid_2x2),
)

/** Seven segments do not fit the pane's width in one row; four and three reads evenly. */
private const val BILINGUAL_LAYOUTS_PER_ROW = 4
