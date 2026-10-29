@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.demo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.unit.dp
import org.churchpresenter.dialogs.RemoteEventDialogContent
import org.churchpresenter.dialogs.resolveRemoteEventPresentation
import org.churchpresenter.serverui.ConnectionQrDialogContent
import org.churchpresenter.serverui.RemoteClientManager
import org.churchpresenter.serverui.ServerSettingsTab
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.server.RemoteEvent
import org.churchpresenter.server.RemoteEventType
import org.junit.Assume.assumeTrue
import java.awt.BasicStroke
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.test.Test

/**
 * The mobile app's Canvas: a tablet's whiteboard put live on the outputs. The tablet is the mobile
 * app's own screens (its screenshot set, in the ChurchPresenter-Mobile checkout beside this one); the
 * projector draws the page through the real live output, as a tablet's pages arrive.
 */
class MobileCanvasDemoTour {
    private val companion = CompanionServer(shutdownGraceMs = 0)

    @Test
    fun mobileCanvas() {
        assumeTrue("ChurchPresenter-Mobile is not checked out beside this repo", CANVAS_SHOTS.isDirectory)
        try {
            record()
        } finally {
            companion.stop()
        }
    }

    private fun record() {
        recordTour(
            "mobile-canvas",
            tab = "Canvas",
            liveLabel = "From your tablet",
            steps = 20,
            heading = "Drawing on the " to "",
            panel = { settings, change ->
                Box(Modifier.size(PANEL_W.dp, PANEL_H.dp), contentAlignment = Alignment.Center) {
                    when {
                        prompt.value -> Box(Modifier.size(PROMPT_W.dp, PROMPT_H.dp)) { DrawingRequest() }
                        desk.value == "server" -> Box(Modifier.size(DESK_W.dp, PANEL_H.dp)) {
                            ServerSettingsTab(settings, change, companion, RemoteClientManager())
                        }
                        desk.value == "qr" -> Box(Modifier.size(QR_W.dp, QR_H.dp)) {
                            ConnectionQrDialogContent(SERVER_URL, apiKey = null, onDismiss = {})
                        }
                        else -> Tablet(tablet.value)
                    }
                }
            },
        ) { canvasTour() }
    }

    private fun DemoTour.canvasTour() {
        say("intro")
        intro("Draw on a tablet, live on the big screen.")
        say("why")
        caption("A whiteboard for the room", "Every stroke on the big screen")
        connect()
        openCanvas()
        tools()
        pages()
        liveDrawing()
        say("more-menu")
        show("more-menu")
        panelNote("More options", "Rename, pages, clear", tabletBox(MORE_MENU), numbered = true)
        closePanel()
        say("outro")
        outro()
    }

    /** The computer's server switched on and its code; the tablet set to remote control and connected. */
    private fun DemoTour.connect() {
        say("server")
        desk.value = "server"
        openPanel("Computer and tablet", PANEL_W, PANEL_H)
        point("Settings, Server", "On the computer", hasText("Companion Server"))
        say("start")
        val toggle = panelItems(isToggleable()).firstOrNull()
        if (toggle != null) {
            panelNote("Companion Server", "Switch it on", toggle, click = true, numbered = true)
        } else {
            caption("Companion Server", "Switch it on")
        }
        say("qr")
        desk.value = "qr"
        pause(0.3)
        point("The connection code", "This computer's address", CONNECTION_CODE)
        desk.value = ""
        say("tablet-mode")
        show("mode-picker-tablet/remote-selected")
        panelNote("Remote control", "The tablet drives the computer", tabletBox(REMOTE_CARD), numbered = true)
        say("scan")
        desk.value = "qr"
        pause(0.3)
        point("Scan the code", "With the tablet's camera", CONNECTION_CODE)
        desk.value = ""
        say("manual")
        show("settings-tablet/server")
        panelNote("Or type the address", "Settings, Server", tabletBox(ADDRESS), numbered = true)
        say("connected")
        show("status-tablet/healthy")
        panelNote("Server connected", "And what this device may do", tabletBox(CONNECTED))
    }

    /** Canvas found in More, opened blank, and its tool rail. */
    private fun DemoTour.openCanvas() {
        say("open")
        show("more-two-pane/nothing-open")
        panelNote("More, then Canvas", "Sketch, write and draw", tabletBox(CANVAS_ENTRY), numbered = true)
        show("empty")
        say("empty")
        panelNote("A blank page", "Ready to draw on", tabletScreen())
        say("rail")
        panelNote("The tools", "Down the left side", tabletBox(RAIL), numbered = true)
    }

