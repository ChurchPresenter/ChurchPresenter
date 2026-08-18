package songlibrary.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import java.util.ResourceBundle

/**
 * The window's own strings, in the language the app is set to.
 *
 * Snapshot state, so changing language while the window is open redraws it rather than waiting for
 * it to be closed and opened again. Standalone, the machine's locale is all there is to go on;
 * hosted inside ChurchPresenter the app sets it — see `SongLibraryWindow`.
 */
object Strings {

    private var bundle: ResourceBundle by mutableStateOf(bundleFor(Locale.getDefault()))

    fun setLocale(locale: Locale) {
        bundle = bundleFor(locale)
    }

    /**
     * `ResourceBundle.getBundle` tries the *default locale's* bundle before the base one, so asking
     * for a language this module has no bundle for answers in whatever the machine is set to — a
     * Dutch user on a Russian machine would get Russian. No-fallback lookup gives English instead.
     */
    private fun bundleFor(locale: Locale): ResourceBundle =
        ResourceBundle.getBundle(
            "songlibrary_strings",
            locale,
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES),
        )

    operator fun get(key: String): String = bundle.getString(key)

    /** [key]'s text with `%1$s`-style placeholders filled in. */
    fun format(key: String, vararg args: Any): String = String.format(bundle.getString(key), *args)
}
