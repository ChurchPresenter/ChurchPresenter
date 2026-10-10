package org.churchpresenter.helper.intent

import kotlin.math.abs

/**
 * The words the rule parser understands, by meaning. English only: a request typed in another
 * language reaches the rules through its `Glossary`, already in these words.
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
    val TOO_BIG = listOf(
        "too big", "too large", "too much text", "doesn't fit", "does not fit", "don't fit", "do not fit", "won't fit",
    )

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

    /** Words and phrases for a text announcement — including the ones churches page parents with. */
    val ANNOUNCEMENT = listOf(
        "announcement", "announcements", "announce", "notice", "notices", "nursery", "baby room", "babies room",
        "kids room", "children's room", "childcare", "child care", "page a parent", "page parents", "call a parent",
        "call parents", "parents of", "lost and found", "parking", "headlights", "car lights", "license plate",
        "licence plate", "scrolling text", "ticker", "crawl", "marquee", "banner", "welcome message",
        "message on screen", "message on the screen", "text on screen", "text on the screen", "news",
    )

    /** Counting down — to a length of time, or to a time of day. */
    val COUNTDOWN = listOf(
        "countdown", "count down", "countdowns", "timer", "timers", "time left", "minutes left", "starts in",
        "pre-service", "preservice", "until the service", "before the service",
    )

    /** Counting up from zero. */
    val COUNT_UP = listOf("count up", "stopwatch", "stop watch", "elapsed", "how long it's been")

    /** The time of day, on the screen. */
    val CLOCK = listOf(
        "clock", "current time", "time of day", "show the time", "display the time", "what time it is",
        "the time on",
    )

    /** First words that take something down rather than put it up. */
    val TAKE_DOWN = setOf("hide", "stop", "remove", "end", "close", "pause", "delete")

    /** A screen for the people up front — what a stage monitor gets called. */
    val STAGE_MONITOR = listOf(
        "stage monitor", "stage monitors", "stage display", "stage screen", "confidence monitor",
        "confidence screen", "foldback", "band monitor", "band screen", "musician monitor", "musicians screen",
        "speaker monitor", "pastor screen", "preacher screen", "notes monitor", "worship team screen",
        "worship team monitor", "platform monitor", "comfort monitor",
    )

    /** On the stage monitor, what the operator asks to change, by topic. */
    val STAGE_LAYOUT = listOf(
        "zone", "zones", "layout", "layouts", "arrangement", "arrange", "split", "quad", "grid", "divide",
        "sections", "parts", "columns", "rows", "how many", "resize", "areas", "boxes",
    )

    /** Not "add", "show" or "display": "add a stage monitor" and "stage display" are its setup. */
    val STAGE_CONTENT = listOf(
        "what goes where", "what shows", "what is shown", "what's shown", "assign", "put", "move", "where does",
        "which zone", "goes where",
    )
    val STAGE_TEXT = listOf(
        "font", "fonts", "text size", "size", "bigger", "smaller", "larger", "color", "colour", "colors",
        "colours", "readable", "hard to read", "background",
    )
    val STAGE_MESSAGE = listOf(
        "message", "messages", "send", "tell", "alert", "note to", "notify", "text the", "message the",
    )

    /** What makes a lower third, or a full screen, about an output rather than the graphic itself. */
    val OUTPUT_WORDS = setOf(
        "display", "displays", "output", "outputs", "screen", "screens", "monitor", "monitors", "projector",
        "tv", "stream", "streaming", "livestream", "obs", "vmix", "ndi", "profile", "profiles", "setup",
    )

    /** Making something new, rather than showing or setting up what is there. */
    val MAKE = setOf(
        "make", "create", "generate", "design", "new", "build", "edit", "name", "names", "title", "titles",
        "animated", "animate", "template",
    )

    /** Full screen, however it is written. */
    val FULL_SCREEN = listOf("full screen", "fullscreen", "full-screen", "main screen", "main display", "main output")

    /** Asking to bring files in, rather than to put them on screen. */
    val ADD = setOf("add", "import", "load", "upload", "open", "choose", "pick", "select", "get")

    /**
     * Bringing songs over from another program. Not "move" or "switch": "move the song up" and
     * "switch to songs" are about the app's own songs.
     */
    val CONVERT = setOf(
        "convert", "converting", "conversion", "converter", "import", "importing", "migrate", "migrating",
        "transfer", "transferring",
    )

    /**
     * The programs the converter reads songs from, by how people write their names, with its id
     * for each and the name as the program writes it. Only those its source list shows: a source
     * it withholds is left out here too.
     */
    val SONG_SOURCES = listOf(
        SongSourceWords(listOf("songbeamer", "song beamer"), "songbeamer", "SongBeamer"),
        SongSourceWords(listOf("openlp", "open lp"), "openlp", "OpenLP"),
        SongSourceWords(listOf("opensong"), "opensong", "OpenSong"),
        SongSourceWords(listOf("freeshow", "free show"), "freeshow", "FreeShow"),
        SongSourceWords(listOf("freeworship", "free worship"), "freeworship", "Free Worship"),
        SongSourceWords(listOf("easyslides", "easy slides"), "easyslides", "EasySlides"),
        SongSourceWords(listOf("quelea"), "quelea", "Quelea"),
        SongSourceWords(listOf("softprojector", "soft projector"), "softprojector", "SoftProjector"),
        SongSourceWords(listOf("videopsalm", "video psalm"), "videopsalm", "VideoPsalm"),
    )

    /** Names for the Song Library Manager, or for changing many songs at once in it. */
    val SONG_LIBRARY = listOf(
        "song library", "songs library", "my library", "library manager", "manage songs", "manage my songs",
        "batch edit", "bulk edit", "mass edit", "batch change", "bulk change", "mass change", "batch rename",
        "bulk rename", "edit songs at once", "organize songs", "organise songs", "organize my songs",
        "organise my songs", "clean up songs", "clean up my songs", "tidy up songs", "tidy my songs",
        "song metadata", "song details", "change songbook", "change the songbook", "set the songbook",
        "change author", "change the author", "categorize songs", "categorise songs", "song catalog",
        "song catalogue",
    )

    /** Words that make editing songs about many of them. */
    val MANY = setOf(
        "multiple", "many", "several", "all", "batch", "bulk", "mass", "lots", "together", "every", "selected",
        "these", "entire", "whole",
    )

    /** Changing songs, as opposed to showing them. */
    val EDIT = setOf(
        "edit", "editing", "change", "changing", "update", "rename", "fix", "tag", "retag", "modify", "organize",
        "organise", "clean", "tidy", "categorize", "categorise", "assign",
    )

    /** What a planned service is called. */
    val SERVICE = setOf(
        "service", "services", "sunday", "sundays", "saturday", "wednesday", "event", "events", "meeting",
        "meetings", "mass", "gathering", "gatherings", "calendar", "planner",
    )

    /** Asking for the Calendar Manager, or to plan services ahead of time. */
    val CALENDAR = listOf(
        "calendar", "calendar manager", "service calendar", "church calendar", "service planner", "planner",
        "plan a service", "plan service", "plan services", "plan ahead", "plan sunday", "plan next sunday",
        "plan next sunday's", "next sunday's service", "sunday's service",
        "plan for sunday", "add a service", "add service", "new service", "create a service", "schedule a service",
        "upcoming service", "upcoming services", "future service", "future services", "next sunday",
        "next week's service", "service plan", "services planned",
    )

    /** A service that comes round again. */
    val RECURRING = listOf(
        "recurring", "recur", "recurs", "repeat", "repeats", "repeating", "repeated", "every week", "every sunday",
        "each sunday", "each week", "weekly", "biweekly", "bi-weekly", "fortnightly", "every other week",
        "every two weeks", "every 2 weeks", "monthly", "every month", "series",
    )

    /** A run of show kept to start new services from. */
    val TEMPLATE = listOf("template", "templates", "service template", "save as template")

    /** Putting a planned service into the Schedule tab. */
    val LOAD_SERVICE = listOf(
        "load into schedule", "load into the schedule", "load the service", "load a service", "load sunday",
        "auto load", "auto-load", "autoload", "load automatically", "automatically load", "into the schedule tab",
    )

    /** Running a service's items on their own. Not "timer": a countdown is the Announcements tab's. */
    val AUTOMATE = listOf("automate", "automation", "automatic", "automatically", "cue", "cues")

    /** Documents the converter reads lyrics out of — only taken with a song word beside them. */
    val SONG_DOCUMENTS = setOf(
        "pdf", "pdfs", "word", "docx", "doc", "powerpoint", "pptx", "ppt", "keynote", "document", "documents",
    )

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

    /** Asking for CCLI Reports: how often songs were used. */
    val CCLI = listOf(
        "ccli", "statistics", "stats", "usage report", "song report", "song usage", "how many times", "how often",
        "play count", "play counts", "most sung", "copyright report", "ccli report",
    )

    /** Planning Center Online, however it is written. */
    val PLANNING_CENTER = listOf("planning center", "planning centre", "planningcenter", "pco")

    /** Running the app from a phone or tablet — the remote and the code to scan for it. */
    val PHONE_REMOTE = listOf(
        "from my phone", "from the phone", "from a phone", "on my phone", "phone remote", "use my phone",
        "connect my phone", "connect a phone", "from my tablet", "from a tablet", "from my ipad", "remote control",
        "qr code", "scan the code", "control it from",
    )

    /** Word starts that mean favorites, however they are spelled. */
    val FAVORITE = listOf("favorit", "favourit", "fave", "starred")
    val STAR_A_SONG = listOf("star a song", "star this song", "star the song", "star songs", "star my songs")

    /** Phrases that make a background about one song rather than all of them. */
    val ONE_SONG = listOf(
        "this song", "one song", "each song", "per song", "single song", "a song", "specific song", "individual song",
        "certain song", "particular song", "its own", "own background", "song's own", "different background",
    )

    /** Showing more than one verse at a time. */
    val MULTI_VERSE = listOf(
        "several verses", "multiple verses", "more than one verse", "many verses", "two verses", "three verses",
        "few verses", "a few verses", "verse range", "range of verses", "verses together", "group of verses",
        "select verses", "select several", "select multiple", "whole passage", "a passage",
    )

    /** Looking something up, in other words than "search". */
    val LOOK_UP = listOf("look up", "look for", "lookup")
    val FIND_VERSE = listOf("find a verse", "find verses", "find the verse", "find a passage", "which verse says")
    val FIND_SONG = listOf(
        "find a song", "find songs", "find the song", "find my song", "song number", "by number", "by title",
        "filter songs", "filter the songs",
    )

    /** Going back to verses already shown. */
    val BIBLE_HISTORY = listOf(
        "bible history", "verse history", "history", "what did we just show", "what did we show", "recently shown",
        "recent verses", "verses we showed", "shown before", "shown earlier", "go back to a verse",
    )

    /** Passages that point at each other. */
    val CROSS_REFS = listOf(
        "cross reference", "cross references", "cross-reference", "cross-references", "cross ref", "cross refs",
        "crossref", "crossrefs", "related verses", "related passages", "parallel passages", "similar verses", "refs",
    )

    /** A web page, to put on screen. */
    val WEBSITE = setOf("website", "websites", "webpage", "webpages", "url", "browser", "site", "web")
    val WEB_PAGE = listOf("web page", "web site", "internet page")

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

/** A program the converter reads songs from: the [phrases] that name it, its [id] and its [name]. */
internal class SongSourceWords(val phrases: List<String>, val id: String, val name: String)
