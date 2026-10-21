package org.churchpresenter.sharedui.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SectionTranslation
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The song fit's search answers each probe from one measure per line wherever scaling settles it,
 * and measures for real only near the edge. Its answer has to be the one the plain search gives --
 * every line measured at every probe -- or a song would come up at another size than it always has.
 * [plainFit] is that search as it was, kept here as the oracle; every case below is held to it.
 */
class SectionFitSearchTest {

    private val measurer = TextMeasurer(
        defaultFontFamilyResolver = createFontFamilyResolver(),
        defaultDensity = Density(1f),
        defaultLayoutDirection = LayoutDirection.Ltr,
    )

    /** How many lines [plainFit] laid out, for the cost comparison. */
    private var plainMeasures = 0

    /** The plain binary search's probe, unchanged: every line of every unit measured at every size. */
    @Suppress("LongParameterList")
    private fun plainFits(
        sections: List<LyricSection>,
        style: TextStyle,
        width: Int,
        height: Int,
        indicator: Boolean,
        styleText: (String) -> AnnotatedString,
    ): (Int) -> Boolean = { size ->
        val sized = style.copy(fontSize = size.sp)
        fun measure(text: AnnotatedString) = measurer.measure(
            text = text, style = sized, constraints = Constraints(), density = Density(1f),
        ).size.also { plainMeasures++ }
        sections.all { section ->
            section.allLanguageLines().filter { it.isNotEmpty() }.all { lines ->
                val measured = lines.map { measure(styleText(it)) }
                val indicatorHeight = if (indicator) 4 + measure(AnnotatedString("* * *")).height else 0
                measured.none { it.width > width } && measured.sumOf { it.height } + indicatorHeight <= height
            }
        }
    }

    @Suppress("LongParameterList")
    private fun plainFit(
        sections: List<LyricSection>,
        style: TextStyle,
        width: Int,
        height: Int,
        indicator: Boolean = false,
        styleText: (String) -> AnnotatedString = { AnnotatedString(it) },
    ): Int {
        val fits = plainFits(sections, style, width, height, indicator, styleText)
        var low = 8
        var high = 300
        while (high - low > 1) {
            val mid = (low + high) / 2
            if (fits(mid)) low = mid else high = mid
        }
        return (low - 1).coerceAtLeast(8)
    }

    @Suppress("LongParameterList")
    private fun search(
        sections: List<LyricSection>,
        style: TextStyle,
        width: Int,
        height: Int,
        indicator: Boolean = false,
        styleText: (String) -> AnnotatedString = { AnnotatedString(it) },
    ) = SectionFitSearch(measurer, SectionFitLines.of(sections, indicator, styleText), style, width, height)

    /** Words in the style's own face: the lines the search predicts. */
    private val plainWords = listOf(
        "Amazing", "grace", "how", "sweet", "the", "sound", "That", "saved", "a", "wretch", "like", "me",
        "WWWWW", "iiiii", "Благодать", "Господня", "Łaska", "żółć", "—", "fl", "Hallelujah!", "O",
        "Ångström", "naïve", "123", "...",
    )

    /** Words a fallback face draws -- emoji among them, whose boxes do not scale -- mixed in. */
    private val mixedWords = plainWords + listOf("恩典", "奇异", "🙏", "✝️", "ﬁ", "שָׁלוֹם", "ثلاثة", "नमस्ते")

    private fun randomLine(random: Random, words: List<String>): String =
        if (random.nextInt(12) == 0) "" else List(1 + random.nextInt(9)) { words.random(random) }.joinToString(" ")

    private fun randomSection(random: Random, words: List<String>): LyricSection {
        val count = 1 + random.nextInt(6)
        return LyricSection(
            lines = List(count) { randomLine(random, words) },
            translations = List(random.nextInt(3)) {
                val lines = if (random.nextBoolean()) count else random.nextInt(4)
                SectionTranslation(lines = List(lines) { randomLine(random, words) })
            },
        )
    }

    private fun randomStyle(random: Random): Pair<TextStyle, (String) -> AnnotatedString> {
        val letterEm = listOf(0f, 0.04f, -0.02f, 0.15f).random(random)
        val wordEm = listOf(0f, 0f, 0.3f).random(random)
        val transform = listOf("none", "uppercase", "lowercase").random(random)
        val style = TextStyle(
            fontWeight = if (random.nextBoolean()) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (random.nextInt(4) == 0) FontStyle.Italic else FontStyle.Normal,
            letterSpacing = letterEm.em,
            fontFamily = listOf(FontFamily.Default, FontFamily.Serif, FontFamily.Monospace).random(random),
        )
        return style to { line: String -> styledDisplayText(line, transform, letterEm, wordEm) }
    }

    /** [count] random songs in random styles and boxes, each held to the plain search. */
    private fun assertMatchesPlainSearch(seed: Int, count: Int, words: List<String>) {
        val random = Random(seed)
        repeat(count) { case ->
            val sections = List(1 + random.nextInt(6)) { randomSection(random, words) }
            val (style, styleText) = randomStyle(random)
            val width = 120 + random.nextInt(1800)
            val height = 60 + random.nextInt(1400)
            val indicator = random.nextBoolean()
            val expected = plainFit(sections, style, width, height, indicator, styleText)
            val actual = search(sections, style, width, height, indicator, styleText).result()
            assertEquals(expected, actual, "seed $seed case $case: ${width}x$height, $style, $sections")
        }
    }

