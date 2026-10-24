@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.LiveOutputCallbacks
import org.churchpresenter.app.churchpresenter.MainDesktop
import org.churchpresenter.app.churchpresenter.MainDesktopPublishers
import org.churchpresenter.app.churchpresenter.ScheduleActions
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.WebAccessState
import org.churchpresenter.app.churchpresenter.tabs.LocalWebEngineAvailable
import org.churchpresenter.companionsurface.CompanionSatelliteViewModel
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.liveoutput.PresenterOutputContent
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.media.viewmodel.LocalMediaViewModel
import org.churchpresenter.media.viewmodel.MediaViewModel
import org.churchpresenter.qa.QAManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.screenshot.PinnedFonts
import org.churchpresenter.stt.STTManager
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import org.junit.Assume.assumeTrue
import java.awt.image.BufferedImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.churchpresenter.lowerthird.render.LowerThirdOffscreenRenderer
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.hypot

// The main window and, beside it off camera, the projector it drives — in dp.
private const val APP_W = 1600f
private const val APP_H = 1000f
private const val GAP = 48f
private const val PROJ_W = 880f
private const val PROJ_H = 495f
private const val CANVAS_W = APP_W + GAP + PROJ_W + GAP

// A dialog a tour opens (see [recordTour]'s panel), rendered beside the projector at its own size.
private const val PANEL_W = 1500f
private const val PANEL_H = 900f

/** How far the clock moves per capture while a dialog is open: far enough for a preview to show its design. */
private const val PANEL_TICK_MS = 3_000L
private const val PANEL_STEP_MS = 150

/** Where in a lower third's run the video holds it: past the entrance, before the exit. */
private const val LOWER_THIRD_HOLD = 0.55f
private const val STILL_FRAMES = 120
private const val STILL_FRAME_MS = 16L
private const val PANEL_SCROLL_TRIES = 12
private const val PANEL_SCROLL_PX = 200f

/** Flip to record the app in light mode instead. */
private const val DARK_MODE = true
private val THEME = if (DARK_MODE) ThemeMode.DARK else ThemeMode.LIGHT

private const val FPS = 30
private const val TAB_AREA_LEFT = 380f

/** Captured at twice the dp size, so a 2× zoom on the 1080p frame stays sharp. */
private val DENSITY = System.getenv("CP_DEMO_DENSITY")?.toFloatOrNull() ?: 2f

/** `CP_DEMO_SECONDS` stops a tour after that long — for a quick look at a tour's opening. */
private val MAX_FRAMES = System.getenv("CP_DEMO_SECONDS")?.toIntOrNull()?.let { it * FPS }

private class LengthReached : RuntimeException()

/**
 * Records one tour as 1920×1080 PNG frames in `$CP_DEMO_OUT/<name>/`, laid out as the Tab Demo Video
 * storyboard (see [DemoStage]), from the real main window and the output it drives. Skipped unless
 * `CP_DEMO_OUT` is set, so CI never records. The finished MP4, narrated (see [DemoNarrator]), goes to
 * `<CP_DEMO_VIDEOS>/<tab>/` — `~/Downloads/ChurchPresenter Videos/<tab>/` by default — as
 * `churchpresenter-<name>-tab.mp4`.
 *
 * [tab] names the tab in the cards; [liveLabel] is what the projector scene's header says it shows;
 * [steps] is how many numbered steps the tour has, for the progress dashes.
 */
