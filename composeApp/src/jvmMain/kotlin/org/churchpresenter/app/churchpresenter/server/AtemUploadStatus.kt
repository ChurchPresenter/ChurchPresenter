package org.churchpresenter.app.churchpresenter.server

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * Shared, observable status of an ATEM media upload. Both [CompanionServer]'s API/Companion
 * upload endpoints and the in-app Lower Third upload publish here; the Lower Third tab collects
 * [state] so one progress bar reflects whichever upload is current.
 *
 * Only a single upload is shown at a time, but each upload is tagged with an id returned by
 * [begin]; [progress]/[complete]/[fail]/[clear] only touch the status if it still belongs to that
 * id. This stops an earlier upload that finishes mid-way through a newer one from clearing or
 * mislabelling the newer upload's bar when they overlap.
 */
object AtemUploadStatus {

    /** Live status of the current upload, or null when idle. */
    data class Status(
        val id: Long,             // identifies the owning upload
        val name: String,
        val clip: Boolean,
        val slot: Int,            // 1-based, for display
        val progress: Float,      // 0f..1f
        val error: String? = null
    )

    private val _state = MutableStateFlow<Status?>(null)
    val state: StateFlow<Status?> = _state.asStateFlow()

    private val nextId = AtomicLong(0L)

    /** Begin a new upload and return its id; pass that id to the other calls. */
    fun begin(name: String, clip: Boolean, slot: Int): Long {
        val id = nextId.incrementAndGet()
        _state.value = Status(id, name, clip, slot, 0f)
        return id
    }

    fun progress(id: Long, p: Float) =
        _state.update { if (it?.id == id) it.copy(progress = p.coerceIn(0f, 1f)) else it }

    fun complete(id: Long) =
        _state.update { if (it?.id == id) it.copy(progress = 1f) else it }

    fun fail(id: Long, msg: String?) =
        _state.update { if (it?.id == id) it.copy(error = msg ?: "Upload failed") else it }

    fun clear(id: Long) =
        _state.update { if (it?.id == id) null else it }
}