    @Test
    fun `random songs fit at the plain search's size - set one`() =
        assertMatchesPlainSearch(seed = 1, count = 20, words = plainWords)

    @Test
    fun `random songs fit at the plain search's size - set two`() =
        assertMatchesPlainSearch(seed = 2, count = 20, words = plainWords)

    @Test
    fun `random songs with fallback faces fit at the plain search's size - set one`() =
        assertMatchesPlainSearch(seed = 3, count = 12, words = mixedWords)

    @Test
    fun `random songs with fallback faces fit at the plain search's size - set two`() =
        assertMatchesPlainSearch(seed = 4, count = 12, words = mixedWords)

    /**
     * Not only the search's own path: every size it could ever probe gets the plain answer, so no
     * box can steer it onto a size where the scaled reference is wrong.
     */
    @Test
    fun `every probe size is answered as the plain search answers it`() {
        val sections = listOf(
            LyricSection(
                lines = listOf("Amazing grace how sweet the sound", "That saved a wretch like me 🙏"),
                translations = listOf(SectionTranslation(lines = listOf("Благодать Господня", "恩典 奇异"))),
            ),
        )
        listOf(400 to 300, 1600 to 900).forEach { (width, height) ->
            val style = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = 0.04.em)
            val styleText = { line: String -> styledDisplayText(line, "uppercase", 0.04f, 0.3f) }
            val plain = plainFits(sections, style, width, height, true, styleText)
            val fast = search(sections, style, width, height, true, styleText)
            (8 until 300 step 3).forEach { size ->
                assertEquals(plain(size), fast.fits(size), "size $size in ${width}x$height")
            }
        }
    }

    @Test
    fun `the corners of the range come out as the plain search's`() {
        val style = TextStyle()
        val tiny = listOf(LyricSection(lines = listOf("A line far too long for the box it is in, by miles and miles")))
        assertEquals(plainFit(tiny, style, 40, 20), search(tiny, style, 40, 20).result())
        val huge = listOf(LyricSection(lines = listOf("i")))
        assertEquals(plainFit(huge, style, 4000, 4000), search(huge, style, 4000, 4000).result())
        val noUnits = listOf(LyricSection(lines = emptyList()))
        assertEquals(plainFit(noUnits, style, 800, 600), search(noUnits, style, 800, 600).result())
    }

    @Test
    fun `a song is fitted with a fraction of the plain search's measuring`() {
        val verse = listOf("Amazing grace how sweet the sound", "That saved a wretch like me",
            "I once was lost but now am found", "Was blind but now I see")
        val chorus = listOf("My chains are gone", "I've been set free", "My God my Savior has ransomed me")
        val sections = List(4) { i -> LyricSection(lines = verse.map { "$it $i" }) } +
            List(4) { LyricSection(lines = chorus) }
        plainMeasures = 0
        val expected = plainFit(sections, TextStyle(), 1800, 1000, indicator = true)
        val fast = search(sections, TextStyle(), 1800, 1000, indicator = true)
        assertEquals(expected, fast.result())
        assertTrue(fast.measureCount * 3 < plainMeasures, "${fast.measureCount} measures against $plainMeasures")
    }

    @Test
    fun `a second output reads the first one's fit from the cache`() {
        val sections = listOf(LyricSection(lines = listOf("Cached once", "read twice")))

        // Each output builds its own style, its face looked up by name: equal, not the same object.
        fun style() = TextStyle(fontWeight = FontWeight.Bold, fontFamily = Utils.systemFontFamilyOrDefault("Serif"))
        val first = calculateAutoFitForAllSections(measurer, sections, style(), 900, 500)
        assertEquals(plainFit(sections, style(), 900, 500), first)

        val otherWindow = TextMeasurer(createFontFamilyResolver(), Density(2f), LayoutDirection.Ltr)
        val table = SectionFitLines.of(sections, false) { AnnotatedString(it) }
        val key = SectionFitCache.Key(table, style(), 900, 500, table.probe(otherWindow, style()))
        assertEquals(first, SectionFitCache.get(key))
        assertEquals(first, calculateAutoFitForAllSections(otherWindow, sections, style(), 900, 500))
    }

    @Test
    fun `a resolver that lays the probe out differently does not read the cached fit`() {
        val sections = listOf(LyricSection(lines = listOf("The same words", "in another face")))
        val style = TextStyle(fontFamily = FontFamily.Serif)
        val fitted = calculateAutoFitForAllSections(measurer, sections, style, 700, 400)
        val table = SectionFitLines.of(sections, false) { AnnotatedString(it) }
        val probe = table.probe(measurer, style)
        assertEquals(fitted, SectionFitCache.get(SectionFitCache.Key(table, style, 700, 400, probe)))
        val otherFaces = IntSize(probe.width + 1, probe.height)
        assertNull(SectionFitCache.get(SectionFitCache.Key(table, style, 700, 400, otherFaces)))
    }
}
