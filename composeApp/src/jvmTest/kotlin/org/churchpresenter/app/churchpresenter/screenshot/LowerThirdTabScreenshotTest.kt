@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.atem.AtemMediaSlot
import org.churchpresenter.atem.AtemState
import org.churchpresenter.atem.AtemUploadStatus
import org.churchpresenter.app.churchpresenter.tabs.LowerThirdLabel
import org.churchpresenter.app.churchpresenter.tabs.lottieFolder
import org.churchpresenter.app.churchpresenter.tabs.lottieFolderWithContent
import org.churchpresenter.app.churchpresenter.tabs.lottieSized
import org.churchpresenter.app.churchpresenter.tabs.lowerThirdTab
import org.churchpresenter.app.churchpresenter.tabs.ltButton
import org.churchpresenter.app.churchpresenter.tabs.openAtemDialog
import org.churchpresenter.app.churchpresenter.tabs.waitForAtemPrepared
import org.churchpresenter.app.churchpresenter.tabs.selectPreset
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * Every state of the Lower Third tab, in both themes.
 *
 * The tab has two halves that change independently — the preset list and preview on the left and
 * centre, and the ATEM strip that only exists once a switcher has answered — so most states here are
 * a combination of "which preset is chosen" and "what the switcher is doing". The switcher is the
 * injected fake from `LowerThirdTabTestSupport`, never real hardware: the real query is UDP with a 5s
 * timeout and would cost five seconds a shot.
 *
 * Two states decay on their own and are captured with the frame clock held: a playing animation ends
 * after its tween, and a failed upload clears itself after 8s. Auto-advance would run straight through
 * both, and the capture would silently come out identical to the state before it.
 */
class LowerThirdTabScreenshotTest {

    // ── The switcher the fake answers as ────────────────────────────────────────────────────────

    private fun atem(
        fps: Double = 25.0,
        stills: List<AtemMediaSlot> = listOf(
            AtemMediaSlot(index = 0, name = "Welcome", isUsed = true),
            AtemMediaSlot(index = 1, name = "Speaker Name", isUsed = true),
            AtemMediaSlot(index = 2, name = "", isUsed = false),
        ),
        clips: List<AtemMediaSlot> = listOf(
            AtemMediaSlot(index = 0, name = "Opening Titles", isUsed = true),
            AtemMediaSlot(index = 1, name = "", isUsed = false),
        ),
        clipMaxFrames: List<Int> = listOf(600, 600),
    ) = AtemState(
        fps = fps,
        videoMode = "1080p25",
        stillSlots = stills,
        clipSlots = clips,
        clipMaxFrames = clipMaxFrames,
    )

    private fun shoot(
        name: String,
        folder: () -> File? = { lottieFolder(*PRESETS) },
        settings: (AppSettings) -> AppSettings = { it },
        atemReachable: Boolean = false,
        quickUpload: Boolean = false,
        queryAtemState: suspend (String, Int) -> AtemState = { _, _ -> atem() },
        selectedLowerThirdItem: ScheduleItem.LowerThirdItem? = null,
        width: Dp? = null,
        rootIndex: Int = 0,
        drive: ComposeUiTest.() -> Unit = {},
    ) = stackedThemes(SECTION, name) { mode, file ->
        lowerThirdTab(
            folder = folder(),
            queryAtemState = queryAtemState,
            atemReachable = atemReachable,
            quickUpload = quickUpload,
            selectedLowerThirdItem = selectedLowerThirdItem,
            settings = settings,
            width = width,
            themeMode = mode,
        ) { _ ->
            drive()
            captureTo(file, rootIndex)
        }
    }

    /**
     * The upload bar is fed by a process-wide object shared with the Companion/API upload endpoints,
     * so whatever a test publishes there outlives it. Cleared after every test rather than only after
     * the ones that publish — a leftover bar would add a row to every later tab shot.
     */
    @AfterTest
    fun clearUploadStatus() {
        AtemUploadStatus.state.value?.let { AtemUploadStatus.clear(it.id) }
    }

