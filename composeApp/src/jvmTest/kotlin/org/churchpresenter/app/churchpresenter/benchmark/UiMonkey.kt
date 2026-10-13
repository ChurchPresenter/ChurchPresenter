@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.benchmark

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import org.churchpresenter.app.churchpresenter.MainDesktopComposeHarness
import org.churchpresenter.liveoutput.PresenterManager
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private const val DEFAULT_SEED = 20_261_011L
private const val DEFAULT_STEPS = 300

/** How often the elapsed time goes into the log, so a run's cost per step can be read off it. */
private const val TIMING_EVERY = 100

/**
 * The wall clock a run may take: a fixed allowance for composing the root, then a step's worth for
 * each step. A CI runner takes about 80 ms a step; this leaves room for more than twice that.
 */
internal fun monkeyTimeout(steps: Int): Duration = 60.seconds + (steps * 200).milliseconds

/**
 * A smart monkey on the main window: from a seed, it presses what a mouse can press, types into
 * what takes text and sends the keys an operator sends, for a number of steps, and fails on the
 * first exception the app throws. It walks the semantics tree, so every press lands on a real
 * control.
 *
 * Not part of `jvmTest`: it runs as `./gradlew :composeApp:monkeyTest` (`-PmonkeySeed`,
 * `-PmonkeySteps`). The seed is logged first and every action after it, a line at a time, to
 * `monkey.log` in `build/reports/monkey/`.
 *
 * The seed fixes every choice the monkey makes, but not the app's own timing: work the app does in
 * the background (generating a lower third, loading a library) can finish sooner or later, change
 * which controls are on screen, and so send two runs of one seed apart after a while. The log is the
 * exact record of what a run did; the seed reproduces it as far as the app's timing allows.
 *
 * Controls in [OFF_LIMITS] are never pressed: they quit, open a native file chooser or a window
 * (which a headless test cannot show), or reach the network. A press the test framework cannot make
 * -- the control went away between finding it and pressing it -- is logged and passed over; only
 * what the app itself throws fails the run.
 */
class UiMonkey : MainDesktopComposeHarness() {

    private val seed = System.getProperty("monkey.seed")?.toLongOrNull() ?: DEFAULT_SEED
    private val steps = System.getProperty("monkey.steps")?.toIntOrNull() ?: DEFAULT_STEPS

    // Emptied first, then written a line at a time: a run the JVM is killed out of still leaves
    // every action up to the one that killed it.
    private val logFile = System.getProperty("monkey.reportDir")?.let { dir ->
        File(dir).apply { mkdirs() }.resolve("monkey.log").apply { writeText("") }
    }

    @Test
    fun `the main window survives a run of random presses, typing and keys`() {
        record("seed $seed, $steps steps")
        val start = TimeSource.Monotonic.markNow()
        root(withOneSong(), presenterManager = PresenterManager(), testTimeout = monkeyTimeout(steps)) { _ ->
            val random = Random(seed)
            repeat(steps) { step ->
                act(step, random)
                if ((step + 1) % TIMING_EVERY == 0) record("${step + 1} steps in ${start.elapsedNow()}")
            }
        }
        record("done: $steps steps, no exception")
    }

    private fun ComposeUiTest.act(step: Int, random: Random) {
        val roll = random.nextInt(10)
        val description = when {
            roll < 7 -> press(random)
            roll < 8 -> type(random)
            else -> key(random)
        } ?: return
        record("step $step: $description")
        try {
            waitForIdle()
        } catch (e: AssertionError) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            fail("step $step ($description) threw ${e::class.simpleName}: ${e.message} -- replay with seed $seed", e)
        }
    }

    private fun ComposeUiTest.press(random: Random): String? {
        val nodes = onAllNodes(PRESSABLE).fetchSemanticsNodes(atLeastOneRootRequired = false)
        if (nodes.isEmpty()) return null
        val index = random.nextInt(nodes.size)
        val name = nameOf(nodes[index])
        return attempt("press \"$name\"") { onAllNodes(PRESSABLE)[index].performClick() }
    }

    private fun ComposeUiTest.type(random: Random): String? {
        val fields = onAllNodes(hasSetTextAction()).fetchSemanticsNodes(atLeastOneRootRequired = false)
        if (fields.isEmpty()) return null
        val index = random.nextInt(fields.size)
        val text = WORDS[random.nextInt(WORDS.size)]
        return attempt("type \"$text\" into \"${nameOf(fields[index])}\"") {
            onAllNodes(hasSetTextAction())[index].performTextInput(text)
        }
    }

    private fun ComposeUiTest.key(random: Random): String {
        val key = KEYS[random.nextInt(KEYS.size)]
        return attempt("key $key") { onAllNodes(isRoot())[0].performKeyInput { pressKey(key) } }
    }

    /**
     * Runs [action], described by [description]. The test framework's own refusal -- a control gone
     * by the time it is pressed -- is logged and passed over; anything the app throws fails the run.
     */
    private fun attempt(description: String, action: () -> Unit): String {
        try {
            action()
        } catch (e: AssertionError) {
            return "$description -- skipped, ${e.message?.lineSequence()?.firstOrNull()}"
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            record(description)
            fail("$description threw ${e::class.simpleName}: ${e.message} -- replay with seed $seed", e)
        }
        return description
    }

    private fun record(line: String) {
        logFile?.appendText(line + "\n")
    }

    private companion object {
        val KEYS = listOf(
            Key.Escape, Key.Enter, Key.Tab, Key.PageDown, Key.PageUp,
            Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.Spacebar,
        )
        val WORDS = listOf("Amazing", "John 3:16", "", "   ", "ü€漢字", "a".repeat(500), "<script>", "0")

        /**
         * What the monkey never presses: what quits, opens a native file chooser or another window,
         * or reaches the network. Matched against a control's name, ignoring case.
         */
        val OFF_LIMITS = listOf(
            // What quits, or opens a native file chooser.
            "quit", "exit", "open", "save", "browse", "choose", "select file", "import", "export",
            "select folder", "select image", "select video", "upload", "add files",
            // What reaches the network.
            "update", "download", "contact", "send", "report", "share", "website", "connect",
            "sign in", "log in", "planning center",
            // What opens a window of its own -- found by the monkey itself, as a HeadlessException.
            "help", "settings", "options", "manager", "library", "converter", "statistics", "calendar",
            "props", "add label", "macros", "message", "new song", "edit song", "edit clear groups",
        )

        val PRESSABLE: SemanticsMatcher = hasClickAction() and SemanticsMatcher("not off limits") { node ->
            val name = nameOf(node).lowercase()
            OFF_LIMITS.none { it in name }
        }

        fun nameOf(node: SemanticsNode): String {
            val config = node.config
            return config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                ?: config.getOrNull(SemanticsProperties.Text)?.joinToString()
                ?: config.getOrNull(SemanticsProperties.TestTag)
                ?: "(unnamed)"
        }
    }
}
