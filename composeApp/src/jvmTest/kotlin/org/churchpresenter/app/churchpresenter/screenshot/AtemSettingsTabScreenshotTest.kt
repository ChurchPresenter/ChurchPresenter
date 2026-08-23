@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.AtemSettings
import org.churchpresenter.app.churchpresenter.dialogs.tabs.AtemSettingsTab
import org.churchpresenter.theme.ChurchPresenterTheme
import kotlin.test.Test
import org.churchpresenter.ui.screenshot.captureTo
import org.churchpresenter.ui.screenshot.stackedThemes

/**
 * The ATEM tab of the settings dialog, in both themes.
 *
 * **What the switcher reported on the last Test Connection is the axis.** Every number on this tab
 * is a slot or a keyer index, and until a switcher has answered, the tab has no idea how many of
 * either exist: the fields carry bare labels, the capacity and keyer lines say "unknown", and
 * nothing can be out of range. Once the counts are known — they are persisted in the settings, not
 * held in memory — every one of those labels grows a range, the reference lines fill in, and a
 * number past the end turns its field red. So the states below are driven by those persisted counts
 * rather than by a switcher.
 *
 * **The Test Connection button is never pressed.** It opens a socket to a real ATEM: with one
 * plugged in the images would carry that machine's switcher, and without one the button spends its
 * connect timeout and lands on an error whose wording comes from the host OS. The states behind it —
 * connecting, connected, and the error line — are the only part of this tab these images do not
 * reach.
 */
class AtemSettingsTabScreenshotTest {

    // ── Before any switcher has answered ────────────────────────────────────────────────────────

    /**
     * A fresh install: no host, so Test Connection is disabled and nothing has been detected.
     *
     * Both reference lines read as unknown, every field label is bare, and the key fields are the
     * upstream pair (M/E and Key) that the tab shows until asked for a downstream keyer.
     */
    @Test
    fun `as it opens`() = shoot("defaults")

    /** A host typed in, which is all it takes to arm Test Connection. */
    @Test
    fun `a host set but never tested`() = shoot(
        "host_set",
        settings = atem { copy(host = "10.0.0.40", port = 9910) },
    )

    // ── Once a switcher has been read ───────────────────────────────────────────────────────────

    /**
     * The counts a Test Connection persists.
     *
     * Every slot and keyer label grows the range it was told about, the clip-capacity line spells
     * out how much footage fits at the detected frame rate, and the keyer line lists what each M/E
     * carries — none of which the tab can say before a switcher has answered once.
     */
    @Test
    fun `a switcher that has been detected`() = shoot("detected", settings = detected())

    /**
     * A media pool whose clip banks are not the same size.
     *
     * The capacity line has two forms — one sentence when every bank holds the same number of
     * frames, another listing them when they differ — and this is the second, with the frames left
     * over from no bank at all reported after it.
     */
    @Test
    fun `clip banks of different sizes`() = shoot(
        "capacity_mixed",
        settings = detected {
            copy(detectedClipMaxFrames = listOf(1800, 900), detectedUnassignedFrames = 240, clipFps = 59.94)
        },
    )

    /**
     * Slots and keyers pointing past what the switcher has.
     *
     * Nothing stops a number being typed that the hardware cannot honour, so every field that knows
     * its range marks itself in the error color instead — four of them at once here, across both
     * cards.
     */
    @Test
    fun `slots beyond what the switcher has`() = shoot(
        "out_of_range",
        settings = detected {
            copy(
                defaultStillSlot = 40,
                defaultClipSlot = 8,
                keyIndex = 6,
                backgroundSlot1 = 60,
            )
        },
    )

    // ── The key, and the switches under it ──────────────────────────────────────────────────────

    /**
     * Driving a downstream keyer instead of an upstream one.
     *
     * The M/E and Key pair is replaced by a single DSK field — the same row, one field fewer — and
     * the switches under it are all on, which is the other half of this image: off is the default
     * and already in every other state here.
     */
    @Test
    fun `a downstream keyer, with every switch on`() = shoot(
        "downstream_key",
        settings = detected {
            copy(
                useDownstreamKey = true,
                dskIndex = 1,
                quickUpload = true,
                goLiveKey = true,
                keyPreRollMs = 500,
                keyPostRollMs = 750,
            )
        },
    )

    /** A render size and frame rate off the defaults, including a fractional NTSC rate. */
    @Test
    fun `a fractional frame rate`() = shoot(
        "render_settings",
        settings = detected { copy(renderWidth = 1280, renderHeight = 720, clipFps = 29.97) },
    )

    // ── Harness ─────────────────────────────────────────────────────────────────────────────────

    private fun shoot(
        name: String,
        settings: AppSettings = AppSettings(),
        drive: ComposeUiTest.() -> Unit = {},
    ) = stackedThemes(SECTION, name) { mode, file ->
        runComposeUiTest {
            setContent {
                ChurchPresenterTheme(themeMode = mode) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize()) {
                            var current by remember { mutableStateOf(settings) }
                            AtemSettingsTab(
                                settings = current,
                                onSettingsChange = { transform -> current = transform(current) },
                            )
                        }
                    }
                }
            }
            waitForIdle()
            drive()
            waitForIdle()
            captureTo(file)
        }
    }

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────────

    private fun atem(edit: AtemSettings.() -> AtemSettings) = AppSettings(atemSettings = AtemSettings().edit())

    /**
     * A switcher already read once — an ATEM 2 M/E, roughly.
     *
     * Two M/Es with four upstream keyers each, two downstream keyers, twenty stills and four clip
     * banks of equal size. Written straight into the settings because that is where a successful
     * Test Connection leaves them; nothing else on the tab distinguishes the two.
     */
    private fun detected(edit: AtemSettings.() -> AtemSettings = { this }) = atem {
        copy(
            host = "10.0.0.40",
            detectedStillSlots = 20,
            detectedClipSlots = 4,
            detectedClipMaxFrames = listOf(1800, 1800, 1800, 1800),
            detectedMixEffects = 2,
            detectedKeyersPerMe = listOf(4, 4),
            detectedDownstreamKeyers = 2,
            clipFps = 30.0,
        ).edit()
    }

    private companion object {
        const val SECTION = "atemSettingsTab"
    }
}
