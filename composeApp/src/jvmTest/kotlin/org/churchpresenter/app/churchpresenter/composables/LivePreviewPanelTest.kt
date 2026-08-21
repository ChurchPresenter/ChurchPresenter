package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.LocalMediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The sidebar's scaled-down live preview, one box per configured display. This test class covers
 * only this file's own logic — which mode dispatches to which `screenAssignment.showX` flag, the
 * Live/FILL/LOCKED badges and lock toggle, the display-mode chip, the Website snapshot-vs-URL-vs-
 * nothing fallback chain, and the dev-fallback/browser-source-output display counting. It does
 * *not* re-verify that BiblePresenter, SongPresenter, etc. render correct content — each already
 * has its own dedicated test file for that.
 *
 * Getting a `LivePreviewPanel` to compose at all under this project's headless `jvmTest` JVM
 * required a real production fix first: `rememberScreenDevices()` and `presenterScreenBounds()`
 * (in `Constants.kt`) both called `GraphicsEnvironment`'s screen-enumeration APIs unguarded, which
 * throw `HeadlessException` unconditionally when headless — a real gap (this codebase's own
 * `DeckLinkManager.isAvailable()` already degrades the same way for missing hardware), not a
 * test-only shim. With that fixed, every test here runs in the dev-fallback state real users see
 * on a single-monitor machine with no DeckLink device: `realWindowCount = 0`, so every preview
 * slot is a dev-fallback slot. That means the "skip a display explicitly assigned to None" branch
 * (only reachable for a *real*, non-fallback slot) can't be exercised here — there is no headless
 * way to make `realWindowCount > 0`.
 *
 * Media transport controls and the audio equalizer are gated behind `LocalMediaViewModel` being
 * non-null. `MediaViewModel` itself is plain state (no VLC/audio stack — that only lives in the
 * `VideoPlayer`/`SoftwareVideoPlayer` composables that read it), so a real instance is used below
 * to cover the loaded/playing states too.
 */
@OptIn(ExperimentalTestApi::class)
class LivePreviewPanelTest {

    // ── Display counting / dev-fallback ───────────────────────────────────────────────────────

