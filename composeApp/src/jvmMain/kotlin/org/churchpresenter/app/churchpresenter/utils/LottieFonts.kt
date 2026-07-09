package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import io.github.alexzhirkevich.compottie.assets.LottieFontManager
import io.github.alexzhirkevich.compottie.assets.LottieFontSpec
import java.io.File

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

    override suspend fun font(font: LottieFontSpec): Font? {
        val wantBold = font.weight >= FontWeight.SemiBold || font.name.endsWith("-Bold")
        val key = "${font.family}|$wantBold|${font.style}"
        synchronized(cache) { if (cache.containsKey(key)) return cache[key] }
        val resolved = loadFont(font.family, wantBold, font.style)
        synchronized(cache) { cache[key] = resolved }
        return resolved
    }

    private fun loadFont(family: String, wantBold: Boolean, style: FontStyle): Font? {
        val bytes = bundledFontBytes(family, wantBold)
            ?: systemFontBytes(family, wantBold)
            ?: return null
        val weight = if (wantBold) FontWeight.Bold else FontWeight.Normal
        return Font(identity = "$family-$weight-$style", data = bytes, weight = weight, style = style)
    }

    private fun bundledFontBytes(family: String, wantBold: Boolean): ByteArray? {
        val (regularFile, boldFile) = fontFiles[family] ?: return null
        // Families without a bold cut serve the regular data declared at the requested weight,
        // so the renderer synthesizes the heavier stroke instead of falling back entirely.
        val fileName = if (wantBold && boldFile != null) boldFile else regularFile
        return LottieFonts::class.java
            .getResourceAsStream("/fonts/$fileName")
            ?.use { it.readBytes() }
    }

    // ── System font lookup (fallback for families not bundled with the app) ──────

    private val systemFontDirs: List<File> by lazy {
        val os = System.getProperty("os.name").lowercase()
        val home = System.getProperty("user.home")
        when {
            os.contains("mac") -> listOf(
                "/System/Library/Fonts",
                "/System/Library/Fonts/Supplemental",
                "/Library/Fonts",
                "$home/Library/Fonts"
            )
            os.contains("win") -> listOf(
                (System.getenv("WINDIR") ?: "C:\\Windows") + "\\Fonts",
                "$home\\AppData\\Local\\Microsoft\\Windows\\Fonts"
            )
            else -> listOf(
                "/usr/share/fonts",
                "/usr/local/share/fonts",
                "$home/.fonts",
                "$home/.local/share/fonts"
            )
        }.map(::File)
    }

    /** Normalized filename (sans extension) → font file; built once, first hit wins. */
    private val systemFontIndex: Map<String, File> by lazy {
        val index = mutableMapOf<String, File>()
        for (dir in systemFontDirs) {
            if (!dir.isDirectory) continue
            dir.walkTopDown().maxDepth(3)
                .filter { it.isFile && it.extension.lowercase() in setOf("ttf", "otf", "ttc") }
                .forEach { index.putIfAbsent(normalizeName(it.nameWithoutExtension), it) }
        }
        index
    }

    private fun normalizeName(name: String): String =
        name.lowercase().filter { it.isLetterOrDigit() }

    /**
     * Finds an installed font file by common naming patterns: "Verdana Bold.ttf" (macOS),
     * "verdanab.ttf"/"verdanabd.ttf" (Windows), "Verdana-Regular.ttf". A bold request falls
     * back to the regular cut (the requested FontWeight then synthesizes the heavier stroke).
     */
    private fun systemFontBytes(family: String, wantBold: Boolean): ByteArray? {
        val base = normalizeName(family)
        if (base.isEmpty()) return null
        val boldKeys = listOf("${base}bold", "${base}bd", "${base}b")
        val regularKeys = listOf(base, "${base}regular")
        val keys = if (wantBold) boldKeys + regularKeys else regularKeys
        for (key in keys) {
            systemFontIndex[key]?.let { return it.readBytes() }
        }
        return null
    }
}
