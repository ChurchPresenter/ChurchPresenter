@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.demo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.unit.dp
import org.junit.Assume.assumeTrue
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * The mobile app on its own: the phone is the presenter and a TV the screen, with no computer. Both
 * are the mobile app's own screens (its screenshot and marketing sets, in the ChurchPresenter-Mobile
 * checkout beside this one, or `CP_MOBILE_SHOTS`); the main window is never shown.
 */
class StandaloneDemoTour {

    @Test
    fun standalone() {
        assumeTrue("ChurchPresenter-Mobile is not checked out beside this repo", MOBILE_ROOT.isDirectory)
        recordTour(
            "standalone",
            tab = "Standalone Mode",
            liveLabel = "From your phone",
            steps = 19,
            heading = "Using " to "",
            panel = { _, _ ->
                Box(Modifier.size(PANEL_W.dp, PANEL_H.dp), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DemoPhone(phone.value)
                        Spacer(Modifier.width(GAP_DP.dp))
                        Tv(tv.value)
                    }
                    DemoSheet(wide.value)
                }
            },
        ) { standaloneTour() }
    }

    private fun DemoTour.standaloneTour() {
        say("intro")
        intro("Your phone as the presenter. No computer needed.", establish = false)
        show("screenshots/mode-picker/standalone-selected")
        openPanel("Phone and TV", PANEL_W, PANEL_H)
        say("why")
        panelNote("No computer needed", "Small groups, outdoors, a backup", shown())
        say("mode")
        panelNote("Standalone", "Then Continue", screenBox(MODE_CARD), numbered = true)
        say("tabs")
        show(CONTROLLER)
        panelNote("The tabs", "Present, Songs, Bible, Library, More", screenBox(TAB_BAR))
        settings()
        songs()
        bibles()
        screens()
        present()
        look()
        say("announcements")
        show("screenshots/local-notices/populated")
        panelNote("Announcements", "Go live from More", screenBox(NOTICE_GO_LIVE), numbered = true)
        onTv("announcement")
        pause(1.2)
        closePanel()
        say("outro")
        outro()
    }

    /** The gear, and the three pages of it a standalone phone uses. */
    private fun DemoTour.settings() {
        say("settings")
        panelNote("Settings", "The gear, top right", screenBox(GEAR), numbered = true)
        say("settings-mode")
        show("screenshots/mode-picker/standalone-selected")
        panelNote("Mode", "Standalone, Remote control or Calendar", screenBox(MODES))
        say("settings-computer")
        show("screenshots/desktop-address/with-hint")
        panelNote("Computer", "Where songs and Bibles come from", shown())
        say("settings-appearance")
        show("screenshots/settings/appearance")
        panelNote("Appearance", "System, light or dark", shown())
    }

    /** The library on the phone: songs synced from the computer, or written here. */
    private fun DemoTour.songs() {
        say("library")
        show(LIBRARY)
        panelNote("Library", "Songs and announcements", shown(), numbered = true)
        say("sync")
        panelNote("Sync from Computer", "Same Wi-Fi as the computer", screenBox(SYNC), numbered = true)
        say("sync-books")
        show("screenshots/song-sync/idle")
        panelNote("Which songbooks?", "All of them, or just some", shown())
        say("write")
        show(LIBRARY)
        panelNote("Add", "Write your own", screenBox(ADD))
    }

    /** A Bible copied from the computer, or downloaded from the catalog after its licence. */
    private fun DemoTour.bibles() {
        say("bibles")
        show("screenshots/bible-empty/get-bibles")
        panelNote("Bibles on the phone", "Two ways to get one", shown())
        say("bible-computer")
        show("screenshots/bible-sync/two-installed")
        panelNote("From your computer", "See what your computer has", shown(), numbered = true)
        say("bible-web")
        show("screenshots/bible-empty/get-bibles")
        panelNote("From the internet", "Get Bibles", screenBox(GET_BIBLES), numbered = true)
        say("bible-filter")
        show("screenshots/bible-catalog/english")
        panelNote("The catalog", "By language and by source", shown())
        say("licence")
        show("screenshots/bible-licence/catalog")
        panelNote("Copyright and licensing", "Only Bibles you're licensed to show", shown(), numbered = true)
        say("bible-ready")
        show("screenshots/bible-install/downloading")
        pause(1.2)
        show("screenshots/bible-install/ready")
        panelNote("Ready to use", "Downloaded and converted", shown())
    }

    /** The screens the phone can present to, on iOS, on Android and in any browser. */
    private fun DemoTour.screens() {
        say("screens")
        show(CONTROLLER)
        panelNote("Screens", "At the top of Present", screenBox(SCREEN_PILL), numbered = true)
        show("screenshots/output-targets/mixed")
        say("ios")
        panelNote("iPhone and iPad", "Screen Mirroring to an Apple TV", shown())
        say("android")
        panelNote("Android", "Cast to a Chromecast", shown())
        say("web")
        show("screenshots/qr-code/display-url")
        panelNote("Web page", "Any browser, or scan the code", shown(), numbered = true)
        say("stays")
        show(CONTROLLER)
        onTv("song")
        pause(0.4)
        panelNote("Slides on the TV", "Controls on the phone", tvScreen())
    }

    /** A song sent to the Present tab, taken live, stepped through and cleared; then a verse. */
    private fun DemoTour.present() {
        onTv("")
        say("song")
        show("screenshots/song-detail/standalone")
        panelNote("Present", "The button at the bottom right", screenBox(PRESENT_BUTTON), numbered = true)
        say("present")
        show(CONTROLLER)
        panelNote("Present tab", "Preview and every verse", screenBox(PREVIEW))
        say("live")
        panelNote("Live", "On the screen", screenBox(LIVE), numbered = true)
        onTv("song")
        pause(0.8)
        say("next")
        onTv("song-split-verse")
        panelNote("Prev and Next", "Through the verses", screenBox(PREV_NEXT))
        say("blank")
        onTv("blank")
        panelNote("Blank and Clear", "Hide or take down", screenBox(BLANK_CLEAR))
        onTv("")
        pause(0.5)
        say("bible-present")
        show("screenshots/bible-detail/projecting")
        onTv("bible")
        panelNote("Bible verses", "The same way", shown(), numbered = true)
    }

    /** The Look sheet: themes, saved looks and the backdrop. */
    private fun DemoTour.look() {
        // The Look sheet is shot over the empty controller, so the screen is clear for it.
        onTv("")
        say("look")
        show("screenshots/standalone-controller/idle")
        panelNote("Look", "How slides look", screenBox(LOOK), numbered = true)
        show("screenshots/look-sheet/saved-themes")
        say("look-themes")
        panelNote("Themes and colors", "Gradient, text, size, font", shown())
        say("saved-looks")
        panelNote("Saved looks", "Ready for next week", screenBox(SAVED_LOOKS))
        say("backdrop")
        show("screenshots/standalone-controller/idle")
        panelNote("Backdrop", "Gradient, image or black", screenBox(BACKDROP))
    }

    /** A whole screen goes on the phone; a sheet floats over the phone and the TV, large enough to read. */
    private fun DemoTour.show(shot: String) {
        clearNote()
        if (isSheet(shot)) {
            wide.value = shot
        } else {
            wide.value = ""
            phone.value = shot
        }
        ui.waitForIdle()
        pause(0.3)
    }

    /** What the TV shows from now: an output [slide], or nothing. */
    private fun DemoTour.onTv(slide: String) {
        tv.value = slide
        pause(0.3)
    }

    /** The screen a note points at: the floating sheet while one is up, else the phone's. */
    private fun DemoTour.shown(): Target =
        if (wide.value.isEmpty()) phoneScreen() else panelItems(hasContentDescription(DEMO_SHEET_LABEL)).first()

    private fun DemoTour.tvScreen(): Target = panelItems(hasContentDescription(TV_LABEL)).first()
}