    /** Each tool, with its options where it has them. */
    private fun DemoTour.tools() {
        show("pen-dock")
        say("pen")
        panelNote("Pen", "Your finger or a stylus", tabletBox(tool(PEN)), numbered = true)
        say("stylus")
        panelNote("Use a stylus", "For better accuracy", tabletScreen())
        say("pen-bar")
        panelNote("The pen bar", "Color, thickness, opacity", tabletBox(PEN_BAR))
        TOOL_OPTIONS.forEach { stop ->
            say(stop.key)
            show(stop.shot)
            panelNote(stop.heading, stop.sub, tabletBox(stop.box), numbered = stop.key in NUMBERED)
        }
        say("snap")
        show("options-shapes")
        panelNote("Snap to shapes", "A rough circle comes out neat", tabletBox(SNAP))
        say("select")
        show("shape-selected")
        panelNote("Select", "Move, resize, turn", tabletBox(SELECTED), numbered = true)
        say("layers")
        show("layers")
        panelNote("Layers", "Hide, lock, reorder", tabletBox(LAYERS), numbered = true)
    }

    /** The pages along the bottom, and the save it says it has made. */
    private fun DemoTour.pages() {
        say("pages")
        show("many-pages")
        panelNote("Pages", "Plus adds a page", tabletBox(MANY_PAGES), numbered = true)
        say("saved")
        show("pen-dock")
        panelNote("Saved", "As you work", tabletBox(SAVED))
    }

    /**
     * Go Live: the computer's prompt, the page on the projector, strokes arriving on it, the drawing
     * stepping aside for a song, back again, and ended.
     */
    private fun DemoTour.liveDrawing() {
        say("go-live")
        panelNote("Go Live", "Put the page on the big screen", tabletBox(GO_LIVE), numbered = true)
        say("ask")
        prompt.value = true
        pause(0.3)
        val allow = panelItem(hasContentDescription("Allow for Session"))
        panelNote("Allow for Session", "Then it goes straight up", allow)
        prompt.value = false
        show("live")
        closePanel()
        val (page, screen) = strokes()
        showDrawing(pageOf(page.first()), PAPER)
        say("live")
        captionWhile("It's live", "Every stroke as you draw it", projector = true) {
            pause(0.6)
            page.drop(1).forEachIndexed { i, step ->
                tablet.value = screen[i + 1]
                showDrawing(pageOf(step), PAPER)
                pause(0.15)
            }
            pause(1.0)
        }
        say("step-down")
        quietClick(tab("Songs"))
        runCatching { quietClick(find(hasText("Amazing Grace")) { it.left > dp(TAB_AREA_LEFT_DP) }) }
        goLive(sub = "The drawing steps aside")
        tablet.value = null
        show("pen-dock")
        openPanel("Tablet", PANEL_W, PANEL_H)
        say("go-live-again")
        panelNote("Go Live again", "Back on the big screen", tabletBox(GO_LIVE))
        show("live")
        tablet.value = screen.last()
        closePanel()
        showDrawing(pageOf(page.last()), PAPER)
        pause(0.4)
        say("end-live")
        openPanel("Tablet", PANEL_W, PANEL_H)
        panelNote("End Live", "Takes the drawing down", tabletBox(GO_LIVE), numbered = true)
        closePanel()
        endDrawing()
        pause(0.4)
        say("cleared")
        showLive("Cleared", "The screen is clear again")
        tablet.value = null
        show("more-menu")
        openPanel("Tablet", PANEL_W, PANEL_H)
    }

    private fun DemoTour.show(shot: String) {
        tablet.value = null
        shotName.value = shot
        ui.waitForIdle()
        pause(0.3)
    }

    private fun DemoTour.tabletScreen(): Target = panelItems(hasContentDescription(TABLET_LABEL)).first()

    /** A box on the tablet's screen, its edges as fractions of the screen's width and height. */
    private fun DemoTour.tabletBox(box: Rect): Target {
        val p = tabletScreen()
        val b = p.bounds
        return Target(
            p.node,
            Rect(
                b.left + b.width * box.left,
                b.top + b.height * box.top,
                b.left + b.width * box.right,
                b.top + b.height * box.bottom,
            ),
        )
    }
}

private data class ToolStop(val key: String, val shot: String, val heading: String, val sub: String, val box: Rect)

