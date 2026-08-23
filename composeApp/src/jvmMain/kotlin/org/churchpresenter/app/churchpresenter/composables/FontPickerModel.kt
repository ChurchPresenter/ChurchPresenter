package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.churchpresenter.app.churchpresenter.utils.FontFace

/** Which heading a run of families sits under. */
enum class FontGroupKind { RECENT, RECOMMENDED, ALL, MATCHES }

/** A heading and the families under it. */
data class FontGroup(val kind: FontGroupKind, val items: List<FontFace>)

/**
 * The families picked this session, most recent first.
 *
 * Shared by every picker in the app — a font chosen for the songs is the one likely wanted for the
 * verses a moment later — and deliberately **not** persisted: it is a shortcut for the sitting a
 * service is being set up in, not a preference, and nothing about it is worth a settings migration.
 */
object RecentFonts {

    private const val KEPT = 3

    var names: List<String> by mutableStateOf(emptyList())
        private set

    fun record(name: String) {
        if (name.isBlank()) return
        names = (listOf(name) + names.filterNot { it.equals(name, ignoreCase = true) }).take(KEPT)
    }

    /** Empties the list. Tests use it to start from a known state; nothing in the app calls it. */
    fun clear() {
        names = emptyList()
    }
}

/** A script the preview can catch a family out on — the two the app's Bibles are written in. */
enum class PreviewScript { CYRILLIC, HEBREW }

private val CYRILLIC_RANGE = '\u0400'..'\u04FF'
private val HEBREW_RANGE = '\u0590'..'\u05FF'

/**
 * The scripts in [text] that [face] cannot draw.
 *
 * This is what the preview warns about: a verse in a family with no Cyrillic is not an error
 * anywhere in the app — it simply comes out of whatever fallback font the renderer finds, in front
 * of the congregation, looking nothing like the one that was picked.
 */
fun missingScripts(text: String, face: FontFace): List<PreviewScript> = buildList {
    if (!face.cyrillic && text.any { it in CYRILLIC_RANGE }) add(PreviewScript.CYRILLIC)
    if (!face.hebrew && text.any { it in HEBREW_RANGE }) add(PreviewScript.HEBREW)
}

/** The families [query] leaves, in the order they were given. */
fun filterFonts(faces: List<FontFace>, query: String): List<FontFace> {
    val needle = query.trim()
    if (needle.isEmpty()) return faces
    return faces.filter { it.name.contains(needle, ignoreCase = true) }
}

/**
 * The list as the menu shows it.
 *
 * Untouched it is three groups: what was picked this session, what is worth projecting, and
 * everything else. The moment anything is typed it collapses to one flat run of matches, because a
 * search that returns two names has nothing to group.
 */
fun groupFonts(faces: List<FontFace>, query: String, recents: List<String>): List<FontGroup> {
    val matching = filterFonts(faces, query)
    if (query.isNotBlank()) {
        return if (matching.isEmpty()) emptyList() else listOf(FontGroup(FontGroupKind.MATCHES, matching))
    }
    val byName = matching.associateBy { it.name }
    val recent = recents.mapNotNull { byName[it] }
    val recentNames = recent.map { it.name }.toSet()
    val recommended = matching.filter { it.recommended && it.name !in recentNames }
    val spokenFor = recentNames + recommended.map { it.name }
    val rest = matching.filter { it.name !in spokenFor }
    return listOfNotNull(
        FontGroup(FontGroupKind.RECENT, recent).takeIf { recent.isNotEmpty() },
        FontGroup(FontGroupKind.RECOMMENDED, recommended).takeIf { recommended.isNotEmpty() },
        FontGroup(FontGroupKind.ALL, rest).takeIf { rest.isNotEmpty() },
    )
}

/** Every family the menu is showing, in the order the arrow keys walk them. */
fun visibleFonts(groups: List<FontGroup>): List<FontFace> = groups.flatMap { it.items }

/**
 * Where the highlight lands after the list has changed under it.
 *
 * The rule is one line so that it holds for every way the list can change — typing, clearing, a
 * pick that reorders the recents: keep the family already highlighted if it survived, otherwise
 * take the first row there is.
 */
fun highlightAfterFilter(visible: List<FontFace>, current: String): String =
    when {
        visible.any { it.name == current } -> current
        else -> visible.firstOrNull()?.name.orEmpty()
    }
