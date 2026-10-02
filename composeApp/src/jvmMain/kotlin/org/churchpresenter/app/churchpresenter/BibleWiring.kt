package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import org.churchpresenter.app.churchpresenter.composables.rememberTokenGate
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.app.churchpresenter.viewmodel.clearDetectedReferences
import org.churchpresenter.app.churchpresenter.viewmodel.getSelectedVerses
import org.churchpresenter.app.churchpresenter.viewmodel.logLiveReference
import org.churchpresenter.diagnostics.CrashReporter

/**
 * The Bible work that has to go on while the Bible tab is not composed: the Lookup Engine's
 * lifecycle, the Stage Monitor's next verse, auto-follow while live, and schedule verses.
 */
@Composable
internal fun MainDesktopScope.BibleWiring() {
    // Engine link lifecycle — owned here (not in BibleTab) so it survives tab switches: BibleTab is
    // composed inside AnimatedContent and would otherwise restart the engine every time the operator
    // navigates back to it. Started when STT connects, stopped on disconnect / when disabled.
    val sttConnected = sttManager?.connected?.value == true
    val bibleEngineSettings = appSettings.bibleEngineSettings
    // The SET of bibles to index (sorted, blanks removed). Keying the restart on this means swapping
    // primary↔secondary (same set) does NOT re-index, while changing to a different bible does.
    val engineBibles = remember(appSettings.bibleSettings.translationList()) {
        engineBibleFiles(appSettings.bibleSettings)
    }
    // storageDirectory is a key because it is READ below as bibleRoot: without it, changing the
    // Bible folder mid-service leaves the engine on the old root — old verse index, and a version
    // corpus that is built once at start and never rebuilt. engineBibles does not cover it, being
    // file NAMES: move to another folder holding the same names and the set never changes.
    LaunchedEffect(
        sttConnected, bibleEngineSettings.enabled, bibleEngineSettings.runLocal,
        bibleEngineSettings.host, bibleEngineSettings.port, engineBibles,
        appSettings.bibleSettings.storageDirectory,
    ) {
        if (shouldRunBibleEngine(sttConnected, bibleEngineSettings.enabled, engineBibles)) {
            bibleEngineClient.start(
                sttUrl = appSettings.sttSettings.serverUrl,
                bibleRoot = appSettings.bibleSettings.storageDirectory,
                bibleFiles = engineBibles,
                runLocal = bibleEngineSettings.runLocal,
                host = bibleEngineSettings.host,
                port = bibleEngineSettings.port,
                level = bibleViewModel.textMatchLevel.value.name.lowercase(),
                continuationSpeed = bibleViewModel.continuationSpeed.value.name.lowercase(),
            )
        } else {
            bibleEngineClient.stop()
            bibleViewModel.clearDetectedReferences(reason = "expired")
        }
    }

    // Keep the Stage Monitor's "Next" verse in sync with whatever is currently selected —
    // recomputes automatically whenever the underlying Bible selection changes, from any source
    // (manual click, auto-follow, remote API), since nextVerses is a derived state.
    val nextVerses by bibleViewModel.nextVerses
    LaunchedEffect(nextVerses) {
        presenterManager.setNextVerses(nextVerses)
    }

    AutoFollowWiring()
    ScheduleVerseWiring()
}

/**
 * When Bible is live and the user is on a different tab, keeps the presenter in sync with new
 * auto-follow detections. BibleTab is inside AnimatedContent and leaves the composition on tab
 * switch, so its own LaunchedEffect can't fire while the user is away.
 */
@Composable
private fun MainDesktopScope.AutoFollowWiring() {
    val autoFollowLiveToken by bibleViewModel.autoFollowLiveToken
    val mainAutoFollowTokenGate = rememberTokenGate(autoFollowLiveToken)
    LaunchedEffect(autoFollowLiveToken) {
        if (!mainAutoFollowTokenGate.consume()) return@LaunchedEffect
        if (!shouldMainHandleAutoFollow(
                activeTabIndex = effectiveTabIndex,
                bibleTabIndex = visibleTabs.indexOf(Tabs.BIBLE),
                presentingMode = presentingMode,
            )
        ) return@LaunchedEffect
        val verses = bibleViewModel.getSelectedVerses()
        if (verses.isNotEmpty()) {
            live.onVerseSelected(verses)
            val primary = verses.first()
            bibleViewModel.logLiveReference(
                displayBookIndex = bibleViewModel.selectedBookIndex.value,
                chapter    = primary.chapter,
                verseStart = primary.verseNumber,
                verseEnd   = null,
                source     = "auto",
                autoFollow = true,
                matchType  = bibleViewModel.autoFollowLiveMatchType.value,
            )
        }
    }
}

/**
 * The same stand-in as [AutoFollowWiring], for a schedule verse clicked while BibleTab is not
 * composed — the Bible tab hidden in settings (selectTab then declines to switch, so the tab never
 * appears), or disposed by the visibleTabs clamp. Without this the item is silently dropped while
 * the presenter has already been switched to BIBLE, which is the blank output that was reported.
 */
@Composable
private fun MainDesktopScope.ScheduleVerseWiring() {
    val scheduleVerseGate = rememberTokenGate(state.selectedBibleVerseItemVersion)
    LaunchedEffect(state.selectedBibleVerseItemVersion) {
        if (!scheduleVerseGate.consume()) return@LaunchedEffect
        val item = state.selectedBibleVerseItem ?: return@LaunchedEffect
        if (!shouldMainResolveScheduleVerse(
                activeTabIndex = effectiveTabIndex,
                bibleTabIndex = visibleTabs.indexOf(Tabs.BIBLE),
            )
        ) return@LaunchedEffect
        val verses = bibleViewModel.resolveVerseSelection(
            bookName = item.bookName,
            chapter = item.chapter,
            verseNumber = item.verseNumber,
            verseRange = item.verseRange,
            bookId = item.bookId,
        )
        if (verses.isEmpty()) {
            CrashReporter.breadcrumb(
                "Bible schedule item did not resolve to a verse",
                category = "schedule",
            )
            return@LaunchedEffect
        }
        live.onVerseSelected(verses)
    }
}
