@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.app.churchpresenter.LiveOutputCallbacks
import org.churchpresenter.app.churchpresenter.MainDesktop
import org.churchpresenter.app.churchpresenter.MainDesktopPublishers
import org.churchpresenter.app.churchpresenter.ScheduleActions
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.WebAccessState
import org.churchpresenter.companionsurface.CompanionSatelliteViewModel
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.liveoutput.PresenterOutputContent
import org.churchpresenter.media.viewmodel.LocalMediaViewModel
import org.churchpresenter.media.viewmodel.MediaViewModel
import org.churchpresenter.qa.QAManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.screenshot.PinnedFonts
import org.churchpresenter.stt.STTManager
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import org.junit.Assume.assumeTrue
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Polygon
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.cos

// The main window and, to its right, the projector it drives — in dp.
private const val APP_W = 1600f
private const val APP_H = 1000f
private const val GAP = 48f
private const val PROJ_W = 880f
private const val PROJ_H = 495f
private const val CANVAS_W = APP_W + GAP + PROJ_W + GAP

private const val FPS = 30
private const val TAB_AREA_LEFT = 380f
private val DENSITY = System.getenv("CP_DEMO_DENSITY")?.toFloatOrNull() ?: 1.25f
private val ACCENT = Color(0x2F, 0x6F, 0xEB)
private val STAGE = androidx.compose.ui.graphics.Color(0xFF15181F)

/**
 * Records one tour into `$CP_DEMO_OUT/<name>/` as numbered PNG frames: the real main window on the
 * left, what its first output shows on the right. Skipped unless `CP_DEMO_OUT` is set, so CI never
 * records; `scripts/record_demos.sh` runs every tour and encodes the videos.
 */
internal fun recordTour(
    name: String,
    startTab: String? = null,
    settings: (AppSettings) -> AppSettings = { it },
    script: DemoTour.() -> Unit,
) {
    val root = System.getenv("CP_DEMO_OUT")?.let(::File)
    assumeTrue("CP_DEMO_OUT is not set", root != null)
    val out = File(root!!, name).apply { deleteRecursively(); mkdirs() }
    TestSingletons.latchSkikoHostOs()
    TestSingletons.latchToTestHome()
    val appSettings = settings(library())
    runSkikoComposeUiTest(size = Size(CANVAS_W, APP_H) * DENSITY, density = Density(DENSITY)) {
        var actions = ScheduleActions()
        val presenter = PresenterManager()
        val media = MediaViewModel()
        var current by mutableStateOf(appSettings)
        val changeSettings = { change: (AppSettings) -> AppSettings -> current = change(current) }
        setContent {
            PinnedFonts {
                ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                    CompositionLocalProvider(LocalMediaViewModel provides media) {
                        Row(Modifier.fillMaxSize().background(STAGE)) {
                            Box(Modifier.size(APP_W.dp, APP_H.dp)) {
                                MainDesktop(
                                    appSettings = current,
                                    onSettingsChange = changeSettings,
                                    presenterManager = presenter,
                                    companionSatelliteViewModel = CompanionSatelliteViewModel(),
                                    qaManager = QAManager(),
                                    sttManager = STTManager(),
                                    theme = ThemeMode.LIGHT,
                                    live = liveCallbacks(presenter),
                                    publish = MainDesktopPublishers(onScheduleActionsReady = { actions = it }),
                                    web = WebAccessState(serverUrl = "http://192.168.1.42:8080"),
                                )
                            }
                            Projector(presenter, current, media)
                        }
                    }
                }
            }
        }
        waitForIdle()
        serviceSchedule(actions)
        waitForIdle()
        startTab?.let {
            onNodeWithText(it).performScrollTo().performClick()
            waitForIdle()
        }
        DemoTour(this, presenter, out, changeSettings).script()
    }
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

@androidx.compose.runtime.Composable
private fun Projector(presenter: PresenterManager, appSettings: AppSettings, media: MediaViewModel) {
    val mode by presenter.slideContent
    Column(
        modifier = Modifier.width((GAP + PROJ_W + GAP).dp).fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Screen 2 · Projector",
            color = androidx.compose.ui.graphics.Color(0xFFB8C2D6),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 14.dp),
        )
        Box(
            Modifier.size(PROJ_W.dp, PROJ_H.dp)
                .border(3.dp, androidx.compose.ui.graphics.Color(0xFF3A4152))
                .background(androidx.compose.ui.graphics.Color.Black),
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
}

