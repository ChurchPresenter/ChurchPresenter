package presentation.engine.fonts

import org.apache.poi.common.usermodel.fonts.FontInfo
import org.apache.poi.sl.draw.DrawFontManager
import org.apache.poi.sl.draw.DrawFontManagerDefault
import java.awt.Font
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Font resolution for slide rendering. Fixes the biggest static-fidelity gap of the old
 * pipeline: POI resolved fonts through plain `java.awt` with zero registration, so any family
 * not already known to the JVM was silently substituted with the default typeface.
 *
 * Three mechanisms, in order:
 *  1. [registerFontStream] — bundled app fonts are registered into the JVM's
 *     [GraphicsEnvironment] at startup so `new Font(family, …)` resolves them.
 *  2. [initialize] — platform font directories are scanned and any font file whose family the
 *     JVM does not already know is registered too. This covers per-user installed fonts
 *     (e.g. `%LOCALAPPDATA%\Microsoft\Windows\Fonts`, `~/.fonts`) that some JVMs miss.
 *  3. [drawFontManager] — installed as POI's `Drawable.FONT_HANDLER`: when a slide asks for a
 *     family that is not available even after 1+2, a curated substitution table picks the
 *     closest installed family (metric-compatible first, bundled app font as the safety net)
 *     instead of letting AWT fall back to Dialog.
 */
object SlideFontRegistry {

    private val initialized = AtomicBoolean(false)

    /** Lower-cased family name → exact-cased family name, for every family the JVM can resolve. */
    private val availableFamilies = ConcurrentHashMap<String, String>()

    /** Normalized file name (sans extension) → font file, from the system-directory scan. */
    private val systemFontFileIndex = ConcurrentHashMap<String, File>()

    /**
     * Office-font substitution preferences. First installed candidate wins; every list ends in a
     * family bundled with the app (registered via [registerFontStream]) so slides never fall back
     * to the JVM's default Dialog face.
     */
    private val substitutionTable: Map<String, List<String>> = mapOf(
        "calibri" to listOf("Carlito", "Segoe UI", "Open Sans", "Arial"),
        "calibri light" to listOf("Carlito", "Segoe UI Light", "Open Sans", "Arial"),
        "cambria" to listOf("Caladea", "Georgia", "Lora", "Times New Roman"),
        "segoe ui" to listOf("Open Sans", "Arial", "Helvetica Neue"),
        "arial" to listOf("Helvetica", "Liberation Sans", "Open Sans"),
        "helvetica" to listOf("Arial", "Liberation Sans", "Open Sans"),
        "helvetica neue" to listOf("Helvetica", "Arial", "Open Sans"),
        "times new roman" to listOf("Liberation Serif", "Times", "Georgia", "Lora"),
        "georgia" to listOf("Times New Roman", "Lora"),
        "courier new" to listOf("Liberation Mono", "Courier", "Anonymous Pro"),
        "consolas" to listOf("Menlo", "Liberation Mono", "Anonymous Pro"),
        "comic sans ms" to listOf("Kalam", "Open Sans"),
        "tahoma" to listOf("Verdana", "DejaVu Sans", "Open Sans"),
        "verdana" to listOf("Tahoma", "DejaVu Sans", "Open Sans"),
        "trebuchet ms" to listOf("Verdana", "Open Sans"),
        "century gothic" to listOf("Avant Garde", "Poppins", "Open Sans"),
        "gill sans" to listOf("Gill Sans MT", "Open Sans"),
        "impact" to listOf("Haettenschweiler", "Anton", "Open Sans")
    )

    /** The ultimate fallback family when nothing else matches (bundled with the app). */
    private const val DEFAULT_FAMILY = "Open Sans"

