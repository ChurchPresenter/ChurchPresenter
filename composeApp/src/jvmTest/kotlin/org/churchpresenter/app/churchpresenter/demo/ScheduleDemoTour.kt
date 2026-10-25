@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.unit.dp
import org.churchpresenter.dialogs.AddLabelDialogContent
import java.io.File
import kotlin.test.Test

/** The Schedule tour: adding to it, labels, each row's buttons, running it, the toolbar and its options. */
class ScheduleDemoTour {

    @Test
    fun schedule() = recordTour(
        "schedule",
        tab = "Schedule",
        liveLabel = "Run the service",
        steps = 16,
        heading = "The " to "",
        // Every toolbar button showing, the Calendar Manager's too, which a new install hides.
        settings = { it.copy(hiddenScheduleButtons = emptySet()) },
        panel = { _, _ ->
            Box(Modifier.size(LABEL_W.dp, LABEL_H.dp)) {
                AddLabelDialogContent(onDismiss = {}, onConfirm = { text, fg, bg -> addLabel(text, fg, bg) })
            }
        },
    ) {
        addLabel = { text, fg, bg -> schedule().addLabel(text, fg, bg) }
        scheduleTour()
    }

    private fun DemoTour.scheduleTour() {
        say("intro")
        intro("Plan the whole service, then run it from one list.")
        say("panel")
        note("The Schedule", "Your service, in order", inSchedule("Schedule"))
        say("count")
        note("Item count", "How many items the service has", find(hasText("items", substring = true)))
        adding()
        labels()
        rowButtons()
        running()
        toolbar()
        view()
        options()
        say("outro")
        outro()
    }

    private fun DemoTour.adding() {
        say("add-from-tab")
        type("Find a verse", "From any tab", searchField(), "John 3:16", enter = true)
        say("add-button")
        step(
            "Add to Schedule",
            "It joins the end of the service",
            find(hasContentDescription("Add to Schedule")) { it.left > dp(380f) && it.top < dp(140f) },
        )
        say("add-files")
        note("Add Files…", "Or drag files in from your desktop", inSchedule("Add Files…"))
    }

    /** A colored label, written in the dialog. */
    private fun DemoTour.labels() {
        say("label")
        step("Add Label", "Divide the service into parts", tool("Add Label")) {}
        openPanel("Add Label", LABEL_W, LABEL_H)
        say("label-name")
        panelEdit("Label text", "Closing", panelItems(hasSetTextAction()).first(), "Closing")
        say("label-colors")
        val preset = panelItems(hasText("Aa"))[LABEL_PRESET]
        panelNote("Text and background color", "Each part stands out", preset, click = true)
        panelNote("OK", "Add the label", panelItems(hasText("OK")).last(), click = true)
        closePanel()
        say("label-edit")
        note("In its own colors", "Edit Label changes it later", rowButton("Closing", "Edit Label", label = true))
    }

    private fun DemoTour.rowButtons() {
        say("row-buttons")
        note("Each item's buttons", "Let's go through them", rowButton("Amazing Grace", "Note"))
        say("go-live-button")
        note("Go Live", "Straight to the big screen", rowButton("Amazing Grace", "Go Live"))
        say("reorder")
        note("Move Up and Move Down", "One place at a time", rowButton("Amazing Grace", "Move Up"))
        say("note")
        step("Note", "A message for whoever runs it", rowButton("John 3:16", "Note"))
        say("note-text")
        type("Write the note", "It stays with the item", find(hasSetTextAction()) { it.right < dp(720f) }, NOTE)
        dump("note")
        say("remove")
        step("Remove", "Take an item out", rowButton("Fellowship lunch", "Remove"))
    }

    private fun DemoTour.running() {
        say("preview")
        step("Click an item", "It opens in its tab", inSchedule("John 3:16"))
        say("go-live")
        goLive(rowButton("John 3:16", "Go Live"), sub = "Puts the reading on the projector")
        say("live")
        showLive(sub = "John 3:16 on the big screen")
        say("double-click")
        note("Double-click", "Live in a single move", inSchedule("Amazing Grace"))
        say("undo")
        step("Undo", "Brings back the lunch notice", tool("Undo"))
    }