/** A TV on a stand, showing the output [slide] the phone sends, or dark with nothing on it. */
@Composable
private fun Tv(slide: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(TV_W.dp, TV_H.dp)
                .clip(RoundedCornerShape(TV_RADIUS_DP.dp))
                .background(Color(TV_BODY))
                .padding(TV_BEZEL_DP.dp),
        ) {
            Box(Modifier.fillMaxSize().background(Color(TV_OFF))) {
                tvImage(slide)?.let {
                    Image(
                        it,
                        contentDescription = TV_LABEL,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } ?: Box(Modifier.fillMaxSize().semantics { contentDescription = TV_LABEL })
            }
        }
        Box(Modifier.size(STAND_NECK_W.dp, STAND_NECK_H.dp).background(Color(TV_BODY)))
        Box(
            Modifier.size(STAND_FOOT_W.dp, STAND_FOOT_H.dp)
                .clip(RoundedCornerShape(STAND_FOOT_H.dp))
                .background(Color(TV_BODY)),
        )
    }
}

private fun tvImage(slide: String): ImageBitmap? = if (slide.isEmpty()) null else tvImages.getOrPut(slide) {
    File(MOBILE_ROOT, "screenshots/output/${slide}__dark.png").takeIf { it.isFile }
        ?.let { ImageIO.read(it).toComposeImageBitmap() }
}

