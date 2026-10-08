package org.churchpresenter.helper.intent

import org.churchpresenter.calendar.model.parseReference
import org.churchpresenter.helper.action.HelperAction

// The rules that do a job outright rather than show where it is done: a countdown, an announcement,
// a song found or added, the schedule stepped, and two questions answered.

private val MINUTES = Regex("""\b(\d{1,3})\s*-?\s*(?:minutes?|mins?|m)\b""")
private const val MAX_COUNTDOWN_MINUTES = 600

/** "5 minute countdown", "start a 10 min timer", "countdown for 3 minutes". */
internal fun countdownRule(r: Request): Resolution? {
    if (!r.hasPhrase(Vocabulary.COUNTDOWN) || r.first in Vocabulary.TAKE_DOWN || r.has(Vocabulary.CLEAR)) return null
    val minutes = MINUTES.find(r.text)?.groupValues?.get(1)?.toIntOrNull() ?: return null
    if (minutes !in 1..MAX_COUNTDOWN_MINUTES) return null
    return act(HelperAction.StartCountdown(minutes))
}

private val TELL_PARENTS = Regex(
    """^(?:please\s+)?(?:tell|ask)\s+(?:the\s+)?(parents?\s+of\s+.+?)\s+to\s+(.+?)[.!]?$""",
    RegexOption.IGNORE_CASE,
)
private val ANNOUNCE = Regex("""^(?:please\s+)?announce\s*:?\s+(.+)$""", RegexOption.IGNORE_CASE)
private val LABELLED = Regex(
    "^(?:please\\s+)?(?:show|put up|display|post)\\s+(?:an?\\s+|the\\s+)?" +
        """(?:announcement|message|text|notice)\s*:?\s+(.+)$""",
    RegexOption.IGNORE_CASE,
)
private val QUOTED = Regex("""["“”«]([^"“”«»]+)["“”»]""")

/**
 * "Tell the parents of Sam to come to the nursery", "announce: coffee after the service",
 * "show the message "Welcome!"" — the words go on screen as an announcement, as typed.
 */
internal fun announcementRule(r: Request): Resolution? {
    val raw = r.raw
    val text = TELL_PARENTS.find(raw)?.let { m ->
        "${m.groupValues[1].replaceFirstChar { it.uppercase() }}, please ${m.groupValues[2]}."
    } ?: ANNOUNCE.find(raw)?.groupValues?.get(1)
        ?: LABELLED.find(raw)?.groupValues?.get(1)
        ?: QUOTED.find(raw)?.groupValues?.get(1)?.takeIf { r.hasPhrase(Vocabulary.ANNOUNCEMENT) || r.first == "show" }
    val shown = text?.trim()?.trim('"', '“', '”', '«', '»')?.trim()
    return shown?.takeIf { it.isNotEmpty() }?.let { act(HelperAction.ShowAnnouncement(it)) }
}

private val TO_SCHEDULE = listOf(
    "to the schedule", "to schedule", "into the schedule", "on the schedule", "in the schedule",
)
private val ADD_VERBS = setOf("add", "put", "insert", "queue", "include", "append")
private val SONG_PREFIX = Regex("""^(?:the\s+|a\s+)?(?:song|hymn)\s+(?:number\s+|called\s+|named\s+)?""")

/** "Add John 3:16 to the schedule", "put amazing grace on the schedule", "add song 245 to schedule". */
internal fun addToScheduleRule(r: Request): Resolution? {
    if (r.first !in ADD_VERBS) return null
    val phrase = TO_SCHEDULE.firstOrNull { r.text.containsPhrase(it) } ?: return null
    val what = r.text.removePrefix("${r.first} ").substringBefore(" $phrase").trim()
    if (what.isEmpty() || what in setOf("it", "this", "that", "this song", "this verse")) return null
    val ref = parseReference(asReference(what))
    if (ref != null && ref.bookName.split(' ').last() !in Vocabulary.NOT_A_BOOK) {
        val book = ref.bookName.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        val first = maxOf(1, ref.firstVerse)
        return act(
            HelperAction.AddVerseToSchedule(
                book, ref.chapter, first, maxOf(first, ref.lastVerse), ref.copy(bookName = book).display,
            ),
        )
    }
    return act(HelperAction.AddSongToSchedule(what.replace(SONG_PREFIX, "").trim()))
}

