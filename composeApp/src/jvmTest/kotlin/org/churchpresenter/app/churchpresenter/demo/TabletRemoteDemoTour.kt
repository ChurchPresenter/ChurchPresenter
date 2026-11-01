@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.demo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.map
import org.churchpresenter.serverui.ConnectionQrDialogContent
import org.churchpresenter.serverui.RemoteClientManager
import org.churchpresenter.serverui.ServerSettingsTab
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.RemoteControlFlows
import org.churchpresenter.app.churchpresenter.screenshot.LIBRARY
import org.churchpresenter.app.churchpresenter.screenshot.library
import org.churchpresenter.dialogs.PresentationRemoteDialog
import org.churchpresenter.dialogs.RemoteEventDialogContent
import org.churchpresenter.dialogs.resolveRemoteEventPresentation
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.server.RemoteEvent
import org.churchpresenter.server.RemoteEventType
import org.churchpresenter.server.TunnelStatus
import org.churchpresenter.server.broadcastSlideChange
import org.churchpresenter.server.updatePresentation
import org.churchpresenter.server.updatePresentationLiveStatus
import org.churchpresenter.server.updateFileUploadEnabled
import org.churchpresenter.server.updatePresentationRemoteSettings
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.slides.data.RecentPresentationFiles
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import kotlin.math.abs
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.poi.xslf.usermodel.XSLFTextShape
import java.awt.Dimension
import java.awt.Color as AwtColor
import java.awt.Rectangle
import kotlin.test.Test

/**
 * Changing the slides from a tablet: the deck loaded and Remote Control switched on at the church
 * computer, the remote's own web page opened on the tablet and allowed, then the slides moved from it
 * while the big screen follows. The tablet's screen is the real page, served by the tour's own
 * companion server and drawn by headless Chrome at a tablet's size; each tap sends what the page's
 * button sends.
 */
class TabletRemoteDemoTour {
    private val companion = CompanionServer(shutdownGraceMs = 0)
    private val watcher = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val http = HttpClient.newHttpClient()

    @Test
    fun tabletRemote() {
        library()
        val sermon = File(LIBRARY, "Decks/$DECK")
        easter.parentFile.deleteRecursively()
        writeDeck(sermon, DECK_SLIDES)
        writeDeck(easter, EASTER_SLIDES)
        writeDeck(youth, YOUTH_SLIDES)
        RecentPresentationFiles.files.clear()
        RecentPresentationFiles.pinned.clear()
        RecentPresentationFiles.files.add(sermon.absolutePath)
        profile.deleteRecursively()
        companion.start(PORT, SHOWN_HOST)
        companion.updateFileUploadEnabled(false)
        // The computer's question, put to the tour's panel; a tablet already allowed goes straight through.
        watcher.launch {
            companion.onPresentationRemoteConnect.collect { request ->
                if (request.clientId in allowed) request.decision.complete(true) else asking.value = request.decision
                lastClient = request.clientId
            }
        }
        try {
            recordTour(
                "tablet-remote",
                tab = "Slides from a Tablet",
                liveLabel = "Changed from the tablet",
                steps = 15,
                heading = "" to "",
                settings = {
                    it.copy(
                        serverSettings = it.serverSettings.copy(port = PORT, serverHost = SHOWN_HOST),
                        presentationRemoteSettings = it.presentationRemoteSettings.copy(remoteControlEnabled = false),
                    )
                },
                // What the app's own window publishes and listens to, so the page sees the deck and moves it.
                publish = {
                    it.copy(
                        onPresentationSlidesLoaded = { id, path, name, type, slides, notes ->
                            if (shown?.id != id) shown = Shown(id, 0, slides.size, false)
                            companion.updatePresentation(id, path, name, type, slides, notes)
                        },
                        onSlideChanged = { id, index, total, playing ->
                            shown = Shown(id, index, total, playing)
                            companion.broadcastSlideChange(id, index, total, playing)
                        },
                    )
                },
                flows = RemoteControlFlows(
                    remotePresentationGotoFlow = companion.onPresentationGoto,
                    selectSlideFlow = companion.onSelectSlide.map { it.id to it.index },
                    uploadPresentationFlow = companion.onPresentationUploaded,
                ),
                panel = { settings, change -> Panel(settings, change) },
            ) { remoteTour() }
        } finally {
            watcher.cancel()
            companion.stop()
        }
    }

