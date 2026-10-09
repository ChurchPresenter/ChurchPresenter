package org.churchpresenter.helper.suggest

import org.churchpresenter.helper.intent.normalize
import org.churchpresenter.strings.generated.resources.helper_example_bible_style
import org.churchpresenter.strings.generated.resources.helper_example_title_style
import org.churchpresenter.strings.generated.resources.helper_example_end_marker
import org.churchpresenter.strings.generated.resources.helper_example_lyrics_position
import org.churchpresenter.strings.generated.resources.helper_example_lyrics_style
import org.churchpresenter.strings.generated.resources.helper_example_edit_lyrics
import org.churchpresenter.strings.generated.resources.helper_example_cross_refs
import org.churchpresenter.strings.generated.resources.helper_example_bible_history
import org.churchpresenter.strings.generated.resources.helper_example_website
import org.churchpresenter.strings.generated.resources.helper_example_ccli
import org.churchpresenter.strings.generated.resources.helper_example_planning_center
import org.churchpresenter.strings.generated.resources.helper_example_song_background
import org.churchpresenter.strings.generated.resources.helper_example_favorites
import org.churchpresenter.strings.generated.resources.helper_example_multi_verse
import org.churchpresenter.strings.generated.resources.helper_example_bible_search
import org.churchpresenter.strings.generated.resources.helper_example_song_search
import org.churchpresenter.strings.generated.resources.helper_example_stage_layout
import org.churchpresenter.strings.generated.resources.helper_example_stage_monitor
import org.churchpresenter.strings.generated.resources.helper_example_lower_third_output
import org.churchpresenter.strings.generated.resources.helper_example_full_screen_output
import org.churchpresenter.strings.generated.resources.helper_example_announcement
import org.churchpresenter.strings.generated.resources.helper_example_countdown
import org.churchpresenter.strings.generated.resources.helper_example_clock
import org.churchpresenter.strings.generated.resources.helper_example_pictures
import org.churchpresenter.strings.generated.resources.helper_example_slideshow
import org.churchpresenter.strings.generated.resources.helper_example_presentation
import org.churchpresenter.strings.generated.resources.helper_example_video
import org.churchpresenter.strings.generated.resources.helper_example_lower_third
import org.churchpresenter.strings.generated.resources.helper_example_convert
import org.churchpresenter.strings.generated.resources.helper_example_song_library
import org.churchpresenter.strings.generated.resources.helper_example_calendar
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_example_bg
import org.churchpresenter.strings.generated.resources.helper_example_bible_translation
import org.churchpresenter.strings.generated.resources.helper_example_chords
import org.churchpresenter.strings.generated.resources.helper_example_clear
import org.churchpresenter.strings.generated.resources.helper_example_display
import org.churchpresenter.strings.generated.resources.helper_example_identify
import org.churchpresenter.strings.generated.resources.helper_example_next
import org.churchpresenter.strings.generated.resources.helper_example_remote
import org.churchpresenter.strings.generated.resources.helper_example_schedule
import org.churchpresenter.strings.generated.resources.helper_example_shortcuts
import org.churchpresenter.strings.generated.resources.helper_example_song_language
import org.churchpresenter.strings.generated.resources.helper_example_text_size
import org.churchpresenter.strings.generated.resources.helper_example_undo
import org.churchpresenter.strings.generated.resources.helper_example_verse
import org.churchpresenter.strings.generated.resources.helper_example_where
import org.jetbrains.compose.resources.StringResource

/**
 * A request the helper can offer as a chip: the [label] it shows, in the operator's language, and the
 * [request] it sends, in English — the language the rules read, so a chip still works once its label
 * is translated. [keywords] are more words for the same request: a request the rules did not understand
 * is matched against both — by meaning (see `intent/semantic`), nudged by [keywordScores].
 */
