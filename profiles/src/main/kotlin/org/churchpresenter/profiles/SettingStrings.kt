package org.churchpresenter.profiles

import org.churchpresenter.settings.CAPTION_BOX_BAND
import org.churchpresenter.settings.CAPTION_BOX_CARD
import org.churchpresenter.settings.CAPTION_BREAK_SEGMENT
import org.churchpresenter.settings.CAPTION_BREAK_SENTENCE
import org.churchpresenter.settings.CAPTION_STYLE_POP_ON
import org.churchpresenter.settings.CAPTION_STYLE_ROLL_UP
import org.churchpresenter.settings.CAPTION_STYLE_RSVP
import org.churchpresenter.settings.CAPTION_STYLE_TICKER
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.TextBoxOverflow
import org.churchpresenter.settings.WebPageView
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.animation_crossfade
import org.churchpresenter.strings.generated.resources.announcements
import org.churchpresenter.strings.generated.resources.backdrop_border_color
import org.churchpresenter.strings.generated.resources.backdrop_mode_border
import org.churchpresenter.strings.generated.resources.background_opacity
import org.churchpresenter.strings.generated.resources.bible
import org.churchpresenter.strings.generated.resources.bible_letter_spacing
import org.churchpresenter.strings.generated.resources.bible_split_browse_mode
import org.churchpresenter.strings.generated.resources.bible_word_spacing
import org.churchpresenter.strings.generated.resources.bilingual_grid_1x3
import org.churchpresenter.strings.generated.resources.bilingual_grid_1x4
import org.churchpresenter.strings.generated.resources.bilingual_grid_2x2
import org.churchpresenter.strings.generated.resources.bilingual_grid_3x1
import org.churchpresenter.strings.generated.resources.bilingual_grid_4x1
import org.churchpresenter.strings.generated.resources.bottom
import org.churchpresenter.strings.generated.resources.canvas_bible_ref_color
import org.churchpresenter.strings.generated.resources.canvas_bible_ref_font_size
import org.churchpresenter.strings.generated.resources.canvas_camera_connection
import org.churchpresenter.strings.generated.resources.canvas_camera_format
import org.churchpresenter.strings.generated.resources.canvas_clock_font_size
import org.churchpresenter.strings.generated.resources.canvas_source_video
import org.churchpresenter.strings.generated.resources.canvas_text_bg_color
import org.churchpresenter.strings.generated.resources.canvas_transform
import org.churchpresenter.strings.generated.resources.center
import org.churchpresenter.strings.generated.resources.clear_layer_captions
import org.churchpresenter.strings.generated.resources.desktop_view
import org.churchpresenter.strings.generated.resources.dictionary_bible_primary
import org.churchpresenter.strings.generated.resources.instance_link_layer_messages
import org.churchpresenter.strings.generated.resources.left
import org.churchpresenter.strings.generated.resources.media_subtitles
import org.churchpresenter.strings.generated.resources.middle
import org.churchpresenter.strings.generated.resources.mobile_view
import org.churchpresenter.strings.generated.resources.pictures
import org.churchpresenter.strings.generated.resources.profile_box_area_margins
import org.churchpresenter.strings.generated.resources.profile_box_overflow_cut
import org.churchpresenter.strings.generated.resources.profile_box_overflow_shrink
import org.churchpresenter.strings.generated.resources.profile_box_overflow_spill
import org.churchpresenter.strings.generated.resources.profile_caption_bionic
import org.churchpresenter.strings.generated.resources.profile_caption_blank_line
import org.churchpresenter.strings.generated.resources.profile_caption_break_phrase
import org.churchpresenter.strings.generated.resources.profile_caption_break_sentence
import org.churchpresenter.strings.generated.resources.profile_caption_clear
import org.churchpresenter.strings.generated.resources.profile_caption_dim
import org.churchpresenter.strings.generated.resources.profile_caption_layout_interleaved
import org.churchpresenter.strings.generated.resources.profile_caption_reading_speed
import org.churchpresenter.strings.generated.resources.profile_caption_shape_band
import org.churchpresenter.strings.generated.resources.profile_caption_shape_card
import org.churchpresenter.strings.generated.resources.profile_caption_style_pop_on
import org.churchpresenter.strings.generated.resources.profile_caption_style_roll_up
import org.churchpresenter.strings.generated.resources.profile_caption_style_rsvp
import org.churchpresenter.strings.generated.resources.profile_caption_style_ticker
import org.churchpresenter.strings.generated.resources.profile_caption_ticker_speed
import org.churchpresenter.strings.generated.resources.profile_display_mode
import org.churchpresenter.strings.generated.resources.profile_fade_in
import org.churchpresenter.strings.generated.resources.profile_fade_out
import org.churchpresenter.strings.generated.resources.profile_group_boxes
import org.churchpresenter.strings.generated.resources.profile_group_translations
import org.churchpresenter.strings.generated.resources.profile_layout
import org.churchpresenter.strings.generated.resources.profile_layout_side_by_side
import org.churchpresenter.strings.generated.resources.profile_layout_stacked
import org.churchpresenter.strings.generated.resources.profile_line_spacing
import org.churchpresenter.strings.generated.resources.profile_mode_full
import org.churchpresenter.strings.generated.resources.profile_mode_lower_third
import org.churchpresenter.strings.generated.resources.profile_name
import org.churchpresenter.strings.generated.resources.profile_position_above_lyrics
import org.churchpresenter.strings.generated.resources.profile_position_below_lyrics
import org.churchpresenter.strings.generated.resources.profile_position_bottom
import org.churchpresenter.strings.generated.resources.profile_position_top
import org.churchpresenter.strings.generated.resources.profile_ref_above
import org.churchpresenter.strings.generated.resources.profile_ref_after
import org.churchpresenter.strings.generated.resources.profile_second_voice_color
import org.churchpresenter.strings.generated.resources.profile_song_position
import org.churchpresenter.strings.generated.resources.profile_source_bible
import org.churchpresenter.strings.generated.resources.profile_text_chord_color
import org.churchpresenter.strings.generated.resources.profile_text_color
import org.churchpresenter.strings.generated.resources.profile_text_shadow
import org.churchpresenter.strings.generated.resources.profile_text_style
import org.churchpresenter.strings.generated.resources.profile_third_voice_color
import org.churchpresenter.strings.generated.resources.profile_title_slide_background
import org.churchpresenter.strings.generated.resources.profile_vertical_alignment
import org.churchpresenter.strings.generated.resources.profile_web_page_view_follow
import org.churchpresenter.strings.generated.resources.profile_word_wrap
import org.churchpresenter.strings.generated.resources.props_title
import org.churchpresenter.strings.generated.resources.qa_admin_password_label
import org.churchpresenter.strings.generated.resources.qa_qr_message_label
import org.churchpresenter.strings.generated.resources.qa_voting_enabled
import org.churchpresenter.strings.generated.resources.right
import org.churchpresenter.strings.generated.resources.server_url_label
import org.churchpresenter.strings.generated.resources.song_background_blur
import org.churchpresenter.strings.generated.resources.song_background_dim
import org.churchpresenter.strings.generated.resources.song_number_corner
import org.churchpresenter.strings.generated.resources.songs
import org.churchpresenter.strings.generated.resources.stage_monitor_metronome_position
import org.churchpresenter.strings.generated.resources.stage_monitor_show_chords
import org.churchpresenter.strings.generated.resources.tab_canvas
import org.churchpresenter.strings.generated.resources.tab_dictionary
import org.churchpresenter.strings.generated.resources.tab_web
import org.churchpresenter.strings.generated.resources.text_color
import org.churchpresenter.strings.generated.resources.tooltip_bold
import org.churchpresenter.strings.generated.resources.tooltip_italic
import org.churchpresenter.strings.generated.resources.tooltip_strikethrough
import org.churchpresenter.strings.generated.resources.tooltip_underline
import org.churchpresenter.strings.generated.resources.top
import org.churchpresenter.stt.presenter.LAYOUT_INTERLEAVED
import org.jetbrains.compose.resources.StringResource