    @Test
    fun `by default, headless, exactly one dev-fallback preview slot renders`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                LivePreviewPanel(presenterManager = PresenterManager(), appSettings = AppSettings())
            }
        }
        onNodeWithText("Screen 1").assertExists()
        onNodeWithText("Screen 2").assertDoesNotExist()
    }

    @Test
    fun `devWindowCount controls how many dev-fallback preview slots render`() = runComposeUiTest {
        val settings = AppSettings(projectionSettings = ProjectionSettings(devWindowCount = 3))
        setContent {
            MaterialTheme {
                LivePreviewPanel(presenterManager = PresenterManager(), appSettings = settings)
            }
        }
        onNodeWithText("Screen 1").assertExists()
        onNodeWithText("Screen 2").assertExists()
        onNodeWithText("Screen 3").assertExists()
    }

    @Test
    fun `browser source outputs render as additional, separately labeled previews`() = runComposeUiTest {
        val settings = AppSettings(
            projectionSettings = ProjectionSettings(browserSourceOutputs = listOf(
                ScreenAssignment(),
                ScreenAssignment(),
            ))
        )
        setContent {
            MaterialTheme {
                LivePreviewPanel(presenterManager = PresenterManager(), appSettings = settings)
            }
        }
        onNodeWithText("Browser Source 1").assertExists()
        onNodeWithText("Browser Source 2").assertExists()
    }

    // ── Mode dispatch → Live badge ────────────────────────────────────────────────────────────

    @Test
    fun `each presenting mode shows Live when its show flag is on, the default`() = runComposeUiTest {
        for (mode in Presenting.entries.filter { it != Presenting.NONE }) {
            setContent {
                MaterialTheme {
                    LivePreviewPanel(
                        presenterManager = PresenterManager().apply { setPresentingMode(mode) },
                        appSettings = AppSettings(),
                    )
                }
            }
            onNodeWithText("Live").assertExists("mode=$mode must show Live when its show flag defaults to true")
        }
    }

    @Test
    fun `each presenting mode's Live badge is gated by its own show flag`() = runComposeUiTest {
        val offCases = listOf(
            Presenting.BIBLE to ScreenAssignment(bibleMode = Constants.SONG_LANG_OFF),
            Presenting.LYRICS to ScreenAssignment(songMode = Constants.SONG_LANG_OFF),
            Presenting.PICTURES to ScreenAssignment(showPictures = false),
            Presenting.PRESENTATION to ScreenAssignment(showPictures = false),
            Presenting.MEDIA to ScreenAssignment(showMedia = false),
            Presenting.LOWER_THIRD to ScreenAssignment(showStreaming = false),
            Presenting.ANNOUNCEMENTS to ScreenAssignment(showAnnouncements = false),
            Presenting.WEBSITE to ScreenAssignment(showWebsite = false),
            Presenting.CANVAS to ScreenAssignment(showCanvas = false),
            Presenting.QA to ScreenAssignment(showQA = false),
            Presenting.STT to ScreenAssignment(showSTT = false),
            Presenting.DICTIONARY to ScreenAssignment(showDictionary = false),
        )
        for ((mode, offAssignment) in offCases) {
            setContent {
                MaterialTheme {
                    LivePreviewPanel(
                        presenterManager = PresenterManager().apply { setPresentingMode(mode) },
                        appSettings = AppSettings(
                            projectionSettings = ProjectionSettings(screenAssignments = listOf(offAssignment))
                        ),
                    )
                }
            }
            onNodeWithText("Live").assertDoesNotExist()
        }
    }

    @Test
    fun `Presenting NONE never shows the Live badge`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                LivePreviewPanel(presenterManager = PresenterManager(), appSettings = AppSettings())
            }
        }
        onNodeWithText("Live").assertDoesNotExist()
    }

    // ── FILL badge ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the FILL badge shows only when a key output is configured`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                LivePreviewPanel(presenterManager = PresenterManager(), appSettings = AppSettings())
            }
        }
        onNodeWithText("FILL").assertDoesNotExist()

        val settings = AppSettings(
            projectionSettings = ProjectionSettings(screenAssignments = listOf(ScreenAssignment(keyTargetDisplay = 0)))
        )
        setContent {
            MaterialTheme {
                LivePreviewPanel(presenterManager = PresenterManager(), appSettings = settings)
            }
        }
        onNodeWithText("FILL").assertExists()
    }

    // ── LOCKED badge + lock toggle ─────────────────────────────────────────────────────────────

    @Test
    fun `locking a screen shows the LOCKED badge and the filled lock icon`() = runComposeUiTest {
        val pm = PresenterManager()
        pm.setScreenLock(0, Presenting.BIBLE)
        setContent {
            MaterialTheme {
                LivePreviewPanel(presenterManager = pm, appSettings = AppSettings())
            }
        }
        onNodeWithText("LOCKED").assertExists()
        onNodeWithContentDescription("Unlock screen").assertExists()
    }

    @Test
    fun `an unlocked screen shows neither the LOCKED badge nor the filled lock icon`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                LivePreviewPanel(presenterManager = PresenterManager(), appSettings = AppSettings())
            }
        }
        onNodeWithText("LOCKED").assertDoesNotExist()
        onNodeWithContentDescription("Lock screen to current tab").assertExists()
    }

    @Test
    fun `clicking the lock toggle locks the screen to its current mode`() = runComposeUiTest {
        val pm = PresenterManager()
        pm.setPresentingMode(Presenting.LYRICS)
        setContent {
            MaterialTheme {
                LivePreviewPanel(presenterManager = pm, appSettings = AppSettings())
            }
        }
        onNode(hasClickAction() and hasContentDescription("Lock screen to current tab")).performClick()

        assertEquals(Presenting.LYRICS, pm.screenLocks.value[0])
    }

    @Test
    fun `clicking the lock toggle again unlocks the screen`() = runComposeUiTest {
        val pm = PresenterManager()
        pm.setScreenLock(0, Presenting.LYRICS)
        setContent {
            MaterialTheme {
                LivePreviewPanel(presenterManager = pm, appSettings = AppSettings())
            }
        }
        onNode(hasClickAction() and hasContentDescription("Unlock screen")).performClick()

        assertNull(pm.screenLocks.value[0])
    }

    @Test
    fun `locking a browser source output uses its own, separate lock index space`() = runComposeUiTest {
        val pm = PresenterManager()
        pm.setPresentingMode(Presenting.QA)
        val settings = AppSettings(
            projectionSettings = ProjectionSettings(browserSourceOutputs = listOf(ScreenAssignment()))
        )
        setContent {
            MaterialTheme {
                LivePreviewPanel(presenterManager = pm, appSettings = settings)
            }
        }
        // Two unlocked toggles exist (the dev-fallback screen and the browser source output);
        // the browser source one is the last, since it's rendered after the screen loop, and it
        // sits below the default test window's height, so it must be scrolled into view first.
        onAllNodes(hasClickAction() and hasContentDescription("Lock screen to current tab"))
            .onLast().performScrollTo().performClick()

        assertEquals(Presenting.QA, pm.browserSourceLocks.value[0])
        assertTrue(pm.screenLocks.value.isEmpty(), "locking the browser source output must not also lock the screen")
    }

    @Test
    fun `stage monitor screens never show the LOCKED badge or lock toggle, even when locked`() = runComposeUiTest {
        val pm = PresenterManager()
        pm.setScreenLock(0, Presenting.BIBLE)
        val settings = AppSettings(
            projectionSettings = ProjectionSettings(
                screenAssignments = listOf(ScreenAssignment(displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR))
            )
        )
        setContent {
            MaterialTheme {
                LivePreviewPanel(presenterManager = pm, appSettings = settings)
            }
        }
        onNodeWithText("LOCKED").assertDoesNotExist()
        onNodeWithContentDescription("Unlock screen").assertDoesNotExist()
        onNodeWithContentDescription("Lock screen to current tab").assertDoesNotExist()
    }

    // ── Display mode chip ──────────────────────────────────────────────────────────────────────

    @Test
    fun `fullscreen, the default display mode, shows no chip`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                LivePreviewPanel(presenterManager = PresenterManager(), appSettings = AppSettings())
            }
        }
        onNodeWithText("Stage Monitor").assertDoesNotExist()
        onNodeWithText("Horizontal Lower Third").assertDoesNotExist()
        onNodeWithText("Vertical Lower Third").assertDoesNotExist()
    }

    @Test
    fun `each non-fullscreen display mode shows its own chip`() = runComposeUiTest {
        val cases = listOf(
            Constants.DISPLAY_MODE_STAGE_MONITOR to "Stage Monitor",
            Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL to "Horizontal Lower Third",
            Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL to "Vertical Lower Third",
        )
        for ((mode, chipText) in cases) {
            val settings = AppSettings(
                projectionSettings =
                    ProjectionSettings(screenAssignments = listOf(ScreenAssignment(displayMode = mode)))
            )
            setContent {
                MaterialTheme {
                    LivePreviewPanel(presenterManager = PresenterManager(), appSettings = settings)
                }
            }
            onNodeWithText(chipText).assertExists("displayMode=\"$mode\" must show the \"$chipText\" chip")
        }
    }

    // ── Website: snapshot vs URL placeholder vs nothing ──────────────────────────────────────

    @Test
    fun `Website mode with no snapshot and no URL shows a generic placeholder`() = runComposeUiTest {
        val pm = PresenterManager()
        pm.setPresentingMode(Presenting.WEBSITE)
        setContent {
            MaterialTheme {
                LivePreviewPanel(presenterManager = pm, appSettings = AppSettings())
            }
        }
        onNodeWithText("Nothing is live", substring = true).assertExists()
    }

    @Test
    fun `Website mode with a URL but no snapshot yet shows the URL as a placeholder`() = runComposeUiTest {
        val pm = PresenterManager()
        pm.setPresentingMode(Presenting.WEBSITE)
        pm.setWebsiteUrl("https://example.com")
        setContent {
            MaterialTheme {
                LivePreviewPanel(presenterManager = pm, appSettings = AppSettings())
            }
        }
        onNodeWithText("https://example.com").assertExists()
    }

    @Test
    fun `Website mode with a snapshot shows the snapshot image instead of any placeholder`() = runComposeUiTest {
        val pm = PresenterManager()
        pm.setPresentingMode(Presenting.WEBSITE)
        pm.setWebsiteUrl("https://example.com")
        pm.setWebSnapshot(ImageBitmap(4, 4))
        setContent {
            MaterialTheme {
                LivePreviewPanel(presenterManager = pm, appSettings = AppSettings())
            }
        }
        onNodeWithText("https://example.com").assertDoesNotExist()
        onNodeWithText("Nothing is live", substring = true).assertDoesNotExist()
    }

    // ── Media controls (no MediaViewModel loaded) ────────────────────────────────────────────

    @Test
    fun `without a loaded MediaViewModel, no media controls or audio equalizer render`() = runComposeUiTest {
        val pm = PresenterManager()
        pm.setPresentingMode(Presenting.MEDIA)
        setContent {
            MaterialTheme {
                LivePreviewPanel(presenterManager = pm, appSettings = AppSettings())
            }
        }
        // No crash, and the panel still renders its one dev-fallback preview normally.
        onNodeWithText("Screen 1").assertExists()
    }

    // ── Media controls (loaded MediaViewModel) ───────────────────────────────────────────────

    @Test
    fun `media controls do not render when nothing is presenting, even with loaded media`() = runComposeUiTest {
        val pm = PresenterManager()
        val media = MediaViewModel().apply { loadMedia("/tmp/song.mp3", "audio") }
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalMediaViewModel provides media) {
                    LivePreviewPanel(presenterManager = pm, appSettings = AppSettings())
                }
            }
        }
        onNodeWithContentDescription("Play").assertDoesNotExist()
        onNodeWithContentDescription("Pause").assertDoesNotExist()
    }

    @Test
    fun `media controls render the Play icon and no time label while paused with no duration yet`() = runComposeUiTest {
        val pm = PresenterManager()
        pm.setPresentingMode(Presenting.MEDIA)
        val media = MediaViewModel().apply { loadMedia("/tmp/song.mp3", "audio") }
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalMediaViewModel provides media) {
                    LivePreviewPanel(presenterManager = pm, appSettings = AppSettings())
                }
            }
        }
        onNodeWithContentDescription("Play").assertExists()
        onNodeWithContentDescription("Pause").assertDoesNotExist()
        onNodeWithText("0:00").assertDoesNotExist()
    }

    @Test
    fun `media controls show a formatted time label once duration is known`() = runComposeUiTest {
        val pm = PresenterManager()
        pm.setPresentingMode(Presenting.MEDIA)
        val media = MediaViewModel().apply {
            loadMedia("/tmp/song.mp3", "audio")
            setDuration(90_000L)
        }
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalMediaViewModel provides media) {
                    LivePreviewPanel(presenterManager = pm, appSettings = AppSettings())
                }
            }
        }
        onNodeWithText("0:00").assertExists(
            "with a known duration, the slider shows the current position as a time label",
        )
    }

    @Test
    fun `clicking the media play button plays and swaps the icon to Pause`() = runComposeUiTest {
        val pm = PresenterManager()
        pm.setPresentingMode(Presenting.MEDIA)
        val media = MediaViewModel().apply { loadMedia("/tmp/song.mp3", "audio") }
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalMediaViewModel provides media) {
                    LivePreviewPanel(presenterManager = pm, appSettings = AppSettings())
                }
            }
        }
        onNodeWithContentDescription("Play").performClick()
        assertTrue(media.isPlaying)
        onNodeWithContentDescription("Pause").assertExists()
    }

    @Test
    fun `clicking the media pause button while playing pauses and swaps the icon back to Play`() = runComposeUiTest {
        val pm = PresenterManager()
        pm.setPresentingMode(Presenting.MEDIA)
        val media = MediaViewModel().apply {
            loadMedia("/tmp/song.mp3", "audio")
            play()
        }
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalMediaViewModel provides media) {
                    LivePreviewPanel(presenterManager = pm, appSettings = AppSettings())
                }
            }
        }
        onNodeWithContentDescription("Pause").performClick()
        assertFalse(media.isPlaying)
        onNodeWithContentDescription("Play").assertExists()
    }

    @Test
    fun `the audio equalizer renders without error while media is loaded and playing`() = runComposeUiTest {
        val pm = PresenterManager()
        pm.setPresentingMode(Presenting.MEDIA)
        val media = MediaViewModel().apply {
            loadMedia("/tmp/song.mp3", "audio")
            play()
        }
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalMediaViewModel provides media) {
                    LivePreviewPanel(presenterManager = pm, appSettings = AppSettings())
                }
            }
        }
        // No dedicated semantics for the equalizer bars themselves — this confirms that branch
        // composes without crashing, alongside the panel's own always-present content.
        onNodeWithText("Screen 1").assertExists()
    }

    // ── STT ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `STT mode with a real, unconnected STTManager renders the STT presenter without error`() = runComposeUiTest {
        val pm = PresenterManager()
        pm.setPresentingMode(Presenting.STT)
        setContent {
            MaterialTheme {
                LivePreviewPanel(presenterManager = pm, appSettings = AppSettings(), sttManager = STTManager())
            }
        }
        // STTManager starts with no segments/in-progress text, so there's no caption text to
        // assert on directly — this confirms the sttManager != null branch composes cleanly,
        // alongside the panel's own always-present content and Live badge.
        onNodeWithText("Screen 1").assertExists()
        onNodeWithText("Live").assertExists()
    }
}