    /** What the panel beside the app shows: the dialog, the question, the server page or a tablet. */
    @Composable
    private fun Panel(settings: AppSettings, change: ((AppSettings) -> AppSettings) -> Unit) {
        LaunchedEffect(settings.presentationRemoteSettings) {
            companion.updatePresentationRemoteSettings(settings.presentationRemoteSettings, apiKey = "")
        }
        val (w, h) = when (view.value) {
            DIALOG -> DIALOG_W to DIALOG_H
            ASK -> ASK_W to ASK_H
            SERVER -> SERVER_W to SERVER_H
            QR -> QR_W to QR_H
            else -> PANEL_W to PANEL_H
        }
        Box(Modifier.size(w.dp, h.dp), contentAlignment = Alignment.Center) {
            when (view.value) {
                DIALOG -> Box(Modifier.size(DIALOG_W.dp, DIALOG_H.dp)) {
                    PresentationRemoteDialog(
                        settings = settings,
                        onSettingsChange = change,
                        serverUrl = "http://$SHOWN_HOST:$PORT",
                        apiKeyEnabled = false,
                        apiKey = "",
                        tunnelStatus = TunnelStatus.Idle,
                        tunnelUrl = "",
                        presentationDisplayUrl = "",
                        onPresentationDisplayUrlChanged = {},
                        onStartTunnel = {},
                        onStopTunnel = {},
                        onDismiss = {},
                        frame = { _, content -> content() },
                    )
                }
                ASK -> Row(
                    horizontalArrangement = Arrangement.spacedBy(PAIR_GAP_DP.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val prompt = Modifier.size(PROMPT_W.dp, PROMPT_H.dp)
                        .semantics { contentDescription = PROMPT_LABEL }
                    Box(prompt) {
                        ConnectRequest()
                    }
                    Tablet(TABLET_SMALL_W)
                }
                SERVER -> Box(Modifier.size(SERVER_W.dp, SERVER_H.dp)) {
                    ServerSettingsTab(settings, change, companion, RemoteClientManager())
                }
                QR -> Box(Modifier.size(QR_W.dp, QR_H.dp).semantics { contentDescription = QR_LABEL }) {
                    ConnectionQrDialogContent(serverUrl = companion.serverUrl.value, apiKey = null, onDismiss = {})
                }
                MOBILE -> Tablet(TABLET_W, mobile = true)
                else -> Tablet(TABLET_W)
            }
        }
    }

    private fun DemoTour.remoteTour() {
        say("intro")
        intro("The speaker runs the slides from the pulpit.")
        segment("deck")
        say("tab")
        openTab("Presentation", "On the church computer")
        say("recent")
        val deck = find(hasText(DECK)) { it.left > dp(TAB_AREA_LEFT_DP) }
        step("A recent file", "Double-click it", deck, double = true, numbered = true) {
            deck.node.performTouchInput { doubleClick() }
            waitFor("the deck is rendered", DECK_WAIT_MS) { hasNode(hasText("Slide 5")) }
        }
        say("remote-open")
        val remote = find(hasContentDescription("Open Remote Control"))
        step("Remote Control", "At the top of the slides", remote, numbered = true) {}
        view.value = DIALOG
        openPanel("Remote Control", DIALOG_W, DIALOG_H)
        say("enable")
        val enable = panelItem(hasText("Enable Remote Control"))
        panelNote("Enable Remote Control", "Switch it on", enable, click = true, numbered = true)
        say("qr")
        panelNote("The QR code", "Scan it with the tablet", qrCode())
        closePanel()
        segment("connect")
        snap()
        view.value = ASK
        openPanel("Computer and tablet", ASK_W, ASK_H)
        say("opens")
        panelNote("The remote, in the browser", "Waiting for the computer", tablet())
        say("approve")
        panelNote("Allow this tablet?", "On the church computer", panelItem(hasContentDescription(PROMPT_LABEL)))
        say("allow")
        val session = panelItem(hasContentDescription("Allow for Session"))
        panelNote("Allow for Session", "Until ChurchPresenter closes", session, click = true, numbered = true)
        snap()
        closePanel()
        view.value = TABLET
        openPanel("Tablet", PANEL_W, PANEL_H)
        say("page")
        panelNote("The slides, on the tablet", "Now, next, and every slide", tablet())
        closePanel()
        segment("present")
        say("go-live")
        goLive(sub = "The slides on the big screen")
        companion.updatePresentationLiveStatus(true)
        tap("forward", "forward-live", "Forward", FORWARD) { slide + 1 }
        tap("jump", "jump-live", "Slide five", SLIDE_FIVE) { FIVE }
        tap("backward", "backward-live", "Backward", BACKWARD) { slide - 1 }
        segment("upload")
        upload()
        segment("mobile")
        mobileApp()
        segment("end")
        say("outro")
        outro()
    }

    /**
     * One tap on the tablet: the button called out on the page, what it sends sent, the page drawn
     * again, and the big screen it changed.
     */
    private fun DemoTour.tap(line: String, liveLine: String, heading: String, box: Rect, to: () -> Int) {
        snap()
        view.value = TABLET
        openPanel("Tablet", PANEL_W, PANEL_H)
        say(line)
        val target = to()
        panelNote(heading, "Tap it", tabletBox(box), numbered = true)
        post("/api/presentation-remote/goto/$target")
        slide = target
        waitFor("slide ${target + 1} is shown", SLIDE_WAIT_MS) { hasNode(hasText("Slide ${target + 1}")) }
        settle()
        snap()
        pause(0.3)
        if (line == "forward") {
            say("notes")
            panelNote("Speaker notes", "From PowerPoint or Keynote", tabletBox(NOTES))
        }
        closePanel()
        say(liveLine)
        showLive(sub = "Slide ${target + 1}, from the tablet")
    }

    /**
     * The speaker's own deck, sent from the tablet: Upload, the file picked, the file sent as the
     * page sends it, then the slides on the tablet and the deck open on the computer.
     */
    private fun DemoTour.upload() {
        snap()
        view.value = TABLET
        openPanel("Tablet", PANEL_W, PANEL_H)
        say("upload")
        panelNote("Upload", "Tap it", tabletBox(UPLOAD), numbered = true)
        clearNote()
        picker.value = PICKER_FILES
        pause(0.3)
        say("pick")
        panelNote(EASTER, "Tap the file", panelItem(hasText(EASTER)), numbered = true)
        clearNote()
        picker.value = null
        pause(0.3)
        val data = Base64.getEncoder().encodeToString(easter.readBytes())
        post("/api/presentation-remote/upload", """{"name":"$EASTER","data":"data:$PPTX_TYPE;base64,$data"}""")
        waitFor("the uploaded deck is rendered", DECK_WAIT_MS) {
            hasNode(hasText(EASTER)) && hasNode(hasText("Slide ${EASTER_SLIDES.size}")) &&
                !hasNode(hasText("Slide ${DECK_SLIDES.size}"))
        }
        settle()
        snap()
        pause(0.3)
        say("uploaded")
        panelNote("The new slides", "Sent from the tablet", tablet())
        closePanel()
        say("upload-desk")
        note(EASTER, "Open on the church computer", find(hasText(EASTER)) { it.left > dp(TAB_AREA_LEFT_DP) })
    }

    /**
     * The other way in: the ChurchPresenter Mobile app, connected to the computer's Companion Server
     * by its QR code, and its Present tab — the computer's open presentation, slide by slide, a tap
     * putting one on the big screen, and the app's own upload.
     */
    private fun DemoTour.mobileApp() {
        say("mobile-intro")
        val gear = find(hasContentDescription("Settings")) { it.top < dp(GEAR_TOP_DP) }
        step("Settings", "On the church computer", gear) {}
        view.value = SERVER
        openPanel("Settings — Server", SERVER_W, SERVER_H)
        say("mobile-server")
        panelNote("Companion Server", "Already running", panelItem(hasText("Running")))
        say("mobile-allow")
        val label = panelItem(hasText("Allow File Upload")).bounds
        val upload = panelItems(isToggleable()).minBy { abs(it.bounds.center.y - label.center.y) }
        panelNote("Allow File Upload", "Switch it on", upload, click = true, numbered = true)
        say("mobile-qr")
        panelNote("QR", "Show the connection code", panelItem(hasText("QR")), numbered = true)
        closePanel()
        view.value = QR
        openPanel("Connection QR Code", QR_W, QR_H)
        say("mobile-qr-shown")
        panelNote("The connection code", "The server's address", panelItem(hasContentDescription(QR_LABEL)))
        closePanel()
        mobileShot.value = "screenshots/demo/connect-tablet"
        mobileSlides.value = emptyList()
        view.value = MOBILE
        openPanel("ChurchPresenter Mobile", PANEL_W, PANEL_H)
        say("mobile-scan")
        panelNote("Scan QR Code", "In the mobile app", tabletBox(SCAN), numbered = true)
        clearNote()
        showMobile("screenshots/demo/present-list", live = -1)
        say("mobile-present")
        panelNote("Present", "In the side bar", tabletBox(PRESENT_TAB), numbered = true)
        say("mobile-list")
        panelNote("The open presentation", "Every slide, from the computer", tablet())
        say("mobile-tap")
        panelNote("Slide three", "Tap it", tabletBox(tileBox(2)), numbered = true)
        val id = shown?.id.orEmpty()
        post("/api/presentations/$id/select", """{"index":2}""")
        waitFor("slide 3 is live", SLIDE_WAIT_MS) { hasNode(hasText("Slide 3")) }
        settle()
        clearNote()
        showMobile("screenshots/demo/present-live", live = 2)
        say("mobile-tap-live")
        panelNote("Live", "Marked on the tablet", tabletBox(tileBox(2)))
        closePanel()
        say("mobile-tap-screen")
        showLive(sub = "Slide 3, from the mobile app")
        view.value = MOBILE
        openPanel("ChurchPresenter Mobile", PANEL_W, PANEL_H)
        say("mobile-upload")
        panelNote("Upload Presentation", "Tap it", tabletBox(FAB_UPLOAD), numbered = true)
        clearNote()
        picker.value = MOBILE_PICKER_FILES
        pause(0.3)
        say("mobile-pick")
        panelNote(YOUTH, "Tap the file", panelItem(hasText(YOUTH)), numbered = true)
        clearNote()
        picker.value = null
        pause(0.3)
        val data = Base64.getEncoder().encodeToString(youth.readBytes())
        post("/api/presentations/upload", """{"name":"$YOUTH","data":"data:$PPTX_TYPE;base64,$data"}""")
        waitFor("the app's deck is rendered", DECK_WAIT_MS) {
            hasNode(hasText(YOUTH)) && hasNode(hasText("Slide ${YOUTH_SLIDES.size}")) &&
                !hasNode(hasText("Slide ${YOUTH_SLIDES.size + 1}"))
        }
        settle()
        showMobile("screenshots/demo/present-uploaded", live = -1)
        say("mobile-uploaded")
        panelNote("Youth Night", "Sent from the app", tablet())
        say("mobile-schedule")
        panelNote("Add to Schedule", "On the computer's schedule", tabletBox(FAB_SCHEDULE))
        say("mobile-project")
        panelNote("Project to Screen", "On, or off again", tabletBox(FAB_PROJECT))
        closePanel()
    }

    /** The app's [shot], its tiles filled with the slides the computer serves for its open deck. */
    private fun DemoTour.showMobile(shot: String, live: Int) {
        val id = shown?.id.orEmpty()
        val count = shown?.total ?: 0
        mobileSlides.value = (0 until count).mapNotNull { i ->
            val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$PORT/api/presentations/$id/slides/$i")).build()
            val bytes = http.send(request, HttpResponse.BodyHandlers.ofByteArray()).body()
            runCatching { ImageIO.read(bytes.inputStream()).toComposeImageBitmap() }.getOrNull()
        }
        mobileShot.value = shot
        mobileLive.value = live
        pause(0.3)
    }

    /** Slide [i]'s tile, as fractions of the app's screen. */
    private fun tileBox(i: Int): Rect = tile(i).let {
        Rect(it.left / MOBILE_W, it.top / MOBILE_H, it.right / MOBILE_W, it.bottom / MOBILE_H)
    }

    /** The remote's page as the tablet's browser shows it now, drawn by headless Chrome. */
    private fun DemoTour.snap() {
        val out = File(profile.parentFile, "tablet-remote.png")
        out.delete()
        // Until the computer says yes the page waits on a request, which never lets virtual time run out.
        val wait = if (allowed.isEmpty()) "--timeout=$PAGE_WAIT_MS" else "--virtual-time-budget=$PAGE_SETTLE_MS"
        val chrome = ProcessBuilder(
            CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--no-first-run",
            // Without it the shot can be a frame older than the page, from before the notes came in.
            "--run-all-compositor-stages-before-draw",
            "--window-size=$PAGE_W,$PAGE_H", "--user-data-dir=${profile.path}", wait,
            "--screenshot=${out.path}", "http://127.0.0.1:$PORT/presentation-remote",
        ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        // The page's socket says hello with the slide but not its notes, which clears them until the
        // next status poll; saying the slide again, notes and all, keeps them on the tablet's screen.
        val resend = watcher.launch {
            while (true) {
                delay(RESEND_MS)
                shown?.let { companion.broadcastSlideChange(it.id, it.index, it.total, it.playing) }
            }
        }
        if (!chrome.waitFor(CHROME_WAIT_S, TimeUnit.SECONDS)) chrome.destroyForcibly().waitFor()
        resend.cancel()
        if (out.isFile) page.value = ImageIO.read(out).toComposeImageBitmap()
        ui.waitForIdle()
    }

    private fun post(path: String, body: String = "") {
        val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$PORT$path"))
            .header("Content-Type", "application/json")
            .header("X-Device-Id", lastClient)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        http.send(request, HttpResponse.BodyHandlers.discarding())
    }

    /** The QR image itself, which has no name of its own: the square above its caption. */
    private fun DemoTour.qrCode(): Target {
        val label = panelItem(hasText("QR Code shows:"))
        val bottom = label.bounds.top - dp(QR_GAP_DP)
        val half = dp(QR_DP) / 2
        val x = label.bounds.center.x
        return Target(label.node, Rect(x - half, bottom - 2 * half, x + half, bottom))
    }

    private fun DemoTour.tablet(): Target = panelItems(hasContentDescription(TABLET_LABEL)).first()

    private fun DemoTour.tabletBox(box: Rect): Target = screenBox(box, tablet())
}

/** A tablet in landscape, showing the remote's page as the browser last drew it. */
@Composable
private fun Tablet(width: Float, mobile: Boolean = false) {
    val shot = page.value
    val inner = width - 2 * BEZEL_DP
    val height = inner * PAGE_H / PAGE_W + 2 * BEZEL_DP
    Box(
        Modifier.size(width.dp, height.dp)
            .clip(RoundedCornerShape(RADIUS_DP.dp))
            .background(Color(BODY))
            .border(2.dp, Color(EDGE), RoundedCornerShape(RADIUS_DP.dp))
            .padding(BEZEL_DP.dp),
    ) {
        if (mobile) {
            MobileScreen(Modifier.fillMaxSize().clip(RoundedCornerShape((RADIUS_DP - BEZEL_DP).dp)))
        } else if (shot != null) {
            Image(
                shot,
                contentDescription = TABLET_LABEL,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape((RADIUS_DP - BEZEL_DP).dp)),
            )
        }
        picker.value?.let { FilePicker(it, Modifier.align(Alignment.Center)) }
    }
}

/**
 * The mobile app's screen, as its own screenshot of it, with each slide the computer serves drawn
 * into its tile — the app's harness cannot load an image — and the live badge and the buttons drawn
 * back over them.
 */
@Composable
private fun MobileScreen(modifier: Modifier) {
    val shot = mobileShots.getOrPut(mobileShot.value) {
        File(MOBILE_ROOT, "${mobileShot.value}__dark.png").takeIf { it.isFile }
            ?.let { ImageIO.read(it).toComposeImageBitmap() }
    } ?: return
    val slides = mobileSlides.value
    val live = mobileLive.value
    Canvas(modifier.semantics { contentDescription = TABLET_LABEL }) {
        val scale = size.width / shot.width
        fun px(r: IntRect) = IntOffset((r.left * scale).toInt(), (r.top * scale).toInt()) to
            IntSize((r.width * scale).toInt(), (r.height * scale).toInt())
        fun shotPart(r: IntRect) {
            val (at, sized) = px(r)
            drawImage(shot, IntOffset(r.left, r.top), IntSize(r.width, r.height), at, sized)
        }
        drawImage(shot, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
        slides.forEachIndexed { i, slide ->
            val inset = if (i == live) LIVE_INSET else 0
            val (at, sized) = px(tile(i).deflate(inset))
            drawImage(slide, dstOffset = at, dstSize = sized)
        }
        if (live in slides.indices) shotPart(chip(live))
        shotPart(FABS)
    }
}

/** Slide [i]'s tile on the app's Present tab: two to a line, 16:9, in the screenshot's pixels. */
private fun tile(i: Int): IntRect {
    val left = TILE_X + (i % 2) * TILE_STEP_X
    val top = TILE_Y + (i / 2) * TILE_STEP_Y
    return IntRect(left, top, left + TILE_W, top + TILE_H)
}

/** The live badge in the corner of slide [i]'s tile. */
private fun chip(i: Int): IntRect = tile(i).let {
    IntRect(it.right - CHIP_W - CHIP_PAD, it.top + CHIP_PAD, it.right - CHIP_PAD, it.top + CHIP_PAD + CHIP_H)
}

/** The tablet's own file picker, opened by Upload: the files on the tablet it may send. */
@Composable
private fun FilePicker(files: List<Pair<String, String>>, modifier: Modifier) {
    Column(
        modifier.width(PICKER_W.dp)
            .clip(RoundedCornerShape(PICKER_RADIUS_DP.dp))
            .background(Color(PICKER_BG))
            .padding(PICKER_PAD_DP.dp),
        verticalArrangement = Arrangement.spacedBy(PICKER_GAP_DP.dp),
    ) {
        Text("Choose a file", color = Color.White, fontSize = PICKER_TITLE_SP.sp, fontWeight = FontWeight.Bold)
        files.forEach { (name, kind) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(PICKER_ROW_RADIUS_DP.dp))
                    .background(Color(PICKER_ROW)).padding(PICKER_ROW_PAD_DP.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(name, color = Color.White, fontSize = PICKER_ROW_SP.sp)
                Text(kind, color = Color(PICKER_KIND), fontSize = PICKER_ROW_SP.sp)
            }
        }
    }
}

/** What the church computer asks the first time a tablet opens the remote. */
@Composable
private fun ConnectRequest() {
    val event = RemoteEvent(
        type = RemoteEventType.PRESENTATION_CONNECT,
        title = "",
        clientId = lastClient,
        clientLabel = "Pastor's iPad",
    )
    val shown = resolveRemoteEventPresentation(event, 1, isClientKnownAllowed = false, isClientKnownBlocked = false)
    val answer: (Boolean) -> Unit = { yes ->
        if (yes) allowed += lastClient
        asking.value?.complete(yes)
        asking.value = null
    }
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
        onAllow = { answer(true) },
        onAllowForSession = { answer(true) },
        onAllowPermanently = { answer(true) },
        onBlockForSession = { answer(false) },
        onBlockPermanently = { answer(false) },
        onDeny = { answer(false) },
    )
}

