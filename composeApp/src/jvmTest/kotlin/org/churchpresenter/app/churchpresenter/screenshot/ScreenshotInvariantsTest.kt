package org.churchpresenter.app.churchpresenter.screenshot

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The two rules that decide whether a screenshot is compared in CI at all.
 *
 * Both fail **silently**. `.github/workflows/screenshots.yml` records with `--tests
 * '*ScreenshotTest*'` and matches images between the two sides of the comparison by their path
 * relative to [SCREENSHOT_ROOT], so a class named something else is simply never rendered, and an
 * image written outside that root simply has no counterpart. Neither produces a failure, a warning,
 * or a missing-file error — the image just stops being looked at, and stays that way for as long as
 * nobody thinks to check. Each has already happened: a batch of 63 images was dead on arrival for
 * the naming rule, and four files plus [PresenterScreenshotTest] had their own path literal.
 *
 * So these are asserted here rather than left to review. Reading the sources is the only way: what
 * the record task picks up is decided by a class *name*, and where an image lands is decided by a
 * path *literal*, neither of which exists as a value at runtime.
 *
 * Sources resolve relative to the module directory, the same way [AppPreviewSupport]'s fixtures do.
 */
class ScreenshotInvariantsTest {

    private val packageDir =
        File("src/jvmTest/kotlin/org/churchpresenter/app/churchpresenter/screenshot")

    /** Every `.kt` in the screenshot package, paired with its text. */
    private fun sources(): List<Pair<File, String>> {
        val files = packageDir.listFiles { f: File -> f.extension == "kt" }?.sortedBy { it.name }
        if (files.isNullOrEmpty()) {
            // A moved package or a changed working directory would otherwise leave every assertion
            // below vacuously true — the exact silence this class exists to end.
            fail("no sources found at ${packageDir.absolutePath}; this test can no longer see what it checks")
        }
        return files.map { it to it.readText() }
    }

    @Test
    fun `the screenshot package is where this test thinks it is`() {
        val names = sources().map { (file, _) -> file.name }
        assertTrue(names.size > 20, "expected the whole screenshot package, saw ${names.size} files")
        assertTrue("ScreenshotSupport.kt" in names, "saw $names")
    }

    @Test
    fun `every class that takes a screenshot is named so CI renders it`() {
        val offenders = sources().mapNotNull { (file, text) ->
            if (!text.contains("@Test")) return@mapNotNull null
            val declared = CLASS_DECLARATION.find(text)?.groupValues?.get(1) ?: return@mapNotNull null
            // This class itself asserts about sources rather than rendering any; it needs no images
            // in CI and deliberately sits outside the record filter.
            if (declared == this::class.simpleName) return@mapNotNull null
            if (declared.endsWith("ScreenshotTest")) null else "${file.name}: class $declared"
        }
        assertEquals(
            emptyList(), offenders,
            "the record job runs --tests '*ScreenshotTest*'; a class outside that pattern is never " +
                "rendered in CI and its images are never compared"
        )
    }

    /**
     * `SCREENSHOT_ROOT` plus every constant in the package defined *from* it — `ROOT` in
     * [AppPreviewSupport] is `"$SCREENSHOT_ROOT/previewApp"`, and a capture through that is under
     * the root just as surely as one naming it outright. Resolved rather than allow-listed, so a new
     * sub-root is accepted the moment it is derived correctly and never when it is not.
     */
    private fun rootDerivedNames(): Set<String> {
        val derived = sources().flatMap { (_, text) ->
            ROOT_DERIVED_CONSTANT.findAll(text).map { it.groupValues[1] }
        }
        return derived.toSet() + "SCREENSHOT_ROOT"
    }

    @Test
    fun `every capture is written under the screenshot root`() {
        val roots = rootDerivedNames()
        val offenders = sources().flatMap { (file, text) ->
            text.lineSequence().withIndex()
                .filter { (_, line) -> CAPTURE_CALL.containsMatchIn(line) }
                // A capture either builds its path from a root or takes a File the caller built;
                // only a path literal spelled out on the spot can miss it.
                .filter { (_, line) -> line.contains(".png\"") && roots.none { it in line } }
                .map { (i, line) -> "${file.name}:${i + 1}: ${line.trim()}" }
        }
        assertEquals(
            emptyList(), offenders,
            "images are matched between the two sides of the comparison by their path relative to " +
                "SCREENSHOT_ROOT, so one written elsewhere is not reported as differing — it has no " +
                "counterpart and silently stops being compared"
        )
    }

