package org.churchpresenter.app.churchpresenter.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.churchpresenter.core.models.io.writeTextAtomically
import java.io.File

/**
 * The pictures and presentation slides the operator has hidden (#676), remembered per folder and per
 * presentation file so they are still hidden the next time it is opened.
 *
 * A picture is kept by its file name within its folder, so reordering the folder or a file arriving
 * in it leaves the rest alone. A slide is kept by its index within its file.
 *
 * Kept in a file of its own rather than in `AppSettings`: it is a record of content, not a setting,
 * and grows with every folder and deck ever opened.
 *
 * @param file where it is kept -- a parameter so a test can keep it in a temp directory.
 */
class HiddenItemsStore(
    private val file: File = File(System.getProperty("user.home"), ".churchpresenter/hidden_items.json"),
) {
    @Serializable
    private data class Stored(
        val pictures: Map<String, Set<String>> = emptyMap(),
        val slides: Map<String, Set<Int>> = emptyMap(),
    )

    private val json = Json { ignoreUnknownKeys = true }
    private var stored: Stored = load()

    /** The names of the pictures hidden in [folderPath]. */
    fun hiddenPictures(folderPath: String): Set<String> = stored.pictures[folderPath].orEmpty()

    /** The indexes of the slides hidden in the presentation at [filePath]. */
    fun hiddenSlides(filePath: String): Set<Int> = stored.slides[filePath].orEmpty()

    fun setHiddenPictures(folderPath: String, names: Set<String>) {
        stored = stored.copy(pictures = stored.pictures.with(folderPath, names))
        save()
    }

    fun setHiddenSlides(filePath: String, indexes: Set<Int>) {
        stored = stored.copy(slides = stored.slides.with(filePath, indexes))
        save()
    }

    /** [key] set to [value], or removed when [value] is empty, so shown-again folders leave no trace. */
    private fun <T> Map<String, Set<T>>.with(key: String, value: Set<T>): Map<String, Set<T>> =
        if (value.isEmpty()) this - key else this + (key to value)

    private fun load(): Stored = try {
        if (file.exists()) json.decodeFromString(Stored.serializer(), file.readText()) else Stored()
    } catch (_: Exception) {
        // A damaged file costs the hidden marks, never the folder or the deck.
        Stored()
    }

    private fun save() {
        try {
            file.parentFile?.mkdirs()
            file.writeTextAtomically(json.encodeToString(Stored.serializer(), stored))
        } catch (_: Exception) {
            // Not being able to remember a hidden mark is not worth interrupting a service for.
        }
    }
}

/**
 * The index to move to from [from], stepping by [step] (+1 or -1) over [count] items and passing
 * over the [hidden] ones, or null when there is nowhere to go.
 *
 * With [wrap], running off either end carries on from the other; without it, the end is where it
 * stops. [from] itself is never the answer, hidden or not -- a move is a move.
 */
fun nextVisibleIndex(from: Int, step: Int, count: Int, hidden: Set<Int>, wrap: Boolean): Int? {
    // Every other index, in the order a move would visit them; the first shown one is the answer.
    val order = (1..count).asSequence()
        .map { from + step * it }
        .map { if (wrap) Math.floorMod(it, count) else it }
        .takeWhile { it in 0 until count }
    return order.firstOrNull { it != from && it !in hidden }
}

/** The first index of [count] items that is not [hidden], or 0 when every one is. */
fun firstVisibleIndex(count: Int, hidden: Set<Int>): Int =
    (0 until count).firstOrNull { it !in hidden } ?: 0
