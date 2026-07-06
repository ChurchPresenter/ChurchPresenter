package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import companionsatellite.CompanionConnectionStatus
import companionsatellite.CompanionSatelliteClient
import org.churchpresenter.app.churchpresenter.data.settings.CompanionSatelliteSettings
import org.churchpresenter.app.churchpresenter.models.CompanionButtonState
import org.churchpresenter.app.churchpresenter.models.CompanionConnectionUiState
import org.churchpresenter.app.churchpresenter.models.CompanionSurfacePlacement
import org.churchpresenter.app.churchpresenter.models.CompanionSurfaceSlot
import org.churchpresenter.app.churchpresenter.utils.CrashReporter
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image as SkiaImage

/**
 * Owns every configured [CompanionSatelliteClient] connection to Bitfocus Companion instances on
 * the network, so ChurchPresenter can act as a Satellite-style surface: show Companion's own
 * button grid and press those buttons from inside the app.
 *
 * A single configured connection can be shown at multiple UI placements at once (Tab / Left
 * Sidebar / Right Sidebar) with each on its own page — Companion ties "current page" to one device
 * identity, so each enabled placement is registered as its own client, keyed by
 * [CompanionSurfaceSlot]. Created lazily on first use.
 *
 * Takes no `AppSettings` reference — every method takes the current [CompanionSatelliteSettings]
 * as a parameter, read fresh by the caller at call time. Holding a captured settings reference
 * across calls would go stale the moment the user edits settings without restarting the app
 * (this project's other ViewModels — e.g. `PresentationViewModel` — follow the same rule: settings
 * only ever seed one-time defaults, live changes always flow in as explicit parameters).
 */
class CompanionSatelliteViewModel {
    /** The wire-registration-affecting settings a slot was last started with — used to detect when
     * settings changed under a still-connected slot (e.g. columns edited from 8 to 2) so it can be
     * torn down and re-registered. Companion has no "change my grid shape" message; only a fresh
     * ADD-DEVICE can do that, so leaving a live slot alone after such an edit would silently keep it
     * running with its old shape forever. */
    private data class SlotRegistrationParams(
        val host: String,
        val port: Int,
        val deviceId: String,
        val rows: Int,
        val columns: Int,
        val bitmapSize: Int,
        val productName: String,
        val reconnectDelayMs: Int
    )

    private val clients = mutableMapOf<CompanionSurfaceSlot, CompanionSatelliteClient>()
    private val activeParams = mutableMapOf<CompanionSurfaceSlot, SlotRegistrationParams>()

    private val _connectionStates = mutableStateMapOf<CompanionSurfaceSlot, CompanionConnectionUiState>()
    val connectionStates: Map<CompanionSurfaceSlot, CompanionConnectionUiState> get() = _connectionStates

    private val _buttons = mutableMapOf<CompanionSurfaceSlot, SnapshotStateList<CompanionButtonState>>()

    /** The live button grid for [slot] — created empty on first access. */
    fun buttonsFor(slot: CompanionSurfaceSlot): SnapshotStateList<CompanionButtonState> =
        _buttons.getOrPut(slot) { mutableStateListOf() }

    /** TAB always uses the connection's configured [CompanionSatelliteSettings.deviceId] directly
     * since it's the one placement every connection already had before multi-placement existed — a
     * fixed anchor, not "whichever placement happens to be enabled first." Left/Right Sidebar use
     * their own explicit override field when the user has set one (so a placement can be made to
     * match an id Companion already has permissions/pages configured for), otherwise fall back to a
     * derived id ("$deviceId-left_sidebar"/"$deviceId-right_sidebar") so Companion still treats them
     * as separate surfaces from TAB and from each other without requiring every user to fill in
     * three ids for the common single-placement case. */
    fun deviceIdFor(settings: CompanionSatelliteSettings, placement: CompanionSurfacePlacement): String {
        val override = when (placement) {
            CompanionSurfacePlacement.TAB -> null
            CompanionSurfacePlacement.LEFT_SIDEBAR -> settings.leftSidebarDeviceId.takeIf { it.isNotBlank() }
            CompanionSurfacePlacement.RIGHT_SIDEBAR -> settings.rightSidebarDeviceId.takeIf { it.isNotBlank() }
        }
        if (override != null) return override
        return if (placement == CompanionSurfacePlacement.TAB) settings.deviceId
        else "${settings.deviceId}-${placement.name.lowercase()}"
    }

    /** Connects every enabled placement for [settings] and disconnects any that are no longer
     * enabled, leaving already-live matching placements untouched — so toggling one placement's
     * checkbox never disturbs another placement already connected and sitting on its own page.
     *
     * A placement that's already connected but whose registration-affecting settings (rows,
     * columns, bitmap size, host, port, product name, reconnect delay) changed since it was
     * started gets torn down and restarted with the new values — otherwise editing e.g. columns
     * from 8 to 2 on a live connection would silently keep the old 8-wide grid registered with
     * Companion until the user manually disconnects and reconnects. */
    fun connectAll(settings: CompanionSatelliteSettings) {
        if (settings.host.isBlank()) return
        val desired = CompanionSurfacePlacement.entries
            .filter { settings.isEnabled(it) }
            .map { CompanionSurfaceSlot(settings.id, it) }
            .toSet()
        clients.keys.filter { it.connectionId == settings.id && it !in desired }
            .forEach { disableSlot(it.connectionId, it.placement) }
        desired.forEach { slot ->
            val newParams = paramsFor(slot, settings)
            if (activeParams[slot] != newParams) startSlot(slot, settings, newParams)
        }
    }