    /**
     * A screenshot that renders today's date is stale tomorrow.
     *
     * This one fails silently too, and differently from the others: the image is compared, it just
     * differs on every run, so the whole set becomes noise a reviewer stops reading. It had already
     * happened — `previewApp/ccli_report_*` seeded its services from `LocalDate.now()` and drew a
     * default range ending today, so the three images moved a day at a time and nobody could tell a
     * layout change from the calendar.
     *
     * `System.currentTimeMillis()` is deliberately allowed: it is used here for wait deadlines,
     * which never reach the image. It is the `java.time` `now()` family that gets drawn.
     */
    @Test
    fun `no screenshot renders a live clock`() {
        val liveClock =
            Regex(
                """\b(LocalDate|LocalDateTime|LocalTime|Instant|ZonedDateTime|OffsetDateTime|Year|YearMonth)\.now\(""",
            )
        val offenders = sources()
            .filterNot { (file, _) -> file.name == "${this::class.simpleName}.kt" }
            .flatMap { (file, text) ->
                text.lineSequence().withIndex()
                    .filter { (_, line) -> liveClock.containsMatchIn(line) }
                    // A comment may name the call to say it is deliberately not used.
                    .filterNot { (_, line) -> line.trimStart().startsWith("//") || line.trimStart().startsWith("*") }
                    .map { (i, line) -> "${file.name}:${i + 1}: ${line.trim()}" }
            }
        assertEquals(
            emptyList(), offenders,
            "pin the date instead — pass a fixed value in, the way AppPreviewStatisticsScreenshotTest " +
                "does with FIXED_TODAY, so the committed image does not go stale overnight"
        )
    }

    @Test
    fun `every path that names the root derives it rather than restating it`() {
        val literal = Regex(""""(\.?/)?screenshots/""")
        val offenders = sources()
            // This file spells the path out to search for it; matching itself would be noise.
            .filterNot { (file, _) -> file.name == "${this::class.simpleName}.kt" }
            .flatMap { (file, text) ->
                text.lineSequence().withIndex()
                    .filter { (_, line) -> literal.containsMatchIn(line) }
                    .filterNot { (_, line) -> line.contains("SCREENSHOT_ROOT =") }
                    .map { (i, line) -> "${file.name}:${i + 1}: ${line.trim()}" }
            }
        assertEquals(
            emptyList(), offenders,
            "SCREENSHOT_ROOT is the single definition of that path; a second copy moves out of step " +
                "with it without anything noticing"
        )
    }

    /**
     * Screenshots are committed and must stay that way — see AGENT.md.
     *
     * Under `build/` they are unreviewable: `build/` is git-ignored and routinely deleted, so the
     * images exist only on the machine that last recorded them and no reviewer can open them or ask
     * for a state to be changed before it merges. This location has been flipped three times; this
     * assertion is what stops the fourth from being silent.
     */
    @Test
    fun `screenshots are committed, not hidden under build`() {
        assertTrue(
            !SCREENSHOT_ROOT.startsWith("build/") && !SCREENSHOT_ROOT.contains("/build/"),
            "screenshots must be committed so they can be reviewed and approved; a root under " +
                "build/ is git-ignored and wiped by `clean`, was $SCREENSHOT_ROOT"
        )
    }

    private companion object {
        val CLASS_DECLARATION = Regex("""^(?:internal |private )?class (\w+)""", RegexOption.MULTILINE)
        val CAPTURE_CALL = Regex("""captureRoboImage\(|captureTo\(""")

        /** e.g. `private const val ROOT = "$SCREENSHOT_ROOT/previewApp"` — captures `ROOT`. */
        val ROOT_DERIVED_CONSTANT =
            Regex("""val (\w+)\s*=\s*(?:File\()?"[^"]*\$\{?SCREENSHOT_ROOT""")
    }
}
