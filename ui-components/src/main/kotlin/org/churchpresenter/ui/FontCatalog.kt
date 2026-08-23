package org.churchpresenter.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import java.util.concurrent.ConcurrentHashMap

/**
 * The installed families described: what script each one covers, what it is shaped like, and
 * whether it is one worth putting on a screen.
 *
 * **Coverage is measured through Skia, not AWT.** `java.awt.Font.canDisplayUpTo` answers *true* for
 * every family on macOS — measured here, 186 of 186 claimed both Cyrillic and Hebrew, Apple Braille
 * and Zapfino included — because the CFont behind each one falls back to the system cascade. Skia's
 * typeface is the one Compose actually renders with, so asking it whether the family has the glyph
 * is both accurate (66 of 180 Cyrillic, 12 Hebrew on the same machine) and the same answer the
 * audience will see.
 *
 * The scan costs ~120ms for 180 families, so it runs off the UI thread and is kept for the process
 * — the installed set cannot change while the app runs. [rememberFontCatalog] hands the picker the
 * unmeasured description in the meantime rather than an empty list.
 */
object FontCatalog {

    private const val CYRILLIC_PROBE = 'Я'
    private const val CYRILLIC_PROBE_LOWER = 'ж'
    private const val HEBREW_PROBE = 'א'
    private const val LATIN_NARROW = "i"
    private const val LATIN_WIDE = "W"
    private const val PROBE_SIZE = 12f

    /**
     * One entry per family, measured once and kept for the process — the installed set cannot
     * change while the app runs. Per family rather than per list: the canvas asks about a different
     * set from the settings dialog, and a snapshot keyed by the whole list would re-measure both.
     */
    private val measured = ConcurrentHashMap<String, FontFace>()

    /**
     * Families a text picker has no business offering: icon and dingbat sets, and the internal
     * faces the OS ships for its own chrome. A family already in use is never hidden — someone who
     * set Wingdings deliberately must still see what they picked.
     */
    private val HIDDEN_FAMILIES = setOf(
        "apple braille", "apple color emoji", "apple symbols", "bodoni ornaments", "hololens mdl2 assets",
        "lastresort", "marlett", "ms outlook", "ms reference specialty", "mt extra", "segoe fluent icons",
        "segoe mdl2 assets", "segoe ui emoji", "segoe ui symbol", "symbol", "webdings", "wingdings",
        "wingdings 2", "wingdings 3", "zapf dingbats",
    )

    /** Name fragments that settle a family's shape where the glyphs cannot. Order matters below. */
    private val MONO_HINTS = listOf("mono", "courier", "consolas", "menlo", "monaco", "typewriter", "code")
    private val SERIF_HINTS = listOf(
        "serif", "times", "georgia", "garamond", "palatino", "baskerville", "book", "cambria",
        "constantia", "didot", "charter", "century", "caslon", "bodoni", "minion", "roman",
        "playfair", "merriweather", "utopia", "sitka", "rockwell", "slab", "athelas", "iowan",
    )
    private val DISPLAY_HINTS = listOf(
        "black", "impact", "papyrus", "comic", "chalk", "marker", "script", "brush", "hand",
        "display", "stencil", "showcard", "broadway", "cooper", "algerian", "bauhaus", "harrington",
        "curlz", "jokerman", "zapfino", "chancery", "engraved", "copperplate", "gabriola",
        "phosphate", "trattatello", "party", "savoye", "snell",
    )

    /**
     * The families worth reaching for on a projector: plain, evenly weighted, and installed nearly
     * everywhere. Legibility across a hall is the criterion — a display face reads as a poster and a
     * hairline serif disappears past the third row.
     */
    private val RECOMMENDED = setOf(
        "arial", "avenir", "avenir next", "calibri", "dejavu sans", "fira sans", "franklin gothic medium",
        "futura", "georgia", "gill sans", "helvetica", "helvetica neue", "inter", "lato",
        "liberation sans", "montserrat", "myriad pro", "noto sans", "open sans", "pt sans", "roboto",
        "segoe ui", "source sans pro", "tahoma", "trebuchet ms", "ubuntu", "verdana",
    )

    /** Blocking — never call this from composition. [rememberFontCatalog] is what the UI uses. */
    fun snapshot(families: List<String>, keep: String = ""): FontCatalogSnapshot =
        build(families, keep, measured = true) { name -> measured.computeIfAbsent(name, ::measure) }