private val tvImages = mutableMapOf<String, ImageBitmap?>()
private val phone = mutableStateOf("")
private val tv = mutableStateOf("")
private val wide = mutableStateOf("")

private const val CONTROLLER = "marketing/phone/07-standalone-controller"
private const val LIBRARY = "marketing/phone/06-library"

// Where things sit on the phone's screens, as fractions of its width and height.
private val MODE_CARD = box(0.05f, 0.43f, 0.95f, 0.58f)
private val MODES = box(0.03f, 0.26f, 0.97f, 0.74f)
private val TAB_BAR = box(0f, 0.925f, 1f, 1f)
private val GEAR = box(0.83f, 0.012f, 0.96f, 0.068f)
private val SCREEN_PILL = box(0.03f, 0.08f, 0.42f, 0.125f)
private val PREVIEW = box(0.03f, 0.18f, 0.97f, 0.71f)
private val PREV_NEXT = box(0.03f, 0.73f, 0.97f, 0.795f)
private val BLANK_CLEAR = box(0.03f, 0.805f, 0.66f, 0.875f)
private val LIVE = box(0.655f, 0.805f, 0.97f, 0.875f)
private val SYNC = box(0.03f, 0.16f, 0.72f, 0.195f)
private val ADD = box(0.68f, 0.83f, 0.97f, 0.89f)
private val GET_BIBLES = box(0.07f, 0.615f, 0.93f, 0.695f)
private val PRESENT_BUTTON = box(0.8f, 0.9f, 0.96f, 0.985f)
private val NOTICE_GO_LIVE = box(0.07f, 0.11f, 0.93f, 0.18f)
private val LOOK = box(0.84f, 0.51f, 0.97f, 0.565f)
private val BACKDROP = box(0.03f, 0.56f, 0.97f, 0.635f)
private val SAVED_LOOKS = box(0.03f, 0.24f, 0.6f, 0.41f)

private const val TV_LABEL = "TV screen"
private const val PANEL_W = 1500f
private const val PANEL_H = 900f
private const val GAP_DP = 60
private const val TV_W = 960f
private const val TV_H = 560f
private const val TV_RADIUS_DP = 14
private const val TV_BEZEL_DP = 12
private const val TV_BODY = 0xFF27272A
private const val TV_OFF = 0xFF050506
private const val STAND_NECK_W = 70f
private const val STAND_NECK_H = 60f
private const val STAND_FOOT_W = 320f
private const val STAND_FOOT_H = 14f
