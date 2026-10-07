package org.churchpresenter.helper.intent

import kotlin.math.abs

/**
 * The words the rule parser understands, by meaning. English only for now: the parser reads what
 * the operator types, and these tables move into one per language when another is added.
 */
internal object Vocabulary {
    val BACKGROUND = setOf("background", "backgrounds", "bg", "backdrop")
    val SONG = setOf("song", "songs", "lyrics", "lyric", "worship", "hymn", "hymns")
    val BIBLE = setOf("bible", "scripture", "scriptures", "verse", "verses")
    val BOTH = setOf("all", "both", "everything", "every")
    val FONT = setOf("font", "fonts", "text", "letters", "words", "writing", "size")
    val BIGGER = setOf("bigger", "larger", "increase", "enlarge", "grow", "raise")
    val SMALLER = setOf("smaller", "decrease", "shrink", "reduce", "lower")
    val NEXT = setOf("next", "forward", "advance")
    val PREVIOUS = setOf("previous", "prev", "back", "backward", "backwards")
    val CLEAR = setOf("clear", "blank", "empty", "wipe")
    val SCREEN = setOf(
        "screen", "screens", "output", "outputs", "display", "displays", "projector", "monitor", "monitors",
    )
    val SETTINGS = setOf("settings", "options", "preferences", "setting")
    val SETUP = setOf("setup", "configure", "connect", "assign", "use")
    val UNDO = setOf("undo", "revert")

    /**
     * Word starts that mean another language: "translate", "translation", "bilingual", "languages" —
     * cut short enough to take the usual misspellings too ("langauge", "languge", "tranlsate").
     */
    val TRANSLATION = listOf("transl", "tranl", "bilingual", "multilingual", "lang")

    /** Phrases that ask for a song in another language. */
    val OTHER_LANGUAGE = listOf("another language", "second language", "other language", "two languages", "version in")

    /**
     * Languages a church sings in, by their English names — "Russian lyrics", "add Spanish". The
     * app's own locales, and a few more that congregations commonly sing in.
     */
    val LANGUAGE_NAMES = setOf(
        "english", "spanish", "russian", "ukrainian", "belarusian", "polish", "german", "french", "portuguese",
        "italian", "dutch", "romanian", "czech", "slovak", "croatian", "serbian", "bulgarian", "hungarian",
        "greek", "turkish", "arabic", "farsi", "persian", "hebrew", "hindi", "nepali", "tamil", "thai", "lao",
        "chinese", "mandarin", "cantonese", "japanese", "korean", "vietnamese", "indonesian", "malay",
        "tagalog", "filipino", "swahili", "amharic", "kazakh", "uzbek", "estonian", "latvian", "lithuanian",
        "finnish", "swedish", "norwegian", "danish", "armenian", "georgian", "moldovan", "latin",
    )

    /**
     * Asking to add a language — enough on its own, with no song named: a Bible translation is
     * downloaded, not added. Matched after [normalizeLanguage] fixes the spelling.
     */
    val ADD_LANGUAGE = listOf(
        "add a language", "add language", "add another language", "add a new language", "add a second language",
        "add second language", "new language",
    )

    /** "langauge", "languge", "langage" → "language", so the phrases above match however it is typed. */
    fun normalizeLanguage(text: String): String = text.replace(Regex("""\blang\p{L}*"""), "language")

    /** "Text is too small" asks for bigger; "too big" for smaller. */
    val TOO_SMALL = listOf("too small", "too tiny", "hard to read", "can't read", "cannot read")
    val TOO_BIG = listOf("too big", "too large", "too much text", "doesn't fit", "does not fit")

    /** A screen that is not doing what it should — display setup is the answer. */
    val SCREEN_TROUBLE = listOf(
        "not working", "doesn't work", "does not work", "isn't working", "not showing", "won't show",
        "nothing shows", "nothing on", "no picture", "no signal", "is black", "stays black",
    )

    /** Asking which screen is which — numbering them answers it. */
    val WHICH_SCREEN = listOf("which screen", "which display", "which monitor", "which projector", "number the")

    /** A greeting, or asking what the helper can do. */
    val GREETING = setOf("hello", "hi", "hey", "help", "hiya", "howdy")
    val ABOUT_HELPER = listOf("what can you do", "what do you do", "who are you", "what are you", "how do you work")
    /** Words a greeting comes padded with: "hi there wick", "help me please". */
    val FILLER = setOf("there", "wick", "me", "please", "pls", "again", "i", "need")
    val THANKS = listOf("thanks", "thank you", "thx", "cheers", "much appreciated", "great job", "awesome")

    /** Pictures, and a folder of them — the Pictures tab's album. */
    val PHOTOS = setOf(
        "photo", "photos", "picture", "pictures", "image", "images", "pic", "pics", "pix", "album", "albums",
        "jpg", "jpeg", "png",
    )

    /** Pictures moving on by themselves. */
    val SLIDESHOW = listOf("slideshow", "slide show", "slideshows", "slide shows")

