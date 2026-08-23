package org.churchpresenter.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.GraphicsEnvironment

/**
 * The installed font families, enumerated once per process.
 *
 * AWT's first `availableFontFamilyNames` call walks every font directory on the machine, and nine
 * call sites did it inline in composition — so every settings dialog open paid for it again, on the
 * UI thread, before the dialog could paint. The set cannot change while the app runs (the bundled
 * faces are registered once at startup), so one snapshot serves every picker.
 *
 * Warmed from `main()` after `SlideFontRegistry` registers the bundled faces, so those are in the
 * snapshot; [rememberSystemFonts] covers the cold case without blocking.
 */
object SystemFonts {

    @Volatile
    private var families: List<String>? = null

    private val lock = Any()

    /** Blocking — never call this from composition. Use [rememberSystemFonts] there. */
    fun families(): List<String> {
        families?.let { return it }
        return synchronized(lock) {
            families ?: enumerate().also { families = it }
        }
    }

    /** The snapshot if one has been taken, else null. A field read, so safe in composition. */
    fun cached(): List<String>? = families

    private fun enumerate(): List<String> =
        GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.sortedBy { it.lowercase() }

    /** Drops the snapshot so the next [families] call re-enumerates. Tests only. */
    internal fun reset() {
        synchronized(lock) { families = null }
    }
}

/**
 * The installed font families, empty for the frames before a cold enumeration lands.
 *
 * Warm — which it is in the running app, since startup takes the snapshot long before any picker
 * can be opened — this returns the full list on the first frame and never recomposes.
 */
@Composable
fun rememberSystemFonts(): List<String> {
    var fonts by remember { mutableStateOf(SystemFonts.cached().orEmpty()) }
    LaunchedEffect(Unit) {
        if (fonts.isEmpty()) fonts = withContext(Dispatchers.IO) { SystemFonts.families() }
    }
    return fonts
}