/** Fields whose name a page already shows, word for word: the card reuses that string. */
internal val SETTING_FIELD_STRINGS: Map<String, StringResource> = mapOf(
    "adminPassword" to Res.string.qa_admin_password_label,
    "announcements" to Res.string.announcements,
    "backgroundColor" to Res.string.canvas_text_bg_color,
    "backgroundOpacity" to Res.string.background_opacity,
    "bible" to Res.string.bible,
    "bibleTranslations" to Res.string.profile_source_bible,
    "bionicReading" to Res.string.profile_caption_bionic,
    "blankLineBetween" to Res.string.profile_caption_blank_line,
    "blur" to Res.string.song_background_blur,
    "bold" to Res.string.tooltip_bold,
    "border" to Res.string.backdrop_mode_border,
    "borderColor" to Res.string.backdrop_border_color,
    "canvas" to Res.string.tab_canvas,
    "captions" to Res.string.clear_layer_captions,
    "chordColor" to Res.string.profile_text_chord_color,
    "clearAfterSilence" to Res.string.profile_caption_clear,
    "color" to Res.string.profile_text_color,
    "corner" to Res.string.song_number_corner,
    "crossfade" to Res.string.animation_crossfade,
    "dictionary" to Res.string.tab_dictionary,
    "dim" to Res.string.song_background_dim,
    "dimOlderLines" to Res.string.profile_caption_dim,
    "displayMode" to Res.string.profile_display_mode,
    "fadeIn" to Res.string.profile_fade_in,
    "fadeOut" to Res.string.profile_fade_out,
    "fontSize" to Res.string.canvas_clock_font_size,
    "fullScreen" to Res.string.profile_mode_full,
    "insideMargins" to Res.string.profile_box_area_margins,
    "italic" to Res.string.tooltip_italic,
    "layout" to Res.string.profile_layout,
    "letterSpacing" to Res.string.bible_letter_spacing,
    "lineSpacing" to Res.string.profile_line_spacing,
    "lowerThird" to Res.string.profile_mode_lower_third,
    "messages" to Res.string.instance_link_layer_messages,
    "metronomePosition" to Res.string.stage_monitor_metronome_position,
    "name" to Res.string.profile_name,
    "pictures" to Res.string.pictures,
    "position" to Res.string.profile_song_position,
    "primaryBible" to Res.string.dictionary_bible_primary,
    "props" to Res.string.props_title,
    "qrCodeMessage" to Res.string.qa_qr_message_label,
    "readingSpeedLimit" to Res.string.profile_caption_reading_speed,
    "referenceColor" to Res.string.canvas_bible_ref_color,
    "referenceFontSize" to Res.string.canvas_bible_ref_font_size,
    "secondVoiceColor" to Res.string.profile_second_voice_color,
    "serverUrl" to Res.string.server_url_label,
    "shadow" to Res.string.profile_text_shadow,
    "showChords" to Res.string.stage_monitor_show_chords,
    "songs" to Res.string.songs,
    "splitBrowseMode" to Res.string.bible_split_browse_mode,
    "strikethrough" to Res.string.tooltip_strikethrough,
    "style" to Res.string.profile_text_style,
    "subtitles" to Res.string.media_subtitles,
    "textBoxes" to Res.string.profile_group_boxes,
    "textColor" to Res.string.text_color,
    "thirdVoiceColor" to Res.string.profile_third_voice_color,
    "tickerSpeed" to Res.string.profile_caption_ticker_speed,
    "titleSlideBackground" to Res.string.profile_title_slide_background,
    "transform" to Res.string.canvas_transform,
    "translations" to Res.string.profile_group_translations,
    "underline" to Res.string.tooltip_underline,
    "verticalAlignment" to Res.string.profile_vertical_alignment,
    "video" to Res.string.canvas_source_video,
    "videoConnection" to Res.string.canvas_camera_connection,
    "videoFormat" to Res.string.canvas_camera_format,
    "votingEnabled" to Res.string.qa_voting_enabled,
    "web" to Res.string.tab_web,
    "wordSpacing" to Res.string.bible_word_spacing,
    "wordWrap" to Res.string.profile_word_wrap,
)

