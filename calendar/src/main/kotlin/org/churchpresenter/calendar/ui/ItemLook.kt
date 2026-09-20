package org.churchpresenter.calendar.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.theme.semantic

/**
 * How a run-of-show row is drawn: the icon on the left and the color behind it.
 *
 * The colors come from `:theme`'s semantic content roles rather than from constants of this
 * module's own, so a song is the same color in the planner as it is everywhere else in the app and
 * follows all nine themes. The mockup this window was drawn from used its own hexes; those would
 * have been a tenth palette that drifts the first time anyone touches the real one.
 *
 * Icons are real vector assets, never text or emoji — `AGENT.md` is explicit about that, and the
 * mockup's `♪` / `✝` / `▣` glyphs are exactly what it forbids.
 */
data class ItemLook(val icon: ImageVector, val color: Color)

@Composable
@ReadOnlyComposable
fun lookFor(item: ScheduleItem): ItemLook {
    val semantic = MaterialTheme.semantic
    val scheme = MaterialTheme.colorScheme
    return when (item) {
        is ScheduleItem.SongItem -> ItemLook(Icons.Filled.MusicNote, semantic.contentSongs)
        is ScheduleItem.BibleVerseItem -> ItemLook(Icons.AutoMirrored.Filled.MenuBook, semantic.contentBible)
        is ScheduleItem.PictureItem -> ItemLook(Icons.Filled.Image, semantic.contentPictures)
        is ScheduleItem.PresentationItem -> ItemLook(Icons.Filled.Slideshow, semantic.contentPresentation)
        is ScheduleItem.MediaItem -> ItemLook(Icons.Filled.PlayCircle, semantic.contentMedia)
        is ScheduleItem.LowerThirdItem -> ItemLook(Icons.Filled.VerticalAlignBottom, semantic.contentLowerThird)
        is ScheduleItem.AnnouncementItem -> ItemLook(Icons.Filled.Campaign, semantic.info)
        is ScheduleItem.WebsiteItem -> ItemLook(Icons.Filled.Language, semantic.info)
        is ScheduleItem.SceneItem -> ItemLook(Icons.Filled.Layers, semantic.info)
        is ScheduleItem.DictionaryItem -> ItemLook(Icons.Filled.Translate, semantic.greek)
        // Off screen: a person at the front, in the outline role the section heading uses -- it
        // is on the plan but never on the outputs, so no content color fits it.
        is ScheduleItem.MinistryItem -> ItemLook(Icons.Filled.Person, scheme.outline)
        // A label is a section heading here, which is the one row that is structure rather than
        // content — so it takes the outline role and not a content color.
        is ScheduleItem.LabelItem -> ItemLook(Icons.AutoMirrored.Filled.Label, scheme.outline)
        // A cue is automation: the bolt, in the color the arm switch and the fired marks use.
        is ScheduleItem.CueItem -> ItemLook(Icons.Filled.Bolt, scheme.tertiary)
    }
}

/** Whether a row is a section heading rather than something that goes on screen. */
fun ScheduleItem.isSection(): Boolean = this is ScheduleItem.LabelItem

/** The second line under a row's title — what distinguishes two rows with the same name. */
fun ScheduleItem.subtitle(): String = when (this) {
    is ScheduleItem.SongItem -> songbook
    is ScheduleItem.BibleVerseItem -> verseText.take(SUBTITLE_CHARS)
    is ScheduleItem.PictureItem -> folderName
    is ScheduleItem.PresentationItem -> fileName
    is ScheduleItem.MediaItem -> mediaTitle
    is ScheduleItem.LowerThirdItem -> presetLabel
    is ScheduleItem.AnnouncementItem -> text.take(SUBTITLE_CHARS)
    is ScheduleItem.WebsiteItem -> url
    is ScheduleItem.SceneItem -> sceneName
    is ScheduleItem.DictionaryItem -> transliteration
    is ScheduleItem.MinistryItem -> detail
    is ScheduleItem.LabelItem -> ""
    is ScheduleItem.CueItem -> payload?.displayText ?: action
}

private const val SUBTITLE_CHARS = 60