private val view = mutableStateOf("")
private val picker = mutableStateOf<List<Pair<String, String>>?>(null)
private val mobileShot = mutableStateOf("")
private val mobileShots = mutableMapOf<String, ImageBitmap?>()
private val mobileSlides = mutableStateOf<List<ImageBitmap>>(emptyList())
private val mobileLive = mutableStateOf(-1)
private val page = mutableStateOf<ImageBitmap?>(null)
private val asking = mutableStateOf<CompletableDeferred<Boolean>?>(null)
private val allowed = mutableSetOf<String>()

@Volatile private var lastClient = ""
private var slide = 0

private class Shown(val id: String, val index: Int, val total: Int, val playing: Boolean)

@Volatile private var shown: Shown? = null

/** The tablet's own browser profile, so it is one device across every page it draws. */
private val profile = File(System.getenv("CP_DEMO_OUT") ?: "build/demo", "tablet-remote-chrome")

/** The speaker's own deck, as it sits on the tablet before it is sent. */
private val easter = File(profile.parentFile, "tablet-remote-files/$EASTER")
private val youth = File(profile.parentFile, "tablet-remote-files/$YOUTH")

private const val DIALOG = "dialog"
private const val ASK = "ask"
private const val TABLET = "tablet"
private const val SERVER = "server"
private const val QR = "qr"
private const val MOBILE = "mobile"
private const val YOUTH = "Youth Night.pptx"
private const val TABLET_LABEL = "Tablet screen"
private const val PROMPT_LABEL = "Connect request"
private const val QR_LABEL = "Connection code"
private const val QR_DP = 180f
private const val QR_GAP_DP = 8f
private const val CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
private const val PORT = 48_766
private const val SHOWN_HOST = "192.168.1.42"
private const val FIVE = 4
private const val DECK = "Psalm 23.pptx"
private const val EASTER = "Easter Sunday.pptx"
private const val PPTX_TYPE = "application/vnd.openxmlformats-officedocument.presentationml.presentation"

