@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performTouchInput
import org.churchpresenter.app.churchpresenter.TestSingletons
import kotlin.test.Test

/** The Canvas tour: scenes built from every kind of source and shape, saved and put live. */
class CanvasDemoTour {

    @Test
    fun canvas() {
        TestSingletons.latchToTestHome()
        writeScenes()
        recordTour("canvas", tab = "Canvas", liveLabel = "Show a scene", steps = 20) { canvasTour() }
    }

    private fun DemoTour.canvasTour() {
        say("intro")
        intro("Design your own scenes from pictures, text, video and more.")
        say("open-tab")
        openTab("Canvas", "Scenes you design yourself")
        scenes()
        say("new-scene")
        step("New scene", "Start one from scratch", inTab(hasText("New")))
        say("canvas-size")
        note("Canvas size", "Match your projector, or set your own", find(hasContentDescription("Canvas size")))
        pictureSources()
        textSource()
        moreSources()
        say("live-sources")
        quietClick(find(hasContentDescription("Add source")))
        note("Live video", "Camera, Screen Capture, NDI and OMT", menuItem("Camera"))
        // Closed without adding one: a camera layer would try to open this machine's camera.
        quietClick(text("Scenes"))
        layers()
        shapes()
        say("back-welcome")
        step("Back to Welcome", "Ready to show", inTab(hasText("Welcome")))
        say("go-live")
        goLive(sub = "Sends the scene to the projector")
        say("live")
        showLive(sub = "The Welcome scene, live")
        say("preset")
        note(
            "Save a preset",
            "A named shortcut to this scene for any future service",
            find(hasContentDescription("Save preset")) { it.left > dp(380f) && it.top < dp(140f) },
        )
        say("schedule")
        step(
            "Add it to the schedule",
            "Bring the scene back during the service",
            find(hasContentDescription("Add to Schedule")) { it.left > dp(380f) && it.top < dp(140f) },
        )
        say("outro")
        outro()
    }

    /** The scene list and each scene's own buttons. */
    private fun DemoTour.scenes() {
        say("scenes")
        note("Scenes", "Each one is a full screen you design", text("Scenes"))
        say("switch")
        step("Pick a scene", "Its layers appear on the canvas", inTab(hasText("Countdown")))
        say("rename")
        note("Rename", "Give the scene a name you'll recognize", sceneButton("Rename"))
        say("duplicate")
        note("Duplicate", "Copy a scene to make a variation", sceneButton("Duplicate"))
        say("remove")
        note("Remove", "Delete a scene you no longer need", sceneButton("Remove"))
    }

    private fun DemoTour.pictureSources() {
        add("image", "Image", "A photo or a logo")
        say("image-props")
        note("Choose the picture", "Browse, then Fit, Fill or Stretch it", props("Browse"))
        add("color", "Color", "A solid color or a gradient")
        say("color-props")
        note("Color or gradient", "Pick two colors and the angle between them", props("Gradient"))
        add("video", "Video", "A video file, looped if you like")
        say("video-props")
        note("Loop and volume", "Pick the file, then loop it or set its volume", props("Loop"))
    }

    /** A text layer, typed and styled. */
    private fun DemoTour.textSource() {
        add("text", "Text", "Words on top of everything else")
        say("text-edit")
        val fields = findAll(hasSetTextAction()) { it.left > dp(1100f) }
        type("Type the words", "They appear on the canvas as you type", fields[1], "Welcome Home")
        say("text-font")
        note("Font and size", "Any font on this computer", props("Font Size"))
        say("text-color")
        note("Font color", "And the background behind the words", props("Font Color"))
        say("text-spacing")
        note("Letter spacing and curve", "Spread the letters, or bend the line", props("Letter Spacing"))
    }

    private fun DemoTour.moreSources() {
        add("clock", "Clock & Timer", "The time, or a countdown")
        say("clock-props")
        note("Clock or timer", "12 or 24 hour, with or without seconds", props("Show Seconds"))
        add("qr", "QR Code", "People scan it with their phones")
        say("qr-props")
        note("What the code opens", "A web link, Wi-Fi, a phone number and more", props("Error Correction"))
        add("browser", "Browser", "A live web page as a layer")
        say("browser-props")
        note("Web address", "Plus its size and custom styling", props("Custom CSS"))
        add("bible", "Bible", "A verse, styled like the rest")
        say("bible-props")
        note("Pick the verse", "Book, chapter and verses, and the reference", props("Bible Version"))
    }

    /** What each layer's buttons do. */
    private fun DemoTour.layers() {
        say("layers")
        note("Show or hide", "The eye switches a layer on and off", layerButton("Toggle visibility"))
        say("lock")
        note("Lock", "Keep a layer from being moved by accident", layerButton("Toggle lock"))
        say("order")
        note("Move forward or backward", "Change which layer is on top", layerButton("Move forward"))
        say("delete")
        note("Delete", "Remove a layer you don't need", layerButton("Delete source"))
    }

    /** The drawing tools: a rectangle drawn on the canvas, then its colors. */
    private fun DemoTour.shapes() {
        say("shapes")
        step("Shapes", "Rectangle, ellipse, line, arrow, freehand", inTab(hasText("□")))
        say("draw")
        val from = Offset(dp(700f), dp(560f))
        val to = Offset(dp(1000f), dp(640f))
        val canvas = Target(ui.onAllNodes(isRoot())[0], Rect(from, to))
        step("Drag to draw", "The shape appears where you drag", canvas) {
            ui.onAllNodes(isRoot())[0].performTouchInput {
                down(from)
                moveTo(Offset((from.x + to.x) / 2, (from.y + to.y) / 2))
                moveTo(to)
                up()
            }
        }
        say("shape-props")
        runCatching { note("Fill and stroke", "Colors, gradient and stroke width", props("Stroke Width")) }
        say("select")
        step("Select tool", "Move and resize anything on the canvas", inTab(hasText("◆")))
    }

    /** Opens Add source and picks [label]; [key] is the narration said over it. */
    private fun DemoTour.add(key: String, label: String, sub: String) {
        quietClick(find(hasContentDescription("Add source")))
        say("add-$key")
        step("Add $label", sub, menuItem(label))
    }

    private fun DemoTour.inTab(m: SemanticsMatcher) = find(m) { it.left > dp(380f) }

    /** A label in the selected source's properties, on the right. */
    private fun DemoTour.props(label: String): Target {
        val m = hasText(label, substring = true, ignoreCase = true) or
            hasContentDescription(label, substring = true, ignoreCase = true)
        if (hasNode(m) { it.left > dp(1100f) }) return find(m) { it.left > dp(1100f) }
        val shown = ui.onAllNodes(hasText("", substring = true)).fetchSemanticsNodes()
            .filter { it.boundsInWindow.left > dp(1100f) && it.boundsInWindow.left < dp(1600f) }
            .joinToString(" | ") { it.config.getOrNull(SemanticsProperties.Text)?.joinToString("").orEmpty() }
        error("no \"$label\" in the properties; they show: $shown")
    }

    private fun DemoTour.sceneButton(label: String) =
        find(hasContentDescription(label)) { it.left > dp(380f) && it.left < dp(600f) && it.top < dp(400f) }

    private fun DemoTour.layerButton(label: String) =
        find(hasContentDescription(label)) { it.left > dp(380f) && it.left < dp(600f) && it.top > dp(400f) }
}
