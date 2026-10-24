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
        tour(
            stop("Camera", "A camera plugged into this computer", menuItem("Camera")),
            stop("Screen Capture", "Another screen on this computer", menuItem("Screen Capture")),
            stop("NDI Source", "Video from your network", menuItem("NDI Source")),
            stop("OMT Source", "Video from your network", menuItem("OMT Source")),
        )
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
        tour(
            stop("Choose the picture", "Browse for a photo or your logo", props("Browse")),
            stop("Scale", "Fit, fill or stretch it", props("Scale")),
        )
        add("color", "Color", "A solid color or a gradient")
        say("color-props")
        tour(
            stop("Color", "A single solid color", props("Color 1")),
            stop("Gradient", "Blend two colors, at any angle", props("Gradient")),
        )
        add("video", "Video", "A video file, looped if you like")
        say("video-props")
        tour(
            stop("Choose the file", "Any video on this computer", props("File path")),
            stop("Loop", "Play it over and over", props("Loop")),
            stop("Volume", "How loud it plays", props("Volume")),
        )
    }

    /** A text layer, typed and styled. */
    private fun DemoTour.textSource() {
        add("text", "Text", "Words on top of everything else")
        say("text-edit")
        val field = reveal { textContentField() }
        type("Type the words", "They appear on the canvas as you type", field, "Welcome Home")
        say("text-font")
        tour(
            stop("Font", "Any font on this computer", props("Font")),
            stop("Font size", "How big the words are", props("Font Size")),
        )
        say("text-styles")
        tour(
            stop("Bold", "Heavier letters", style("Bold")),
            stop("Italic", "Slanted letters", style("Italic")),
            stop("Underline", "A line under the words", style("Underline")),
            stop("Strikethrough", "A line through them", style("Strikethrough")),
            stop("Outline", "An edge around each letter", style("Outline")),
        )
        say("text-align")
        tour(
            stop("Horizontal", "Left, center or right", props("Horizontal")),
            stop("Vertical", "Top, middle or bottom", props("Vertical")),
        )
        say("text-color")
        tour(
            stop("Font color", "The color of the words", props("Font Color")),
            stop("Background", "A color behind them, or transparent", props("Transparent background")),
        )
        say("text-spacing")
        tour(
            stop("Letter spacing", "Spread the letters apart", props("Letter Spacing")),
            stop("Curve", "Bend the line into an arc", props("Curve")),
        )
    }

    private fun DemoTour.moreSources() {
        add("clock", "Clock & Timer", "The time, or a countdown")
        say("clock-props")
        tour(
            stop("Format", "12 or 24 hour", props("Format")),
            stop("Hours and seconds", "Show them or hide them", props("Show Seconds")),
            stop("Font and colors", "Make it match the scene", props("Text Color")),
        )
        add("qr", "QR Code", "People scan it with their phones")
        say("qr-props")
        tour(
            stop("Type", "A link, Wi-Fi, phone, email or contact card", props("Type")),
            stop("Colors", "Foreground and background", props("Foreground")),
            stop("Error correction", "How sturdy the code is", props("Error Correction")),
        )
        add("browser", "Browser", "A live web page as a layer")
        say("browser-props")
        tour(
            stop("Web address", "The page to show", props("URL")),
            stop("Size", "Its width and height", props("Width")),
            stop("Custom CSS", "Your own styling", props("Custom CSS")),
        )
        add("bible", "Bible", "A verse, styled like the rest")
        say("bible-props")
        tour(
            stop("Bible version", "Which translation", props("Bible Version")),
            stop("Book and verses", "Where the passage starts and ends", props("Start Verse")),
            stop("Styling", "The verse and the reference, separately", props("Reference")),
        )
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

    /** The text layer's own words: the first field under the properties' "Text" heading, not Name or X. */
    private fun DemoTour.textContentField(): Target {
        val heading = findAll(hasText("Text") and !hasSetTextAction()) { it.left > dp(1100f) }.last()
        return findAll(hasSetTextAction()) { it.left > dp(1100f) && it.top >= heading.bounds.bottom - 1f }.first()
    }

    private fun stop(title: String, body: String, target: Target) = Triple(title, body, target)

    /** A text-style key (bold, italic…) in the properties, by its name. */
    private fun DemoTour.style(name: String) = find(hasContentDescription(name)) { it.left > dp(1100f) }

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