/** A control on screen: how to drive it, and where it is drawn. */
internal class Target(val node: SemanticsNodeInteraction, val bounds: Rect)

internal class DemoTour(
    val ui: ComposeUiTest,
    private val presenter: PresenterManager,
    private val out: File,
    private val changeSettings: ((AppSettings) -> AppSettings) -> Unit,
) {
    private val d = DENSITY
    private var frame = 0
    private var stepNumber = 0
    private var cursor = Pt(APP_W * d * 0.55f, APP_H * d * 0.6f)
    private var shot: BufferedImage = grab()

    // ── Finding controls (main window only, never the projector) ──────────

    fun find(matcher: SemanticsMatcher, where: (Rect) -> Boolean = { true }): Target {
        val nodes = ui.onAllNodes(matcher)
        val hit = nodes.fetchSemanticsNodes(atLeastOneRootRequired = false).withIndex()
            .firstOrNull { inApp(it.value) && where(it.value.boundsInWindow) }
            ?: error("nothing on screen matches $matcher")
        return Target(nodes[hit.index], hit.value.boundsInWindow)
    }

    fun dp(value: Float) = value * d

    /** Every match, top to bottom. */
    fun findAll(matcher: SemanticsMatcher, where: (Rect) -> Boolean = { true }): List<Target> {
        val nodes = ui.onAllNodes(matcher)
        return nodes.fetchSemanticsNodes(atLeastOneRootRequired = false).withIndex()
            .filter { inApp(it.value) && where(it.value.boundsInWindow) }
            .sortedBy { it.value.boundsInWindow.top }
            .map { Target(nodes[it.index], it.value.boundsInWindow) }
    }

    fun settle() = ui.settleLive(presenter)

    /** True once the selected verses lead with [text] — what Go Live would show first. */
    fun selectionLeads(text: String) =
        presenter.selectedVerses.value.firstOrNull()?.verseText?.contains(text) == true

    /** Changes the app's settings off camera, as the Settings window would, and fades to the result. */
    fun changeSettings(change: (AppSettings) -> AppSettings, settled: () -> Boolean = { true }) {
        changeSettings.invoke(change)
        waitFor("the settings change took effect", condition = settled)
        crossfadeTo(grab(), 0.4)
    }

    /** True when [text] is on screen anywhere, the projector included. */
    fun showing(text: String) = ui.onAllNodes(hasText(text, substring = true))
        .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    /** A click that is not part of the lesson: no pointer, no callout. */
    fun quietClick(target: Target) {
        target.node.performClick()
        ui.waitForIdle()
        crossfadeTo(grab(), 0.2)
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

    /** The Go Live on the row showing [rowText] — in the schedule panel, or in the tab's list. */
    fun goLiveOnRow(rowText: String, inSchedule: Boolean): Target {
        val row = text(rowText, inTabArea = !inSchedule).bounds
        return find(hasContentDescription("Go Live")) {
            it.center.y in row.top..row.bottom && (it.left > TAB_AREA_LEFT * d) != inSchedule
        }
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

    // ── The tour ──────────────────────────────────────────────────────────

    fun title(heading: String, sub: String, seconds: Double = 2.6) {
        shot = grab()
        card(heading, sub, seconds)
    }

    /**
     * One numbered step: the pointer travels to [target], the callout explains it, the click lands,
     * and the screen crossfades to what the click did.
     */
    fun step(
        heading: String,
        sub: String,
        target: Target,
        double: Boolean = false,
        rightButton: Boolean = false,
        keycap: String? = null,
        aim: Pt = target.bounds.center.let { Pt(it.x, it.y) },
        action: () -> Unit = {
            if (double) target.node.performTouchInput { doubleClick() } else target.node.performClick()
        },
    ) {
        val n = ++stepNumber
        val tip = { g: Graphics2D ->
            callout(g, target.bounds, n, heading, sub)
            keycap?.let { keycap(g, Rect(cursor.x - 250f * d, cursor.y + 40f * d, cursor.x, cursor.y + 40f * d), it) }
            if (rightButton) badge(g, "Right-click")
        }
        moveTo(aim, 0.9)
        hold(0.9, tip)
        ripple(tip)
        if (double) ripple(tip)
        action()
        ui.waitForIdle()
        crossfadeTo(grab(), 0.3, tip)
        hold(0.35, tip)
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

    /** A step that presses [key] — drawn as a keycap over [area] — and fades to what it did. */
    fun press(heading: String, sub: String, area: Target, key: Key, label: String, after: () -> Unit = {}) {
        val n = ++stepNumber
        val tip = { g: Graphics2D ->
            callout(g, area.bounds, n, heading, sub)
            keycap(g, Rect(area.bounds.center.x - 250f * d, area.bounds.top + 20f * d, area.bounds.center.x, area.bounds.top + 20f * d), label)
        }
        shot = grab()
        hold(1.6, tip)
        ui.onAllNodes(isRoot())[0].performKeyInput { pressKey(key) }
        ui.waitForIdle()
        after()
        crossfadeTo(grab(), 0.3, tip)
        hold(0.6, tip)
    }

    /** A step that clicks into [field] and types [text] a letter at a time, then optionally Enter. */
    fun type(heading: String, sub: String, field: Target, text: String, enter: Boolean = false) {
        step(heading, sub, field, aim = Pt(field.bounds.left + 120f * d, field.bounds.center.y))
        val n = stepNumber
        val tip = { g: Graphics2D -> callout(g, field.bounds, n, heading, sub) }
        for (i in 1..text.length) {
            field.node.performTextReplacement(text.take(i))
            ui.waitForIdle()
            shot = grab()
            hold(if (text.length > 20) 0.05 else 0.1, tip)
        }
        hold(0.5, tip)
        if (enter) {
            hold(0.7) { g -> tip(g); keycap(g, field.bounds, "Enter ⏎") }
            field.node.performKeyInput { pressKey(Key.Enter) }
            ui.waitForIdle()
            crossfadeTo(grab(), 0.3)
        }
    }

    /** Scrolls [target] into view off camera, so the next step can point at it. */
    fun reveal(target: () -> Target): Target {
        target().node.performScrollTo()
        ui.waitForIdle()
        crossfadeTo(grab(), 0.25)
        return target()
    }

    /** Scrolls the list that shows [visible] until [wanted] is in it, off camera. */
    fun scrollList(visible: String, wanted: SemanticsMatcher, where: (Rect) -> Boolean = { true }): Target {
        val lists = ui.onAllNodes(hasScrollAction() and hasAnyDescendant(hasText(visible)))
        val narrowest = lists.fetchSemanticsNodes().withIndex().filter { inApp(it.value) }
            .minBy { it.value.boundsInWindow.width }
        lists[narrowest.index].performScrollToNode(wanted)
        ui.waitForIdle()
        crossfadeTo(grab(), 0.25)
        return find(wanted, where)
    }

    fun goLive(target: Target = goLiveButton(), sub: String = "Sends it to the projector") =
        step("Click Go Live", sub, target) {
            target.node.performClick()
            ui.waitForIdle()
            ui.settleLive(presenter)
        }

    /** Points at the projector: what the audience sees now. */
    fun showLive(heading: String = "It's live!", sub: String) {
        val screen = projectorBounds()
        moveTo(Pt(screen.center.x, screen.bottom + 130f * d), 0.8)
        hold(3.0) { g -> callout(g, screen, null, heading, sub) }
    }

    /** A callout with no click, on [target]. */
    fun note(heading: String, sub: String, target: Target, seconds: Double = 2.4) {
        shot = grab()
        moveTo(Pt(target.bounds.right - 20f * d, target.bounds.center.y), 0.7)
        hold(seconds) { g -> callout(g, target.bounds, null, heading, sub) }
    }

    fun pause(seconds: Double) {
        shot = grab()
        hold(seconds)
    }

    private fun projectorBounds(): Rect {
        val left = (APP_W + GAP) * d
        // The label above the screen is 18sp plus 14dp of padding; the pair is centered vertically.
        val top = (APP_H - PROJ_H) / 2f * d + 18f * d
        return Rect(left, top, left + PROJ_W * d, top + PROJ_H * d)
    }

    /** The window and any open popup (a menu is a compose root of its own) drawn where it sits. */
    private fun grab(): BufferedImage {
        // Hold-live is a deliberate operator choice; a stray modifier key in the harness must not leave it on.
        if (presenter.bibleHold.value) {
            presenter.setBibleHold(false)
            ui.waitForIdle()
        }
        val roots = ui.onAllNodes(isRoot())
        val nodes = roots.fetchSemanticsNodes()
        val base = roots[0].captureToImage().toAwtImage()
        if (nodes.size == 1) return base
        val img = BufferedImage(base.width, base.height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.drawImage(base, 0, 0, null)
        for (i in 1 until nodes.size) {
            val at = nodes[i].boundsInWindow
            g.drawImage(roots[i].captureToImage().toAwtImage(), at.left.toInt(), at.top.toInt(), null)
        }
        g.dispose()
        return img
    }

    // ── Timeline ──────────────────────────────────────────────────────────

    private fun frames(seconds: Double) = (seconds * FPS).toInt().coerceAtLeast(1)

    private fun hold(seconds: Double, overlay: (Graphics2D) -> Unit = {}) {
        repeat(frames(seconds)) { emit(shot) { g -> overlay(g); pointer(g, cursor) } }
    }

    private fun moveTo(to: Pt, seconds: Double) {
        val from = cursor
        val n = frames(seconds)
        for (i in 1..n) {
            val t = ease(i / n.toFloat())
            cursor = Pt(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t)
            emit(shot) { g -> pointer(g, cursor) }
        }
    }

    private fun ripple(overlay: (Graphics2D) -> Unit) {
        val n = frames(0.4)
        for (i in 0 until n) {
            val t = i / n.toFloat()
            emit(shot) { g ->
                overlay(g)
                val r = (10f + 34f * t) * d
                g.color = Color(ACCENT.red, ACCENT.green, ACCENT.blue, (200 * (1 - t)).toInt())
                g.stroke = BasicStroke(3f * d)
                g.draw(Ellipse2D.Float(cursor.x - r, cursor.y - r, r * 2, r * 2))
                pointer(g, cursor, pressed = i < n / 3)
            }
        }
    }

    private fun crossfadeTo(next: BufferedImage, seconds: Double, overlay: (Graphics2D) -> Unit = {}) {
        val from = shot
        val n = frames(seconds)
        for (i in 1..n) {
            val alpha = ease(i / n.toFloat())
            emit(from) { g ->
                g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
                g.drawImage(next, 0, 0, null)
                g.composite = AlphaComposite.SrcOver
                overlay(g)
                pointer(g, cursor)
            }
        }
        shot = next
    }

    private fun card(heading: String, sub: String, seconds: Double) {
        val n = frames(seconds)
        for (i in 0 until n) {
            val fade = minOf(1f, i / 8f, (n - i) / 8f)
            emit(shot) { g ->
                g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.82f * fade)
                g.color = Color(16, 20, 32)
                g.fillRect(0, 0, shot.width, shot.height)
                g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fade)
                g.color = Color.WHITE
                g.font = Font("Inter", Font.BOLD, (60 * d).toInt())
                centered(g, heading, shot.width / 2f, shot.height / 2f - 10 * d)
                g.color = Color(0xB8, 0xC6, 0xE6)
                g.font = Font("Inter", Font.PLAIN, (28 * d).toInt())
                centered(g, sub, shot.width / 2f, shot.height / 2f + 48 * d)
            }
        }
    }

    private fun emit(base: BufferedImage, overlay: (Graphics2D) -> Unit) {
        val img = BufferedImage(base.width, base.height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.drawImage(base, 0, 0, null)
        overlay(g)
        g.dispose()
        ImageIO.write(img, "png", File(out, "f%05d.png".format(frame++)))
    }

    // ── Drawing ───────────────────────────────────────────────────────────

    private fun callout(g: Graphics2D, target: Rect, step: Int?, heading: String, sub: String) {
        val pad = 6f * d
        val hole = RoundRectangle2D.Float(
            target.left - pad, target.top - pad, target.width + pad * 2, target.height + pad * 2, 14f * d, 14f * d,
        )
        val canvas = g.deviceConfiguration.bounds
        val dim = Area(Rectangle(0, 0, canvas.width, canvas.height))
        dim.subtract(Area(hole))
        g.color = Color(10, 14, 24, 105)
        g.fill(dim)
        g.color = ACCENT
        g.stroke = BasicStroke(3f * d)
        g.draw(hole)

        val headFont = Font("Inter", Font.BOLD, (19 * d).toInt())
        val subFont = Font("Inter", Font.PLAIN, (14.5f * d).toInt())
        val badge = if (step != null) 30f * d else 0f
        val headW = g.getFontMetrics(headFont).stringWidth(heading) + badge
        val textW = maxOf(headW, g.getFontMetrics(subFont).stringWidth(sub).toFloat())
        val w = textW + 36f * d
        val h = 74f * d
        val below = target.bottom + pad + 16f * d + h < canvas.height
        val x = (target.center.x - w / 2).coerceIn(12f * d, canvas.width - w - 12f * d)
        val y = if (below) target.bottom + pad + 14f * d else target.top - pad - 14f * d - h

        g.color = Color(0, 0, 0, 60)
        g.fill(RoundRectangle2D.Float(x + 2 * d, y + 4 * d, w, h, 16f * d, 16f * d))
        g.color = ACCENT
        g.fill(RoundRectangle2D.Float(x, y, w, h, 16f * d, 16f * d))
        val ax = target.center.x.coerceIn(x + 20 * d, x + w - 20 * d).toInt()
        val base = if (below) y + 1 else y + h - 1
        val tip = if (below) y - 10 * d else y + h + 10 * d
        val half = (10 * d).toInt()
        g.fill(Polygon(intArrayOf(ax - half, ax, ax + half), intArrayOf(base.toInt(), tip.toInt(), base.toInt()), 3))

        var tx = x + 18f * d
        if (step != null) {
            g.color = Color.WHITE
            g.fill(Ellipse2D.Float(tx, y + 13f * d, 22f * d, 22f * d))
            g.color = ACCENT
            g.font = Font("Inter", Font.BOLD, (13 * d).toInt())
            centered(g, step.toString(), tx + 11f * d, y + 29f * d)
            tx += badge
        }
        g.color = Color.WHITE
        g.font = headFont
        g.drawString(heading, tx, y + 31f * d)
        g.color = Color(0xDD, 0xE7, 0xFF)
        g.font = subFont
        g.drawString(sub, x + 18f * d, y + 57f * d)
    }

    private fun badge(g: Graphics2D, label: String) {
        g.font = Font("Inter", Font.BOLD, (14 * d).toInt())
        val w = g.fontMetrics.stringWidth(label) + 20f * d
        val pill = RoundRectangle2D.Float(cursor.x + 22f * d, cursor.y + 22f * d, w, 28f * d, 14f * d, 14f * d)
        g.color = Color(0x1F, 0x24, 0x33)
        g.fill(pill)
        g.color = Color.WHITE
        g.drawString(label, cursor.x + 32f * d, cursor.y + 41f * d)
    }

    private fun keycap(g: Graphics2D, field: Rect, label: String) {
        g.font = Font("Inter", Font.BOLD, (16 * d).toInt())
        val w = g.fontMetrics.stringWidth(label) + 28f * d
        val x = field.left + 230f * d
        val y = field.center.y - 17f * d
        val cap = RoundRectangle2D.Float(x, y, w, 34f * d, 9f * d, 9f * d)
        g.color = Color.WHITE
        g.fill(cap)
        g.color = Color(0x33, 0x33, 0x33)
        g.stroke = BasicStroke(2f * d)
        g.draw(cap)
        g.drawString(label, x + 14f * d, y + 23f * d)
    }

    private fun pointer(g: Graphics2D, at: Pt, pressed: Boolean = false) {
        val s = d * if (pressed) 0.9f else 1f
        val xs = floatArrayOf(0f, 0f, 4.5f, 7.5f, 10f, 7f, 12f)
        val ys = floatArrayOf(0f, 17f, 13f, 20f, 19f, 12f, 12f)
        val poly = Polygon(
            IntArray(xs.size) { (at.x + xs[it] * 1.5f * s).toInt() },
            IntArray(ys.size) { (at.y + ys[it] * 1.5f * s).toInt() },
            xs.size,
        )
        g.color = Color.BLACK
        g.fill(poly)
        g.color = Color.WHITE
        g.stroke = BasicStroke(1.6f * s)
        g.draw(poly)
    }

    private fun centered(g: Graphics2D, text: String, cx: Float, baseline: Float) {
        g.drawString(text, cx - g.fontMetrics.stringWidth(text) / 2f, baseline)
    }

    private fun ease(t: Float) = ((1 - cos(t * PI)) / 2).toFloat()
}

internal data class Pt(val x: Float, val y: Float)
