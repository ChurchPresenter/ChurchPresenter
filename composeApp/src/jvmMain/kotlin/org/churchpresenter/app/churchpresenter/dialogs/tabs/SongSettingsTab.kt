package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bottom
import churchpresenter.composeapp.generated.resources.number_before_title
import churchpresenter.composeapp.generated.resources.show_title
import churchpresenter.composeapp.generated.resources.show_number
import churchpresenter.composeapp.generated.resources.every_page
import churchpresenter.composeapp.generated.resources.first_page
import churchpresenter.composeapp.generated.resources.none
import churchpresenter.composeapp.generated.resources.show_song_number_before_title
import churchpresenter.composeapp.generated.resources.song_chunk
import churchpresenter.composeapp.generated.resources.song_chunk_line
import churchpresenter.composeapp.generated.resources.song_chunk_verse
import churchpresenter.composeapp.generated.resources.song_show_on_title_slide
import churchpresenter.composeapp.generated.resources.song_language_scope
import churchpresenter.composeapp.generated.resources.song_number_corner
import churchpresenter.composeapp.generated.resources.song_number_offset_needs_corner
import churchpresenter.composeapp.generated.resources.song_number_offset_x
import churchpresenter.composeapp.generated.resources.song_number_offset_y
import org.churchpresenter.theme.components.DropdownSelector
import org.churchpresenter.app.churchpresenter.composables.LabeledCheckbox
import org.churchpresenter.app.churchpresenter.composables.LabeledControl
import org.churchpresenter.app.churchpresenter.composables.SegmentedButton
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonItem
import org.churchpresenter.app.churchpresenter.composables.SliderNumberField
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongNumberOffset
import org.churchpresenter.settings.SongTitleSlideNumber
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

private val SCOPE_BUTTON_WIDTH = 82.dp

/** Wide enough for "Bottom Right" and the chevron, so no corner reads ellipsized. */
private val CORNER_DROPDOWN_WIDTH = 118.dp
private val NUMBER_OFFSET_FIELD_WIDTH = 56.dp

/**
 * The Song tab of the settings dialog was retired once every control it held moved to the Profiles
 * tab -- a song slide's number/title/lyrics/look-ahead/next-section appearance is entirely styling,
 * and [org.churchpresenter.settings.SongSettings] has no content-only field of the kind
 * [org.churchpresenter.settings.BibleSettings]'s translation stack is for the Bible tab. What
 * remains in this file is what the Profiles tab's own Song pane draws: [SongElementOptions] and
 * its helpers, kept here because that is where they have always lived. The chip strip that used to
 * sit above them went with the tab -- the pane has one of its own.
 */

/**
 * Everything under the element chips that belongs to the selected element -- what a slide holds,
 * which languages it shows, when the number and the title appear and where the number sits.
 *
 * Split out so the Profiles tab's Song pane can draw these controls under its own chip strip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SongElementOptions(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    element: SongStyleElement,
    target: SongStyleTarget,
    language: SongStyleLanguage = SongStyleLanguage.PRIMARY,
    titleSlideView: Boolean = false,
    /** See [SongLanguageScopeButtons]: set by the per-output dialog, absent on the global tab. */
    outputMode: String? = null,
    onOutputModeChange: ((String) -> Unit)? = null,
) {
    val song = settings.songSettings
    if (titleSlideView) {
        SongTitleSlideOptions(settings, onSettingsChange, element, target, outputMode, onOutputModeChange)
        return
    }
    // The chunk and the language scope belong to the output rather than to a language, and the
    // first language's panel already carries them -- a second copy here would be the same control
    // twice. So would the show/position row, which is the number's and the title's alone.
    if (language.isTranslation) return
    // How much of the song a slide holds, and which languages it shows. Both belong to the output
    // rather than to an element, so they sit under the tabs rather than in the grid -- and on a row
    // of their own, because five element tabs plus both of these is wider than the pane and left
    // them crushed to a column of single letters.
    //
    // Flowing rather than a hard row: the two labelled groups are wider than a narrow pane, and a
    // `Row` clips rather than wraps, so the last option lost its right-hand half ("Secondary" drawn
    // as "Seco") with nothing to say the control continued past the edge. Each label is wrapped
    // with its own control so the pair moves as one -- flowing them separately puts a lone "Lang"
    // at the end of the first line and its buttons at the start of the next.
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LabeledControl(stringResource(Res.string.song_chunk)) {
        SegmentedButton(
            items = listOf(
                SegmentedButtonItem(Constants.SONG_DISPLAY_MODE_VERSE, stringResource(Res.string.song_chunk_verse)),
                SegmentedButtonItem(Constants.SONG_DISPLAY_MODE_LINE, stringResource(Res.string.song_chunk_line)),
            ),
            selectedValue = song.chunkFor(element, target),
            onValueChange = { mode ->
                onSettingsChange { s -> s.copy(songSettings = s.songSettings.withChunk(element, target, mode)) }
            },
            buttonWidth = SCOPE_BUTTON_WIDTH,
            buttonHeight = 30.dp,
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
        )
        }
        LabeledControl(stringResource(Res.string.song_language_scope)) {
            SongLanguageScopeButtons(settings, onSettingsChange, target, outputMode, onOutputModeChange)
        }
    }
    SongAppearanceRow(settings, onSettingsChange, element, target)
    if (element == SongStyleElement.NUMBER) {
        SongNumberOffsetControls(settings, onSettingsChange, target)
    }
}

