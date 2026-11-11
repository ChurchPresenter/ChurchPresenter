package org.churchpresenter.liveoutput

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting

/**
 * Takes the announcement on air down once it has been up for its Show For seconds
 * (`AnnouncementsSettings.showForSeconds`; 0 keeps it up), the way its last loop ending does: the
 * whole display, or only the announcement, as the System setting says. An announcement sent live
 * again starts its time over; one cleared first is left alone.
 */
@Composable
fun AnnouncementExpiry(presenterManager: PresenterManager, appSettings: AppSettings) {
    val seconds = appSettings.announcementsSettings.showForSeconds
    val clearsDisplay = appSettings.projectionSettings.overlayEndClearsDisplay
    val up = Presenting.ANNOUNCEMENTS in presenterManager.overlays.value
    // Keyed on each showing too: a new announcement sent over the last one gets its own time.
    val shown = presenterManager.announcementsShown.intValue
    LaunchedEffect(up, shown, seconds) {
        if (!up || seconds <= 0) return@LaunchedEffect
        delay(seconds * MILLIS_PER_SECOND)
        if (presenterManager.announcementsShown.intValue == shown) {
            presenterManager.overlayFinished(Presenting.ANNOUNCEMENTS, clearsDisplay)
        }
    }
}

private const val MILLIS_PER_SECOND = 1000L