/** A typed reference in the form [parseReference] reads: "john chapter 3 verse 16" → "john 3:16". */
internal fun asReference(text: String): String = text
    .replace(Regex("""\bchapter (\d+) verses? (\d+)"""), "$1:$2")
    .replace(Regex("""^(.*\p{L}) (\d{1,3}) (\d{1,3})$"""), "$1 $2:$3")
    .replace(Regex("""\s*:\s*"""), ":")
    .replace(Regex("""(\d) ?- ?(\d)"""), "$1-$2")

private val SCHEDULE_WORDS = listOf("in the schedule", "from the schedule", "on the schedule", "schedule item")
private val GO_TO = Regex(
    """^(?:go to|jump to|skip to|move to|show|open)\s+(?:the\s+)?(.+?)\s+(?:in|from|on) the schedule$""",
)

/** "Next item", "previous in the schedule", "go to the sermon in the schedule". */
internal fun scheduleStepRule(r: Request): Resolution? {
    val aboutItem = r.says("item", "items") || r.hasPhrase(SCHEDULE_WORDS) || r.says("schedule")
    if (!aboutItem) return null
    GO_TO.find(r.text)?.let { m ->
        val name = m.groupValues[1]
        if (name !in setOf("next", "next item", "previous", "previous item", "last item")) {
            return act(HelperAction.ScheduleGoTo(name))
        }
    }
    return when {
        r.has(Vocabulary.NEXT) -> act(HelperAction.ScheduleStep(forward = true))
        r.has(Vocabulary.PREVIOUS) || r.says("last item") -> act(HelperAction.ScheduleStep(forward = false))
        else -> null
    }
}

private val WHATS_LIVE = listOf(
    "what's live", "whats live", "what is live", "what's on screen", "what is on screen", "what's on the screen",
    "what is on the screen", "what's showing", "what is showing", "what are we showing", "what's projected",
    "what is projected", "what's up on screen", "what's on air", "what is on air", "is anything live",
)

internal fun whatsLiveRule(r: Request): Resolution? =
    if (r.hasPhrase(WHATS_LIVE)) act(HelperAction.WhatsLive) else null

private val VERSION = listOf(
    "what version", "which version", "app version", "version of the app", "version am i", "version is this",
    "check for updates", "check for update", "check for an update", "any updates", "an update", "is there an update",
    "update the app", "latest version", "up to date", "newer version", "new version of the app",
)

/** "What version is this", "check for updates". A Bible's version is the Bible rules'. */
internal fun versionRule(r: Request): Resolution? {
    if (Vocabulary.BIBLE_NAMES.any { r.text.containsWordPrefix(it) }) return null
    return if (r.hasPhrase(VERSION)) act(HelperAction.CheckForUpdates) else null
}

private const val FIND = """(?:(?:show|open|find|sing|play|pull up|bring up)\s+)?(?:the\s+)?"""
private val SONG_NUMBER = Regex("""^$FIND(?:song|hymn)\s+(?:number\s+|no\s+|#\s*)?(\d{1,5})$""")
private val NAMED_SONG = Regex("""^$FIND(?:song|hymn)\s+(?:called\s+|named\s+)?(.+)$""")
private val SONG_LAST = Regex("""^$FIND(.+?)\s+(?:song|hymn)$""")
private val LOOSE = Regex("""^(?:show|open|sing|play|pull up|bring up)\s+(?:the\s+)?(.+)$""")
private val NOT_A_SONG = setOf("me", "it", "this", "that", "everything", "all", "something", "more", "help")

private val NOT_A_TITLE = Vocabulary.NEXT + Vocabulary.PREVIOUS + setOf("new", "a", "this", "that", "my", "your")

/** "Song 245", "show the song amazing grace", "amazing grace song" — said outright to be a song. */
internal fun namedSongRule(r: Request): Resolution? {
    val text = r.text.removePrefix("#")
    val query = SONG_NUMBER.find(text)?.groupValues?.get(1)
        ?: NAMED_SONG.find(text)?.groupValues?.get(1)
        ?: SONG_LAST.find(text)?.groupValues?.get(1)
    return query?.let(::songQuery)
}

/** "Sing how great thou art", "show amazing grace": a song, since nothing else claimed it. */
internal fun songLookupRule(r: Request): Resolution? {
    val rest = LOOSE.find(r.text)?.groupValues?.get(1) ?: return null
    if (rest.split(' ').any { it in Vocabulary.NOT_A_BOOK || it in Vocabulary.SETTINGS }) return null
    return songQuery(rest)
}

private fun songQuery(query: String): Resolution? {
    val q = query.trim()
    if (q.length < 2 || q in NOT_A_SONG || q.split(' ').first() in NOT_A_TITLE) return null
    return act(HelperAction.FindSong(q))
}