/** A tablet: the mobile app's screen in a rounded dark body, landscape. */
@Composable
private fun Tablet(drawn: ImageBitmap?) {
    Box(
        Modifier.size(TABLET_W.dp, TABLET_H.dp)
            .clip(RoundedCornerShape(TABLET_RADIUS_DP.dp))
            .background(Color(TABLET_BODY))
            .border(2.dp, Color(TABLET_EDGE), RoundedCornerShape(TABLET_RADIUS_DP.dp))
            .padding(TABLET_BEZEL_DP.dp),
    ) {
        // A screen shot shorter than the tablet's sits centered on the app's own background.
        Box(
            Modifier.fillMaxSize()
                .clip(RoundedCornerShape((TABLET_RADIUS_DP - TABLET_BEZEL_DP).dp))
                .background(Color(SCREEN_BG)),
            contentAlignment = Alignment.Center,
        ) {
            (drawn ?: image(shotName.value))?.let {
                Image(
                    it,
                    contentDescription = TABLET_LABEL,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxWidth().aspectRatio(it.width.toFloat() / it.height),
                )
            }
        }
    }
}

/** What the computer asks when the tablet first goes live with its drawing. */
@Composable
private fun DrawingRequest() {
    val event = RemoteEvent(
        type = RemoteEventType.PROJECT,
        title = "Sunday sermon sketch",
        detail = "Drawing",
        clientId = "c41d09a7",
        clientLabel = "Pastor's iPad",
    )
    val shown = resolveRemoteEventPresentation(event, 1, isClientKnownAllowed = false, isClientKnownBlocked = false)
    RemoteEventDialogContent(
        event = event,
        actionLabel = shown.actionLabel,
        typeIcon = shown.typeIcon,
        typeAccent = shown.typeAccent,
        bodyTitle = shown.bodyTitle,
        remaining = 0,
        showAllowPermanently = true,
        isClientKnownAllowed = false,
        isClientKnownBlocked = false,
        isInstanceLinkFollower = false,
        onAllow = {},
        onAllowForSession = {},
        onAllowPermanently = {},
        onBlockForSession = {},
        onBlockPermanently = {},
        onDeny = {},
    )
}

/**
 * A heart drawn on the live page a stroke at a time: each step's whole tablet screen, and the same
 * moment as the full-size picture of it the page is cut from.
 */
