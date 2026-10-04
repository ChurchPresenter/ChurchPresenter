package org.churchpresenter.server

/**
 * Describes a pending remote API event waiting for user approval.
 */
data class RemoteEvent(
    val type: RemoteEventType,
    val title: String,
    val detail: String = "",
    /** The value of the X-Device-Id header sent by the remote client. Empty if none provided. */
    val clientId: String = "",
    /** Human-readable label saved for this device. Empty if none has been set. */
    val clientLabel: String = ""
)

enum class RemoteEventType {
    ADD_TO_SCHEDULE,
    REMOVE_FROM_SCHEDULE,
    PROJECT,
    PRESENTATION_CONNECT,
    /** A phone asking to plan the calendar through the relay. */
    CALENDAR_ENROLL,
    PRESENT,    // instant: select_song_section / select_picture / select_slide / select_bible_verse
    UPLOAD,     // instant: presentation or picture upload
    CLEAR,      // instant: POST /api/clear
    QA_ADD,
    QA_EDIT,
    QA_DELETE,
    QA_APPROVE,
    QA_DENY,
    QA_DONE,
    QA_DISPLAY,
    QA_CLEAR_DISPLAY,
    QA_ADMIN_CONNECT,
    /** A tablet opening a Browser Source output's musician view, to transpose its chords. */
    MUSICIAN_CONNECT,
}
