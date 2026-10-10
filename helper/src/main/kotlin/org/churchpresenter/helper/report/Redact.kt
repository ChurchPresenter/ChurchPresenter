package org.churchpresenter.helper.report

import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.intent.ResolveContext
import org.churchpresenter.helper.intent.Resolution
import org.churchpresenter.helper.intent.RuleIntentResolver

/** One line of a chat as it will be sent: who said it, and what. */
data class ChatLine(val fromOperator: Boolean, val text: String)

/** The church's own words a sent chat must not carry: its profiles', outputs' and screens' names. */
data class ChatNames(
    val profiles: List<String> = emptyList(),
    val outputs: List<String> = emptyList(),
)

const val MASK_ANNOUNCEMENT = "[announcement]"
const val MASK_TEXT = "[text]"
const val MASK_PROFILE = "[profile]"
const val MASK_OUTPUT = "[output]"
const val MASK_NUMBER = "[number]"

/** The most a sent chat carries; older lines go first. The contact endpoint takes about 20 KB. */
const val MAX_TRANSCRIPT_CHARS = 20_000

private const val OPERATOR_LABEL = "You"
private const val WICK_LABEL = "Wick"

private val QUOTED = Regex("""["“”«„]([^"“”«»„]+)["“”»]""")

// Three or more digits that are not part of a chapter:verse — "Psalm 119:105" stays.
private val LONG_NUMBER = Regex("""(?<![\d:])\d{3,}(?![\d:])""")

// Psalms is the one book with more than 99 chapters: "Psalm 119" alone is a reference too.
private val PSALM_CHAPTER = Regex("""(?i)\b(?:psalms?|ps)\.?\s+$""")

private const val MIN_NAME_LENGTH = 2

/**
 * [lines] with what could identify a church or a person masked, as they are previewed and sent:
 * an announcement or a page to parents becomes [MASK_ANNOUNCEMENT], quoted text [MASK_TEXT], the
 * church's own profile and output names [MASK_PROFILE] and [MASK_OUTPUT], and any run of three or
 * more digits [MASK_NUMBER]. Bible references stay — they are what Wick most needs to read.
 */
fun redactChat(lines: List<ChatLine>, names: ChatNames, context: ResolveContext = ResolveContext()): List<ChatLine> {
    val resolver = RuleIntentResolver()
    // What each announcement put on screen, so Wick's own "Put this on screen: “…”?" is masked too.
    val announced = mutableListOf<String>()
    val masked = lines.map { line ->
        if (!line.fromOperator) return@map line
        val action = (resolver.resolveNow(line.text, context) as? Resolution.Act)?.action
        val shown = action as? HelperAction.ShowAnnouncement
        if (shown == null) {
            line
        } else {
            announced += shown.text
            line.copy(text = MASK_ANNOUNCEMENT)
        }
    }
    return masked.map { line ->
        var text = line.text
        announced.filter { it.isNotBlank() }.forEach { text = text.replace(it, MASK_ANNOUNCEMENT, ignoreCase = true) }
        text = QUOTED.replace(text) { m ->
            val inside = m.groups[1]!!.range
            if (m.groupValues[1].isMasked()) {
                m.value
            } else {
                // The quote marks stay, so the line still reads as Wick wrote it.
                m.value.replaceRange(inside.first - m.range.first, inside.last - m.range.first + 1, MASK_TEXT)
            }
        }
        text = maskNames(text, names.profiles, MASK_PROFILE)
        text = maskNames(text, names.outputs, MASK_OUTPUT)
        text = maskNumbers(text)
        line.copy(text = text)
    }
}

/** [lines] as the plain-text transcript that is sent, one line per turn, kept under [maxChars]. */
fun transcriptOf(lines: List<ChatLine>, maxChars: Int = MAX_TRANSCRIPT_CHARS): String {
    val rendered = lines.map { "${if (it.fromOperator) OPERATOR_LABEL else WICK_LABEL}: ${it.text.replace('\n', ' ')}" }
    val kept = ArrayDeque<String>()
    var size = 0
    for (line in rendered.asReversed()) {
        if (size + line.length + 1 > maxChars) break
        kept.addFirst(line)
        size += line.length + 1
    }
    return kept.joinToString("\n")
}

/** The most a note written beside a sent chat may hold. */
const val MAX_NOTE_CHARS = 2_000

private const val NOTE_LABEL = "Note"

/**
 * The message a sent chat carries: the operator's own [note] first, when they wrote one, then the
 * masked [transcript]. The note is sent as written — they typed it knowing it would be sent.
 */
fun chatMessage(note: String, transcript: String): String {
    val written = note.trim().take(MAX_NOTE_CHARS)
    return if (written.isEmpty()) transcript else "$NOTE_LABEL: $written\n\n$transcript"
}

private fun String.isMasked(): Boolean = trim().let { it.startsWith("[") && it.endsWith("]") }

/** Every whole-word mention of one of [names] in [text], longest name first, as [mask]. */
private fun maskNames(text: String, names: List<String>, mask: String): String =
    names.map { it.trim() }
        .filter { it.length >= MIN_NAME_LENGTH }
        .distinct()
        .sortedByDescending { it.length }
        .fold(text) { acc, name ->
            Regex("""(?i)(?<![\p{L}\d])${Regex.escape(name)}(?![\p{L}\d])""").replace(acc, mask)
        }

private fun maskNumbers(text: String): String = LONG_NUMBER.replace(text) { m ->
    val before = text.substring(0, m.range.first)
    if (PSALM_CHAPTER.containsMatchIn(before)) m.value else MASK_NUMBER
}
