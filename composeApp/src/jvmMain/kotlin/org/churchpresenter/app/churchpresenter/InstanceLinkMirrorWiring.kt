package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import org.churchpresenter.app.churchpresenter.viewmodel.invalidateInstanceLinkBibleCache
import org.churchpresenter.app.churchpresenter.viewmodel.setInstanceLinkSource

/**
 * Mirrors the primary's songs, Bible and schedule into this instance's ViewModels while it follows
 * over Instance Link, and hands local editing back on disconnect. Only in Controlled mode — a
 * Controller keeps its own library and schedule and drives the primary instead.
 */
@Composable
internal fun MainDesktopScope.InstanceLinkMirrorWiring() {
    // Mirrors the primary's song catalog — see SongsViewModel.setInstanceLinkSource for the lazy
    // per-song lyric fetch.
    LaunchedEffect(link.connectionStatus, link.remoteSongCatalog, link.role) {
        songsViewModel.setInstanceLinkSource(
            active = shouldMirrorFromPrimary(link.connectionStatus, link.role),
            catalog = link.remoteSongCatalog,
            fetchDetail = link.fetchSongDetail
        )
    }

    // Mirrors the primary's bible — see BibleViewModel.setInstanceLinkSource. The two *UpdatedSignal
    // keys re-run this when the primary announces a bible change: the cache is invalidated first so
    // FULL_REPLICA re-downloads the fresh file.
    LaunchedEffect(
        link.connectionStatus, link.bibleSyncMode, link.role,
        link.bibleUpdatedSignal, link.secondaryBibleUpdatedSignal
    ) {
        if (shouldInvalidateBibleCache(link.bibleUpdatedSignal, link.secondaryBibleUpdatedSignal)) {
            bibleViewModel.invalidateInstanceLinkBibleCache()
        }
        bibleViewModel.setInstanceLinkSource(
            active = shouldMirrorFromPrimary(link.connectionStatus, link.role),
            mode = link.bibleSyncMode,
            fetchBibleFile = link.fetchBibleFile,
            fetchSecondaryBibleFile = link.fetchSecondaryBibleFile,
            fetchBibleTranslations = link.fetchBibleTranslations,
        )
    }

    // Mirrors the primary's schedule, handing local editing back to the operator on disconnect
    // (see ScheduleViewModel.applyRemoteSchedule/stopFollowingRemote).
    LaunchedEffect(link.connectionStatus, link.remoteSchedule, link.role) {
        if (shouldMirrorFromPrimary(link.connectionStatus, link.role)) {
            scheduleViewModel.applyRemoteSchedule(link.remoteSchedule)
        } else {
            scheduleViewModel.stopFollowingRemote()
        }
    }
    LaunchedEffect(link.sendAddToSchedule) {
        scheduleViewModel.onPushToRemoteSchedule = link.sendAddToSchedule
    }
    LaunchedEffect(link.sendRemoveFromSchedule) {
        scheduleViewModel.onRemoveFromRemoteSchedule = link.sendRemoveFromSchedule
    }
}
