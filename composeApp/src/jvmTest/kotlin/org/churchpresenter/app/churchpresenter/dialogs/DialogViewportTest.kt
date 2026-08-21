@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import org.churchpresenter.app.churchpresenter.TEXT_GROWTH_SCALE
import org.churchpresenter.app.churchpresenter.Viewport
import org.churchpresenter.app.churchpresenter.ViewportProbe
import org.churchpresenter.app.churchpresenter.assertFitsDeclaredHeight
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import kotlin.test.Test

/**
 * Every dialog that declares a fixed size and holds no scroll container, checked against the size it
 * declares.
 *
 * These six are the ones with no recourse: `rememberDialogState(width = …, height = …)`, most of them
 * `resizable = false`, and no `verticalScroll`/`LazyColumn` anywhere in the body. Content that needs
 * more room than the declared height is simply cut off — there is no scrollbar, so nothing on screen
 * indicates anything is missing. Everything else in `dialogs/` either scrolls or is resizable, and
 * can recover.
 *
 * The width and height in each case are **read from the same constant the dialog opens at** — see
 * `DialogSizes.kt`. They were originally copied as literals, which left the suite measuring content
 * against a number that no longer had to be the window's: editing the dp literal downward broke
 * nothing, because the test went on asserting against the old figure. `MemoryMonitorWindow` proved
 * it — it could be returned to the 440dp that clipped its Force GC row with every test still green.
 * Sharing the constant closes that: shrinking a window now fails the same assertion that content
 * outgrowing it does.
 *
 * `AddLabelDialog` is the case that already happened once: its height comment records 400dp cutting
 * off the nested colour picker "with no visible scrollbar to hint more was there", found by hand and
 * fixed by hand-measuring to 640dp. This is that measurement, kept — and `AddLabelDialogHeightTest`
 * pins the 400dp version as still failing, so these assertions cannot quietly stop testing anything.
 *
 * See `ViewportAssertions` for why the content is measured unbounded rather than checked for
 * containment.
 *
 * The three bodies that draw nothing but fixed string resources — `AboutDialogContent`,
 * `KonamiEasterEggDialogContent`, `MemoryMonitorDialogContent` — have no parameter to pass a longer
 * string through, so their growth case is covered by scaling the font instead. `TEXT_GROWTH_SCALE`
 * explains why that is both the only available handle and a real scenario in its own right.
 *
 * `MemoryMonitorWindow` is the one this suite found already broken. It needed 414dp of a 460x440
 * window — 6% of slack — and lost the bottom 26dp under the same 30% growth every other dialog here
 * absorbs, so it opened clipped for anyone running OS font scaling above ~1.06x. It has since been
 * raised to 500dp and given a scroll container, and it is measured against 500dp below. It is the
 * only one of the six that now scrolls; it is kept in this suite anyway, because "opens showing all
 * of itself" is the property worth holding, and the scrollbar is the floor under that rather than a
 * substitute for it.
 */
class DialogViewportTest {

    /** Longer than any real value, standing in for a long translation or a long user-typed name. */
    private val longText = "Ein sehr langer Text der zeigt wie lang eine Ubersetzung werden kann " +
        "und dabei weit uber die Breite des Dialogfensters hinauslauft"

    private fun fits(
        width: Dp,
        height: Dp,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) = runComposeUiTest {
        val probe = ViewportProbe()
        setContent {
            MaterialTheme {
                Viewport(width, probe, fontScale) { content() }
            }
        }
        assertFitsDeclaredHeight(height, probe)
    }

    /** The same measurement with every string 30% taller — see `TEXT_GROWTH_SCALE`. */
    private fun fitsWhenTextGrows(width: Dp, height: Dp, content: @Composable () -> Unit) =
        fits(width, height, TEXT_GROWTH_SCALE, content)

    @Test
    fun `AddWebsite content fits its 500x440 window`() =
        fits(
            ADD_WEBSITE_DIALOG_WIDTH,
            ADD_WEBSITE_DIALOG_HEIGHT,
        ) { AddWebsiteDialogContent(onDismiss = {}, onConfirm = { _, _ -> }) }

    @Test
    fun `AddLabel content fits its 500x640 window`() =
        fits(
            ADD_LABEL_DIALOG_WIDTH,
            ADD_LABEL_DIALOG_HEIGHT,
        ) { AddLabelDialogContent(onDismiss = {}, onConfirm = { _, _, _ -> }) }

    @Test
    fun `AddLabel content fits its window with a long label typed in`() = fits(
        ADD_LABEL_DIALOG_WIDTH,
        ADD_LABEL_DIALOG_HEIGHT,
    ) {
        AddLabelDialogContent(
            onDismiss = {},
            onConfirm = { _, _, _ -> },
            existingText = longText,
            isEdit = true,
        )
    }

    @Test
    fun `About content fits its 420x490 window`() = fits(ABOUT_DIALOG_WIDTH, ABOUT_DIALOG_HEIGHT) {
        AboutDialogContent(onDismiss = {}, appSettings = AppSettings(), theme = ThemeMode.LIGHT)
    }

