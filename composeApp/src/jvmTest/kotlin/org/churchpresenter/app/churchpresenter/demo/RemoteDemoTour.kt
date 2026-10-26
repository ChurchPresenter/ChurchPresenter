@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.demo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import org.churchpresenter.dialogs.RemoteEventDialogContent
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.server.RemoteEvent
import org.churchpresenter.server.RemoteEventType
import org.churchpresenter.serverui.ConnectionQrDialogContent
import org.churchpresenter.serverui.RemoteClientManager
import org.churchpresenter.serverui.ServerSettingsTab
import org.junit.Assume.assumeTrue
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test

/**
 * Connecting the mobile app: the computer's side drawn live, the phone's side from the mobile app's
 * own screens (its screenshot set, in the ChurchPresenter-Mobile checkout beside this one).
 */
class RemoteDemoTour {
    private val companion = CompanionServer(shutdownGraceMs = 0)

    @Test
    fun remote() {
        assumeTrue("ChurchPresenter-Mobile is not checked out beside this repo", MOBILE_SHOTS.isDirectory)
        try {
            recordTour(
                "remote",
                tab = "Mobile App",
                liveLabel = "From your phone",
                steps = 12,
                heading = "Connecting the " to "",
                panel = { settings, change ->
                    Row(Modifier.size(PANEL_W.dp, PANEL_H.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(DESK_W.dp, PANEL_H.dp), contentAlignment = Alignment.Center) {
                            when (desk.value) {
                                "server" -> ServerSettingsTab(settings, change, companion, RemoteClientManager())
                                "qr" -> Box(Modifier.size(QR_W.dp, QR_H.dp)) {
                                    ConnectionQrDialogContent(SERVER_URL, apiKey = null, onDismiss = {})
                                }
                                "prompt" -> Box(Modifier.size(PROMPT_W.dp, PROMPT_H.dp)) { SongRequest() }
                            }
                        }
                        Spacer(Modifier.width(GAP_DP.dp))
                        Phone(phone.value, tabs.value)
                    }
                },
            ) { remoteTour() }
        } finally {
            companion.stop()
        }
    }

    private fun DemoTour.remoteTour() {
        say("intro")
        intro("Run your service from your phone or tablet.")
        say("why")
        caption("Your phone, as a remote", "Songs, verses and Go Live from anywhere")
        computer()
        phoneSetup()
        appTabs()
        addBible()
        sendSong()
        say("mirror")
        show("output/song")
        panelNote("What's on screen", "On the phone too", phoneScreen(), numbered = true)
        closePanel()
        say("outro")
        outro()
    }

    /** The computer: the server switched on, and its connection code. */
    private fun DemoTour.computer() {
        say("server")
        desk.value = "server"
        phone.value = "splash/initial"
        openPanel("Computer and phone", PANEL_W, PANEL_H)
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
        val code = hasText("Close") or hasText("churchpresenter://", substring = true)
        point("The connection code", "This computer's address", code)
    }

    /** The phone: remote control chosen, the code scanned, connected. */
    private fun DemoTour.phoneSetup() {
        say("phone-mode")
        show("mode-picker/remote-selected")
        panelNote("Remote control", "The phone drives the computer", phoneBand(MODE_TOP, MODE_BOTTOM), numbered = true)
        say("phone-open")
        show("connect-setup/initial")
        panelNote("Connect to the computer", "One-time setup", phoneScreen())
        say("phone-scan")
        val scan = phoneBox(SCAN_NOTE_LEFT, SCAN_NOTE_TOP, SCAN_BUTTON_RIGHT, SCAN_NOTE_BOTTOM)
        panelNote("Scan QR Code", "The camera opens", scan)
        say("scanning")
        show(SCANNER)
        panelNote("Point it at the code", "It reads the address", phoneScreen())
        say("phone-manual")
        show("connect-setup/initial")
        panelNote("Or type the address", "Same Wi-Fi network", phoneBand(MANUAL_TOP, MANUAL_BOTTOM))
        say("api-key")
        desk.value = "server"
        pause(0.3)
        point("API Key Protection", "Phones need the key", hasText("API Key Protection"))
        say("api-key-phone")
        panelNote("API Key", "Only when typing the address", phoneBand(API_TOP, API_BOTTOM))
        say("connected")
        desk.value = ""
        show("status/healthy")
        panelNote("Server connected", "And what this device may do", phoneScreen(), numbered = true)
    }