/** What the title slide draws, for the element selected -- its own view of [SongElementOptions]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SongTitleSlideOptions(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    element: SongStyleElement,
    target: SongStyleTarget,
    outputMode: String? = null,
    onOutputModeChange: ((String) -> Unit)? = null,
) {
    val song = settings.songSettings
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LabeledCheckbox(
                checked = song.shownOnTitleSlide(element) == true,
                onCheckedChange = { on ->
                    onSettingsChange { s -> s.copy(songSettings = s.songSettings.withShownOnTitleSlide(element, on)) }
                },
                label = stringResource(Res.string.song_show_on_title_slide),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("song_show_on_title_slide"),
            )
            // The number only: on the title's row ahead of it, or on a row of its own above.
            if (element == SongStyleElement.TITLE_SLIDE_NUMBER) {
                val cornered = song.layoutExtras.titleSlideNumber.cornerFor(target.isLowerThird) != Constants.NONE
                LabeledCheckbox(
                    checked = song.titleSlideNumberBeforeTitle,
                    onCheckedChange = { on ->
                        onSettingsChange { s ->
                            s.copy(songSettings = s.songSettings.copy(titleSlideNumberBeforeTitle = on))
                        }
                    },
                    // Nothing to lead when the number is not in the flow at all.
                    enabled = song.titleSlideShowSongNumber && !cornered,
                    label = stringResource(Res.string.show_song_number_before_title),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("song_titleSlideNumberBeforeTitle"),
                )
            }
            // The title only: the output's language, the same setting the lyric slides read,
            // decides which of the song's titles the slide opens with -- both, or one of them.
            // Nothing else on the slide has a translation.
            if (element == SongStyleElement.TITLE) {
                LabeledControl(stringResource(Res.string.song_language_scope)) {
                    SongLanguageScopeButtons(settings, onSettingsChange, target, outputMode, onOutputModeChange)
                }
            }
        }
    // Where the title slide's number goes, which is the whole point of separating it from the lyric
    // slides' one: the same corner-and-nudge pair those have, over this slide's own fields.
    if (element == SongStyleElement.TITLE_SLIDE_NUMBER) {
        TitleSlideNumberPlacement(settings, onSettingsChange, target)
    } else {
        // Every other element the slide draws -- the title and the four credits. The number is not
        // among them because it has the richer placement above; this is the plain positioning switch
        // the lyrics and the section label already carry, and the five of them had nothing at all.
        ElementOffsetStripRow(
            label = element.label(),
            offset = song.titleSlideOffset(element, target),
            tagPrefix = titleSlideOffsetTag(element),
        ) { v ->
            onSettingsChange { s ->
                s.copy(songSettings = s.songSettings.withTitleSlideOffset(element, target, v))
            }
        }
    }
}

/** The title slide number's corner and, once it has one, how far inward from it the number sits. */
@Composable
private fun TitleSlideNumberPlacement(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    target: SongStyleTarget,
) {
    val lowerThird = target.isLowerThird
    val number = settings.songSettings.layoutExtras.titleSlideNumber
    fun update(transform: (SongTitleSlideNumber) -> SongTitleSlideNumber) = onSettingsChange { s ->
        s.copy(
            songSettings = s.songSettings.copy(
                layoutExtras = s.songSettings.layoutExtras.copy(
                    titleSlideNumber = transform(s.songSettings.layoutExtras.titleSlideNumber),
                ),
            ),
        )
    }
    LabeledControl(stringResource(Res.string.song_number_corner)) {
        DropdownSelector(
            label = "",
            value = number.cornerFor(lowerThird),
            options = songNumberCornerOptions(),
            onValueChange = { v ->
                update { if (lowerThird) it.copy(lowerThirdCorner = v) else it.copy(corner = v) }
            },
            compact = true,
            modifier = Modifier.width(CORNER_DROPDOWN_WIDTH).testTag("title_slide_number_corner"),
        )
    }
    if (number.cornerFor(lowerThird) != Constants.NONE) {
        val offset = number.offsetFor(lowerThird)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ControlColumn(stringResource(Res.string.song_number_offset_x), Modifier.weight(1f)) {
                SliderNumberField(
                    value = offset.xPercent,
                    range = SongNumberOffset.PERCENT_RANGE,
                    onValueChange = { v ->
                        val next = offset.copy(xPercent = v)
                        update { if (lowerThird) it.copy(lowerThirdOffset = next) else it.copy(offset = next) }
                    },
                    fieldWidth = NUMBER_OFFSET_FIELD_WIDTH,
                    modifier = Modifier.testTag("title_slide_number_offset_x"),
                )
            }
            ControlColumn(stringResource(Res.string.song_number_offset_y), Modifier.weight(1f)) {
                SliderNumberField(
                    value = offset.yPercent,
                    range = SongNumberOffset.PERCENT_RANGE,
                    onValueChange = { v ->
                        val next = offset.copy(yPercent = v)
                        update { if (lowerThird) it.copy(lowerThirdOffset = next) else it.copy(offset = next) }
                    },
                    fieldWidth = NUMBER_OFFSET_FIELD_WIDTH,
                    modifier = Modifier.testTag("title_slide_number_offset_y"),
                )
            }
        }
    }
}