// Where the page's controls sit, as fractions of the tablet's screen; measured from the page.
private val FORWARD = Rect(0.503f, 0.93f, 0.992f, 0.99f)
private val BACKWARD = Rect(0.008f, 0.93f, 0.497f, 0.99f)
private val UPLOAD = Rect(0.926f, 0.009f, 0.993f, 0.056f)
private val NOTES = Rect(0.005f, 0.066f, 0.995f, 0.116f)

// The mobile app's controls, as fractions of its 1180x820 screen; measured from its screenshots.
private val SCAN = Rect(0.044f, 0.423f, 0.986f, 0.472f)
private val PRESENT_TAB = Rect(0.012f, 0.327f, 0.17f, 0.38f)
private val FAB_UPLOAD = Rect(0.944f, 0.773f, 0.986f, 0.834f)
private val FAB_SCHEDULE = Rect(0.944f, 0.846f, 0.986f, 0.907f)
private val FAB_PROJECT = Rect(0.944f, 0.92f, 0.986f, 0.98f)
private val FABS = IntRect(1110, 630, 1168, 808)
private const val MOBILE_W = 1180f
private const val MOBILE_H = 820f
private const val TILE_X = 228
private const val TILE_Y = 108
private const val TILE_W = 466
private const val TILE_H = 262
private const val TILE_STEP_X = 474
private const val TILE_STEP_Y = 270
private const val LIVE_INSET = 3
private const val CHIP_W = 47
private const val CHIP_H = 26
private const val CHIP_PAD = 6
private const val GEAR_TOP_DP = 60f
private const val SERVER_W = 600f
private const val SERVER_H = 440f
private const val QR_W = 380f
private const val QR_H = 520f
private val SLIDE_FIVE = Rect(0.432f, 0.825f, 0.534f, 0.907f)