enum class SuggestedRequest(val label: StringResource, val request: String, internal val keywords: String) {
    BACKGROUND(
        Res.string.helper_example_bg, "make the song background blue",
        "background backgrounds bg backdrop color colour blue red green black look nicer prettier",
    ),
    VERSE(Res.string.helper_example_verse, "show john 3:16", "verse verses bible scripture chapter john psalm passage"),
    NEW_SONG(Res.string.helper_example_where, "where do i add a song", "song songs add new write create hymn lyrics"),
    PROJECTOR(
        Res.string.helper_example_display, "set up the projector",
        "projector screen screens display displays monitor tv output setup connect second",
    ),
    TEXT_SIZE(
        Res.string.helper_example_text_size, "make the song text bigger",
        "text font size bigger larger smaller small big read letters words",
    ),
    CLEAR(Res.string.helper_example_clear, "clear the screen", "clear blank black empty wipe remove nothing screen"),
    NEXT_SLIDE(Res.string.helper_example_next, "next slide", "next slide slides forward advance previous back"),
    CHORDS(Res.string.helper_example_chords, "how do i add chords to a song", "chord chords cords guitar transpose"),
    SONG_LANGUAGE(
        Res.string.helper_example_song_language, "how do i add a song translation",
        "translation translate language languages bilingual",
    ),
    BIBLE_TRANSLATION(
        Res.string.helper_example_bible_translation, "how do i add a bible translation",
        "bible bibles download translation version versions",
    ),
    SCHEDULE(Res.string.helper_example_schedule, "where is the schedule", "schedule plan service order playlist"),
    REMOTE(Res.string.helper_example_remote, "how do i use the remote", "remote phone tablet companion mobile ipad"),
    IDENTIFY(Res.string.helper_example_identify, "which screen is which", "which identify number numbers"),
    UNDO(Res.string.helper_example_undo, "undo the last change", "undo revert mistake wrong"),
    SHORTCUTS(
        Res.string.helper_example_shortcuts, "show keyboard shortcuts",
        "shortcut shortcuts key keys hotkey keyboard",
    ),
    ANNOUNCEMENT(
        Res.string.helper_example_announcement, "how do i show an announcement",
        "announcement announcements announce message messages notice notices nursery baby babies kids " +
            "children parent parents page parking lost found ticker banner scrolling welcome news",
    ),
    COUNTDOWN(
        Res.string.helper_example_countdown, "how do i show a countdown",
        "countdown countdowns count timer timers minutes left starts stopwatch",
    ),
    CLOCK(
        Res.string.helper_example_clock, "how do i show the clock",
        "clock time current",
    ),
    PICTURES(
        Res.string.helper_example_pictures, "how do i show pictures",
        "photo photos picture pictures image images pic pics album albums jpg",
    ),
    SLIDESHOW(
        Res.string.helper_example_slideshow, "how do i show a slideshow",
        "slideshow slideshows advance",
    ),
    PRESENTATION(
        Res.string.helper_example_presentation, "how do i show a pdf",
        "pdf pdfs powerpoint pptx ppt keynote presentation presentations deck",
    ),
    VIDEO(
        Res.string.helper_example_video, "how do i play a video",
        "video videos movie movies clip clips film mp4 play",
    ),
    LOWER_THIRD(
        Res.string.helper_example_lower_third, "how do i make a lower third",
        "lower third thirds lottie name strap tag nametag chyron speaker",
    ),
    CONVERT(
        Res.string.helper_example_convert, "how do i convert songs",
        "convert converter conversion import migrate transfer " +
            "openlp songbeamer opensong freeshow quelea videopsalm easyslides",
    ),
    SONG_LIBRARY(
        Res.string.helper_example_song_library, "batch edit songs",
        "batch bulk mass multiple many organize organise library songbook author metadata",
    ),
    CALENDAR(
        Res.string.helper_example_calendar, "how do i plan a service",
        "calendar plan planner planning service services sunday " +
            "upcoming future recurring repeat weekly monthly template",
    ),
    STAGE_MONITOR(
        Res.string.helper_example_stage_monitor, "how do i set up a stage monitor",
        "stage monitor confidence foldback band " +
            "musician musicians pastor preacher notes platform",
    ),
    LOWER_THIRD_OUTPUT(
        Res.string.helper_example_lower_third_output, "how do i set up a lower third display",
        "lower third display output stream " +
            "streaming livestream ndi obs vmix overlay",
    ),
    FULL_SCREEN_OUTPUT(
        Res.string.helper_example_full_screen_output, "how do i set up a full screen display",
        "full fullscreen main display " +
            "output projector audience profile",
    ),
    STAGE_LAYOUT(
        Res.string.helper_example_stage_layout, "what goes where on the stage monitor",
        "zone zones layout arrangement chords notes clock next stage " +
            "monitor confidence what goes where",
    ),
    SONG_SEARCH(
        Res.string.helper_example_song_search, "how do i search for a song",
        "search find look filter number title songbook song songs",
    ),
    BIBLE_SEARCH(
        Res.string.helper_example_bible_search, "how do i search the bible",
        "search find look word words bible scripture verse",
    ),
    MULTI_VERSE(
        Res.string.helper_example_multi_verse, "how do i show several verses",
        "several multiple many range passage verses together select",
    ),
    FAVORITES(
        Res.string.helper_example_favorites, "how do i favorite a song",
        "favorite favorites favourite star starred",
    ),
    SONG_BACKGROUND(
        Res.string.helper_example_song_background, "give this song its own background",
        "own background song picture video camera each per",
    ),
    PLANNING_CENTER(
        Res.string.helper_example_planning_center, "import from planning center",
        "planning center pco plan import services",
    ),
    CCLI(Res.string.helper_example_ccli, "ccli report", "ccli report reports statistics stats usage copyright times"),
    WEBSITE(Res.string.helper_example_website, "show a website", "website web page url browser site internet"),
    BIBLE_HISTORY(
        Res.string.helper_example_bible_history, "what did we just show",
        "history recent recently shown earlier before back verse verses",
    ),
    CROSS_REFS(
        Res.string.helper_example_cross_refs, "related verses",
        "cross reference references refs related parallel",
    ),
    EDIT_LYRICS(
        Res.string.helper_example_edit_lyrics, "how do i change song lyrics",
        "lyrics words edit change fix typo mistake correct",
    ),
    LYRICS_STYLE(
        Res.string.helper_example_lyrics_style, "how do i make the lyrics bold",
        "bold italic underline outline shadow style styling look font",
    ),
    LYRICS_POSITION(
        Res.string.helper_example_lyrics_position, "how do i move the lyrics to the top",
        "position top bottom middle left right center align alignment margins move",
    ),
    END_MARKER(Res.string.helper_example_end_marker, "how do i change the end of song marker", "end marker last slide"),
    TITLE_STYLE(
        Res.string.helper_example_title_style, "how do i make the song title bigger",
        "title titles name heading",
    ),
    BIBLE_STYLE(
        Res.string.helper_example_bible_style, "how do i change how bible verses look",
        "bible verse verses reference scripture look style font",
    ),
    ;