    /** What the Presentation tab opens. */
    val PRESENTATION = setOf(
        "pdf", "pdfs", "powerpoint", "pptx", "ppt", "keynote", "presentation", "presentations", "deck",
    )

    /** What the Media tab opens. */
    val VIDEO = setOf("video", "videos", "movie", "movies", "clip", "clips", "film", "mp4", "footage")

    /** A lower third, by the names people give it. */
    val LOWER_THIRD = listOf(
        "lower third", "lower thirds", "lower-third", "lower-thirds", "lowerthird", "lowerthirds", "lottie", "l3",
        "name strap", "name straps", "name tag", "nametag", "name bar", "chyron", "speaker name", "name and title",
    )

    /** Asking to bring files in, rather than to put them on screen. */
    val ADD = setOf("add", "import", "load", "upload", "open", "choose", "pick", "select", "get")

    /** Word starts that mean chords. */
    const val CHORD = "chord"

    /** "Cords" — the common misspelling, taken only beside a song word, since a cord is also a cable. */
    val CHORD_MISSPELLINGS = setOf("cord", "cords")

    /** Words that, beside a Bible, ask to add one: "download a Bible", "get another Bible", "new version". */
    val GET = setOf(
        "add", "download", "get", "install", "import", "new", "another", "more", "other", "version", "versions",
    )

    /** Word starts that make "translation" mean the Bible's, not a song's. */
    val BIBLE_NAMES = listOf("bible", "scripture")

    /** Phrases that make a request a question about where something is. */
    val WHERE = listOf(
        "where", "how do i", "how can i", "how to", "how would i", "find", "show me", "can't find", "cannot find",
    )

    /** Phrases that make a background change last only for this service. */
    val TEMPORARY = listOf("for now", "this service", "temporarily", "for today", "just now")

    /** Leading words before a Bible reference: "show John 3:16", "go to Psalm 23". */
    val VERSE_VERBS = listOf(
        "show me", "show", "open", "go to", "read", "display", "put up", "bring up", "find", "project",
    )

    /** Words that cannot be a Bible book, so "show song 3" is not read as a reference. */
    val NOT_A_BOOK = SONG + SCREEN +
        setOf("slide", "slides", "picture", "pictures", "tab", "page", "number", "step", "item")
}

/** Colour names to `#RRGGBB`. A name not here can still be typed as a hex code. */
internal object ColorNames {
    private val NAMES = mapOf(
        "black" to "#000000",
        "white" to "#FFFFFF",
        "grey" to "#808080",
        "gray" to "#808080",
        "silver" to "#C0C0C0",
        "red" to "#C62828",
        "maroon" to "#6D1B1B",
        "crimson" to "#B71C3C",
        "orange" to "#EF6C00",
        "yellow" to "#F9D71C",
        "gold" to "#C9A227",
        "green" to "#2E7D32",
        "lime" to "#7CB342",
        "olive" to "#6B6B1E",
        "teal" to "#00796B",
        "cyan" to "#00ACC1",
        "turquoise" to "#26A69A",
        "blue" to "#1565C0",
        "navy" to "#0D1B4C",
        "sky" to "#4FC3F7",
        "purple" to "#6A1B9A",
        "violet" to "#7E57C2",
        "lavender" to "#B39DDB",
        "pink" to "#EC407A",
        "magenta" to "#C2185B",
        "brown" to "#5D4037",
        "beige" to "#D7CCB0",
        "cream" to "#F3EBD3",
    )
    private val HEX = Regex("""#([0-9a-f]{6}|[0-9a-f]{3})\b""")
    private const val SHADE = 0.35f

    /** A colour in [tokens]: a hex code, or a name, optionally "dark" or "light". The name as typed, and its hex. */
    fun find(tokens: List<String>, normalized: String): Pair<String, String>? {
        HEX.find(normalized)?.let { match ->
            val digits = match.groupValues[1]
            val full = if (digits.length == 3) digits.map { "$it$it" }.joinToString("") else digits
            return match.value to "#${full.uppercase()}"
        }
        val index = tokens.indexOfFirst { it in NAMES }
        if (index < 0) return null
        val name = tokens[index]
        val base = NAMES.getValue(name)
        return when (tokens.getOrNull(index - 1)) {
            "dark" -> "dark $name" to shade(base, -SHADE)
            "light" -> "light $name" to shade(base, SHADE)
            else -> name to base
        }
    }

    /** [hex] moved toward black (negative [amount]) or white (positive). */
    private fun shade(hex: String, amount: Float): String {
        val channels = (1..5 step 2).map { hex.substring(it, it + 2).toInt(16) }
        val moved = channels.map { c ->
            val target = if (amount < 0) 0 else 255
            (c + (target - c) * abs(amount)).toInt().coerceIn(0, 255)
        }
        return "#" + moved.joinToString("") { "%02X".format(it) }
    }
}
