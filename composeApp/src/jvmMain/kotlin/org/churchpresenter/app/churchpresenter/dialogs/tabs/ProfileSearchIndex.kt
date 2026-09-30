package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.background_above_band_caption
import churchpresenter.composeapp.generated.resources.bible_letter_spacing
import churchpresenter.composeapp.generated.resources.bible_word_spacing
import churchpresenter.composeapp.generated.resources.content_announcements
import churchpresenter.composeapp.generated.resources.content_media
import churchpresenter.composeapp.generated.resources.content_pictures
import churchpresenter.composeapp.generated.resources.content_streaming
import churchpresenter.composeapp.generated.resources.customize_background_opacity
import churchpresenter.composeapp.generated.resources.customize_background_type
import churchpresenter.composeapp.generated.resources.customize_bible
import churchpresenter.composeapp.generated.resources.customize_show_abbreviation
import churchpresenter.composeapp.generated.resources.customize_songs
import churchpresenter.composeapp.generated.resources.identify_screen
import churchpresenter.composeapp.generated.resources.media_subtitles
import churchpresenter.composeapp.generated.resources.output_profile_delete
import churchpresenter.composeapp.generated.resources.output_profile_duplicate
import churchpresenter.composeapp.generated.resources.output_profile_scale
import churchpresenter.composeapp.generated.resources.profile_band_height
import churchpresenter.composeapp.generated.resources.profile_bg_row
import churchpresenter.composeapp.generated.resources.profile_content_align
import churchpresenter.composeapp.generated.resources.profile_content_width
import churchpresenter.composeapp.generated.resources.profile_context_open_general
import churchpresenter.composeapp.generated.resources.profile_context_standalone
import churchpresenter.composeapp.generated.resources.profile_crossfade
import churchpresenter.composeapp.generated.resources.profile_display_mode
import churchpresenter.composeapp.generated.resources.profile_duration
import churchpresenter.composeapp.generated.resources.profile_end_marker
import churchpresenter.composeapp.generated.resources.profile_fade_in
import churchpresenter.composeapp.generated.resources.profile_fade_out
import churchpresenter.composeapp.generated.resources.profile_group_look
import churchpresenter.composeapp.generated.resources.profile_group_placement
import churchpresenter.composeapp.generated.resources.profile_layout
import churchpresenter.composeapp.generated.resources.profile_margins
import churchpresenter.composeapp.generated.resources.profile_name
import churchpresenter.composeapp.generated.resources.profile_nav_live_captions
import churchpresenter.composeapp.generated.resources.profile_nav_outputs
import churchpresenter.composeapp.generated.resources.profile_place_freely
import churchpresenter.composeapp.generated.resources.profile_reference
import churchpresenter.composeapp.generated.resources.profile_repeat_chorus
import churchpresenter.composeapp.generated.resources.profile_section_label
import churchpresenter.composeapp.generated.resources.setup_summary_bible
import churchpresenter.composeapp.generated.resources.profile_source_songs
import churchpresenter.composeapp.generated.resources.profile_space_between_translations
import churchpresenter.composeapp.generated.resources.profile_split_long_verses
import churchpresenter.composeapp.generated.resources.profile_text_alignment
import churchpresenter.composeapp.generated.resources.profile_text_autofit
import churchpresenter.composeapp.generated.resources.profile_text_color
import churchpresenter.composeapp.generated.resources.profile_text_font
import churchpresenter.composeapp.generated.resources.profile_text_highlight
import churchpresenter.composeapp.generated.resources.profile_text_letter_case
import churchpresenter.composeapp.generated.resources.profile_text_outline
import churchpresenter.composeapp.generated.resources.profile_text_shadow
import churchpresenter.composeapp.generated.resources.profile_text_size
import churchpresenter.composeapp.generated.resources.profile_text_style
import churchpresenter.composeapp.generated.resources.profile_title_slide
import churchpresenter.composeapp.generated.resources.profile_translation_divider
import churchpresenter.composeapp.generated.resources.profile_vertical_alignment
import churchpresenter.composeapp.generated.resources.profile_word_wrap
import churchpresenter.composeapp.generated.resources.profile_x_offset
import churchpresenter.composeapp.generated.resources.profile_y_offset
import churchpresenter.composeapp.generated.resources.projection_content_web
import churchpresenter.composeapp.generated.resources.song_background_blur
import churchpresenter.composeapp.generated.resources.song_background_dim
import churchpresenter.composeapp.generated.resources.tab_canvas
import churchpresenter.composeapp.generated.resources.tab_dictionary
import churchpresenter.composeapp.generated.resources.tab_qa
import org.churchpresenter.settings.OutputProfile
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The rows every text page has. */
private val TEXT_ROWS = listOf(
    Res.string.profile_text_font, Res.string.profile_text_size, Res.string.profile_text_color,
    Res.string.profile_text_style, Res.string.profile_text_alignment, Res.string.profile_text_letter_case,
    Res.string.bible_letter_spacing, Res.string.bible_word_spacing, Res.string.profile_text_outline,
    Res.string.profile_text_highlight, Res.string.profile_text_shadow,
)