    /** The keywords, and the words of the request itself. */
    internal val words: List<String> = (keywords.split(' ') + request.split(' ')).distinct()

    companion object {
        /** What the helper offers when it has nothing closer: a little of everything it does. */
        val DEFAULTS = listOf(BACKGROUND, VERSE, NEW_SONG, PROJECTOR)
    }
}

/**
 * How much each request's words are like [input]'s, scaled so the closest scores 1 — empty when no word
 * is like any of them. A typed word scores against each request's keywords: the same word, one starting
 * the other, or one or two letters off ("clera" for "clear", "chrods" for "chords"), which is what the
 * sentence model, reading whole words, does not catch.
 */
fun keywordScores(input: String): Map<SuggestedRequest, Double> {
    val typed = normalize(input).split(' ').filter { it.length > 1 && it !in STOP_WORDS }
    val raw = SuggestedRequest.entries.associateWith { request ->
        typed.sumOf { word -> request.words.maxOf { wordMatch(word, it) } }
    }
    val top = raw.values.maxOrNull() ?: 0.0
    return if (top <= 0.0) emptyMap() else raw.mapValues { (_, score) -> score / top }
}

/** How alike two words are, from 1 (the same) down to 0 (nothing alike). */
internal fun wordMatch(typed: String, keyword: String): Double {
    val shorter = minOf(typed.length, keyword.length)
    return when {
        typed == keyword -> 1.0
        shorter >= PREFIX_MIN && (typed.startsWith(keyword) || keyword.startsWith(typed)) -> PREFIX_SCORE
        shorter >= ONE_OFF_MIN && editDistance(typed, keyword) <= 1 -> ONE_OFF_SCORE
        shorter >= TWO_OFF_MIN && editDistance(typed, keyword) <= 2 -> TWO_OFF_SCORE
        else -> 0.0
    }
}

/** Letters to add, drop, change or swap to turn [a] into [b] — a swap of two neighbours counts once. */
internal fun editDistance(a: String, b: String): Int {
    val d = Array(a.length + 1) { i -> IntArray(b.length + 1) { j -> if (i == 0) j else if (j == 0) i else 0 } }
    for (i in 1..a.length) {
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
            if (isSwap(a, b, i, j)) {
                d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
            }
        }
    }
    return d[a.length][b.length]
}

/** Whether the letters just before [i] in [a] and [j] in [b] are the same two, the other way round. */
private fun isSwap(a: String, b: String, i: Int, j: Int): Boolean =
    i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]

/** Words that say nothing about which request it is. */
private val STOP_WORDS = setOf(
    "the", "a", "an", "to", "of", "on", "in", "it", "is", "my", "me", "i", "do", "how", "can", "you", "please",
    "make", "set", "get", "want", "need", "this", "that", "and", "or", "for", "with", "where", "what", "show",
)

private const val PREFIX_MIN = 3
private const val ONE_OFF_MIN = 4
private const val TWO_OFF_MIN = 6
private const val PREFIX_SCORE = 0.8
private const val ONE_OFF_SCORE = 0.7
private const val TWO_OFF_SCORE = 0.5