    /** The app's five tabs, and the service's schedule. */
    private fun DemoTour.appTabs() {
        say("tabs")
        show("songs-tab/demo-library", withTabs = true)
        panelNote("The app's tabs", "Songs, Bible, Media, Present, More", tabBar(), numbered = true)
        TABS.forEachIndexed { i, (key, shot) ->
            say(key)
            show(shot, withTabs = true)
            panelNote(TAB_NAMES[i], TAB_SUBS[i], tab(i))
        }
        say("uploads")
        desk.value = "server"
        pause(0.3)
        point("Allow File Upload", "Photos and presentations", hasText("Allow File Upload"))
        say("uploads-phone")
        show("upload-overlay/halfway")
        panelNote("Sending to your computer", "Ready when it's done", phoneScreen(), numbered = true)
        show("upload-overlay/complete")
        desk.value = ""
        say("schedule")
        show("schedule-drawer/demo")
        panelNote("The schedule", "The service, in order, live item marked", phoneScreen(), numbered = true)
    }

    /** A Bible downloaded onto the phone itself. */
    private fun DemoTour.addBible() {
        say("bible-none")
        show("bible-empty/get-bibles")
        val getBibles = phoneBox(GET_LEFT, GET_TOP, GET_RIGHT, GET_BOTTOM)
        panelNote("Get Bibles", "None on the phone yet", getBibles, numbered = true)
        say("bible-catalog")
        show("bible-catalog/english")
        panelNote("The catalog", "By language and source", phoneScreen())
        say("bible-licence")
        show("bible-licence/catalog")
        panelNote("Copyright and licensing", "Before anything downloads", phoneScreen())
        say("bible-download")
        show("bible-install/downloading")
        pause(1.2)
        show("bible-install/ready")
        panelNote("Ready to use", "Downloaded and converted", phoneScreen())
        say("bible-pick")
        show("bible-selector/sheet")
        panelNote("Choose a translation", "The Bibles on your phone", phoneScreen())
    }

    /** A song sent from the phone: the computer asks, then puts it up with nobody at it. */
    private fun DemoTour.sendSong() {
        // The phone adds Amazing Grace to the schedule, so it starts out of it: selected, then removed
        // the way the schedule's own Remove does.
        runCatching {
            quietClick(find(hasText("Amazing Grace", substring = true)) { it.right < dp(SCHEDULE_RIGHT_DP) })
            schedule().removeSelected()
            ui.waitForIdle()
        }
        // Ready behind the panel, so the computer can take it live without a pointer on it.
        quietClick(tab("Songs"))
        runCatching { quietClick(find(hasText("Amazing Grace")) { it.left > dp(TAB_AREA_LEFT_DP) }) }
        say("song-open")
        show("songs-tab/demo-library", withTabs = true)
        panelNote("Songs", "Back to the song library", tab(0), numbered = true)
        say("song-pick")
        panelNote("Amazing Grace", "Tap it to open it", phoneBand(SONG_TOP, SONG_BOTTOM))
        show("song-detail/loaded")
        say("add")
        panelNote("Add to schedule", "The orange button", phoneBox(BUTTON_LEFT, ADD_TOP, BUTTON_RIGHT, ADD_BOTTOM))
        permission()
        say("added")
        schedule().addSong(SONG_NUMBER, "Amazing Grace", "Hymnal", SONG_ID)
        ui.waitForIdle()
        pause(0.3)
        // The schedule column, left of the tabs; the Songs tab lists the same title. The window over
        // the main one would hide it, so it steps aside for this.
        closePanel()
        val added = findAll(hasText("Amazing Grace", substring = true)) { it.right < dp(SCHEDULE_RIGHT_DP) }
            .maxBy { it.bounds.top }
        note("In the schedule", "Added from the phone", added)
        openPanel("Computer and phone", PANEL_W, PANEL_H)
        say("send")
        val present = phoneBox(PRESENT_LEFT, PRESENT_TOP, PRESENT_RIGHT, PRESENT_BOTTOM)
        panelNote("Present", "The green button", present)
        show("song-detail/projecting")
        closePanel()
        say("live")
        quietClick(goLiveButton())
        showLive("It's live", "Sent from the phone")
        say("clients")
        desk.value = "server"
        openPanel("Computer and phone", PANEL_W, PANEL_H)
        point("Remote Clients", "Allowed and blocked devices", hasText("Remote Clients"))
        desk.value = ""
    }