    /** The description available without the glyph scan: shapes and recommendations, no coverage. */
    fun unmeasuredSnapshot(families: List<String>, keep: String = ""): FontCatalogSnapshot =
        build(families, keep, measured = false, describe = ::unmeasured)

    private fun build(
        families: List<String>,
        keep: String,
        measured: Boolean,
        describe: (String) -> FontFace,
    ): FontCatalogSnapshot {
        val visible = families.filter { !isHidden(it) || it.equals(keep, ignoreCase = true) }
        return FontCatalogSnapshot(visible.map(describe), families.size - visible.size, measured)
    }

    /** What can be said about a family without opening it: its shape, and whether to lead with it. */
    private fun unmeasured(name: String) = FontFace(
        name = name,
        category = categoryOf(name, monospaced = null),
        cyrillic = false,
        hebrew = false,
        recommended = isRecommended(name),
    )

    fun isHidden(name: String): Boolean =
        name.startsWith(".") || name.startsWith("#") || name.lowercase() in HIDDEN_FAMILIES

    internal fun isRecommended(name: String): Boolean = name.lowercase() in RECOMMENDED

    /**
     * [monospaced] as Skia measured it, or null where it could not be — a family with no Latin
     * glyphs measures "i" and "W" as the same missing-glyph box and would read as monospaced.
     */
    internal fun categoryOf(name: String, monospaced: Boolean?): FontCategory {
        val lower = name.lowercase()
        return when {
            monospaced == true || MONO_HINTS.any { it in lower } -> FontCategory.MONO
            DISPLAY_HINTS.any { it in lower } -> FontCategory.DISPLAY
            SERIF_HINTS.any { it in lower } -> FontCategory.SERIF
            else -> FontCategory.SANS
        }
    }

    /**
     * A family Skia cannot produce a typeface for is described as far as its name allows.
     *
     * **Nothing here is closed.** `matchFamilyStyle` hands back a reference to a typeface Skia is
     * caching and Compose is rendering the very same family with — the picker draws every name in
     * its own face — so closing it would drop the refcount on a live object rather than free a
     * private one. Skiko's cleaner releases these when they fall out of use; there are a few hundred
     * of them, once per process.
     */
    private fun measure(name: String): FontFace {
        val face = FontMgr.default.matchFamilyStyle(name, FontStyle.NORMAL) ?: return unmeasured(name)
        val hasLatin = face.getUTF32Glyph(LATIN_NARROW[0].code) != NO_GLYPH &&
            face.getUTF32Glyph(LATIN_WIDE[0].code) != NO_GLYPH
        val monospaced = if (!hasLatin) {
            null
        } else {
            val probe = org.jetbrains.skia.Font(face, PROBE_SIZE)
            probe.measureTextWidth(LATIN_NARROW) == probe.measureTextWidth(LATIN_WIDE)
        }
        return FontFace(
            name = name,
            category = categoryOf(name, monospaced),
            cyrillic = face.getUTF32Glyph(CYRILLIC_PROBE.code) != NO_GLYPH &&
                face.getUTF32Glyph(CYRILLIC_PROBE_LOWER.code) != NO_GLYPH,
            hebrew = face.getUTF32Glyph(HEBREW_PROBE.code) != NO_GLYPH,
            recommended = isRecommended(name),
        )
    }

    /** Drops the scan so the next [snapshot] measures again. Tests only. */
    internal fun reset() {
        measured.clear()
    }

    private const val NO_GLYPH: Short = 0
}

/**
 * The catalog for [families] — described immediately, measured a moment later.
 *
 * The first frame carries shapes and recommendations only, so the picker opens without waiting on
 * the glyph scan; the badges arrive when it lands. [keep] is the family currently in use, which is
 * offered even when it is one of the hidden ones.
 */
@Composable
fun rememberFontCatalog(families: List<String>, keep: String = ""): FontCatalogSnapshot {
    var snapshot by remember(families, keep) {
        mutableStateOf(FontCatalog.unmeasuredSnapshot(families, keep))
    }
    LaunchedEffect(families, keep) {
        if (!snapshot.measured && families.isNotEmpty()) {
            snapshot = withContext(Dispatchers.IO) { FontCatalog.snapshot(families, keep) }
        }
    }
    return snapshot
}
