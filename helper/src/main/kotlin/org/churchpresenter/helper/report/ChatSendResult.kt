package org.churchpresenter.helper.report

/** How sending a chat to the ChurchPresenter team went. */
enum class ChatSendResult {
    SENT,

    /** Too many sent from here just now. */
    RATE_LIMITED,

    /** Not delivered: no connection, or the server refused it. */
    FAILED,
}

/** Sends a chat [message] — the operator's note and the transcript — with the [email] they left (may be blank). */
typealias ChatSender = suspend (message: String, email: String) -> ChatSendResult
