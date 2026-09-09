package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.runtime.mutableStateListOf
import kotlinx.serialization.json.Json
import org.churchpresenter.core.models.text.TextBackdrop

/**
 * The looks this operator has actually used, newest first -- what the presets row offers.
 *
 * The row used to be four looks written into the source. Those are fine as a starting point and
 * wrong as the whole list: a church settles on one backdrop and uses it every week, and having to
 * rebuild it from a preset that is nearly right is the work the row exists to save. So the stored
 * looks come first and [TEXT_BACKDROP_PRESETS] fills whatever slots are left, dropping off the end
 * one at a time as the operator's own list grows past [MAX].
 *
 * Written to disk the way [RecentColors] is -- one small JSON file under the app's own directory,
 * failing silently, because losing a preset row is not worth an error dialog over.
 *
 * Deduplicated by content, not by when it was saved: pressing Save and then closing the dialog on
 * the same look is one entry, and re-picking a look already in the row moves it to the front rather
 * than storing it twice.
 */
internal object SavedTextBackdrops {
    /** Two rows of four in the dialog, which is as much history as fits without a scroll. */
    const val MAX = 8

    private val file = java.io.File(System.getProperty("user.home"), ".churchpresenter/saved_backdrops.json")

    val looks = mutableStateListOf<TextBackdrop>()

    init { load() }

    /**
     * Puts [backdrop] at the front, unless there is nothing to draw.
     *
     * An empty backdrop is the Off state, not a look: saving it would fill the row with blank
     * swatches that restyle nothing when picked.
     */
    fun add(backdrop: TextBackdrop) {
        if (backdrop.isEmpty) return
        looks.remove(backdrop)
        looks.add(0, backdrop)
        while (looks.size > MAX) looks.removeLast()
        save()
    }

    internal fun load() {
        try {
            if (file.exists()) {
                val json = Json { ignoreUnknownKeys = true }
                val stored = json.decodeFromString<List<TextBackdrop>>(file.readText())
                looks.clear()
                looks.addAll(stored.filterNot { it.isEmpty }.take(MAX))
            }
        } catch (_: Exception) {
        }
    }

    private fun save() {
        try {
            file.parentFile?.mkdirs()
            val json = Json { encodeDefaults = true }
            file.writeText(json.encodeToString(looks.toList()))
        } catch (_: Exception) {
        }
    }
}
