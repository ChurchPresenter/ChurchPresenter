package org.churchpresenter.helper.intent

import org.churchpresenter.helper.action.GuideStep
import org.churchpresenter.helper.action.GuideTour
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.helperText
import org.churchpresenter.sharedui.guide.GuideTarget
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.bible
import org.churchpresenter.strings.generated.resources.customize_group_reference
import org.churchpresenter.strings.generated.resources.customize_group_verse_text
import org.churchpresenter.strings.generated.resources.edit_song
import org.churchpresenter.strings.generated.resources.helper_hint_look_alignment
import org.churchpresenter.strings.generated.resources.helper_hint_look_bible_page
import org.churchpresenter.strings.generated.resources.helper_hint_look_element
import org.churchpresenter.strings.generated.resources.helper_hint_look_end_marker
import org.churchpresenter.strings.generated.resources.helper_hint_look_margins
import org.churchpresenter.strings.generated.resources.profile_text_font
import org.churchpresenter.strings.generated.resources.helper_hint_look_font
import org.churchpresenter.strings.generated.resources.helper_hint_look_profile
import org.churchpresenter.strings.generated.resources.helper_hint_look_shadow
import org.churchpresenter.strings.generated.resources.helper_hint_look_size
import org.churchpresenter.strings.generated.resources.helper_hint_look_songs_title
import org.churchpresenter.strings.generated.resources.helper_hint_look_style
import org.churchpresenter.strings.generated.resources.helper_hint_look_vertical
import org.churchpresenter.strings.generated.resources.helper_hint_pick_and_edit
import org.churchpresenter.strings.generated.resources.helper_hint_song_lyrics
import org.churchpresenter.strings.generated.resources.profile_end_marker
import org.churchpresenter.strings.generated.resources.profile_margins
import org.churchpresenter.strings.generated.resources.profile_text_alignment
import org.churchpresenter.strings.generated.resources.profile_text_shadow
import org.churchpresenter.strings.generated.resources.profile_text_size
import org.churchpresenter.strings.generated.resources.profile_text_style
import org.churchpresenter.strings.generated.resources.profile_vertical_alignment
import org.churchpresenter.strings.generated.resources.song_element_lyrics
import org.churchpresenter.strings.generated.resources.song_element_number
import org.churchpresenter.strings.generated.resources.songs
import org.churchpresenter.strings.generated.resources.title
import org.jetbrains.compose.resources.StringResource

/** Which text a look question is about: the page it is styled on, and its chip on that page. */
internal enum class LookPart(val songs: Boolean, val element: String, val label: StringResource) {
    LYRICS(true, "SONG_LYRICS", Res.string.song_element_lyrics),
    TITLE(true, "SONG_TITLE", Res.string.title),
    NUMBER(true, "SONG_NUMBER", Res.string.song_element_number),
    VERSE(false, "BIBLE_TEXT", Res.string.customize_group_verse_text),
    REFERENCE(false, "BIBLE_REFERENCE", Res.string.customize_group_reference),
}

/** What about it: one row, the placement rows, or its main rows when the question names none. */
internal enum class LookAspect { FONT, SIZE, STYLE, SHADOW, POSITION, MARGINS, ALL }