    // ── The preset list and preview ─────────────────────────────────────────────────────────────

    /**
     * No folder configured at all renders byte-identically to this — the tab reduces both to an
     * empty preset list — so it is not shot separately; a second identical image tells a reviewer
     * nothing. `LowerThirdFolderTest` covers that the two are reached by different paths.
     */
    @Test
    fun `an empty folder offers only the generator`() =
        shoot("no_presets", folder = { lottieFolder() })

    @Test
    fun `presets listed, none chosen yet`() = shoot("browsing")

    @Test
    fun `a preset chosen`() = shoot("preset_selected") { selectPreset("Welcome") }

    @Test
    fun `a list long enough to scroll`() =
        shoot("many_presets", folder = { lottieFolder(*BIG_LIBRARY) })

    @Test
    fun `a preset whose name does not fit the list`() = shoot(
        "long_name",
        folder = { lottieFolder(LONG_NAME) },
    ) { selectPreset(LONG_NAME) }

    @Test
    fun `a wider preset list`() = shoot(
        "wide_list",
        settings = { it.copy(maximizedLayout = it.maximizedLayout.copy(lowerThirdListWidthDp = 380)) },
    ) { selectPreset("Welcome") }

    /**
     * Held clock, not `waitForIdle()`: the tween is 2000ms and auto-advance would run it to the end,
     * flipping the button back to Play before the shot — the state would never appear.
     */
    @Test
    fun `the animation playing`() = shoot("playing") {
        selectPreset("Welcome")
        mainClock.autoAdvance = false
        ltButton(LowerThirdLabel.PLAY).performClick()
        mainClock.advanceTimeByFrame()
    }

    @Test
    fun `a preset opened from the schedule`() = shoot(
        "from_schedule",
        selectedLowerThirdItem = ScheduleItem.LowerThirdItem(
            id = "schedule-1",
            presetId = "Speaker Name",
            presetLabel = "Speaker Name",
            pauseAtFrame = false,
            pauseDurationMs = 0L,
        ),
    )

    /** A square design against the 16:9 output — the tab says so under the title. */
    @Test
    fun `a preset that does not match the output shape`() = shoot(
        "aspect_mismatch",
        folder = { lottieFolderWithContent("Square Badge" to lottieSized(1080, 1080)) },
    ) { selectPreset("Square Badge") }