    /** The computer's question the first time the phone changes something, and each of its answers. */
    private fun DemoTour.permission() {
        say("allow")
        desk.value = "prompt"
        pause(0.3)
        val request = hasText("Worship leader's iPhone", substring = true)
        point("Allow this device?", "Six choices", request)
        // The choices are icon buttons, named by their tooltips.
        PERMISSIONS.forEach { (key, name, sub) ->
            say(key)
            point(name, sub, hasContentDescription(name))
        }
        say("allow-session")
        point("Allow for Session", "Our choice here", hasContentDescription("Allow for Session"))
        desk.value = ""
    }

    private fun DemoTour.show(shot: String, withTabs: Boolean = false) {
        phone.value = shot
        tabs.value = withTabs
        ui.waitForIdle()
        pause(0.3)
    }

    private fun DemoTour.phoneScreen(): Target = panelItems(hasContentDescription(PHONE_LABEL)).first()

    /** A band of the phone's screen, from [top] to [bottom] as fractions of its height. */
    private fun DemoTour.phoneBand(top: Float, bottom: Float): Target = phoneBox(0f, top, 1f, bottom)

    /** A box on the phone's screen, its edges as fractions of the screen's width and height. */
    private fun DemoTour.phoneBox(left: Float, top: Float, right: Float, bottom: Float): Target {
        val p = phoneScreen()
        val b = p.bounds
        return Target(
            p.node,
            b.copy(
                left = b.left + b.width * left,
                top = b.top + b.height * top,
                right = b.left + b.width * right,
                bottom = b.top + b.height * bottom,
            ),
        )
    }

    private fun DemoTour.tabBar(): Target = panelItems(hasContentDescription(TABS_LABEL)).first()

    /** The [i]th of the five tabs along the bottom. */
    private fun DemoTour.tab(i: Int): Target {
        val bar = tabBar()
        val b = bar.bounds
        val w = b.width / TAB_NAMES.size
        return Target(bar.node, b.copy(left = b.left + w * i, right = b.left + w * (i + 1)))
    }
}