private const val PAGE_W = 1180
private const val PAGE_H = 820
private const val PAGE_WAIT_MS = 3_000

// Past the page's 2.5-second status poll, which is what brings a slide's notes back after connecting.
private const val PAGE_SETTLE_MS = 8_000
private const val RESEND_MS = 300L
private const val CHROME_WAIT_S = 30L
private const val DECK_WAIT_MS = 30_000L
private const val SLIDE_WAIT_MS = 10_000L
private const val TAB_AREA_LEFT_DP = 380f
private const val PANEL_W = 1340f
private const val PANEL_H = 940f
private const val ASK_W = 1100f
private const val ASK_H = 460f
private const val DIALOG_W = 400f
private const val DIALOG_H = 720f
private const val PROMPT_W = 480f
private const val PROMPT_H = 340f
private const val PAIR_GAP_DP = 40
private const val TABLET_W = 1300f
private const val TABLET_SMALL_W = 560f
private const val BEZEL_DP = 14
private const val RADIUS_DP = 30
private const val BODY = 0xFF2C2E33
private const val PICKER_BG = 0xF21C1D21
private const val PICKER_ROW = 0xFF2A2C31
private const val PICKER_KIND = 0xFF9CA3AF
private const val PICKER_W = 760
private const val PICKER_RADIUS_DP = 18
private const val PICKER_PAD_DP = 30
private const val PICKER_GAP_DP = 16
private const val PICKER_ROW_RADIUS_DP = 10
private const val PICKER_ROW_PAD_DP = 22
private const val PICKER_TITLE_SP = 34
private const val PICKER_ROW_SP = 28
private const val NAVY = 0x14223A
private const val GOLD = 0xF2C94C
private const val DECK_W = 960
private const val DECK_H = 540
private const val TITLE_PT = 60.0
private const val SUB_PT = 30.0
private const val EDGE = 0xFF45474D