internal fun recordTour(
    name: String,
    tab: String,
    liveLabel: String,
    steps: Int,
    startTab: String? = null,
    heading: Pair<String, String> = "The " to " Tab",
    settings: (AppSettings) -> AppSettings = { it },
    panel: (@Composable (AppSettings, ((AppSettings) -> AppSettings) -> Unit) -> Unit)? = null,
    script: DemoTour.() -> Unit,
) {
    val root = System.getenv("CP_DEMO_OUT")?.let(::File)
    assumeTrue("CP_DEMO_OUT is not set", root != null)
    val out = File(root!!, name).apply { deleteRecursively(); mkdirs() }
    TestSingletons.latchSkikoHostOs()
    TestSingletons.latchToTestHome()
    // A previous tour's schedule autosave would open every recording on "Restore unsaved schedule?".
    File(System.getProperty("user.home"), ".churchpresenter/autosave_schedule.tmp").delete()
    val appSettings = settings(library())
    val width = if (panel == null) CANVAS_W else CANVAS_W + PANEL_W
    runSkikoComposeUiTest(size = Size(width, APP_H) * DENSITY, density = Density(DENSITY)) {
        var actions = ScheduleActions()
        val presenter = PresenterManager()
        val media = MediaViewModel()
        var current by mutableStateOf(appSettings)
        var frozen by mutableStateOf(false)
        val changeSettings = { change: (AppSettings) -> AppSettings -> current = change(current) }
        setContent {
            PinnedFonts {
                ChurchPresenterTheme(themeMode = THEME) {
                    // No browser engine starts here; the Web tab is told it has one, and is shown a page.
                    CompositionLocalProvider(
                        LocalMediaViewModel provides media,
                        LocalWebEngineAvailable provides true,
                    ) {
                        Row(Modifier.fillMaxSize().background(Color.Black)) {
                            Box(Modifier.size(APP_W.dp, APP_H.dp)) {
                                MainDesktop(
                                    appSettings = current,
                                    onSettingsChange = changeSettings,
                                    presenterManager = presenter,
                                    companionSatelliteViewModel = CompanionSatelliteViewModel(),
                                    qaManager = QAManager(),
                                    sttManager = STTManager(),
                                    theme = THEME,
                                    live = liveCallbacks(presenter),
                                    publish = MainDesktopPublishers(onScheduleActionsReady = { actions = it }),
                                    web = WebAccessState(
                                        serverUrl = "http://192.168.1.42:8080",
                                        // What AppWiring does: Blank Output freezes the slide output.
                                        presentationFrozen = frozen,
                                        onFreezeToggle = {
                                            frozen = !frozen
                                            presenter.setSlideFrozen(frozen)
                                        },
                                    ),
                                )
                            }
                            Projector(presenter, current, media)
                            panel?.let {
                                Box(
                                    Modifier.padding(start = GAP.dp, top = ((APP_H - PANEL_H) / 2).dp)
                                        .size(PANEL_W.dp, PANEL_H.dp),
                                ) { it(current, changeSettings) }
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        demoSchedule(actions)
        waitForIdle()
        startTab?.let {
            onNodeWithText(it).performScrollTo().performClick()
            waitForIdle()
        }
        val narrator = DemoNarrator(root)
        try {
            val setup = TourSetup(name, tab, liveLabel, steps, hasPanel = panel != null, heading = heading)
            DemoTour(setup, this, presenter, out, changeSettings, narrator).script()
        } catch (_: LengthReached) {
            // CP_DEMO_SECONDS cut the tour short; what was recorded is the preview.
        }
        // One folder per tab, so each video sits with its own thumbnail and script.
        val videos = File(
            System.getenv("CP_DEMO_VIDEOS")?.let(::File)
                ?: File(System.getenv("HOME"), "Downloads/ChurchPresenter Videos"),
            if (heading.second == " Tab") tab else heading.first + tab + heading.second,
        )
        val suffix = if (MAX_FRAMES == null) "" else "-preview"
        narrator.encode(out, FPS, File(videos, "churchpresenter-$name-tab$suffix.mp4"))
    }
}

/** A short service — half a panel — so what a tour adds to it stands out. */
private fun demoSchedule(actions: ScheduleActions) {
    actions.addMedia(File(LIBRARY, "Media/Welcome Loop.mp4").absolutePath, "Welcome Loop", "video", "")
    actions.addSong(320, "Come Thou Fount Of Every Blessing", "Hymnal", "song-320")
    actions.addSong(12, "Amazing Grace", "Hymnal", "song-12")
    actions.addPresentation(File(LIBRARY, "Decks/Sermon.pdf").absolutePath, "Sermon", 6, "pdf")
    actions.addPicture(File(LIBRARY, "Gallery").absolutePath, "Gallery", 12)
    actions.addAnnouncement(
        ScheduleItem.AnnouncementItem(id = "ann-1", text = "Fellowship lunch in the hall after the service"),
    )
}

private fun liveCallbacks(presenter: PresenterManager) = LiveOutputCallbacks(
    presenting = { presenter.setPresentingMode(it) },
    onVerseSelected = { presenter.setSelectedVerses(it) },
    onSongItemSelected = {
        presenter.setLyricSection(it)
        presenter.setDisplayedLyricSection(it)
    },
    onAllSectionsChanged = { presenter.setAllLyricSections(it) },
    onSectionIndexChanged = { presenter.setSongDisplaySectionIndex(it) },
    onLineIndexChanged = { presenter.setSongDisplayLineIndex(it) },
)

/** The first output, exactly as its window draws it — cropped out of each capture by [DemoTour]. */
@Composable
private fun Projector(presenter: PresenterManager, appSettings: AppSettings, media: MediaViewModel) {
    val mode by presenter.slideContent
    Box(
        Modifier.padding(start = GAP.dp, top = ((APP_H - PROJ_H) / 2).dp)
            .size(PROJ_W.dp, PROJ_H.dp)
            .background(Color.Black),
    ) {
        PresenterOutputContent(
            screenAssignment = appSettings.projectionSettings.getAssignment(0),
            effectiveMode = mode,
            screenNumber = 1,
            presenterManager = presenter,
            appSettings = appSettings,
            mediaViewModel = media,
            sttManager = STTManager(),
            serverUrl = "",
            qaDisplayUrl = "",
            identifyingScreen = false,
            lottieComposition = null,
            clearAnnouncementOnFinish = {},
        )
    }
}

/** A control on screen: how to drive it, and where it is drawn. */
internal class Target(val node: SemanticsNodeInteraction, val bounds: Rect)

/** What names a tour: its file names, the tab it shows, the projector scene's label, its step count. */
internal class TourSetup(
    val name: String,
    val tab: String,
    val liveLabel: String,
    val steps: Int,
    val hasPanel: Boolean = false,
    val heading: Pair<String, String> = "The " to " Tab",
)

internal class DemoTour(
    setup: TourSetup,
    val ui: ComposeUiTest,
    private val presenter: PresenterManager,
    private val out: File,
    private val changeSettings: ((AppSettings) -> AppSettings) -> Unit,
    private val narrator: DemoNarrator,
) {
    private val name = setup.name
    private val tab = setup.tab
    private val hasPanel = setup.hasPanel
    private val d = DENSITY
    private val appW = APP_W * d
    private val appH = APP_H * d
    private val stage = DemoStage(appW, appH, setup.heading)
    private var frame = 0
    private var stepNumber = 0

    /** The open panel's own size in dp — each dialog is drawn top-left in the panel area at its size. */
    private var panelSize = Size(PANEL_W, PANEL_H)

    private val scene: Scene = capture().let { (app, projector, panel) ->
        Scene(app, projector, Camera.full(appW, appH)).apply {
            this.panel = panel
            header = setup.tab to setup.liveLabel
            steps = setup.steps
            cursor = Pt(appW * 0.55f, appH * 0.6f)
        }
    }

    // ── Finding controls (main window only, never the projector) ──────────

    fun find(matcher: SemanticsMatcher, where: (Rect) -> Boolean = { true }): Target {
        val nodes = ui.onAllNodes(matcher)
        val hit = nodes.fetchSemanticsNodes(atLeastOneRootRequired = false).withIndex()
            .firstOrNull { inApp(it.value) && where(it.value.boundsInWindow) }
            ?: error("nothing on screen matches $matcher")
        return Target(nodes[hit.index], hit.value.boundsInWindow)
    }

    fun dp(value: Float) = value * d

    /** [text] inside the open menu or dropdown — a compose root of its own, listed after the window. */
    fun menuItem(text: String): Target {
        val nodes = ui.onAllNodes(hasText(text))
        val hit = nodes.fetchSemanticsNodes().withIndex().last()
        return Target(nodes[hit.index], hit.value.boundsInWindow)
    }

    /** Every match, top to bottom. */
    fun findAll(matcher: SemanticsMatcher, where: (Rect) -> Boolean = { true }): List<Target> {
        val nodes = ui.onAllNodes(matcher)
        return nodes.fetchSemanticsNodes(atLeastOneRootRequired = false).withIndex()
            .filter { inApp(it.value) && where(it.value.boundsInWindow) }
            .sortedBy { it.value.boundsInWindow.top }
            .map { Target(nodes[it.index], it.value.boundsInWindow) }
    }

    fun settle() = ui.settleLive(presenter)

    /** What a live web page puts on the outputs, without a browser: [title] and a picture of it. */
    fun showWebPage(title: String, page: BufferedImage) {
        presenter.setPresentingMode(Presenting.WEBSITE)
        presenter.setWebPageTitle(title)
        presenter.setWebSnapshot(page.toComposeImageBitmap())
        ui.waitForIdle()
    }

    /** True once the selected verses lead with [text] — what Go Live would show first. */
    fun selectionLeads(text: String) =
        presenter.selectedVerses.value.firstOrNull()?.verseText?.contains(text) == true

    /** Changes the app's settings off camera, as the Settings window would, and fades to the result. */
    fun changeSettings(change: (AppSettings) -> AppSettings, settled: () -> Boolean = { true }) {
        changeSettings.invoke(change)
        waitFor("the settings change took effect", condition = settled)
        refresh(0.4)
    }

    /** True when [text] is on screen anywhere, the projector included. */
    fun showing(text: String) = ui.onAllNodes(hasText(text, substring = true))
        .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    /** A click that is not part of the lesson: no pointer, no callout. */
    fun quietClick(target: Target) {
        scene.focus = null
        target.node.performClick()
        ui.waitForIdle()
        refresh(0.2)
    }

    fun text(text: String, inTabArea: Boolean = true) =
        find(hasText(text, substring = true)) { !inTabArea || it.left > TAB_AREA_LEFT * d }

    fun tab(label: String) = find(hasText(label)) { it.top < 60f * d }

    /** The tab's search box: the topmost text field right of the schedule. */
    fun searchField(): Target {
        val nodes = ui.onAllNodes(hasSetTextAction())
        val hit = nodes.fetchSemanticsNodes().withIndex()
            .filter { inApp(it.value) && it.value.boundsInWindow.left > TAB_AREA_LEFT * d }
            .minBy { it.value.boundsInWindow.top }
        return Target(nodes[hit.index], hit.value.boundsInWindow)
    }

    /** The tab's own Go Live: the rightmost enabled one, once whatever it would show has loaded. */
    fun goLiveButton(): Target {
        val live = hasContentDescription("Go Live") and isEnabled()
        waitFor("the tab's Go Live button is enabled") { hasNode(live) { it.left > TAB_AREA_LEFT * d } }
        val nodes = ui.onAllNodes(live)
        val hit = nodes.fetchSemanticsNodes().withIndex().filter { inApp(it.value) }
            .maxBy { it.value.boundsInWindow.left }
        return Target(nodes[hit.index], hit.value.boundsInWindow)
    }

    fun hasNode(matcher: SemanticsMatcher, where: (Rect) -> Boolean = { true }) =
        ui.onAllNodes(matcher).fetchSemanticsNodes(atLeastOneRootRequired = false)
            .any { inApp(it) && where(it.boundsInWindow) }

    fun waitFor(what: String, timeoutMs: Long = 15_000L, condition: () -> Boolean) {
        ui.waitUntil(what, timeoutMs) {
            Snapshot.sendApplyNotifications()
            condition()
        }
        ui.waitForIdle()
    }

    private fun inApp(node: SemanticsNode) = node.boundsInWindow.left < APP_W * d

    // ── The storyboard's scenes ───────────────────────────────────────────

    /** 01 Intro, then 02 Establish: the title card, then the whole window with its caption. */
    fun intro(subtitle: String) {
        val start = frame
        val talk = narrate("The $tab tab", subtitle)
        repeat(frames(4.0)) { age -> write(stage.intro(tab, subtitle, age)) }
        scene.camera = Camera.full(appW, appH).let { full ->
            val c = full.dst.center
            val hw = full.dst.width * 0.47f
            val hh = full.dst.height * 0.47f
            full.copy(dst = Rect(c.x - hw, c.y - hh, c.x + hw, c.y + hh))
        }
        scene.caption = "The main window — schedule, tabs, live preview"
        scene.captionAge = 0
        camTo(Camera.full(appW, appH), 0.6)
        hold(2.4)
        padTo(start, talk)
        scene.caption = null
    }

    /** 06 Outro: the tab's own title again, held for YouTube's end-screen cards. */
    fun outro() {
        val talk = narrate("That's the $tab tab", "")
        repeat(frames(maxOf(4.0, talk + 2.0))) { age -> write(stage.outro(tab, age)) }
    }

    /**
     * One numbered step (04 Feature step): the camera moves in on [target], its outline and tooltip
     * appear, the pointer glides over and clicks, and the frame crossfades to what the click did. A
     * [live] step plays in the projector scene (05) instead, and sends what it did to the screen.
     */
    fun step(
        heading: String,
        sub: String,
        target: Target,
        double: Boolean = false,
        rightButton: Boolean = false,
        keycap: String? = null,
        live: Boolean = false,
        numbered: Boolean = true,
        zoom: Float = 1.5f,
        deferPad: Boolean = false,
        aim: Pt = target.bounds.center.let { Pt(it.x, it.y) },
        action: () -> Unit = {
            if (double) target.node.performTouchInput { doubleClick() } else target.node.performClick()
        },
    ) {
        val n = if (numbered) ++stepNumber else null
        scene.step = stepNumber
        val start = frame
        val talk = narrate(heading, sub)
        scene.focus = null
        scene.caption = null
        camTo(if (live) Camera.projector(appW, appH) else Camera.zoom(target.bounds, zoom, appW, appH))
        scene.focus = Focus(target.bounds, n, if (numbered) heading else tabTitle(heading), sub, compact = live)
        scene.focusAge = 0
        scene.keycap = keycap
        scene.pointerTag = when {
            rightButton -> "Right-click"
            double -> "Double-click"
            else -> null
        }
        glide(aim)
        hold(0.4)
        click()
        if (double) click()
        action()
        // The drawn pointer is the one the viewer follows; the real one leaves, so no tooltip lingers.
        runCatching { ui.onAllNodes(isRoot())[0].performMouseInput { exit() } }
        ui.waitForIdle()
        // A click can scroll its list; the outline follows the control rather than where it was.
        val moved = runCatching { target.node.fetchSemanticsNode().boundsInWindow }.getOrNull()
        // Only a scroll (same column, same size): a control whose label changed now names another one.
        val focus = scene.focus
        if (moved != null && focus != null && sameColumn(moved, target.bounds)) {
            scene.focus = Focus(moved, focus.step, focus.title, focus.body, focus.compact)
        }
        refresh(0.3, live)
        hold(0.35)
        scene.keycap = null
        scene.pointerTag = null
        if (deferPad) spoken = start to talk else padTo(start, talk)
    }

    private fun sameColumn(a: Rect, b: Rect) = abs(a.left - b.left) < 2f && abs(a.width - b.width) < 2f

    /** 03 Tab select: a 2× zoom on the tab bar; clicked again off camera until it really is open. */
    fun openTab(label: String, sub: String) {
        val target = tab(label)
        step("Open the $label tab", sub, target, numbered = false, zoom = 2f) {
            repeat(5) {
                if (hasNode(hasText(label) and isSelected()) { it.top < 60f * d }) return@step
                target.node.performClick()
                ui.waitForIdle()
            }
        }
    }

    /** A right-click on [target]: opens its menu. */
    fun rightClick(heading: String, sub: String, target: Target) =
        step(heading, sub, target, rightButton = true) {
            target.node.performMouseInput { rightClick() }
        }

    /** A click with Shift held. */
    fun shiftClick(heading: String, sub: String, target: Target) =
        step(heading, sub, target, keycap = "Shift") {
            target.node.performKeyInput { keyDown(Key.ShiftLeft) }
            target.node.performClick()
            target.node.performKeyInput { keyUp(Key.ShiftLeft) }
        }

    /** A step that presses [key] — drawn as a keycap beside [area] — and fades to what it did. */
    fun press(
        heading: String,
        sub: String,
        area: Target,
        key: Key,
        label: String,
        live: Boolean = false,
        after: () -> Unit = {},
    ) {
        val n = ++stepNumber
        scene.step = stepNumber
        val start = frame
        val talk = narrate(heading, sub)
        scene.focus = null
        camTo(Camera.zoom(area.bounds, 1.5f, appW, appH))
        scene.focus = Focus(area.bounds, n, heading, sub, compact = false)
        scene.focusAge = 0
        scene.keycap = label
        hold(1.4)
        ui.onAllNodes(isRoot())[0].performKeyInput { pressKey(key) }
        ui.waitForIdle()
        after()
        if (live) awaitProjectorChange()
        refresh(0.3)
        hold(0.6)
        scene.keycap = null
        padTo(start, talk)
    }

    /** A step that clicks into [field] and types [text] a letter at a time, then optionally Enter. */
    fun type(heading: String, sub: String, field: Target, text: String, enter: Boolean = false) {
        step(heading, sub, field, deferPad = true, aim = Pt(field.bounds.left + 120f * d, field.bounds.center.y))
        for (i in 1..text.length) {
            fieldAt(field.bounds).performTextReplacement(text.take(i))
            ui.waitForIdle()
            capture().let { (app, projector) ->
                scene.app = app
                scene.projector = projector
            }
            hold(if (text.length > 20) 0.05 else 0.1)
        }
        hold(0.5)
        spoken?.let { (start, talk) -> padTo(start, talk) }
        spoken = null
        if (enter) {
            scene.keycap = "Enter ⏎"
            hold(0.7)
            fieldAt(field.bounds).performKeyInput { pressKey(Key.Enter) }
            ui.waitForIdle()
            scene.keycap = null
            refresh(0.3)
        }
    }

    /** The text field drawn where [bounds] is: found afresh, since a field can be rebuilt as it is typed in. */
    private fun fieldAt(bounds: Rect): SemanticsNodeInteraction {
        val nodes = ui.onAllNodes(hasSetTextAction())
        val hit = nodes.fetchSemanticsNodes().withIndex()
            .firstOrNull { it.value.boundsInWindow.contains(bounds.center) } ?: error("no text field at $bounds")
        return nodes[hit.index]
    }

    /** Scrolls [target] into view off camera, so the next step can point at it. */
    fun reveal(target: () -> Target): Target {
        scene.focus = null
        target().node.performScrollTo()
        ui.waitForIdle()
        refresh(0.25)
        return target()
    }

    /** Scrolls the list that shows [visible] until [wanted] is in it, off camera. */
    fun scrollList(visible: String, wanted: SemanticsMatcher, where: (Rect) -> Boolean = { true }): Target {
        scene.focus = null
        val lists = ui.onAllNodes(hasScrollAction() and hasAnyDescendant(hasText(visible)))
        val narrowest = lists.fetchSemanticsNodes().withIndex().filter { inApp(it.value) }
            .minBy { it.value.boundsInWindow.width }
        lists[narrowest.index].performScrollToNode(wanted)
        ui.waitForIdle()
        refresh(0.25)
        return find(wanted, where)
    }

    fun goLive(target: Target = goLiveButton(), sub: String = "Sends it to the projector") =
        step("Click Go Live", sub, target, live = true) {
            target.node.performClick()
            ui.waitForIdle()
            ui.settleLive(presenter)
            holdLowerThirdOnScreen()
        }

    /**
     * A designed lower third animates out at its end, so its last frame (where the screenshot suites
     * hold it) is empty; the video holds it part way through instead, with the banner fully in.
     */
    private fun holdLowerThirdOnScreen() {
        if (presenter.slideContent.value != Presenting.LOWER_THIRD) return
        val count = presenter.lottieFrameCount.value ?: return
        val shown = (count * LOWER_THIRD_HOLD).toInt()
        presenter.setLottieCurrentFrameIndex(shown)
        ui.waitForIdle()
        if (!blank(capture().second)) return
        // The projector's own frames, rendered at its size, can lag far behind; a still of the banner,
        // rendered here the way the output screenshots do, stands in on the projector until it clears.
        projectorStill = lowerThirdStill(presenter.lottieJsonContent.value)
    }

    /** Shown on the projector instead of its capture while set — a lower third's still frame. */
    private var projectorStill: BufferedImage? = null

    private fun lowerThirdStill(json: String): BufferedImage? = runCatching {
        val w = (PROJ_W * d).toInt()
        val h = (PROJ_H * d).toInt()
        val argb = runBlocking {
            LowerThirdOffscreenRenderer(w, h).withSession(json, initialProgress = LOWER_THIRD_HOLD) { renderFrame ->
                var pixels = renderFrame(LOWER_THIRD_HOLD)
                var pumped = 1
                while (pixels.none { (it ushr 24) and 0xFF > 8 } && pumped++ < STILL_FRAMES) {
                    delay(STILL_FRAME_MS)
                    pixels = renderFrame(LOWER_THIRD_HOLD)
                }
                pixels.copyOf()
            }
        }
        BufferedImage(w, h, BufferedImage.TYPE_INT_RGB).apply {
            val g = createGraphics()
            g.color = java.awt.Color.BLACK
            g.fillRect(0, 0, w, h)
            g.drawImage(
                BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).also { it.setRGB(0, 0, w, h, argb, 0, w) },
                0,
                0,
                null,
            )
            g.dispose()
        }
    }.getOrNull()

    /** 05: the operator and the projector side by side, a caption saying what the audience sees. */
    fun showLive(heading: String = "It's live!", sub: String) {
        val talk = narrate(heading, sub)
        scene.focus = null
        camTo(Camera.projector(appW, appH))
        // The first live moment, unannotated, for the video's thumbnail.
        File(out, "thumb-app.png").takeUnless { it.exists() }?.let {
            ImageIO.write(scene.app, "png", it)
            ImageIO.write(scene.projector, "png", File(out, "thumb-projector.png"))
        }
        scene.caption = sub
        scene.captionAge = 0
        hold(maxOf(2.2, talk + 0.4))
        scene.caption = null
    }

    /**
     * One spoken line that names several controls: the pointer and the outline move to each of [stops]
     * in turn, each held for its share of the line, so whatever is being said is what is pointed at.
     */
    fun tour(vararg stops: Triple<String, String, Target>) {
        val start = frame
        val (heading, sub, _) = stops.first()
        val talk = narrate(heading, sub)
        val share = maxOf(1.6, (talk + 0.3) / stops.size)
        stops.forEach { (title, body, target) ->
            val begun = frame
            scene.focus = null
            camTo(Camera.zoom(target.bounds, 1.5f, appW, appH))
            scene.focus = Focus(target.bounds, null, title, body, compact = false)
            scene.focusAge = 0
            glide(Pt(target.bounds.right - 20f * d, target.bounds.center.y))
            val left = share - (frame - begun) / FPS.toDouble()
            if (left > 0) hold(left)
        }
        padTo(start, talk)
    }

    /** A tooltip with no click, on [target]. */
    fun note(heading: String, sub: String, target: Target, seconds: Double = 2.4) {
        val start = frame
        val talk = narrate(heading, sub)
        scene.focus = null
        camTo(Camera.zoom(target.bounds, 1.5f, appW, appH))
        scene.focus = Focus(target.bounds, null, heading, sub, compact = false)
        scene.focusAge = 0
        glide(Pt(target.bounds.right - 20f * d, target.bounds.center.y))
        hold(maxOf(0.5, seconds - 1.2))
        padTo(start, talk)
    }

    // ── A dialog, as a floating window ────────────────────────────────────

    /** A control inside the tour's panel (the dialog rendered beside the projector). */
    fun panelItem(matcher: SemanticsMatcher): Target = panelItems(matcher).firstOrNull() ?: error(
        "nothing in the panel matches $matcher; it shows: " + ui.onAllNodes(hasText("", substring = true))
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .filter { it.boundsInWindow.left >= CANVAS_W * d }
            .joinToString(" | ") { node ->
                val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("")
                "$text@${node.boundsInWindow.top.toInt()}"
            },
    )

    /** Every control in the panel matching [matcher], top to bottom. */
    fun panelItems(matcher: SemanticsMatcher): List<Target> {
        val nodes = ui.onAllNodes(matcher)
        return nodes.fetchSemanticsNodes().withIndex()
            .filter { it.value.boundsInWindow.left >= CANVAS_W * d }
            .sortedBy { it.value.boundsInWindow.top }
            .map { Target(nodes[it.index], it.value.boundsInWindow) }
    }

    /** Scrolls the panel until [target] is in view, off camera, and returns it where it now is. */
    fun panelReveal(matcher: SemanticsMatcher): Target {
        scene.focus = null
        // Below the dialog's fold a control is not in the tree at all, so the dialog is scrolled to it.
        repeat(PANEL_SCROLL_TRIES) {
            if (panelItems(matcher).isNotEmpty()) return@repeat
            panelItems(hasScrollAction()).firstOrNull()?.node
                ?.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, PANEL_SCROLL_PX * d) }
            ui.waitForIdle()
        }
        runCatching { panelItem(matcher).node.performScrollTo() }
        ui.waitForIdle()
        refresh(0.25)
        return panelItem(matcher)
    }

    /**
     * Holds a dialog's looping preview on one frame: under test an endless animation never advances,
     * so a tap [fraction] of the way along its timeline (right of [play], up to [end]) pauses it there
     * and it draws that frame directly.
     */
    fun panelScrub(play: Target, end: Target, fraction: Float) {
        val x = play.bounds.right + (end.bounds.left - play.bounds.right) * fraction
        ui.onAllNodes(isRoot())[0].performTouchInput { click(Offset(x, play.bounds.center.y)) }
        ui.waitForIdle()
        refresh(0.2)
    }

    /** A tooltip on a field in the open panel, which is then filled with [text]. */
    fun panelEdit(heading: String, sub: String, target: Target, text: String) {
        val start = frame
        val talk = narrate(heading, sub)
        scene.focus = Focus(panelToFrame(target.bounds), null, heading, sub, compact = false, onFrame = true)
        scene.focusAge = 0
        hold(1.0)
        target.node.performTextReplacement(text)
        ui.waitForIdle()
        refresh(0.3)
        hold(0.4)
        padTo(start, talk)
    }

    /** Opens the panel as a window titled [title], [width]×[height] dp, sliding in over the dimmed app. */
    fun openPanel(title: String, width: Float, height: Float) {
        panelSize = Size(width, height)
        panelOpen = true
        scene.focus = null
        scene.panelTitle = title
        refresh(0.1)
        savedCursor = scene.cursor
        scene.cursor = null
        val n = frames(0.45)
        for (i in 1..n) {
            scene.panelShown = ease(i / n.toFloat())
            emit()
        }
    }

    fun closePanel() {
        panelOpen = false
        scene.focus = null
        val n = frames(0.35)
        for (i in n - 1 downTo 0) {
            scene.panelShown = ease(i / n.toFloat())
            emit()
        }
        scene.cursor = savedCursor
        // A click inside the dialog took the keyboard from the tab; the banner it raises is not the lesson.
        if (hasNode(hasText("Keyboard focus lost", substring = true))) {
            quietClick(find(hasText("Keyboard focus lost", substring = true)))
        }
    }

    /** A tooltip on a control in the open panel; with [click], the control is pressed too. */
    fun panelNote(heading: String, sub: String, target: Target, click: Boolean = false, numbered: Boolean = false) {
        val n = if (numbered) ++stepNumber else null
        scene.step = stepNumber
        val start = frame
        val talk = narrate(heading, sub)
        scene.focus = Focus(panelToFrame(target.bounds), n, heading, sub, compact = false, onFrame = true)
        scene.focusAge = 0
        hold(1.2)
        if (click) {
            target.node.performClick()
            ui.waitForIdle()
            refresh(0.3)
        }
        hold(0.4)
        padTo(start, talk)
    }

    /**
     * Lets an open dialog catch up: a generator parses its preview off the UI thread (real time) and
     * then animates it (the test clock), so both are given time — this is a recording, not a test.
     */
    private fun settlePanel() {
        repeat(PANEL_TICK_MS.toInt() / PANEL_STEP_MS) {
            Thread.sleep(PANEL_STEP_MS.toLong())
            ui.mainClock.advanceTimeBy(PANEL_STEP_MS.toLong())
            ui.waitForIdle()
        }
    }

    private var savedCursor: Pt? = null
    private var panelOpen = false

    private fun panelToFrame(r: Rect): Rect {
        val content = panelContent(panelSize.width * d, panelSize.height * d)
        val k = content.width / (panelSize.width * d)
        val top = (APP_H - PANEL_H) / 2 * d
        return Rect(
            content.left + (r.left - CANVAS_W * d) * k,
            content.top + (r.top - top) * k,
            content.left + (r.right - CANVAS_W * d) * k,
            content.top + (r.bottom - top) * k,
        )
    }

    fun pause(seconds: Double) {
        refresh(0.2)
        hold(seconds)
    }

    // ── Narration ─────────────────────────────────────────────────────────

    /** The spoken script, `key = line`, from `demo-narration/<tour>.txt` in the test resources. */
    private val script: Map<String, String> = javaClass.getResource("/demo-narration/$name.txt")?.readText()
        ?.lines().orEmpty()
        .filter { it.isNotBlank() && !it.startsWith("#") && "=" in it }
        .associate { it.substringBefore("=").trim() to it.substringAfter("=").trim() }

    /** What the narrator says over the next step, in place of reading its callout. */
    private var nextLine: String? = null

    /** A typing step's line, padded once the typing is done rather than before it starts. */
    private var spoken: Pair<Int, Double>? = null

    /** Says the script's [key] line over the next step — full sentences, where the callout is a few words. */
    fun say(key: String) {
        nextLine = script[key] ?: error("demo-narration/$name.txt has no line \"$key\"")
    }

    /** Says the line queued by [say], or the callout itself; returns its length in seconds. */
    private fun narrate(heading: String, sub: String): Double {
        nextLine?.let {
            nextLine = null
            return narrator.say(it, frame)
        }
        val sentence = heading.trim().let { if (it.last() in ".!?…") it else "$it." }
        return narrator.say(if (sub.isBlank()) sentence else "$sentence $sub", frame)
    }

    /** Holds until the line that started at [start] has been said, with a breath after. */
    private fun padTo(start: Int, talk: Double) {
        val missing = ((talk + 0.35) * FPS).toInt() - (frame - start)
        if (missing > 0) hold(missing / FPS.toDouble())
    }

    // ── Timeline ──────────────────────────────────────────────────────────

    private fun frames(seconds: Double) = (seconds * FPS).toInt().coerceAtLeast(1)

    private fun hold(seconds: Double) {
        repeat(frames(seconds)) { emit() }
    }

    /** Moves the camera (800ms, eased) unless it is already there. */
    private fun camTo(to: Camera, seconds: Double = 0.8) {
        val from = scene.camera
        if (from.near(to)) {
            scene.camera = to
            return
        }
        val n = frames(seconds)
        for (i in 1..n) {
            scene.camera = from.lerp(to, ease(i / n.toFloat()))
            emit()
        }
    }

    /** The pointer's glide: a gentle curve, eased, 600ms. */
    private fun glide(to: Pt, seconds: Double = 0.6) {
        val from = scene.cursor ?: to
        val len = hypot(to.x - from.x, to.y - from.y)
        if (len < 2f) return
        val bend = Pt((from.x + to.x) / 2 + (to.y - from.y) * 0.12f, (from.y + to.y) / 2 - (to.x - from.x) * 0.12f)
        val n = frames(seconds)
        for (i in 1..n) {
            val t = ease(i / n.toFloat())
            val u = 1 - t
            scene.cursor = Pt(
                u * u * from.x + 2 * u * t * bend.x + t * t * to.x,
                u * u * from.y + 2 * u * t * bend.y + t * t * to.y,
            )
            emit()
        }
    }

    /** A click: the pointer dips to 0.9 and a 40px ring ripples out. */
    private fun click() {
        val n = frames(0.4)
        for (i in 0 until n) {
            scene.pressed = i < n / 3
            scene.ripple = i / n.toFloat()
            emit()
        }
        scene.pressed = false
        scene.ripple = null
    }

    /** Takes a fresh capture and crossfades to it; a [live] change also draws the arrow and pulses LIVE. */
    private fun refresh(seconds: Double, live: Boolean = false) {
        if (live) awaitProjectorChange()
        // A dialog's own animations (a generator's preview) only move when the clock does.
        if (panelOpen) settlePanel()
        val (app, projector, panel) = capture()
        scene.panel = panel
        scene.appBefore = scene.app
        scene.app = app
        scene.projectorBefore = scene.projector
        scene.projector = projector
        if (live) {
            scene.live = true
            scene.liveAge = 0
            scene.arrow = 0f
        }
        val n = maxOf(frames(seconds), if (live) frames(0.4) else 0)
        val fade = frames(if (live) 0.3 else seconds)
        for (i in 1..n) {
            scene.appFade = ease(minOf(1f, i / fade.toFloat()))
            scene.projectorFade = scene.appFade
            if (live) scene.arrow = minOf(1f, i / frames(0.4).toFloat())
            emit()
        }
        scene.appBefore = null
        scene.projectorBefore = null
        scene.appFade = 1f
        scene.projectorFade = 1f
    }

    private fun emit() {
        write(stage.render(scene))
        scene.focusAge++
        scene.captionAge++
        scene.liveAge++
    }

    private fun write(img: BufferedImage) {
        if (MAX_FRAMES != null && frame >= MAX_FRAMES) throw LengthReached()
        ImageIO.write(img, "png", File(out, "f%05d.png".format(frame++)))
    }

    /**
     * Waits (up to 3s) for the projector to show something new: a picture or slide is decoded off the
     * UI thread, so the frame after Go Live can still be the old one. Nothing changing is not a failure.
     */
    private fun awaitProjectorChange() {
        val before = scene.projector
        runCatching {
            ui.waitUntil("the projector shows the new content", 3_000L) {
                Snapshot.sendApplyNotifications()
                capture().second.let { !sameImage(it, before) && !blank(it) }
            }
        }
        ui.waitForIdle()
    }

    /** Nearly all black: a picture still decoding, not a picture. */
    private fun blank(img: BufferedImage): Boolean {
        var lit = 0
        var seen = 0
        val step = maxOf(1, img.width / 80)
        for (y in 0 until img.height step step) for (x in 0 until img.width step step) {
            val rgb = img.getRGB(x, y)
            if (((rgb shr 16) and 0xFF) + ((rgb shr 8) and 0xFF) + (rgb and 0xFF) > 120) lit++
            seen++
        }
        return lit * 200 < seen
    }

    private fun sameImage(a: BufferedImage, b: BufferedImage): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        val step = maxOf(1, a.width / 64)
        for (y in 0 until a.height step step) for (x in 0 until a.width step step) {
            if (a.getRGB(x, y) != b.getRGB(x, y)) return false
        }
        return true
    }

    /** The app's window and the projector's picture, cut from one capture of both (and any open popup). */
    private fun capture(): Triple<BufferedImage, BufferedImage, BufferedImage?> {
        // Hold-live is a deliberate operator choice; a stray modifier key in the harness must not leave it on.
        if (presenter.bibleHold.value) {
            presenter.setBibleHold(false)
            ui.waitForIdle()
        }
        val roots = ui.onAllNodes(isRoot())
        val nodes = roots.fetchSemanticsNodes()
        val base = roots[0].captureToImage().toAwtImage()
        val img = BufferedImage(base.width, base.height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.drawImage(base, 0, 0, null)
        val panelArea = panelArea()
        for (i in 1 until nodes.size) {
            val at = nodes[i].boundsInWindow
            // A dropdown's pop-up spans the whole window; only its menu is drawn, where it opened.
            if (panelOpen && at.width > panelArea.width * 1.2f) {
                val menu = roots[i].onChildren()
                menu.fetchSemanticsNodes().forEachIndexed { c, node ->
                    val m = node.boundsInWindow
                    g.drawImage(menu[c].captureToImage().toAwtImage(), m.left.toInt(), m.top.toInt(), null)
                }
                continue
            }
            // A pop-up the open dialog raised (an alert is centered on the whole window) is drawn
            // over that dialog instead, where the viewer is looking.
            val to = if (relocated(at, panelArea)) dialogPopupOrigin(at, panelArea) else Pt(at.left, at.top)
            g.drawImage(roots[i].captureToImage().toAwtImage(), to.x.toInt(), to.y.toInt(), null)
        }
        g.dispose()
        val app = img.getSubimage(0, 0, appW.toInt(), appH.toInt())
        val projector = img.getSubimage(
            ((APP_W + GAP) * d).toInt(), ((APP_H - PROJ_H) / 2 * d).toInt(), (PROJ_W * d).toInt(), (PROJ_H * d).toInt(),
        )
        val panel = if (!hasPanel) null else img.getSubimage(
            (CANVAS_W * d).toInt(), ((APP_H - PANEL_H) / 2 * d).toInt(), (panelSize.width * d).toInt(),
            (panelSize.height * d).toInt(),
        )
        if (presenter.slideContent.value != Presenting.LOWER_THIRD) projectorStill = null
        return Triple(app, projectorStill ?: projector, panel)
    }

    /** Where the open dialog sits in the window, in pixels. */
    private fun panelArea() = Rect(
        CANVAS_W * d,
        (APP_H - PANEL_H) / 2 * d,
        CANVAS_W * d + panelSize.width * d,
        (APP_H - PANEL_H) / 2 * d + panelSize.height * d,
    )

    /** An alert the dialog raised: centered on the whole window, so not already inside the dialog. */
    private fun relocated(popup: Rect, dialog: Rect) = panelOpen && popup.center.x > appW &&
        !(popup.left >= dialog.left - 1f && popup.right <= dialog.right + 1f)

    /** A pop-up of [size] drawn centered over the open dialog. */
    private fun dialogPopupOrigin(popup: Rect, dialog: Rect) =
        Pt(dialog.center.x - popup.width / 2, dialog.center.y - popup.height / 2)

    /** A control inside a pop-up the open dialog raised, where it is drawn (over the dialog). */
    fun dialogPopupItem(matcher: SemanticsMatcher): Target {
        val roots = ui.onAllNodes(isRoot()).fetchSemanticsNodes().drop(1)
        val nodes = ui.onAllNodes(matcher)
        val hit = nodes.fetchSemanticsNodes().withIndex().last()
        val b = hit.value.boundsInWindow
        val popup = roots.firstOrNull { it.boundsInWindow.contains(b.center) }?.boundsInWindow
            ?: return Target(nodes[hit.index], b)
        if (!relocated(popup, panelArea())) return Target(nodes[hit.index], b)
        val origin = dialogPopupOrigin(popup, panelArea())
        val dx = origin.x - popup.left
        val dy = origin.y - popup.top
        return Target(nodes[hit.index], Rect(b.left + dx, b.top + dy, b.right + dx, b.bottom + dy))
    }

    /** The tab-select tooltip is titled with the tab's own name, as in the storyboard. */
    private fun tabTitle(heading: String) = heading.removePrefix("Open the ").removeSuffix(" tab")
}

internal data class Pt(val x: Float, val y: Float)
