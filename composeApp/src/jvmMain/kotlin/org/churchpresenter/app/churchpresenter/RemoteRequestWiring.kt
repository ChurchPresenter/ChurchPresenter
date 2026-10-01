package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.dialogs.text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.remote_api_calendar_enroll_code
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.dialogs.RemoteEvent
import org.churchpresenter.app.churchpresenter.dialogs.RemoteEventType
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.app.churchpresenter.server.CalendarEnrollDecision
import org.churchpresenter.app.churchpresenter.server.asReply
import org.churchpresenter.app.churchpresenter.dialogs.enrollCodeText
import org.churchpresenter.app.churchpresenter.utils.UsageEvent
import org.churchpresenter.app.churchpresenter.utils.UsageEvents
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.app.churchpresenter.server.remoteAccessDecision
import org.churchpresenter.app.churchpresenter.server.addScheduleItem
import org.churchpresenter.app.churchpresenter.server.batchEventSummary
import org.churchpresenter.app.churchpresenter.server.emitRemoteTabSelection
import org.churchpresenter.app.churchpresenter.server.RemoteApproval
import org.churchpresenter.app.churchpresenter.server.remoteApproval
import org.churchpresenter.app.churchpresenter.server.executeProjectItem
import org.churchpresenter.app.churchpresenter.server.qaActionType
import org.churchpresenter.app.churchpresenter.server.remoteEventLabel
import org.churchpresenter.app.churchpresenter.server.withAnnouncement

/*
 * The requests remote devices make of this app, each allowed, refused or put to the operator
 * according to which devices are trusted — see remoteAccessDecision.
 */

/** A phone adding a row to the Schedule: allowed, refused, or put to the operator. */
@Composable
internal fun MainWindowScope.RemoteAddRequests() {
    with(root) {
        LaunchedEffect(remoteClientManager.blockedClients, sessionBlockedClients.toList()) {
            companionServer.blockedClientIds =
                remoteClientManager.blockedClients + sessionBlockedClients
        }

        LaunchedEffect(Unit) {
            companionServer.onAddToSchedule.collect { pending ->
                val clientId = pending.clientId
                val access = remoteAccessDecision(
                    clientId,
                    remoteClientManager.allowedClients, remoteClientManager.blockedClients,
                    sessionAllowedClients, sessionBlockedClients,
                )
                val item = pending.item
                val add: () -> Unit = {
                    UsageEvents.record(UsageEvent.REMOTE_ADDED_TO_SCHEDULE)
                    addScheduleItem(item, currentScheduleActions) { song ->
                        coroutineScope.launch { remoteSelectSongFlow.emit(song) }
                    }
                    pending.decision.complete(true)
                }
                val (eTitle, eDetail) = remoteEventLabel(item)
                when (val outcome = remoteApproval(
                    access,
                    type = RemoteEventType.ADD_TO_SCHEDULE,
                    title = eTitle,
                    detail = eDetail,
                    clientId = clientId,
                    clientLabel = remoteClientManager.getLabel(clientId),
                )) {
                    RemoteApproval.Reject -> pending.decision.complete(false)
                    is RemoteApproval.Approve -> {
                        add()
                        remoteActivityNotifications.add(outcome.notification)
                    }
                    is RemoteApproval.Ask -> remoteEventQueue.add(Triple(
                        outcome.event, add, { pending.decision.complete(false) },
                    ))
                }
            }
        }
    }
}

/** A phone asking to plan the calendar through the relay. */
@Composable
internal fun MainWindowScope.RemoteEnrollRequests() {
    with(root) {
        // A phone asking to plan the calendar through the relay: blocked devices
        // are refused, everyone else is asked, and on Allow the phone is handed
        // its enrollment in the reply -- no QR to scan afterwards.
        val enrollCodeFormat = stringResource(Res.string.remote_api_calendar_enroll_code)
        LaunchedEffect(Unit) {
            companionServer.onCalendarEnroll.collect { pending ->
                val clientId = pending.clientId
                val access = remoteAccessDecision(
                    clientId,
                    remoteClientManager.allowedClients, remoteClientManager.blockedClients,
                    sessionAllowedClients, sessionBlockedClients,
                )
                // Sync off means no relay, so there is nothing to enroll into; a blocked phone is refused.
                calendarEnrollGate(appSettings.calendarSync.enabled, access)?.let { answer ->
                    pending.decision.complete(answer)
                    return@collect
                }
                val enroll: () -> Unit = {
                    coroutineScope.launch {
                        // The phone stopped waiting (or was refused) while this prompt
                        // sat in the queue.
                        if (pending.decision.isCompleted) return@launch
                        val enrollment = calendarSync.enroll(clientId, pending.deviceName)
                        pending.decision.complete(
                            if (enrollment == null) {
                                CalendarEnrollDecision.RelayFailed
                            } else {
                                UsageEvents.record(UsageEvent.CALENDAR_PHONE_ADDED)
                                CalendarEnrollDecision.Approved(enrollment.asReply())
                            },
                        )
                    }
                }
                remoteEventQueue.add(Triple(
                    RemoteEvent(
                        type = RemoteEventType.CALENDAR_ENROLL,
                        title = pending.deviceName,
                        detail = enrollCodeText(pending.code, enrollCodeFormat),
                        clientId = clientId,
                        clientLabel = remoteClientManager.getLabel(clientId),
                    ),
                    enroll,
                    { pending.decision.complete(CalendarEnrollDecision.Denied) },
                ))
            }
        }
    }
}

