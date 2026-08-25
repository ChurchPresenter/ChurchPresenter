package org.churchpresenter.canvas

import org.churchpresenter.ui.FakeCommandRunner

import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.churchpresenter.ui.CommandResult

/**
 * Finding the window a Screen Capture source is pointed at.
 *
 * This runs on every capture tick for a window-targeted source, and it is the step that decides
 * whether the audience sees the right window, the wrong one, or a black rectangle. All of it used to
 * be locked behind `xprop`, `xwininfo` and `osascript`; the lookups now take a [CommandRunner], so the
 * walk itself — how a title is matched, which candidate is skipped, when the search gives up — is
 * exercised against captured tool output.
 *
 * The behaviour worth pinning down is that this is a *search*, not a single lookup: the stacking list
 * holds every window on the desktop, most of which are not the one wanted, and a candidate can fail
 * to qualify at three separate points. Each of those has to leave the walk running rather than end it,
 * or a window listed behind an unnamed or unrealised one becomes uncapturable.
 *
 * `osName` is passed in rather than faked through the system property, which skiko latches JVM-wide.
 */
class WindowBoundsTest {

    private val stackingList =
        "_NET_CLIENT_LIST_STACKING(WINDOW): window id # 0x1400003, 0x1600005, 0x1800007"

    private fun named(title: String) = "_NET_WM_NAME(UTF8_STRING) = \"$title\""

    private fun xwininfo(x: Int, y: Int, w: Int, h: Int) = """
        xwininfo: Window id: 0x1600005 "Some Window"

          Absolute upper-left X:  $x
          Absolute upper-left Y:  $y
          Relative upper-left X:  0
          Relative upper-left Y:  0
          Width: $w
          Height: $h
          Depth: 24
    """.trimIndent()

    // ── The X11 walk ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `the window whose name matches exactly is the one measured`() {
        val runner = FakeCommandRunner { command ->
            when {
                command.contains("-root") -> CommandResult(0, stackingList)
                command.first() == "xwininfo" -> CommandResult(0, xwininfo(100, 200, 1280, 720))
                command.contains("0x1600005") -> CommandResult(0, named("Sermon Notes"))
                else -> CommandResult(0, named("Something Else"))
            }
        }

