package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.RemoteClientManager
import org.churchpresenter.app.churchpresenter.dialogs.RemoteActivityNotification
import org.churchpresenter.app.churchpresenter.dialogs.RemoteEvent
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.theme.ThemeCustomization
import org.churchpresenter.app.churchpresenter.server.CalendarInvite
import org.churchpresenter.app.churchpresenter.server.CalendarSyncService
import org.churchpresenter.calendar.CalendarHost
import org.churchpresenter.calendar.ServiceAutoLoader
import org.churchpresenter.calendar.fireCue
import java.io.File
import androidx.compose.runtime.Stable
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.FrameWindowScope
import org.churchpresenter.app.churchpresenter.server.TunnelStatus

/**
 * The main window's own state: the queue of remote requests waiting for the operator, which
 * devices are allowed or blocked this session, the activity toasts, and the calendar invite QR.
 * Remembered for as long as the window's content is composed.
 */
@Stable
internal class MainWindowState(calendarSyncInitiallyOn: Boolean) {
    val remoteEventQueue = mutableStateListOf<Triple<RemoteEvent, () -> Unit, () -> Unit>>()
    val remoteClientManager = RemoteClientManager()
    val sessionAllowedClients = mutableStateListOf<String>()
    val sessionBlockedClients = mutableStateListOf<String>()
    val remoteActivityNotifications = mutableStateListOf<RemoteActivityNotification>()
    // The QR a just-approved phone scans to get its calendar token and key.
    var calendarEnrollQr by mutableStateOf<CalendarInvite?>(null)
    var calendarSyncWasOn by mutableStateOf(calendarSyncInitiallyOn)
}

/**
 * Everything the main window's pieces read, for one composition: the app's state as [root], the
 * window's own state, and the values made per composition — the calendar's service loader, the
 * tunnel, and the settings the outputs render with.
 *
 * Built afresh on every composition and never remembered, like MainDesktopScope, so a lambda made
 * in a piece captures that composition's values. The pieces open with `with(root)`, so the code in
 * them reads the app's state under the names it always had.
 */
// Wide by design: it stands in for the locals of the one function it was split out of.
@Suppress("LongParameterList")
internal class MainWindowScope(
    val root: AppRootState,
    val win: MainWindowState,
    val frame: FrameWindowScope,
    val bannerModifier: Modifier,
    val calendarSync: CalendarSyncService,
    val effectiveAppSettings: AppSettings,
    val tunnelStatus: TunnelStatus,
    val tunnelUrl: String?,
    val onThemeCustomizationChange: (ThemeCustomization) -> Unit,
    val calendarFolder: File,
    val cueHost: CalendarHost,
    val serviceAutoLoader: ServiceAutoLoader,
) {
    val window: ComposeWindow get() = frame.window
    val remoteEventQueue get() = win.remoteEventQueue
    val remoteClientManager get() = win.remoteClientManager
    val sessionAllowedClients get() = win.sessionAllowedClients
    val sessionBlockedClients get() = win.sessionBlockedClients
    val remoteActivityNotifications get() = win.remoteActivityNotifications
    var calendarEnrollQr by win::calendarEnrollQr
    var calendarSyncWasOn by win::calendarSyncWasOn

    val fireScheduleCue: (ScheduleItem.CueItem) -> Unit get() = { cue ->
        fireCue(cueHost, root.currentScheduleItems, cue, loadRows = false)
    }
}