    /** Pauses every live placement for [settings] without disposing them (a manual Disconnect). */
    fun disconnectAll(settings: CompanionSatelliteSettings) {
        clients.keys.filter { it.connectionId == settings.id }.forEach { clients[it]?.disconnect() }
    }

    fun pressButton(slot: CompanionSurfaceSlot, index: Int) = clients[slot]?.pressButton(index)

    /** Tears down one placement's client — used when its checkbox is unchecked, and as the building
     * block for [removeConnection]. */
    fun disableSlot(connectionId: String, placement: CompanionSurfacePlacement) {
        val slot = CompanionSurfaceSlot(connectionId, placement)
        clients.remove(slot)?.dispose()
        activeParams.remove(slot)
        _connectionStates.remove(slot)
        _buttons.remove(slot)
    }

    /** Tears down every placement's client for a connection removed from settings entirely. */
    fun removeConnection(connectionId: String) {
        CompanionSurfacePlacement.entries.forEach { disableSlot(connectionId, it) }
    }

    fun dispose() {
        clients.values.forEach { it.dispose() }
        clients.clear()
        activeParams.clear()
        _connectionStates.clear()
        _buttons.clear()
    }

    private fun paramsFor(slot: CompanionSurfaceSlot, settings: CompanionSatelliteSettings) = SlotRegistrationParams(
        host = settings.host,
        port = settings.port,
        deviceId = deviceIdFor(settings, slot.placement),
        rows = settings.rowsFor(slot.placement),
        columns = settings.columnsFor(slot.placement),
        bitmapSize = settings.bitmapSizeFor(slot.placement),
        productName = settings.productName,
        reconnectDelayMs = settings.reconnectDelayMs
    )

    private fun startSlot(slot: CompanionSurfaceSlot, settings: CompanionSatelliteSettings, params: SlotRegistrationParams) {
        activeParams[slot] = params
        // startRow/startColumn default to 0 (top-left) — ChurchPresenter no longer offers UI to
        // change them; Companion's own per-surface settings already cover this reliably. See
        // buildLayoutManifest's doc in the submodule client for why LAYOUT_MANIFEST registration is
        // still used even at the default offset.
        clientFor(slot).connect(
            host = params.host,
            port = params.port,
            deviceId = params.deviceId,
            rows = params.rows,
            columns = params.columns,
            bitmapSize = params.bitmapSize,
            productName = params.productName,
            reconnectDelayMs = params.reconnectDelayMs.toLong()
        )
    }

    private fun clientFor(slot: CompanionSurfaceSlot): CompanionSatelliteClient = clients.getOrPut(slot) {
        CompanionSatelliteClient(
            onStatusChanged = { status, error ->
                val previousStatus = _connectionStates[slot]?.status
                _connectionStates[slot] = (_connectionStates[slot] ?: CompanionConnectionUiState(slot))
                    .copy(status = status, errorMessage = error ?: "")
                if (status != previousStatus) {
                    when (status) {
                        CompanionConnectionStatus.CONNECTED ->
                            CrashReporter.breadcrumb("Companion Satellite connected (${slot.connectionId}/${slot.placement})", category = "integration")
                        CompanionConnectionStatus.DISCONNECTED ->
                            CrashReporter.breadcrumb("Companion Satellite disconnected (${slot.connectionId}/${slot.placement})", category = "integration")
                        CompanionConnectionStatus.ERROR ->
                            CrashReporter.reportWarning(
                                "Companion Satellite connection error: ${error ?: "unknown"}",
                                tags = mapOf("subsystem" to "companion_satellite", "placement" to slot.placement.name)
                            )
                        CompanionConnectionStatus.CONNECTING -> {}
                    }
                }
            },
            onButtonUpdated = { update ->
                val list = buttonsFor(slot)
                if (update.index in list.indices) {
                    list[update.index] = CompanionButtonState(
                        index = update.index,
                        bitmap = update.bitmapRgb?.let { decodeBitmap(it, update.bitmapSize) },
                        text = update.text,
                        color = update.color,
                        textColor = update.textColor,
                        pressed = update.pressed
                    )
                }
            },
            onButtonsReset = { count ->
                val list = buttonsFor(slot)
                list.clear()
                repeat(count) { list.add(CompanionButtonState(index = it)) }
            },
            onBrightnessChanged = { percent ->
                _connectionStates[slot] = (_connectionStates[slot] ?: CompanionConnectionUiState(slot))
                    .copy(brightness = percent)
            }
        )
    }

    /** Companion streams raw RGB pixels (3 bytes/px, [size]x[size]) for a plain grid device. */
    private fun decodeBitmap(rgb: ByteArray, size: Int): ImageBitmap? = runCatching {
        val pixelCount = rgb.size / 3
        // N32 install expects 4 bytes/px in BGRA byte order (little-endian ARGB) — see the
        // identical approach in presenter/LowerThirdPresenter.kt's intArrayToImageBitmap.
        val argb = ByteArray(pixelCount * 4)
        var src = 0
        var dst = 0
        while (src + 2 < rgb.size) {
            argb[dst] = rgb[src + 2]
            argb[dst + 1] = rgb[src + 1]
            argb[dst + 2] = rgb[src]
            argb[dst + 3] = 0xFF.toByte()
            src += 3
            dst += 4
        }
        val bitmap = Bitmap()
        bitmap.allocN32Pixels(size, size)
        bitmap.installPixels(argb)
        SkiaImage.makeFromBitmap(bitmap).toComposeImageBitmap()
    }.getOrNull()
}
