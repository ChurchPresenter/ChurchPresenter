package org.churchpresenter.server

/**
 * Describes an auto-approved remote action that should be surfaced as a toast
 * so the operator knows what was done on their behalf.
 */
data class RemoteActivityNotification(
    val type: RemoteEventType,
    val title: String,
    val detail: String = "",
    val clientId: String = "",
    val clientLabel: String = ""
)