/** The rows the Bible and Songs pages share beyond their text. */
private val PLACE_ROWS = listOf(
    Res.string.profile_bg_row, Res.string.profile_vertical_alignment, Res.string.profile_margins,
    Res.string.profile_content_width, Res.string.profile_content_align, Res.string.profile_x_offset,
    Res.string.profile_y_offset, Res.string.profile_place_freely, Res.string.profile_band_height,
    Res.string.profile_fade_in, Res.string.profile_fade_out,
    Res.string.profile_duration, Res.string.profile_crossfade, Res.string.profile_layout,
)

/**
 * The words each page's settings go by, for the section search.
 *
 * The rows themselves filter on what they are called, so a page is listed while the search holds
 * text only if one of its rows would still show -- which needs the names without composing every
 * page to find them. A row added to a page belongs in its list here too.
 */
private fun ProfilePage.searchTerms(): List<StringResource> = when (this) {
    ProfilePage.General -> listOf(
        Res.string.profile_name, Res.string.profile_display_mode,
        Res.string.output_profile_duplicate, Res.string.output_profile_delete,
    )
    ProfilePage.Outputs -> listOf(Res.string.profile_nav_outputs, Res.string.identify_screen)
    ProfilePage.Content -> listOf(
        Res.string.customize_bible, Res.string.customize_songs, Res.string.tab_dictionary,
        Res.string.setup_summary_bible, Res.string.profile_source_songs, Res.string.content_pictures,
        Res.string.content_media, Res.string.media_subtitles, Res.string.projection_content_web,
        Res.string.tab_canvas, Res.string.content_streaming, Res.string.content_announcements,
        Res.string.tab_qa, Res.string.profile_nav_live_captions, Res.string.output_profile_scale,
        Res.string.profile_group_placement,
    )
    is ProfilePage.Appearance -> when (pane) {
        CustomizePane.BIBLE -> TEXT_ROWS + PLACE_ROWS + listOf(
            Res.string.customize_show_abbreviation, Res.string.profile_reference,
            Res.string.profile_translation_divider, Res.string.profile_space_between_translations,
            Res.string.profile_split_long_verses,
        )
        CustomizePane.SONGS -> TEXT_ROWS + PLACE_ROWS + listOf(
            Res.string.profile_text_autofit, Res.string.profile_title_slide, Res.string.profile_word_wrap,
            Res.string.profile_repeat_chorus, Res.string.profile_end_marker, Res.string.profile_section_label,
        )
        CustomizePane.BACKGROUND -> listOf(
            Res.string.profile_bg_row, Res.string.customize_background_type, Res.string.song_background_dim,
            Res.string.customize_background_opacity, Res.string.song_background_blur,
            Res.string.background_above_band_caption,
        )
        else -> listOf(Res.string.profile_group_look)
    }
}

/** Every page of [profile] with its searchable words, run together. */
@Composable
internal fun profileSearchIndex(profile: OutputProfile): Map<ProfilePage, String> =
    profileNavSections(profile).flatMap { it.pages }.associateWith { page ->
        (page.searchTerms().map { stringResource(it) } + page.label()).joinToString(" ")
    }

/** The context card of a profile linked to nothing. */
@Composable
internal fun StandaloneContextCard(onOpenGeneral: () -> Unit) {
    PreviewSideCard(Modifier.testTag(CONTEXT_CARD_TAG)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(Res.string.profile_context_standalone),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            LinkText(stringResource(Res.string.profile_context_open_general), onOpenGeneral)
        }
    }
}

/** Test handle for the preview column's context card. */
internal const val CONTEXT_CARD_TAG = "profile_context_card"
