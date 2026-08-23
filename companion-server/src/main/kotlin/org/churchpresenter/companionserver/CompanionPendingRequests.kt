package org.churchpresenter.companionserver

import org.churchpresenter.core.models.schedule.ScheduleItem

/**
 * Wraps an incoming remote request with a [CompletableDeferred] that the UI
 * resolves once the user clicks Allow (true) or Deny/Block (false).
 * The HTTP endpoint suspends on [decision] before sending a response, so the
 * calling device receives the correct status code.
 */
data class PendingRemoteRequest(
    val item: ScheduleItem,
    val clientId: String = "",
    val decision: kotlinx.coroutines.CompletableDeferred<Boolean> = kotlinx.coroutines.CompletableDeferred()
)

/**
 * Same as [PendingRemoteRequest] but carries multiple items — used by the
 * batch add endpoint so the user approves or denies the whole group at once.
 */
data class PendingBatchRequest(
    val items: List<ScheduleItem>,
    val clientId: String = "",
    val decision: kotlinx.coroutines.CompletableDeferred<Boolean> = kotlinx.coroutines.CompletableDeferred()
)

/** Same shape as [PendingRemoteRequest] but for a remove request — carries just the target id and a
 *  human-readable label (resolved from the current schedule, if still present) for the approval UI. */
data class PendingRemoveRequest(
    val id: String,
    val label: String,
    val clientId: String = "",
    val decision: kotlinx.coroutines.CompletableDeferred<Boolean> = kotlinx.coroutines.CompletableDeferred()
)

/**
 * Emitted when a device authenticates against the presentation remote for the first time
 * this session, so the desktop operator can approve/deny it like any other remote action.
 */
data class PendingConnectionRequest(
    val clientId: String = "",
    val decision: kotlinx.coroutines.CompletableDeferred<Boolean> = kotlinx.coroutines.CompletableDeferred()
)
