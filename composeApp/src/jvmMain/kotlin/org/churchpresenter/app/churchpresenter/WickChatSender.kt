package org.churchpresenter.app.churchpresenter

import java.util.Locale
import org.churchpresenter.app.churchpresenter.utils.appTelemetryIdentity
import org.churchpresenter.telemetry.ContactReporter
import org.churchpresenter.helper.report.ChatSendResult

/** The contact type the website files a Wick chat under. */
internal const val WICK_CHAT_TYPE = "wickChat"
private const val WICK_CHAT_NAME = "Wick chat"

/**
 * A chat the operator chose to send, as a Contact Us request: the masked [transcript], the [email]
 * they left for a reply, and the app version, OS, language and [packVersion] for context.
 */
internal fun wickChatRequest(transcript: String, email: String, packVersion: String): ContactReporter.ContactRequest =
    ContactReporter.ContactRequest(
        type = WICK_CHAT_TYPE,
        name = WICK_CHAT_NAME,
        message = transcript,
        email = email,
        context = listOf(
            ContactReporter.defaultContext(appTelemetryIdentity.versionDisplay),
            Locale.getDefault().toLanguageTag(),
            "Wick pack $packVersion",
        ).joinToString(" · "),
    )

/** How the contact endpoint's answer reads in Wick's bubble. */
internal fun ContactReporter.Outcome.asChatResult(): ChatSendResult = when (this) {
    ContactReporter.Outcome.Success -> ChatSendResult.SENT
    ContactReporter.Outcome.RateLimited -> ChatSendResult.RATE_LIMITED
    else -> ChatSendResult.FAILED
}

/** Sends a Wick chat through the Contact Us service, only ever from its Send button. */
internal suspend fun sendWickChat(transcript: String, email: String, packVersion: String): ChatSendResult =
    ContactReporter.submit(
        wickChatRequest(transcript, email, packVersion),
        appTelemetryIdentity.appVersion,
    ).asChatResult()