/** A phone: the mobile app's screen [shot] in a rounded black body, with its tab bar when [withTabs]. */
@Composable
private fun Phone(shot: String, withTabs: Boolean) {
    Box(
        Modifier.size(PHONE_W.dp, PHONE_H.dp)
            .clip(RoundedCornerShape(PHONE_RADIUS_DP.dp))
            .background(Color.Black)
            .border(PHONE_BEZEL_DP.dp, Color(PHONE_EDGE), RoundedCornerShape(PHONE_RADIUS_DP.dp))
            .padding(PHONE_BEZEL_DP.dp),
    ) {
        Column(Modifier.fillMaxSize().clip(RoundedCornerShape((PHONE_RADIUS_DP - PHONE_BEZEL_DP).dp))) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                if (shot == SCANNER) {
                    Scanner()
                } else {
                    image(shot)?.let {
                        // A whole phone screen fills it; a sheet or a landscape view is shown whole.
                        val fullScreen = it.width.toFloat() / it.height < PHONE_SHAPE
                        Image(
                            it,
                            contentDescription = PHONE_LABEL,
                            contentScale = if (fullScreen) ContentScale.Crop else ContentScale.Fit,
                            alignment = Alignment.TopCenter,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (shot == CONNECT) ScanButtonOver(maxWidth, maxHeight)
                }
            }
            if (withTabs) {
                image(TAB_BAR)?.let {
                    Image(
                        it,
                        contentDescription = TABS_LABEL,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * The scan button a phone with a camera shows on the connect screen, over the "No camera detected"
 * note the screenshot (taken on a desktop, which has none) shows in its place: an outlined button
 * with the QR-scanner icon, as `QrScanButton.android.kt` draws it.
 */
@Composable
private fun ScanButtonOver(width: Dp, height: Dp) {
    Box(
        Modifier.offset(x = width * SCAN_NOTE_LEFT, y = height * SCAN_NOTE_TOP)
            .size(width * (1f - SCAN_NOTE_LEFT), height * (SCAN_NOTE_BOTTOM - SCAN_NOTE_TOP))
            .background(Color(SCREEN_BG)),
        contentAlignment = Alignment.CenterStart,
    ) {
        OutlinedButton(onClick = {}) {
            Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
            Spacer(Modifier.width(SCAN_ICON_GAP_DP.dp))
            Text("Scan QR Code")
        }
    }
}

/**
 * The app's QR scanner as Android draws it: the camera, full screen, and a white close button top
 * right. The camera here sees the code the computer is showing.
 */
@Composable
private fun Scanner() {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier.align(Alignment.Center).size(SCREEN_IN_CAMERA_DP.dp)
                .background(Color(MONITOR), RoundedCornerShape(MONITOR_RADIUS_DP.dp))
                .padding(MONITOR_PAD_DP.dp),
        ) {
            Image(
                connectionQr,
                contentDescription = PHONE_LABEL,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Icon(
            Icons.Filled.Close,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding(CLOSE_PAD_DP.dp),
        )
    }
}

/** What the computer asks when the phone first adds a song to the schedule. */
@Composable
private fun SongRequest() = RemoteEventDialogContent(
    event = RemoteEvent(
        type = RemoteEventType.ADD_TO_SCHEDULE,
        title = "Amazing Grace",
        detail = "Hymnal 12",
        clientId = "b7c20e14",
        clientLabel = "Worship leader's iPhone",
    ),
    actionLabel = "Add to Schedule",
    typeIcon = Icons.Filled.Add,
    typeAccent = MaterialTheme.colorScheme.primary,
    bodyTitle = "Amazing Grace",
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

private fun image(shot: String): ImageBitmap? = phoneImages.getOrPut(shot) {
    File(MOBILE_SHOTS, "${shot}__dark.png").takeIf { it.isFile }
        ?.let { ImageIO.read(it) }
        ?.let { if (it.width.toFloat() / it.height < PHONE_SHAPE) it else trimmed(it) }
        ?.toComposeImageBitmap()
}

/**
 * [image] cut down to what differs from its corner colour, with a margin: a dialog shot on a tablet
 * canvas, fitted whole into the phone, would otherwise be a small card in a lot of empty space.
 */
private fun trimmed(image: BufferedImage): BufferedImage {
    val bg = image.getRGB(0, 0)
    fun differs(x: Int, y: Int): Boolean {
        val p = image.getRGB(x, y)
        val d = (0..2).sumOf { shift -> abs((p shr (shift * 8) and BYTE) - (bg shr (shift * 8) and BYTE)) }
        return d > TRIM_TOLERANCE
    }
    var left = image.width
    var top = image.height
    var right = 0
    var bottom = 0
    for (y in 0 until image.height) for (x in 0 until image.width) {
        if (differs(x, y)) {
            left = minOf(left, x)
            top = minOf(top, y)
            right = maxOf(right, x)
            bottom = maxOf(bottom, y)
        }
    }
    if (right <= left || bottom <= top) return image
    val x0 = (left - TRIM_MARGIN).coerceAtLeast(0)
    val y0 = (top - TRIM_MARGIN).coerceAtLeast(0)
    val x1 = (right + TRIM_MARGIN).coerceAtMost(image.width - 1)
    val y1 = (bottom + TRIM_MARGIN).coerceAtMost(image.height - 1)
    return image.getSubimage(x0, y0, x1 - x0 + 1, y1 - y0 + 1)
}

/** The code the computer's connection window shows, as the phone's camera sees it. */
private val connectionQr: ImageBitmap by lazy {
    val matrix = QRCodeWriter().encode(QR_CONTENT, BarcodeFormat.QR_CODE, QR_PX, QR_PX)
    MatrixToImageWriter.toBufferedImage(matrix).toComposeImageBitmap()
}

private val phoneImages = mutableMapOf<String, ImageBitmap?>()
private val desk = mutableStateOf("")
private val phone = mutableStateOf("splash/initial")
private val tabs = mutableStateOf(false)

/** The mobile app's screenshot set; Gradle runs a module's tests from the module's own folder. */
private val MOBILE_SHOTS = File("../../ChurchPresenter-Mobile/composeApp/screenshots").absoluteFile

private val TABS = listOf(
    "tab-songs" to "songs-tab/demo-library",
    "tab-bible" to "bible-tab/demo-library",
    "tab-media" to "media/playing",
    "tab-present" to "presentations/list",
    "tab-more" to "more/remote",
)
private val PERMISSIONS = listOf(
    Triple("perm-block-session", "Block for Session", "Nothing until it closes"),
    Triple("perm-block", "Block Permanently", "Undo under Remote Clients"),
    Triple("perm-deny", "Deny", "Just this request"),
    Triple("perm-session", "Allow for Session", "Until ChurchPresenter closes"),
    Triple("perm-always", "Allow Permanently", "Trusted from now on"),
    Triple("perm-allow", "Allow", "Just this request"),
)
private val TAB_NAMES = listOf("Songs", "Bible", "Media", "Present", "More")
private val TAB_SUBS = listOf(
    "Search by title or number",
    "Book, chapter, verses",
    "Play, pause, volume",
    "Pictures and presentations",
    "Calendar, Q&A, dictionary and more",
)

private const val SCANNER = "scanner"
private const val CONNECT = "connect-setup/initial"
private const val PHONE_SHAPE = 0.6f
private const val BYTE = 0xFF
private const val TRIM_TOLERANCE = 24
private const val TRIM_MARGIN = 12
private const val TAB_BAR = "bottom-tab-bar/remote"
private const val PHONE_LABEL = "Phone screen"
private const val TABS_LABEL = "Phone tabs"
private const val SERVER_URL = "http://192.168.1.10:8765"
private const val QR_CONTENT = "churchpresenter://connect?host=192.168.1.10&port=8765"
private const val QR_PX = 512
private const val PANEL_W = 1500f
private const val PANEL_H = 900f
private const val DESK_W = 1020f
private const val GAP_DP = 30
private const val PHONE_W = 400f
private const val PHONE_H = 850f
private const val PHONE_RADIUS_DP = 48
private const val PHONE_BEZEL_DP = 10
private const val PHONE_EDGE = 0xFF3F3F46
private const val MONITOR = 0xFFE4E4E7
private const val SCREEN_IN_CAMERA_DP = 250
private const val MONITOR_RADIUS_DP = 10
private const val MONITOR_PAD_DP = 18
private const val CLOSE_PAD_DP = 24
private const val QR_W = 560f
private const val QR_H = 680f
private const val PROMPT_W = 760f
private const val PROMPT_H = 560f
private const val TAB_AREA_LEFT_DP = 380f

// Where things sit on the phone's screens, as fractions of its width and height.
private const val MODE_TOP = 0.26f
private const val MODE_BOTTOM = 0.38f
private const val SCAN_NOTE_LEFT = 0.13f
private const val SCAN_NOTE_TOP = 0.65f
private const val SCAN_NOTE_BOTTOM = 0.705f
private const val SCAN_BUTTON_RIGHT = 0.62f
private const val SCREEN_BG = 0xFF0E0E17
private const val SCAN_ICON_GAP_DP = 8
private const val MANUAL_TOP = 0.66f
private const val MANUAL_BOTTOM = 0.95f
private const val API_TOP = 0.93f
private const val API_BOTTOM = 0.99f
private const val SONG_ID = "song-12"
private const val SONG_NUMBER = 12
private const val SCHEDULE_RIGHT_DP = 380f
private const val BUTTON_LEFT = 0.80f
private const val BUTTON_RIGHT = 0.97f
private const val ADD_TOP = 0.83f
private const val ADD_BOTTOM = 0.905f
private const val SONG_TOP = 0.155f
private const val SONG_BOTTOM = 0.26f
private const val GET_LEFT = 0.08f
private const val GET_RIGHT = 0.92f
private const val GET_TOP = 0.62f
private const val GET_BOTTOM = 0.70f
private const val PRESENT_LEFT = 0.80f
private const val PRESENT_TOP = 0.90f
private const val PRESENT_RIGHT = 0.97f
private const val PRESENT_BOTTOM = 0.99f
