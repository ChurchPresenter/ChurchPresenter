package org.churchpresenter.app.churchpresenter.server

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Shared, observable status of an ATEM media upload triggered over the API
 * (Bitfocus Companion / curl). [CompanionServer]'s upload endpoints publish here;
 * the Lower Third tab collects [state] so the same progress bar that shows
 * UI-triggered uploads also reflects API-triggered ones.
 */
object AtemUploadStatus {

    /** Live status of the current API upload, or null when idle. */
    data class Status(
        val name: String,
        val clip: Boolean,
        val slot: Int,            // 1-based, for display
        val progress: Float,      // 0f..1f
        val error: String? = null
    )

    private val _state = MutableStateFlow<Status?>(null)
    val state: StateFlow<Status?> = _state.asStateFlow()

    fun begin(name: String, clip: Boolean, slot: Int) { _state.value = Status(name, clip, slot, 0f) }
    fun progress(p: Float) { _state.update { it?.copy(progress = p.coerceIn(0f, 1f)) } }
    fun complete()         { _state.update { it?.copy(progress = 1f) } }
    fun fail(msg: String?) { _state.update { it?.copy(error = msg ?: "Upload failed") } }
    fun clear()            { _state.value = null }
}