/**
 * When the number or the title appears on [target]'s output, and which of the two leads.
 *
 * Absent for the other three elements: the lyrics *are* the slide, and the look-ahead lines follow
 * whether the output has a look-ahead at all -- so there is nothing here for them to answer, and a
 * control that writes nowhere is worse than no control.
 *
 * This is the pair of settings the tab's rewrite dropped. The columns that used to hold them were
 * left in the tree unreferenced, so the song number kept appearing on the lower third with nothing
 * anywhere in settings to turn it off.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SongAppearanceRow(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    element: SongStyleElement,
    target: SongStyleTarget,
) {
    val show = settings.songSettings.showFor(element, target) ?: return
    // Flowing for the same reason as the chunk/language row above, and it matters most here: this
    // row exists only for the Number and Title elements, so those two were the only ones that ran
    // off the right edge of a narrow pane -- three options plus the ordering checkbox is the widest
    // line the card ever draws.
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LabeledControl(
            stringResource(
                if (element == SongStyleElement.NUMBER) Res.string.show_number else Res.string.show_title,
            ),
        ) {
        SegmentedButton(
            items = listOf(
                SegmentedButtonItem(Constants.NONE, stringResource(Res.string.none)),
                SegmentedButtonItem(Constants.FIRST_PAGE, stringResource(Res.string.first_page)),
                SegmentedButtonItem(Constants.EVERY_PAGE, stringResource(Res.string.every_page)),
            ),
            selectedValue = show,
            onValueChange = { value ->
                onSettingsChange { s ->
                    s.copy(songSettings = s.songSettings.withShow(element, target, value))
                }
            },
            buttonWidth = SCOPE_BUTTON_WIDTH,
            buttonHeight = 30.dp,
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
            modifier = Modifier.testTag("song_show_${element.name.lowercase()}"),
        )
        }
        // The number only. A corner takes it out of the row it shares with the title, so there is
        // no such choice to offer for the title itself.
        if (element == SongStyleElement.NUMBER) {
            val corner = settings.songSettings.numberCorner(target.isLowerThird)
            LabeledControl(stringResource(Res.string.song_number_corner)) {
                DropdownSelector(
                    label = "",
                    value = corner,
                    options = songNumberCornerOptions(),
                    onValueChange = { newCorner ->
                        onSettingsChange { s ->
                            s.copy(songSettings = s.songSettings.withNumberCorner(target.isLowerThird, newCorner))
                        }
                    },
                    compact = true,
                    modifier = Modifier.width(CORNER_DROPDOWN_WIDTH).testTag("song_number_corner"),
                )
            }
        }
        // Only where the two share a position, which is the only case in which their order is a
        // question at all -- elsewhere the slide's own layout already answers it.
        if (element == SongStyleElement.NUMBER && settings.songSettings.numberSharesTitlePosition(target)) {
            LabeledCheckbox(
                checked = settings.songSettings.songNumberBeforeTitle,
                onCheckedChange = { on ->
                    onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(songNumberBeforeTitle = on)) }
                },
                label = stringResource(Res.string.number_before_title),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("song_songNumberBeforeTitle"),
            )
        }
    }
}

/**
 * A free nudge on top of [SongAppearanceRow]'s corner, so the number can be walked into wherever a
 * background image's own box for it actually is. Meaningless with no corner chosen, and drawn as
 * its own full-width block rather than folded into that row's [FlowRow] -- a [SliderNumberField]
 * needs real width to be usable, which a row of otherwise-compact controls doesn't have to spare.
 */