    @Test
    fun `Konami content fits its 420x340 window`() =
        fits(KONAMI_DIALOG_WIDTH, KONAMI_DIALOG_HEIGHT) { KonamiEasterEggDialogContent(onDismiss = {}) }

    @Test
    fun `MemoryMonitor content fits its 460x500 window at its widest readings`() {
        // Terabyte-scale values, so every "%,d MB" row is as wide as the format can make it and the
        // rows are as likely as they ever get to wrap onto a second line.
        val huge = 9_999_999L * 1024L * 1024L
        fits(MEMORY_MONITOR_WINDOW_WIDTH, MEMORY_MONITOR_WINDOW_HEIGHT) {
            MemoryMonitorDialogContent(
                heapUsed = huge,
                heapCommitted = huge,
                heapMax = huge,
                nonHeapUsed = huge,
                nonHeapCommitted = huge,
                gcCount = 999_999L,
                gcTimeMs = 999_999L,
                history = List(120) { huge },
                onForceGc = {},
            )
        }
    }

    /**
     * `RemoteEventDialog` hand-computes `dialogHeight = if (remaining > 0) 330.dp else 290.dp`, so
     * the extra "N behind this one" line has to be paid for by exactly the 40dp that branch adds.
     * Both branches are measured against their own height.
     */
    @Test
    fun `RemoteEvent content fits its 500x290 window`() =
        fits(
            REMOTE_EVENT_DIALOG_WIDTH,
            REMOTE_EVENT_DIALOG_HEIGHT,
        ) { remoteEventContent(remaining = 0, title = "Amazing Grace") }

    @Test
    fun `RemoteEvent content fits its 500x330 window when requests are queued`() =
        fits(
            REMOTE_EVENT_DIALOG_WIDTH,
            REMOTE_EVENT_DIALOG_HEIGHT_QUEUED,
        ) { remoteEventContent(remaining = 4, title = "Amazing Grace") }

    @Test
    fun `RemoteEvent content fits its window with a long item title`() =
        fits(
            REMOTE_EVENT_DIALOG_WIDTH,
            REMOTE_EVENT_DIALOG_HEIGHT,
        ) { remoteEventContent(remaining = 0, title = longText) }

    // Every dialog again with its text 30% taller, which is the only handle on growth the three
    // resource-only bodies have. Kept as separate tests so a failure names both the dialog and the
    // condition, rather than one parameterised test that says only "some dialog, some scale".

    @Test
    fun `AddWebsite content still fits when its text grows`() =
        fitsWhenTextGrows(ADD_WEBSITE_DIALOG_WIDTH, ADD_WEBSITE_DIALOG_HEIGHT) {
            AddWebsiteDialogContent(onDismiss = {}, onConfirm = { _, _ -> })
        }

    @Test
    fun `AddLabel content still fits when its text grows`() =
        fitsWhenTextGrows(ADD_LABEL_DIALOG_WIDTH, ADD_LABEL_DIALOG_HEIGHT) {
            AddLabelDialogContent(onDismiss = {}, onConfirm = { _, _, _ -> })
        }

    @Test
    fun `About content still fits when its text grows`() = fitsWhenTextGrows(ABOUT_DIALOG_WIDTH, ABOUT_DIALOG_HEIGHT) {
        AboutDialogContent(onDismiss = {}, appSettings = AppSettings(), theme = ThemeMode.LIGHT)
    }

    @Test
    fun `Konami content still fits when its text grows`() =
        fitsWhenTextGrows(KONAMI_DIALOG_WIDTH, KONAMI_DIALOG_HEIGHT) { KonamiEasterEggDialogContent(onDismiss = {}) }

    @Test
    fun `MemoryMonitor content still fits when its text grows`() {
        val huge = 9_999_999L * 1024L * 1024L
        fitsWhenTextGrows(MEMORY_MONITOR_WINDOW_WIDTH, MEMORY_MONITOR_WINDOW_HEIGHT) {
            MemoryMonitorDialogContent(
                heapUsed = huge,
                heapCommitted = huge,
                heapMax = huge,
                nonHeapUsed = huge,
                nonHeapCommitted = huge,
                gcCount = 999_999L,
                gcTimeMs = 999_999L,
                history = List(120) { huge },
                onForceGc = {},
            )
        }
    }

    @Test
    fun `RemoteEvent content still fits when its text grows`() =
        fitsWhenTextGrows(REMOTE_EVENT_DIALOG_WIDTH, REMOTE_EVENT_DIALOG_HEIGHT_QUEUED) {
            remoteEventContent(remaining = 4, title = "Amazing Grace")
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun remoteEventContent(remaining: Int, title: String) {
    RemoteEventDialogContent(
        event = RemoteEvent(type = RemoteEventType.ADD_TO_SCHEDULE, title = title),
        actionLabel = "Add to Schedule",
        typeIcon = Icons.Filled.CalendarMonth,
        typeAccent = MaterialTheme.colorScheme.primary,
        bodyTitle = title,
        remaining = remaining,
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
