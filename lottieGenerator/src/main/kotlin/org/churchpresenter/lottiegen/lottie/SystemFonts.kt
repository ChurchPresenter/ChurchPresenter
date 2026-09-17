package org.churchpresenter.lottiegen.lottie

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
 * The installed font families, enumerated once per process — the module-local counterpart of
 * `composeApp`'s `utils/SystemFonts.kt`. Not shared with it: `:lottieGenerator` cannot depend on
 * `:composeApp` (the dependency runs the other way), so the two pickers that already reach a
 * machine-wide font list — the embedded Bible Lower Third Band picker's host font picker, via
 * `rememberSystemFonts()` in `composeApp` — and the two `:lottieGenerator`-only dropdowns using
 * *this* object each enumerate independently. Both land on the same AWT primitive either way.
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

    /**
     * The machine's font families, or none when AWT's font stack will not come up at all.
     *
     * [Throwable] rather than [Exception]: a native font manager that cannot load raises an
     * `ExceptionInInitializerError`, an [Error], and a machine in that state has no fonts to
     * offer either way, so an empty list is the truthful answer and a crash is not.
     */
    internal fun enumerate(
        probe: () -> Array<String> = { GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames },
    ): List<String> = try {
        probe().sortedBy { it.lowercase() }
    } catch (_: Throwable) {
        emptyList()
    }

    /** Drops the snapshot so the next [families] call re-enumerates. Tests only. */
    internal fun reset() {
        synchronized(lock) { families = null }
    }
}

/** The installed font families, empty for the frame before the (cheap, cached) enumeration lands. */
@Composable
fun rememberSystemFonts(): List<String> {
    var fonts by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(Unit) {
        if (fonts.isEmpty()) fonts = withContext(Dispatchers.IO) { SystemFonts.families() }
    }
    return fonts
}
