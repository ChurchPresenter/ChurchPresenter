package org.churchpresenter.dialogs

sealed interface SendStatus {
    data object Idle : SendStatus
    data object Sending : SendStatus
    data object Sent : SendStatus
    data class Error(val message: String) : SendStatus
}