/** A sermon in PowerPoint, every slide with speaker notes for the remote to show. */
private fun writeDeck(file: File, slides: List<Triple<String, String, String>>) {
    XMLSlideShow().use { ppt ->
        ppt.pageSize = Dimension(DECK_W, DECK_H)
        slides.forEach { (title, sub, notes) ->
            val slide = ppt.createSlide()
            slide.background.fillColor = AwtColor(NAVY)
            slide.line(title, Rectangle(80, 180, 800, 100), TITLE_PT, AwtColor.WHITE, bold = true)
            slide.line(sub, Rectangle(80, 290, 800, 60), SUB_PT, AwtColor(GOLD))
            val sheet = ppt.getNotesSlide(slide)
            val body = sheet.placeholders.filterIsInstance<XSLFTextShape>()
                .firstOrNull { it.textType?.name?.contains("BODY") == true } ?: sheet.placeholders.getOrNull(1)
            body?.text = notes
        }
        file.parentFile.mkdirs()
        file.outputStream().use { ppt.write(it) }
    }
}

private fun XSLFSlide.line(
    text: String,
    at: Rectangle,
    size: Double,
    color: AwtColor,
    bold: Boolean = false,
) {
    val box = createTextBox()
    box.anchor = at
    box.clearText()
    box.addNewTextParagraph().addNewTextRun().apply {
        setText(text)
        fontSize = size
        setFontColor(color)
        isBold = bold
    }
}