private fun strokes(): Pair<List<BufferedImage>, List<ImageBitmap>> {
    val base = ImageIO.read(File(CANVAS_SHOTS, "live__dark.png"))
    val points = (0..HEART_POINTS).map { i ->
        val t = 2 * PI * i / HEART_POINTS
        val x = 16 * sin(t).pow(3)
        val y = 13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t)
        HEART_X + x * HEART_SCALE to HEART_Y - y * HEART_SCALE
    }
    val shots = (0..HEART_STEPS).map { step ->
        val img = BufferedImage(base.width, base.height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.drawImage(base, 0, 0, null)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = java.awt.Color(HEART_COLOR)
        g.stroke = BasicStroke(HEART_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        val upTo = points.size * step / HEART_STEPS
        if (upTo > 1) {
            val path = Path2D.Double()
            path.moveTo(points[0].first, points[0].second)
            points.take(upTo).drop(1).forEach { (x, y) -> path.lineTo(x, y) }
            g.draw(path)
        }
        g.dispose()
        img
    }
    return shots to shots.map { it.toComposeImageBitmap() }
}

/** The page the tablet sends: its board, without the app's bars and tools around it. */
private fun pageOf(screen: BufferedImage): BufferedImage =
    screen.getSubimage(PAGE_LEFT, PAGE_TOP, PAGE_RIGHT - PAGE_LEFT, PAGE_BOTTOM - PAGE_TOP)

/** A Canvas screenshot by its name, or any of the app's by `section/name`. */
private fun image(shot: String): ImageBitmap? = tabletImages.getOrPut(shot) {
    File(if ('/' in shot) CANVAS_SHOTS.parentFile else CANVAS_SHOTS, "${shot}__dark.png").takeIf { it.isFile }
        ?.let { ImageIO.read(it) }
        ?.toComposeImageBitmap()
}

private fun box(left: Float, top: Float, right: Float, bottom: Float) = Rect(left, top, right, bottom)

/** The [y]th tool down the rail, its center as a fraction of the screen's height. */
private fun tool(y: Float) = box(RAIL.left, y - TOOL_HALF, RAIL.right, y + TOOL_HALF)

private val tabletImages = mutableMapOf<String, ImageBitmap?>()
private val shotName = mutableStateOf("empty")
private val tablet = mutableStateOf<ImageBitmap?>(null)
private val desk = mutableStateOf("")
private val prompt = mutableStateOf(false)

/**
 * The mobile app's Canvas screenshots: `CP_MOBILE_SHOTS` when set, else the checkout beside this one
 * (Gradle runs a module's tests from the module's own folder).
 */
private val CANVAS_SHOTS = File(
    System.getenv("CP_MOBILE_SHOTS") ?: "../../ChurchPresenter-Mobile/composeApp/screenshots",
    "canvas",
).absoluteFile

private val PAPER = Color(0xFF1B1D26)

// Where things sit on the tablet's screen, as fractions of its width and height.
private val RAIL = box(0.012f, 0.267f, 0.055f, 0.797f)
private const val TOOL_HALF = 0.022f
private const val PEN = 0.396f
private const val HIGHLIGHTER = 0.443f
private const val SHAPES = 0.548f
private const val TEXT = 0.644f
private val CANVAS_ENTRY = box(0.008f, 0.178f, 0.343f, 0.28f)
private val REMOTE_CARD = box(0.048f, 0.345f, 0.343f, 0.785f)
private val ADDRESS = box(0.258f, 0.305f, 0.982f, 0.62f)
private val CONNECTED = box(0.158f, 0.2f, 0.842f, 0.605f)
private val PEN_BAR = box(0.075f, 0.925f, 0.683f, 0.985f)
private val GO_LIVE = box(0.896f, 0.006f, 0.99f, 0.054f)
private val LAYERS = box(0.758f, 0.138f, 0.992f, 0.98f)
private val MORE_MENU = box(0.856f, 0.054f, 0.96f, 0.29f)
private val MANY_PAGES = box(0.53f, 0.925f, 0.985f, 0.985f)
private val SAVED = box(0.087f, 0.025f, 0.25f, 0.055f)
private val SNAP = box(0.063f, 0.905f, 0.275f, 0.968f)
private val SELECTED = box(0.495f, 0.2f, 0.77f, 0.47f)
private const val OPTIONS_LEFT = 0.062f
private const val OPTIONS_RIGHT = 0.278f

private val TOOL_OPTIONS = listOf(
    ToolStop(
        "pen-options", "options-pen", "Pen options", "Pen type, smoothing, pressure",
        box(OPTIONS_LEFT, 0.37f, OPTIONS_RIGHT, 0.9f),
    ),
    ToolStop("highlighter", "options-highlighter", "Highlighter", "Marks without hiding", tool(HIGHLIGHTER)),
    ToolStop(
        "eraser", "options-eraser", "Eraser", "Stroke, pixel or area",
        box(OPTIONS_LEFT, 0.465f, OPTIONS_RIGHT, 0.8f),
    ),
    ToolStop("shapes", "options-shapes", "Shapes", "Fill and border", tool(SHAPES)),
    ToolStop(
        "line", "options-line", "Line & arrow", "Straight, curve or elbow",
        box(OPTIONS_LEFT, 0.44f, OPTIONS_RIGHT, 0.975f),
    ),
    ToolStop("text", "editing-text", "Text", "Headings and references", tool(TEXT)),
    ToolStop(
        "sticky", "options-sticky", "Sticky note", "A question or a reminder",
        box(OPTIONS_LEFT, 0.62f, OPTIONS_RIGHT, 0.975f),
    ),
    ToolStop("image", "insert-image", "Image", "Photos or files", box(0.745f, 0.135f, 0.977f, 0.4f)),
)
private val CONNECTION_CODE = hasText("Close") or hasText("churchpresenter://", substring = true)
private val NUMBERED = setOf("highlighter", "eraser", "shapes", "line", "text", "sticky", "image")

// The live page, in the screenshot's pixels: the board between the bars and right of the tools.
private const val PAGE_LEFT = 100
private const val PAGE_TOP = 64
private const val PAGE_RIGHT = 1350
private const val PAGE_BOTTOM = 920

// The heart drawn while it is live: top right of the page, in the screenshot's pixels.
private const val HEART_X = 1090.0
private const val HEART_Y = 200.0
private const val HEART_SCALE = 3.6
private const val HEART_POINTS = 90
private const val HEART_STEPS = 12
private const val HEART_WIDTH = 7f
private const val HEART_COLOR = 0xF472B6

private const val TABLET_LABEL = "Tablet screen"
private const val PANEL_W = 1500f
private const val PANEL_H = 900f
private const val TABLET_W = 1150f
private const val TABLET_H = 868f
private const val TABLET_RADIUS_DP = 36
private const val TABLET_BEZEL_DP = 16
private const val TABLET_BODY = 0xFF2C2E33
private const val TABLET_EDGE = 0xFF45474D
private const val SCREEN_BG = 0xFF0E0E17
private const val PROMPT_W = 760f
private const val DESK_W = 1020f
private const val QR_W = 560f
private const val QR_H = 680f
private const val SERVER_URL = "http://192.168.1.10:8765"
private const val PROMPT_H = 560f
private const val TAB_AREA_LEFT_DP = 380f