@Composable
private fun SongNumberOffsetControls(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    target: SongStyleTarget,
) {
    val corner = settings.songSettings.numberCorner(target.isLowerThird)
    // The offset nudges a *cornered* number -- `songNumberCornerOffset` in `SongPresenter` is the
    // only reader -- so with no corner there is nothing for it to move. Said out loud rather than
    // simply not drawn: the schema-9 migration pins every upgraded document's corner to None, so on
    // any existing install these fields are missing with nothing on screen to explain why.
    if (corner == Constants.NONE) {
        Text(
            text = stringResource(Res.string.song_number_offset_needs_corner),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val offset = settings.songSettings.numberOffset(target.isLowerThird)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ControlColumn(stringResource(Res.string.song_number_offset_x), Modifier.weight(1f)) {
            SliderNumberField(
                value = offset.xPercent,
                range = SongNumberOffset.PERCENT_RANGE,
                onValueChange = { value ->
                    onSettingsChange { s ->
                        s.copy(
                            songSettings = s.songSettings.withNumberOffset(
                                target.isLowerThird, offset.copy(xPercent = value),
                            ),
                        )
                    }
                },
                fieldWidth = NUMBER_OFFSET_FIELD_WIDTH,
                modifier = Modifier.testTag("song_number_offset_x"),
            )
        }
        ControlColumn(stringResource(Res.string.song_number_offset_y), Modifier.weight(1f)) {
            SliderNumberField(
                value = offset.yPercent,
                range = SongNumberOffset.PERCENT_RANGE,
                onValueChange = { value ->
                    onSettingsChange { s ->
                        s.copy(
                            songSettings = s.songSettings.withNumberOffset(
                                target.isLowerThird, offset.copy(yPercent = value),
                            ),
                        )
                    }
                },
                fieldWidth = NUMBER_OFFSET_FIELD_WIDTH,
                modifier = Modifier.testTag("song_number_offset_y"),
            )
        }
    }
}

/**
 * Rounds the outer corners of a segmented row so its buttons read as one control.
 *
 * Kept here rather than moved with the tab's rewrite: the stage monitor's layout picker and the
 * song columns this tab replaced both still call it, and its test resolves it through this file.
 */
internal fun segmentedItemShape(index: Int, count: Int): Shape {
    val r = 4.dp
    return when {
        count == 1 -> RoundedCornerShape(r)
        index == 0 -> RoundedCornerShape(topStart = r, bottomStart = r, topEnd = 0.dp, bottomEnd = 0.dp)
        index == count - 1 -> RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = r, bottomEnd = r)
        else -> RoundedCornerShape(0.dp)
    }
}