    private val systemFontDirs: List<File> by lazy {
        val os = System.getProperty("os.name", "").lowercase()
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

    /** POI font handler — set as the `Drawable.FONT_HANDLER` rendering hint before slide draws. */
    val drawFontManager: DrawFontManager = SubstitutingFontManager()

    /**
     * Registers one font (TTF/OTF stream) into the JVM. Returns the family name on success.
     * Safe to call repeatedly; registration of an already-known family is a no-op in AWT.
     */
    fun registerFontStream(stream: InputStream): String? {
        return try {
            val font = stream.use { Font.createFont(Font.TRUETYPE_FONT, it) }
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font)
            availableFamilies[font.family.lowercase()] = font.family
            font.family
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Builds the family index and (optionally) scans platform font directories, registering any
     * font file whose family the JVM does not already resolve. Idempotent; call once at startup
     * on a background thread — a directory scan can take a couple of seconds on font-heavy
     * systems.
     */
    fun initialize(scanSystemDirs: Boolean = true) {
        if (!initialized.compareAndSet(false, true)) return
        refreshAvailableFamilies()
        if (!scanSystemDirs) return
        for (dir in systemFontDirs) {
            if (!dir.isDirectory) continue
            dir.walkTopDown().maxDepth(3)
                .filter { it.isFile && it.extension.lowercase() in setOf("ttf", "otf", "ttc") }
                .forEach { fontFile ->
                    systemFontFileIndex.putIfAbsent(normalizeName(fontFile.nameWithoutExtension), fontFile)
                    registerFileIfUnknown(fontFile)
                }
        }
    }

    fun isFamilyAvailable(family: String): Boolean {
        if (availableFamilies.isEmpty()) refreshAvailableFamilies()
        return availableFamilies.containsKey(family.trim().lowercase())
    }

    /**
     * Resolves the family a slide should actually render with: the requested family when
     * installed, else the first installed substitution candidate, else [DEFAULT_FAMILY] when
     * available, else the JVM's logical SansSerif.
     */
    fun resolveFamily(requested: String): String {
        val key = requested.trim().lowercase()
        availableFamilies[key]?.let { return it }
        substitutionTable[key]?.forEach { candidate ->
            availableFamilies[candidate.lowercase()]?.let { return it }
        }
        availableFamilies[DEFAULT_FAMILY.lowercase()]?.let { return it }
        return Font.SANS_SERIF
    }

    /**
     * Finds an installed font *file* by common naming patterns — used by the app's Lottie font
     * loader, which needs raw bytes rather than an AWT family. "Verdana Bold.ttf" (macOS),
     * "verdanab.ttf" (Windows), "Verdana-Regular.ttf" (Linux). A bold request falls back to the
     * regular cut.
     */
    fun findSystemFontFile(family: String, wantBold: Boolean): File? {
        if (systemFontFileIndex.isEmpty()) {
            // Build just the filename index without registering anything (cheap).
            for (dir in systemFontDirs) {
                if (!dir.isDirectory) continue
                dir.walkTopDown().maxDepth(3)
                    .filter { it.isFile && it.extension.lowercase() in setOf("ttf", "otf", "ttc") }
                    .forEach { systemFontFileIndex.putIfAbsent(normalizeName(it.nameWithoutExtension), it) }
            }
        }
        val base = normalizeName(family)
        if (base.isEmpty()) return null
        val boldKeys = listOf("${base}bold", "${base}bd", "${base}b")
        val regularKeys = listOf(base, "${base}regular")
        val keys = if (wantBold) boldKeys + regularKeys else regularKeys
        for (key in keys) {
            systemFontFileIndex[key]?.let { return it }
        }
        return null
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun refreshAvailableFamilies() {
        try {
            GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.forEach {
                availableFamilies.putIfAbsent(it.lowercase(), it)
            }
        } catch (_: Exception) {
            // Headless environments without fontconfig can throw; the registry then only knows
            // explicitly registered fonts, which is still correct behavior.
        }
    }

    private fun registerFileIfUnknown(fontFile: File) {
        try {
            val fonts = Font.createFonts(fontFile)
            for (font in fonts) {
                if (!availableFamilies.containsKey(font.family.lowercase())) {
                    GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font)
                    availableFamilies[font.family.lowercase()] = font.family
                }
            }
        } catch (_: Exception) {
            // Broken/unsupported font file — skip it.
        }
    }

    private fun normalizeName(name: String): String =
        name.lowercase().filter { it.isLetterOrDigit() }

    /**
     * POI font handler that swaps unavailable families for the best installed substitute before
     * POI builds its AWT font. Everything else defers to POI's default behavior.
     */
    private class SubstitutingFontManager : DrawFontManagerDefault() {
        override fun getMappedFont(graphics: Graphics2D, fontInfo: FontInfo): FontInfo {
            val mapped = super.getMappedFont(graphics, fontInfo)
            val typeface = mapped.typeface ?: return mapped
            if (isFamilyAvailable(typeface)) return mapped
            val substitute = resolveFamily(typeface)
            return object : FontInfo by mapped {
                override fun getTypeface(): String = substitute
            }
        }
    }
}