    // ── The ATEM strip ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `a switcher connected`() = shoot("atem_connected", atemReachable = true) {
        selectPreset("Welcome")
    }

    @Test
    fun `a switcher connected with the go-live key armed`() = shoot(
        "atem_key_armed",
        atemReachable = true,
        settings = { it.copy(atemSettings = it.atemSettings.copy(goLiveKey = true)) },
    ) { selectPreset("Welcome") }

    @Test
    fun `one-click upload buttons instead of the dialog`() = shoot(
        "atem_quick_upload",
        atemReachable = true,
        quickUpload = true,
    ) { selectPreset("Welcome") }

    /** No preset chosen, so every ATEM action is disabled — the shape churches see most often. */
    @Test
    fun `the switcher connected but nothing chosen to send`() =
        shoot("atem_nothing_selected", atemReachable = true)

    // ── The upload bar, fed by the shared status object ──────────────────────────────────────────

    @Test
    fun `a still uploading`() = shoot("upload_still", atemReachable = true) {
        selectPreset("Welcome")
        val id = AtemUploadStatus.begin("Welcome", clip = false, slot = 1)
        AtemUploadStatus.progress(id, 0.45f)
        waitForIdle()
    }

    @Test
    fun `a clip being ingested by the switcher`() = shoot("upload_processing", atemReachable = true) {
        selectPreset("Welcome")
        val id = AtemUploadStatus.begin("Opening Titles", clip = true, slot = 2)
        AtemUploadStatus.startProcessing(id)
        AtemUploadStatus.progress(id, 0.7f)
        waitForIdle()
    }

    /**
     * Held clock: the tab clears a failed upload 8s after it appears, and auto-advance would burn
     * through that delay before the capture.
     */
    @Test
    fun `an upload that failed`() = shoot("upload_failed", atemReachable = true) {
        selectPreset("Welcome")
        mainClock.autoAdvance = false
        val id = AtemUploadStatus.begin("Welcome", clip = false, slot = 1)
        AtemUploadStatus.fail(id, "Switcher refused the transfer")
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
    }

    // ── The upload dialog ───────────────────────────────────────────────────────────────────────

    @Test
    fun `the upload dialog in still mode`() =
        shoot("atem_dialog_still", atemReachable = true, rootIndex = 1) { openAtemDialog() }

    @Test
    fun `the upload dialog in clip mode`() =
        shoot("atem_dialog_clip", atemReachable = true, rootIndex = 1) {
            openAtemDialog()
            chooseClipMode()
        }

    /** A clip longer than the chosen slot holds — blocked up front rather than minutes in. */
    @Test
    fun `a clip too long for the slot`() = shoot(
        "atem_dialog_clip_too_long",
        atemReachable = true,
        queryAtemState = { _, _ -> atem(clipMaxFrames = listOf(10, 10)) },
        rootIndex = 1,
    ) {
        openAtemDialog()
        chooseClipMode()
    }

    /** The switcher stops answering: the slot dropdown falls back to a number field. */
    @Test
    fun `the dialog when the switcher will not answer`() = shoot(
        "atem_dialog_unreachable",
        atemReachable = true,
        queryAtemState = { _, _ -> error("Connection refused") },
        rootIndex = 1,
    ) { openAtemDialog() }

    /** A design smaller than the ATEM frame is upscaled, and the dialog warns that it will soften. */
    @Test
    fun `the dialog warning that the design will be upscaled`() = shoot(
        "atem_dialog_upscale",
        folder = { lottieFolderWithContent("Welcome" to lottieSized(640, 360)) },
        atemReachable = true,
        rootIndex = 1,
    ) { openAtemDialog() }

    /** A design of the wrong shape gets side bars, which is a different warning from upscaling. */
    @Test
    fun `the dialog warning that the design is the wrong shape`() = shoot(
        "atem_dialog_aspect_mismatch",
        folder = { lottieFolderWithContent("Welcome" to lottieSized(1080, 1080)) },
        atemReachable = true,
        rootIndex = 1,
    ) { openAtemDialog() }

    // ── Panel widths ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a narrow panel`() = shoot("narrow_panel", width = 420.dp) { selectPreset("Welcome") }

    @Test
    fun `a half-width panel`() = shoot("medium_panel", width = 760.dp) { selectPreset("Welcome") }

    // ── Driving ─────────────────────────────────────────────────────────────────────────────────

    /** Switches the open dialog to clip mode and waits for the clip-only rows to arrive. */
    private fun ComposeUiTest.chooseClipMode() {
        onNodeWithText(CLIP_MODE).performClick()
        // Selecting clip re-queries the switcher, so the fps/capacity line appears a frame later.
        waitUntil("the clip details are composed", RENDER_TIMEOUT_MS) {
            onAllNodesWithText(ATEM_DETAIL_PREFIX, substring = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        // Clip is a different cache entry from still, so the frame render starts over here too.
        waitForAtemPrepared()
    }

    private companion object {
        const val SECTION = "lowerThirdTab"

        /** `atem_mode_clip`, as the dialog's radio row renders it. */
        const val CLIP_MODE = "Clip (full animation)"
        /** The fps/capacity line the dialog only shows for clips. */
        const val ATEM_DETAIL_PREFIX = "ATEM: "

        const val LONG_NAME = "Guest Speaker — Dr Margaret Whitfield, Overseas Missions"

        val PRESETS = arrayOf("Welcome", "Speaker Name", "Sermon Title", "Offering")

        val BIG_LIBRARY = Array(16) { "Preset ${'A' + it}" }
    }
}
