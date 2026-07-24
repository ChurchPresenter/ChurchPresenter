package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import io.github.alexzhirkevich.compottie.assets.LottieFontManager
import io.github.alexzhirkevich.compottie.assets.LottieFontSpec
import presentation.engine.fonts.SlideFontRegistry

/**
 * Resolves the fonts named by lower-third lottie files (declared in the JSON by family name
 * only — no embedded font data). Without this, Compottie has no way to find "Poppins" etc.
 * and silently renders text with its default typeface — the cause of blocky/wrong
 * lower-third lettering.
 *
 * Resolution order:
 *  1. the TTFs bundled under `resources/fonts/` (the families ChurchPresenter-LottieGen
 *     offers; the set and names mirror its `FontRegistry`, which loads the same classpath
 *     resources for text measurement), then
 *  2. installed system fonts, located by filename in the platform font directories — covers
 *     lotties generated with a system family (e.g. Verdana) or made elsewhere.
 *
 * Pass as `fontManager` to every `rememberLottiePainter` call that can draw text.
 */
object LottieFonts : LottieFontManager {

    /** familyName -> (regular file, bold file or null when the family has no bold cut) */
    private val fontFiles = mapOf(
        "Open Sans" to ("OpenSans-Regular.ttf" to "OpenSans-Bold.ttf"),
        "Poppins" to ("Poppins-Regular.ttf" to "Poppins-Bold.ttf"),
        "Raleway" to ("Raleway-Regular.ttf" to "Raleway-Bold.ttf"),
        "Anonymous Pro" to ("AnonymousPro-Regular.ttf" to "AnonymousPro-Bold.ttf"),
        "Patua One" to ("PatuaOne-Regular.ttf" to null),
        "Lora" to ("Lora-Regular.ttf" to "Lora-Bold.ttf"),
        "Abril Fatface" to ("AbrilFatface-Regular.ttf" to null),
        "Cookie" to ("Cookie-Regular.ttf" to null),
        "Oleo Script" to ("OleoScript-Regular.ttf" to "OleoScript-Bold.ttf"),
        "Kalam" to ("Kalam-Regular.ttf" to "Kalam-Bold.ttf"),
        "Fredoka One" to ("FredokaOne-Regular.ttf" to null)
    )

    private val cache = mutableMapOf<String, Font?>()

    /**
     * Classpath resource paths of every bundled font file — registered into the slide-rendering
     * font registry at startup (see main.kt) so POI resolves the same families Lottie does.
     */
    fun bundledFontResources(): List<String> =
        fontFiles.values.flatMap { (regular, bold) -> listOfNotNull(regular, bold) }
            .map { "/fonts/$it" }

    internal fun wantsBold(weight: FontWeight, name: String): Boolean =
        weight >= FontWeight.SemiBold || name.endsWith("-Bold")

    override suspend fun font(font: LottieFontSpec): Font? {
        val wantBold = wantsBold(font.weight, font.name)
        val key = "${font.family}|$wantBold|${font.style}"
        synchronized(cache) { if (cache.containsKey(key)) return cache[key] }
        val resolved = loadFont(font.family, wantBold, font.style)
        synchronized(cache) { cache[key] = resolved }
        return resolved
    }

    internal fun loadFont(family: String, wantBold: Boolean, style: FontStyle): Font? {
        val bytes = bundledFontBytes(family, wantBold)
            ?: systemFontBytes(family, wantBold)
            ?: return null
        val weight = if (wantBold) FontWeight.Bold else FontWeight.Normal
        return Font(identity = "$family-$weight-$style", data = bytes, weight = weight, style = style)
    }

    internal fun bundledFontBytes(family: String, wantBold: Boolean): ByteArray? {
        val (regularFile, boldFile) = fontFiles[family] ?: return null
        // Families without a bold cut serve the regular data declared at the requested weight,
        // so the renderer synthesizes the heavier stroke instead of falling back entirely.
        val fileName = if (wantBold && boldFile != null) boldFile else regularFile
        return LottieFonts::class.java
            .getResourceAsStream("/fonts/$fileName")
            ?.use { it.readBytes() }
    }

    // ── System font lookup (fallback for families not bundled with the app) ──────

    /**
     * Finds an installed font file via the shared slide-rendering font index
     * ([SlideFontRegistry.findSystemFontFile] — same platform directories and filename patterns
     * this object used to scan itself). A bold request falls back to the regular cut (the
     * requested FontWeight then synthesizes the heavier stroke).
     */
    internal fun systemFontBytes(family: String, wantBold: Boolean): ByteArray? =
        SlideFontRegistry.findSystemFontFile(family, wantBold)?.readBytes()
}