private val EDIT_LYRICS = listOf(
    "change song lyrics", "change the lyrics", "change lyrics", "edit lyrics", "edit the lyrics", "edit song lyrics",
    "fix the lyrics", "fix lyrics", "change the words", "edit the words", "update the lyrics", "correct the lyrics",
    "typo", "mistake in the lyrics", "edit a song", "edit the song", "change a song",
)
private val END_MARKER = listOf(
    "end of song marker", "end-of-song marker", "end marker", "end of song", "end-of-song", "end of the song",
    "song end marker", "end indicator", "last slide marker",
)
private val TEXT_WORDS = setOf(
    "lyrics", "lyric", "text", "words", "font", "fonts", "letters", "song", "songs", "title", "titles",
    "verse", "verses", "bible", "scripture", "reference", "references", "number", "numbers",
)
private val STYLE_WORDS = setOf(
    "bold", "italic", "italics", "underline", "underlined", "underlining", "strikethrough", "outline", "outlined",
)
private val SHADOW_WORDS = setOf("shadow", "shadows", "shadowed")
private val MARGIN_WORDS = setOf("margin", "margins", "padding", "edge", "edges")
private val POSITION_WORDS = setOf(
    "top", "bottom", "middle", "left", "right", "center", "centre", "centered", "centred", "align", "aligned",
    "alignment", "position", "positioned", "placement", "vertically", "horizontally",
)
private val FONT_WORDS = setOf("font", "fonts", "typeface", "typefaces", "font family", "lettering")
private val SIZE_WORDS = Vocabulary.BIGGER + Vocabulary.SMALLER + setOf("size", "sizes", "huge", "tiny")
private val LOOK_WORDS = setOf(
    "look", "looks", "style", "styles", "styling", "appearance", "format", "formatting", "design", "color", "colour",
)
private val TITLE_WORDS = listOf("title", "titles", "song title", "song name", "name of the song")
private val NUMBER_WORDS = listOf("song number", "hymn number", "number", "numbers", "song numbers")
private val REFERENCE_WORDS = listOf("reference", "references", "ref", "book name", "chapter and verse")

/**
 * "How do I change song lyrics", "make the lyrics bold", "move the lyrics to the top", "add a shadow",
 * "change the margins", "the end-of-song marker", "make the song title bigger", "style the bible
 * reference": a tour to the row that does it. Making the lyrics or verses bigger, said outright, is
 * the font rule's to do.
 */
internal fun lookRule(r: Request): Resolution? {
    if (r.hasPhrase(EDIT_LYRICS)) return act(HelperAction.Highlight(LookTours.editLyrics()))
    if (r.hasPhrase(END_MARKER)) return act(HelperAction.Highlight(LookTours.endMarker()))
    if (r.says("schedule")) return null
    val aspect = when {
        r.has(STYLE_WORDS) -> LookAspect.STYLE
        r.has(SHADOW_WORDS) || r.says("drop shadow") -> LookAspect.SHADOW
        r.has(MARGIN_WORDS) -> LookAspect.MARGINS
        r.has(POSITION_WORDS) || r.says("on screen", "on the screen") && r.first == "move" -> LookAspect.POSITION
        r.has(SIZE_WORDS) || r.hasPhrase(Vocabulary.TOO_SMALL) || r.hasPhrase(Vocabulary.TOO_BIG) -> LookAspect.SIZE
        // "The font", with no bigger or bolder: the typeface.
        r.hasPhrase(FONT_WORDS.toList()) -> LookAspect.FONT
        r.has(LOOK_WORDS) -> LookAspect.ALL
        else -> return null
    }
    // Margins are only ever the text's; anything else has to say what it is about.
    if (aspect != LookAspect.MARGINS && r.words.none { it in TEXT_WORDS }) return null
    val bible = Vocabulary.BIBLE_NAMES.any { r.text.containsWordPrefix(it) } || r.says("verse", "verses")
    val part = when {
        bible && r.hasPhrase(REFERENCE_WORDS) -> LookPart.REFERENCE
        bible -> LookPart.VERSE
        r.hasPhrase(REFERENCE_WORDS) -> LookPart.REFERENCE
        r.hasPhrase(TITLE_WORDS) -> LookPart.TITLE
        r.hasPhrase(NUMBER_WORDS) -> LookPart.NUMBER
        else -> LookPart.LYRICS
    }
    // "Make the lyrics bigger", said outright, is done rather than shown.
    val mainText = part == LookPart.LYRICS || part == LookPart.VERSE
    if (aspect == LookAspect.SIZE && mainText && !r.isQuestion) return null
    return act(HelperAction.Highlight(LookTours.look(part, aspect)))
}

