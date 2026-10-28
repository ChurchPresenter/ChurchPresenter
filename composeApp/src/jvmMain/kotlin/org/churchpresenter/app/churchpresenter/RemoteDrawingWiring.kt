package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.remote.RemoteDrawingShow
import org.churchpresenter.app.churchpresenter.remote.drawingEventLabel
import org.churchpresenter.app.churchpresenter.remote.settle
import org.churchpresenter.server.PendingDrawingRequest
import org.churchpresenter.server.RemoteEventType
import org.churchpresenter.server.remoteAccessDecision
import org.churchpresenter.server.remoteApproval
import org.churchpresenter.sharedui.utils.UsageEvent
import org.churchpresenter.sharedui.utils.UsageEvents

/*
 * A page drawn on a phone or tablet's canvas, put live from the device — see `COMPANION_API.md`,
 * Drawing. Going live is a project request to the operator, put to them with the same prompt; once
 * allowed, the device's later pages replace the one on screen until anything else takes it.
 */

/** A device's drawing: going live with the operator's say-so, its later pages, and ending it. */
@Composable
internal fun MainWindowScope.RemoteDrawingRequests() {
    with(root) {
        val show = remember(companionServer, presenterManager) {
            RemoteDrawingShow(companionServer.drawings, presenterManager)
        }
        LaunchedEffect(show) {
            companionServer.onDrawingRequest.collect { request -> askToShowDrawing(request, show) }
        }
        // Only the newest page matters: one still decoding when the next arrives is dropped for it.
        LaunchedEffect(show) {
            companionServer.drawingFrames.filterNotNull().collectLatest { frame -> show.refresh(frame) }
        }
        LaunchedEffect(show) {
            companionServer.onDrawingEnd.collect { show.ended() }
        }
    }
}

/** Puts [request] to the operator — or carries it out at once for a trusted device, or refuses it. */
private fun MainWindowScope.askToShowDrawing(request: PendingDrawingRequest, show: RemoteDrawingShow) = with(root) {
    val clientId = request.clientId
    val access = remoteAccessDecision(
        clientId,
        remoteClientManager.allowedClients, remoteClientManager.blockedClients,
        sessionAllowedClients, sessionBlockedClients,
    )
    val allow: () -> Unit = {
        if (!request.decision.isCompleted) UsageEvents.record(UsageEvent.REMOTE_PUT_LIVE)
        coroutineScope.launch { show.allow(request) }
    }
    val (title, detail) = drawingEventLabel(request.title)
    val outcome = remoteApproval(
        access,
        type = RemoteEventType.PROJECT,
        title = title,
        detail = detail,
        clientId = clientId,
        clientLabel = remoteClientManager.getLabel(clientId),
    )
    request.settle(outcome, allow, ::notifyRemoteActivity, remoteEventQueue, coroutineScope)
}
