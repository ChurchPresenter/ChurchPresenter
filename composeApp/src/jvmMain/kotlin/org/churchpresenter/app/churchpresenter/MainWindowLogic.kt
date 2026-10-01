package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.dialogs.RemoteEvent
import org.churchpresenter.app.churchpresenter.dialogs.RemoteEventType
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.UpdateCheckResult
import org.churchpresenter.theme.ThemeMode

/*
 * The main window's decisions, held apart from AppRootState and the composables that act on them:
 * which update result to show, what the remote-approval prompt settles, and the small judgements
 * the wiring makes. Everything here is pure.
 */

/**
 * What a finished update check leaves to show, and whether it shows as if asked for. The first check
 * ever shows its result whatever it is, so the operator learns the app checks; later ones show only an
 * available update. Null leaves whatever was pending as it was.
 */
internal fun pendingUpdateFor(isFirstEverCheck: Boolean, result: UpdateCheckResult): Pair<UpdateCheckResult, Boolean>? =
    when {
        isFirstEverCheck -> result to true
        result is UpdateCheckResult.Available -> result to false
        else -> null
    }

/** Whether the story prompt shows: when it is due, and not on top of an update waiting to be shown. */
internal fun shouldShowStoryPrompt(isDue: Boolean, updatePending: Boolean): Boolean = isDue && !updatePending

/**
 * Whether the operator has something of their own on screen, so a cue must not take over: anything is
 * live, and it is not the row the calendar engine itself put there ([engineItemShowing]).
 */
internal fun isOperatorLive(presentingMode: Presenting, engineItemShowing: Boolean?): Boolean =
    presentingMode != Presenting.NONE && engineItemShowing != true

/** A remote action's toast title: Clear Display has a fixed one of its own, everything else is named by the phone. */
internal fun remoteActivityTitle(type: RemoteEventType, clearDisplayTitle: String, sentTitle: String): String =
    if (type == RemoteEventType.CLEAR) clearDisplayTitle else sentTitle

/**
 * Whether Customize Theme opens with custom colours on: unless a custom look exists and a preset has been
 * picked since, in which case opening it is more likely about the font or the size.
 */
internal fun customColorsByDefault(theme: ThemeMode, customAccent: String): Boolean =
    theme == ThemeMode.CUSTOM || customAccent.isBlank()

/** Whether a session allow or block should record [clientId]: an attributable client not already in [recorded]. */
internal fun shouldRecordSessionClient(clientId: String, recorded: List<String>): Boolean =
    clientId.isNotBlank() && clientId !in recorded

/** The queued remote requests a decision about [clientId] settles -- see remoteEventTargetsClient. */
internal fun <A, B> remoteEventsSettledBy(
    queue: List<Triple<RemoteEvent, A, B>>,
    clientId: String,
): List<Triple<RemoteEvent, A, B>> = queue.filter { remoteEventTargetsClient(it.first.clientId, clientId) }