/** Stored values by the label their own row gives them. "none" and "both" mean different things per field. */
internal val SETTING_VALUE_STRINGS: Map<String, StringResource> = mapOf(
    Constants.BILINGUAL_SIDE_BY_SIDE to Res.string.profile_layout_side_by_side,
    Constants.BILINGUAL_TOP_BOTTOM to Res.string.profile_layout_stacked,
    Constants.BILINGUAL_GRID_1X3 to Res.string.bilingual_grid_1x3,
    Constants.BILINGUAL_GRID_3X1 to Res.string.bilingual_grid_3x1,
    Constants.BILINGUAL_GRID_1X4 to Res.string.bilingual_grid_1x4,
    Constants.BILINGUAL_GRID_4X1 to Res.string.bilingual_grid_4x1,
    Constants.BILINGUAL_GRID_2X2 to Res.string.bilingual_grid_2x2,
    Constants.ABOVE_LYRICS to Res.string.profile_position_above_lyrics,
    Constants.BELOW_LYRICS to Res.string.profile_position_below_lyrics,
    Constants.ABOVE_VERSE to Res.string.profile_position_top,
    Constants.BELOW_VERSE to Res.string.profile_position_bottom,
    Constants.TOP to Res.string.top,
    Constants.MIDDLE to Res.string.middle,
    Constants.BOTTOM to Res.string.bottom,
    Constants.LEFT to Res.string.left,
    Constants.CENTER to Res.string.center,
    Constants.RIGHT to Res.string.right,
    Constants.POSITION_ABOVE to Res.string.profile_ref_above,
    Constants.POSITION_BELOW to Res.string.profile_ref_after,
    CAPTION_BOX_BAND to Res.string.profile_caption_shape_band,
    CAPTION_BOX_CARD to Res.string.profile_caption_shape_card,
    CAPTION_STYLE_POP_ON to Res.string.profile_caption_style_pop_on,
    CAPTION_STYLE_ROLL_UP to Res.string.profile_caption_style_roll_up,
    CAPTION_STYLE_RSVP to Res.string.profile_caption_style_rsvp,
    CAPTION_STYLE_TICKER to Res.string.profile_caption_style_ticker,
    CAPTION_BREAK_SEGMENT to Res.string.profile_caption_break_phrase,
    CAPTION_BREAK_SENTENCE to Res.string.profile_caption_break_sentence,
    LAYOUT_INTERLEAVED to Res.string.profile_caption_layout_interleaved,
    TextBoxOverflow.CUT.name to Res.string.profile_box_overflow_cut,
    TextBoxOverflow.SHRINK.name to Res.string.profile_box_overflow_shrink,
    TextBoxOverflow.SPILL.name to Res.string.profile_box_overflow_spill,
    WebPageView.DESKTOP.name to Res.string.desktop_view,
    WebPageView.MOBILE.name to Res.string.mobile_view,
    WebPageView.FOLLOW_WEB_TAB.name to Res.string.profile_web_page_view_follow,
)