    private fun DemoTour.toolbar() {
        say("new")
        note("New", "A fresh schedule", tool("New Schedule"))
        say("open")
        note("Open", "A schedule you saved", tool("Open Schedule"))
        say("save")
        note("Save", "A schedule file on your computer", tool("Save Schedule"))
        say("save-as")
        note("Save Schedule As", "In the File menu", tool("Save Schedule"))
        say("share")
        note("Share it", "Your team loads it with Open", tool("Open Schedule"))
        say("clear")
        note("Clear Schedule", "Undo can bring it back", tool("Clear Schedule"))
        say("autosave")
        note("Saved as you work", "Restored after a restart", inSchedule("Schedule"))
        say("planning-center")
        note("Planning Center", "Import a plan, songs and all", tool("Import from Planning Center"))
        say("calendar")
        note("Calendar Manager", "Services planned weeks ahead", tool("Calendar Manager"))
        say("export")
        note("Export Run of Show", "A PDF to print or hand out", tool("Calendar Manager"))
    }

    private fun DemoTour.view() {
        say("zoom")
        step("Zoom", "Bigger or smaller rows", tool("Zoom In"))
        say("density")
        val levels = hasText("Compact") or hasText("Normal") or hasText("Detailed")
        val density = find(levels) { it.right < dp(720f) && it.top < dp(100f) }
        note("Compact, Normal or Detailed", "How much each row shows", density)
        dump("density")
    }

    private fun DemoTour.options() {
        say("options")
        step("Schedule Options", "Set up the panel", tool("Schedule Options"))
        dump("options")
        say("options-buttons")
        note("Buttons", "Untick the ones you never use", menuItem("BUTTONS"))
        say("options-under-title")
        note("Show buttons under title", "The toolbar on its own row", menuItem("Show buttons under title"))
        say("options-size")
        note("Toolbar icon size", "Small, medium or large", menuItem("TOOLBAR ICON SIZE"))
    }

    /** Text in the Schedule panel, not in the tab beside it. */
    private fun DemoTour.inSchedule(label: String) =
        find(hasText(label, substring = true)) { it.right < dp(720f) }

    /** A toolbar button of the Schedule panel, by its name. */
    private fun DemoTour.tool(name: String) =
        find(hasContentDescription(name, substring = true)) { it.right < dp(720f) && it.top < dp(100f) }

    /** The button described [desc] on the schedule row showing [rowText]. */
    private fun DemoTour.rowButton(rowText: String, desc: String, label: Boolean = false): Target {
        // A label is matched whole (the Sermon slides row also says Sermon); its text may be capitalized.
        val m = if (label) hasText(rowText, ignoreCase = true) else hasText(rowText, substring = true)
        val row = find(m) { it.right < dp(720f) && it.top > dp(100f) }.bounds
        val slack = if (label) dp(40f) else dp(20f)
        return find(hasContentDescription(desc)) {
            it.right < dp(720f) && it.center.y in (row.top - slack)..(row.bottom + slack)
        }
    }

    private fun DemoTour.dump(name: String) {
        val lines = ui.onAllNodes(isRoot()).fetchSemanticsNodes().flatMapIndexed { r, root ->
            fun walk(n: SemanticsNode): List<String> = listOf(
                "r$r ${n.boundsInWindow} text=${n.config.getOrNull(SemanticsProperties.Text)} " +
                    "desc=${n.config.getOrNull(SemanticsProperties.ContentDescription)}",
            ) + n.children.flatMap(::walk)
            walk(root)
        }.filter { "text=null desc=null" !in it }
        File(System.getenv("CP_DEMO_OUT"), "schedule-$name.txt").writeText(lines.joinToString("\n"))
    }
}

private const val LABEL_W = 460f
private const val LABEL_H = 520f
private const val LABEL_PRESET = 6
private const val NOTE = "Read slowly, then pause for prayer."

/** Adds a label to the app's schedule: set once the tour has the schedule. */
private var addLabel: (String, String, String) -> Unit = { _, _, _ -> }