        assertEquals(Rectangle(100, 200, 1280, 720), linuxWindowBoundsFrom("Sermon Notes", runner::run))
    }

    @Test
    fun `only the matching window is measured, not every window listed`() {
        val runner = FakeCommandRunner { command ->
            when {
                command.contains("-root") -> CommandResult(0, stackingList)
                command.first() == "xwininfo" -> CommandResult(0, xwininfo(0, 0, 800, 600))
                command.contains("0x1400003") -> CommandResult(0, named("Sermon Notes"))
                else -> CommandResult(0, named("Other"))
            }
        }

        linuxWindowBoundsFrom("Sermon Notes", runner::run)

        assertEquals(
            1, runner.programs.count { it == "xwininfo" },
            "the walk stops at the first match rather than measuring the whole desktop",
        )
    }

    @Test
    fun `a title that only nearly matches is not accepted`() {
        // Two windows of one application routinely differ by a suffix alone, and the operator picked
        // this exact string out of a list built the same way.
        val runner = FakeCommandRunner { command ->
            when {
                command.contains("-root") -> CommandResult(0, stackingList)
                command.first() == "xwininfo" -> CommandResult(0, xwininfo(0, 0, 800, 600))
                else -> CommandResult(0, named("Sermon Notes — Edited"))
            }
        }

        assertNull(linuxWindowBoundsFrom("Sermon Notes", runner::run))
    }

    @Test
    fun `an unnamed window is stepped over rather than ending the search`() {
        val runner = FakeCommandRunner { command ->
            when {
                command.contains("-root") -> CommandResult(0, stackingList)
                command.first() == "xwininfo" -> CommandResult(0, xwininfo(10, 20, 640, 480))
                command.contains("0x1400003") -> CommandResult(0, "_NET_WM_NAME:  not found.")
                else -> CommandResult(0, named("Sermon Notes"))
            }
        }

        assertEquals(Rectangle(10, 20, 640, 480), linuxWindowBoundsFrom("Sermon Notes", runner::run))
    }

    @Test
    fun `a window reporting no size is stepped over so a later one can match`() {
        // A mapped-but-unrealised window reports zero geometry. Returning it would capture nothing;
        // stopping at it would hide the real window behind it.
        var measured = 0
        val runner = FakeCommandRunner { command ->
            when {
                command.contains("-root") -> CommandResult(0, stackingList)
                command.first() == "xwininfo" -> {
                    measured++
                    if (measured == 1) CommandResult(0, xwininfo(0, 0, 0, 0))
                    else CommandResult(0, xwininfo(5, 6, 320, 240))
                }
                else -> CommandResult(0, named("Sermon Notes"))
            }
        }

        assertEquals(Rectangle(5, 6, 320, 240), linuxWindowBoundsFrom("Sermon Notes", runner::run))
    }

    @Test
    fun `an empty stacking list finds nothing and measures nothing`() {
        val runner = FakeCommandRunner { CommandResult(0, "") }

        assertNull(linuxWindowBoundsFrom("Sermon Notes", runner::run))
        assertEquals(listOf("xprop"), runner.programs, "with no ids there is nothing to ask about")
    }

    @Test
    fun `a window that has since closed is reported as absent`() {
        val runner = FakeCommandRunner { command ->
            when {
                command.contains("-root") -> CommandResult(0, stackingList)
                command.first() == "xwininfo" -> CommandResult(0, xwininfo(0, 0, 800, 600))
                else -> CommandResult(0, named("Other"))
            }
        }

        assertNull(linuxWindowBoundsFrom("Gone", runner::run))
    }

    // ── xwininfo's own output ─────────────────────────────────────────────────────────────────

    @Test
    fun `the absolute position is taken, not the relative one`() {
        // A window inside a reparenting window manager has a relative origin of 0,0 while sitting
        // anywhere on screen. Capturing at the relative origin grabs the wrong part of the desktop.
        assertEquals(Rectangle(100, 200, 1280, 720), parseXwininfoBounds(xwininfo(100, 200, 1280, 720)))
    }

    @Test
    fun `a report with no usable size yields nothing`() {
        assertNull(parseXwininfoBounds(xwininfo(10, 10, 0, 480)))
        assertNull(parseXwininfoBounds(xwininfo(10, 10, 640, 0)))
    }

    @Test
    fun `output that is not an xwininfo report at all yields nothing`() {
        assertNull(parseXwininfoBounds("xwininfo: error: no such window"))
        assertNull(parseXwininfoBounds(""))
    }

    @Test
    fun `a window at the screen origin is a real answer, not a missing one`() {
        val bounds = parseXwininfoBounds(xwininfo(0, 0, 1920, 1080))

        assertEquals(Rectangle(0, 0, 1920, 1080), bounds, "0,0 is where a maximised window sits")
    }

    @Test
    fun `an unreadable coordinate falls back to zero rather than dropping the window`() {
        val garbled = xwininfo(1, 2, 800, 600).replace("Absolute upper-left X:  1", "Absolute upper-left X:  ?")

        assertEquals(Rectangle(0, 2, 800, 600), parseXwininfoBounds(garbled))
    }

    @Test
    fun `an unreadable Y falls back to zero the same way`() {
        val garbled = xwininfo(1, 2, 800, 600).replace("Absolute upper-left Y:  2", "Absolute upper-left Y:  ?")

        assertEquals(Rectangle(1, 0, 800, 600), parseXwininfoBounds(garbled))
    }

    @Test
    fun `an unreadable width is no window at all`() {
        // Zero is not a fallback for a size the way it is for a position — capturing a rectangle of
        // no width throws, so the walk has to move on to the next candidate instead.
        val garbled = xwininfo(1, 2, 800, 600).replace("Width: 800", "Width: ?")

        assertEquals(null, parseXwininfoBounds(garbled))
    }

    @Test
    fun `an unreadable height is no window either`() {
        val garbled = xwininfo(1, 2, 800, 600).replace("Height: 600", "Height: ?")

        assertEquals(null, parseXwininfoBounds(garbled))
    }

    // ── macOS ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `mac bounds are read from the four numbers AppleScript returns`() {
        val runner = FakeCommandRunner.alwaysReturning("64,100,1440,900")

        assertEquals(Rectangle(64, 100, 1440, 900), macWindowBoundsFrom("Keynote", runner::run))
    }

    @Test
    fun `the title being searched for is embedded in the script that is run`() {
        val runner = FakeCommandRunner.alwaysReturning("0,0,10,10")

        macWindowBoundsFrom("Sermon Notes", runner::run)

        assertTrue(
            runner.calls.single().last().contains("\"Sermon Notes\""),
            "the script has to name the window it is looking for",
        )
    }

    @Test
    fun `an AppleScript answer that is not four numbers is no window`() {
        listOf(
            "",
            "execution error: System Events got an error",
            "64,100",
            "64,100,1440,900,17",
        ).forEach { output ->
            val runner = FakeCommandRunner.alwaysReturning(output)
            assertNull(macWindowBoundsFrom("Keynote", runner::run), "\"$output\" is not a window")
        }
    }

    @Test
    fun `a mac window reporting no size is no window`() {
        val runner = FakeCommandRunner.alwaysReturning("64,100,0,900")

        assertNull(macWindowBoundsFrom("Keynote", runner::run))
    }

    @Test
    fun `a mac window at a negative position is still a real window`() {
        // A window on a display arranged to the left of the primary one has negative coordinates.
        val runner = FakeCommandRunner.alwaysReturning("-1920,0,1920,1080")

        assertEquals(Rectangle(-1920, 0, 1920, 1080), macWindowBoundsFrom("Keynote", runner::run))
    }

    // ── Dispatch ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `each platform's lookup is reached by its own os name`() {
        val linux = FakeCommandRunner { CommandResult(0, "") }
        windowBoundsFor("linux", "Any", linux::run)
        assertEquals("xprop", linux.programs.first())

        val mac = FakeCommandRunner { CommandResult(0, "") }
        windowBoundsFor("mac os x", "Any", mac::run)
        assertEquals("osascript", mac.programs.single())
    }

    @Test
    fun `an unrecognised platform has no window bounds and runs nothing`() {
        val runner = FakeCommandRunner.alwaysReturning("")

        assertNull(windowBoundsFor("plan 9", "Any", runner::run))
        assertTrue(runner.calls.isEmpty())
    }

    @Test
    fun `a lookup that throws is reported as no window rather than propagating`() {
        // This runs inside the capture loop; an exception escaping here would kill the coroutine and
        // freeze the source on its last frame instead of simply finding nothing this tick.
        val exploding = FakeCommandRunner { throw IllegalStateException("display gone") }

        assertNull(windowBoundsFor("linux", "Any", exploding::run))
    }
}