/** A phone removing a row from the Schedule, or adding several at once. */
@Composable
internal fun MainWindowScope.RemoteRemoveRequests() {
    with(root) {
        LaunchedEffect(Unit) {
            companionServer.onRemoveFromSchedule.collect { pending ->
                val clientId = pending.clientId
                val access = remoteAccessDecision(
                    clientId,
                    remoteClientManager.allowedClients, remoteClientManager.blockedClients,
                    sessionAllowedClients, sessionBlockedClients,
                )
                val remove: () -> Unit = {
                    currentScheduleActions.removeById(pending.id)
                    pending.decision.complete(true)
                }
                when (val outcome = remoteApproval(
                    access,
                    type = RemoteEventType.REMOVE_FROM_SCHEDULE,
                    title = pending.label,
                    clientId = clientId,
                    clientLabel = remoteClientManager.getLabel(clientId),
                )) {
                    RemoteApproval.Reject -> pending.decision.complete(false)
                    is RemoteApproval.Approve -> {
                        remove()
                        remoteActivityNotifications.add(outcome.notification)
                    }
                    is RemoteApproval.Ask -> remoteEventQueue.add(Triple(
                        outcome.event, remove, { pending.decision.complete(false) },
                    ))
                }
            }
        }

        LaunchedEffect(Unit) {
            companionServer.onAddBatchToSchedule.collect { pending ->
                val clientId = pending.clientId
                val access = remoteAccessDecision(
                    clientId,
                    remoteClientManager.allowedClients, remoteClientManager.blockedClients,
                    sessionAllowedClients, sessionBlockedClients,
                )
                val addAll: () -> Unit = {
                    UsageEvents.record(UsageEvent.REMOTE_ADDED_TO_SCHEDULE, pending.items.size)
                    for (item in pending.items) {
                        addScheduleItem(item, currentScheduleActions) { song ->
                            coroutineScope.launch { remoteSelectSongFlow.emit(song) }
                        }
                    }
                    pending.decision.complete(true)
                }
                val (batchTitle, batchDetail) = batchEventSummary(pending.items)
                when (val outcome = remoteApproval(
                    access,
                    type = RemoteEventType.ADD_TO_SCHEDULE,
                    title = batchTitle,
                    detail = batchDetail,
                    clientId = clientId,
                    clientLabel = remoteClientManager.getLabel(clientId),
                )) {
                    RemoteApproval.Reject -> pending.decision.complete(false)
                    is RemoteApproval.Approve -> {
                        addAll()
                        remoteActivityNotifications.add(outcome.notification)
                    }
                    is RemoteApproval.Ask -> remoteEventQueue.add(Triple(
                        outcome.event, addAll, { pending.decision.complete(false) },
                    ))
                }
            }
        }
    }
}

/** A phone putting an item live. */
@Composable
internal fun MainWindowScope.RemoteProjectRequests() {
    with(root) {
        LaunchedEffect(Unit) {
            companionServer.onProject.collect { pending ->
                val clientId = pending.clientId
                val access = remoteAccessDecision(
                    clientId,
                    remoteClientManager.allowedClients, remoteClientManager.blockedClients,
                    sessionAllowedClients, sessionBlockedClients,
                )
                val item = pending.item
                val project: () -> Unit = {
                    UsageEvents.record(UsageEvent.REMOTE_PUT_LIVE)
                    if (item is ScheduleItem.AnnouncementItem) {
                        appSettings = appSettings.withAnnouncement(item)
                    }
                    executeProjectItem(
                        item,
                        currentScheduleActions,
                        presenterManager,
                        statisticsManager
                    )
                    coroutineScope.launch {
                        emitRemoteTabSelection(
                            item, remoteSelectSongFlow,
                            remoteSelectPictureFlow, remoteSelectPresentationFlow,
                            remoteSelectMediaFlow,
                        )
                    }
                    pending.decision.complete(true)
                }
                val (pTitle, pDetail) = remoteEventLabel(item)
                when (val outcome = remoteApproval(
                    access,
                    type = RemoteEventType.PROJECT,
                    title = pTitle,
                    detail = pDetail,
                    clientId = clientId,
                    clientLabel = remoteClientManager.getLabel(clientId),
                )) {
                    RemoteApproval.Reject -> pending.decision.complete(false)
                    is RemoteApproval.Approve -> {
                        project()
                        remoteActivityNotifications.add(outcome.notification)
                    }
                    is RemoteApproval.Ask -> remoteEventQueue.add(Triple(
                        outcome.event, project, { pending.decision.complete(false) },
                    ))
                }
            }
        }
    }
}