/** The tours [lookRule] chooses between. */
internal object LookTours {
    /** The Songs tab, Edit, then the lyrics in the editor. */
    fun editLyrics() = GuideTour(
        listOf(
            tabStep(Tabs.SONGS),
            GuideStep(
                GuideTargets.EDIT_SONG,
                helperText(Res.string.helper_hint_pick_and_edit, helperText(Res.string.edit_song)),
                before = HelperAction.SelectTab(Tabs.SONGS),
            ),
            GuideStep(GuideTargets.SONG_LYRICS, helperText(Res.string.helper_hint_song_lyrics)),
        ),
    )

    fun endMarker() = GuideTour(
        onPage(songs = true) + step(
            GuideTargets.PROFILE_END_MARKER,
            Res.string.helper_hint_look_end_marker,
            Res.string.profile_end_marker,
        ),
    )

    /**
     * Settings, a profile, its Songs or Bible page, the chip for [part] when the rows depend on it,
     * then the rows for [aspect].
     */
    fun look(part: LookPart, aspect: LookAspect): GuideTour {
        val chip = GuideStep(
            GuideTargets.lookElement(part.element),
            helperText(Res.string.helper_hint_look_element, helperText(part.label)),
        )
        val rows = when (aspect) {
            LookAspect.FONT -> listOf(font())
            LookAspect.SIZE -> listOf(size())
            LookAspect.STYLE -> listOf(style())
            LookAspect.SHADOW -> listOf(shadow())
            LookAspect.POSITION -> listOf(vertical(), alignment(), margins())
            LookAspect.MARGINS -> listOf(margins())
            LookAspect.ALL -> listOf(font(), size(), style(), alignment(), shadow())
        }
        // Margins are the whole page's; everything else, alignment included, is per part.
        val withChip = if (aspect == LookAspect.MARGINS) rows else listOf(chip) + rows
        return GuideTour(onPage(part.songs) + withChip)
    }

    private fun onPage(songs: Boolean): List<GuideStep> = listOf(
        GuideStep(
            GuideTargets.settingsPage(SettingsPage.PROFILES),
            helperText(Res.string.helper_hint_look_profile),
            before = HelperAction.OpenSettings(SettingsPage.PROFILES),
        ),
        if (songs) {
            step(GuideTargets.PROFILE_SONGS_PAGE, Res.string.helper_hint_look_songs_title, Res.string.songs)
        } else {
            step(GuideTargets.PROFILE_BIBLE_PAGE, Res.string.helper_hint_look_bible_page, Res.string.bible)
        },
    )

    private fun font() =
        step(GuideTargets.PROFILE_TEXT_FONT, Res.string.helper_hint_look_font, Res.string.profile_text_font)
    private fun size() =
        step(GuideTargets.PROFILE_TEXT_SIZE, Res.string.helper_hint_look_size, Res.string.profile_text_size)
    private fun style() =
        step(GuideTargets.PROFILE_TEXT_STYLE, Res.string.helper_hint_look_style, Res.string.profile_text_style)
    private fun shadow() =
        step(GuideTargets.PROFILE_TEXT_SHADOW, Res.string.helper_hint_look_shadow, Res.string.profile_text_shadow)
    private fun alignment() = step(
        GuideTargets.PROFILE_TEXT_ALIGNMENT,
        Res.string.helper_hint_look_alignment,
        Res.string.profile_text_alignment,
    )
    private fun vertical() = step(
        GuideTargets.PROFILE_VERTICAL_ALIGNMENT,
        Res.string.helper_hint_look_vertical,
        Res.string.profile_vertical_alignment,
    )
    private fun margins() =
        step(GuideTargets.PROFILE_MARGINS, Res.string.helper_hint_look_margins, Res.string.profile_margins)

    /** A step whose [hint] names the [label] of what it rings, in the app's own words. */
    private fun step(target: GuideTarget, hint: StringResource, label: StringResource) =
        GuideStep(target, helperText(hint, helperText(label)))
}
