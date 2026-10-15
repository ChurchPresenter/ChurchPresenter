package org.churchpresenter.sharedui.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontVariation
import org.jetbrains.skia.Typeface
import org.jetbrains.skia.paragraph.TypefaceFontProvider

/**
 * Draws [content]'s system-font text in fonts bundled with the tests, so a screenshot is the same
 * picture on any Mac.
 *
 * The app's text is in the system font: on macOS Compose asks for `.AppleSystemUIFont`, and
 * `.AppleSystemUIFontMonospaced` for [FontFamily.Monospace]. Apple revises both with macOS releases,
 * and two Macs on different versions draw the same text a few grey levels apart along every
 * letter's edge -- enough to fail [CHANGE_THRESHOLD] on any image with text in it. Inside this,
 * those names answer with Inter and JetBrains Mono (`src/testFixtures/resources/fonts`, SIL OFL
 * 1.1, licences beside them) ahead of the system's own. Each platform's own name is the one taken
 * over (`Noto Sans` on Linux, `Segoe UI` on Windows), so the suites draw the same fonts in CI too;
 * every other name -- a font a look or a picker chooses -- is untouched.
 *
 * **Every screenshot is taken inside this**, and [captureTo] fails one that is not, by looking for
 * the marker it leaves in the semantics tree. A popup is its own compose root but inherits the
 * fonts through its composition; the marker is looked for across every root.
 */
@Composable
fun PinnedFonts(content: @Composable () -> Unit) {
    val resolver = remember { pinnedFontFamilyResolver() }
    CompositionLocalProvider(LocalFontFamilyResolver provides resolver) {
        // Passes its constraints straight through, so wrapping a suite's content moves nothing.
        Box(Modifier.semantics { this[PinnedFontsMarker] = Unit }, propagateMinConstraints = true) {
            content()
        }
    }
}

/** Left in the semantics tree by [PinnedFonts], for [captureTo] to find. */
internal val PinnedFontsMarker = SemanticsPropertyKey<Unit>("PinnedFonts")

/** A resolver whose font collection looks in [BundledFaces.provider] before the system's fonts. */
private fun pinnedFontFamilyResolver(): FontFamily.Resolver =
    ComposeFontCollection.resolverLookingFirstIn(BundledFaces.provider)

/** The bundled faces, one instance per weight of each variable font, under this platform's system names. */
private object BundledFaces {
    private val SANS = ComposeFontCollection.systemName(FontFamily.SansSerif)
    private val MONO = ComposeFontCollection.systemName(FontFamily.Monospace)
    private val WEIGHTS = (100..900 step 100)

    val provider: FontMgr by lazy {
        TypefaceFontProvider().apply {
            register("InterVariable.ttf", SANS)
            register("InterVariable-Italic.ttf", SANS)
            register("JetBrainsMonoVariable.ttf", MONO)
        }
    }

    private fun TypefaceFontProvider.register(file: String, alias: String) {
        val bytes = BundledFaces::class.java.getResourceAsStream("/fonts/$file")?.use { it.readBytes() }
            ?: error("fonts/$file is missing from the test fixtures")
        val variable = FontMgr.default.makeFromData(Data.makeFromBytes(bytes))
            ?: error("fonts/$file is not a font Skia can read")
        WEIGHTS.forEach { weight -> registerTypeface(variable.weighted(weight), alias) }
    }

    private fun Typeface.weighted(weight: Int): Typeface = makeClone(FontVariation("wght", weight.toFloat()))
}