/** A Q&A admin action, and a presentation remote connecting. */
@Composable
internal fun MainWindowScope.QaAndPresentationConnectRequests() {
    with(root) {
        LaunchedEffect(Unit) {
            companionServer.onQAAdminRequest.collect { pending ->
                val clientId = pending.clientId
                val access = remoteAccessDecision(
                    clientId,
                    remoteClientManager.allowedClients, remoteClientManager.blockedClients,
                    sessionAllowedClients, sessionBlockedClients,
                )
                when (val outcome = remoteApproval(
                    access,
                    type = qaActionType(pending.action),
                    title = remoteEventTitle(pending.text),
                    clientId = clientId,
                    clientLabel = remoteClientManager.getLabel(clientId),
                )) {
                    RemoteApproval.Reject -> pending.decision.complete(false)
                    is RemoteApproval.Approve -> {
                        pending.decision.complete(true)
                        remoteActivityNotifications.add(outcome.notification)
                    }
                    is RemoteApproval.Ask -> remoteEventQueue.add(Triple(
                        outcome.event,
                        { pending.decision.complete(true) },
                        { pending.decision.complete(false) },
                    ))
                }
            }
        }

        LaunchedEffect(Unit) {
            companionServer.onPresentationRemoteConnect.collect { pending ->
                val clientId = pending.clientId
                val access = remoteAccessDecision(
                    clientId,
                    remoteClientManager.allowedClients, remoteClientManager.blockedClients,
                    sessionAllowedClients, sessionBlockedClients,
                )
                when (val outcome = remoteApproval(
                    access,
                    type = RemoteEventType.PRESENTATION_CONNECT,
                    title = "",
                    clientId = clientId,
                    clientLabel = remoteClientManager.getLabel(clientId),
                )) {
                    RemoteApproval.Reject -> pending.decision.complete(false)
                    is RemoteApproval.Approve -> {
                        pending.decision.complete(true)
                        remoteActivityNotifications.add(outcome.notification)
                    }
                    is RemoteApproval.Ask -> remoteEventQueue.add(Triple(
                        outcome.event,
                        { pending.decision.complete(true) },
                        { pending.decision.complete(false) },
                    ))
                }
            }
        }
    }
}

/** A Q&A admin view, and a musician view, connecting. */
@Composable
internal fun MainWindowScope.AdminAndMusicianConnectRequests() {
    with(root) {
        LaunchedEffect(Unit) {
            companionServer.onQaAdminConnect.collect { pending ->
                val clientId = pending.clientId
                val access = remoteAccessDecision(
                    clientId,
                    remoteClientManager.allowedClients, remoteClientManager.blockedClients,
                    sessionAllowedClients, sessionBlockedClients,
                )
                when (val outcome = remoteApproval(
                    access,
                    type = RemoteEventType.QA_ADMIN_CONNECT,
                    title = "",
                    clientId = clientId,
                    clientLabel = remoteClientManager.getLabel(clientId),
                )) {
                    RemoteApproval.Reject -> pending.decision.complete(false)
                    is RemoteApproval.Approve -> {
                        pending.decision.complete(true)
                        remoteActivityNotifications.add(outcome.notification)
                    }
                    is RemoteApproval.Ask -> remoteEventQueue.add(Triple(
                        outcome.event,
                        { pending.decision.complete(true) },
                        { pending.decision.complete(false) },
                    ))
                }
            }
        }

        LaunchedEffect(Unit) {
            companionServer.onMusicianConnect.collect { pending ->
                val clientId = pending.clientId
                val access = remoteAccessDecision(
                    clientId,
                    remoteClientManager.allowedClients, remoteClientManager.blockedClients,
                    sessionAllowedClients, sessionBlockedClients,
                )
                when (val outcome = remoteApproval(
                    access,
                    type = RemoteEventType.MUSICIAN_CONNECT,
                    title = "",
                    clientId = clientId,
                    clientLabel = remoteClientManager.getLabel(clientId),
                )) {
                    RemoteApproval.Reject -> pending.decision.complete(false)
                    is RemoteApproval.Approve -> {
                        pending.decision.complete(true)
                        remoteActivityNotifications.add(outcome.notification)
                    }
                    is RemoteApproval.Ask -> remoteEventQueue.add(Triple(
                        outcome.event,
                        { pending.decision.complete(true) },
                        { pending.decision.complete(false) },
                    ))
                }
            }
        }
    }
}