private val EASTER_SLIDES = listOf(
    Triple("He Is Risen", "Easter Sunday", "Big welcome. Mention the egg hunt after the service."),
    Triple("Luke 24:1-6", "The empty tomb", "Read it slowly, and let the last line land."),
    Triple("Response", "Come and see", "Invite the band up. Close in prayer."),
)

private val YOUTH_SLIDES = listOf(
    Triple("Youth Night", "Friday, 7 PM", "Welcome the new faces by name."),
    Triple("Game Time", "Teams of four", "Five minutes, then wrap it up."),
    Triple("Psalm 119:105", "A lamp to my feet", "Ask: where do you need light this week?"),
    Triple("See You Friday", "Bring a friend", "Remind them about the retreat sign-up."),
)

private val MOBILE_PICKER_FILES = listOf(
    "Youth Night.pptx" to "PowerPoint",
    "Bulletin.pdf" to "PDF",
)

private val PICKER_FILES = listOf(
    "Easter Sunday.pptx" to "PowerPoint",
    "Youth Night.key" to "Keynote",
    "Bulletin.pdf" to "PDF",
)

private val DECK_SLIDES = listOf(
    Triple("The Lord Is My Shepherd", "Psalm 23", "Welcome everyone. Ask them to open to Psalm 23."),
    Triple("Psalm 23:1-6", "Read it together", "Read the whole psalm aloud, slowly."),
    Triple("1. He Provides", "I shall not want", "Story: the food bank volunteers this spring."),
    Triple("2. He Restores", "He restores my soul", "Pause here. Rest is not laziness."),
    Triple("3. He Walks With Us", "Through the valley", "Mention the Johnsons' family, with their permission."),
    Triple("Response", "Take a moment to pray", "Invite the band up. Close in prayer."),
)
