package org.churchpresenter.media.composables

import org.churchpresenter.diagnostics.Log
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent

/** Custom VLC installation directory. Set from saved settings before first VLC access. */
var vlcCustomPath: String = ""

private var _vlcAvailable: Boolean? = null

/**
 * Human-readable reason VLC is unavailable; empty when VLC loaded successfully.
 * Populated by checkVlcAvailable() so the UI can show a targeted error message.
 */
var vlcUnavailableReason: String = ""

/** Returns true if VLC is installed and VLCJ can initialise. */
val isVlcAvailable: Boolean get() = _vlcAvailable ?: checkVlcAvailable().also { _vlcAvailable = it }

/**
 * True when VLC is present on disk but is the wrong CPU architecture
 * (e.g. x86_64 VLC on an Apple-Silicon Mac running an arm64 JVM).
 */
val isVlcArchMismatch: Boolean
    get() = vlcArchMismatchFrom(isVlcAvailable, vlcUnavailableReason)

/**
 * The arch-mismatch decision itself, over the two values [isVlcArchMismatch] reads from globals.
 *
 * Split out because those globals cannot be driven from a test: `isVlcAvailable` caches the result of
 * actually loading libvlc on the machine running the suite, so on a developer's box with VLC
 * installed the getter can only ever answer `false` and the branch that matters is unreachable.
 */
internal fun vlcArchMismatchFrom(available: Boolean, reason: String): Boolean =
    !available && "incompatible architecture" in reason.lowercase()

/**
 * True when a VLC installation was detected on disk but the native library still
 * failed to load (e.g. a corrupted/partial install, or on Windows a missing
 * dependency such as the Visual C++ Redistributable — LoadLibrary reports this as
 * "The specified module could not be found" even though libvlc.dll itself exists).
 * Distinct from "not installed" so the UI doesn't tell the user to install something
 * that is already there.
 */
val isVlcLoadFailed: Boolean
    get() = vlcLoadFailedFrom(isVlcAvailable, vlcUnavailableReason)

/**
 * The load-failed decision itself, over the two values [isVlcLoadFailed] reads from globals — split
 * out for the same reason as [vlcArchMismatchFrom].
 *
 * The three exclusions are what separate "installed but broken" from the two conditions with their
 * own messaging: a blank reason or the literal `not_found` means VLC simply isn't there, and an arch
 * mismatch is reported as itself rather than as a generic load failure.
 */
internal fun vlcLoadFailedFrom(available: Boolean, reason: String): Boolean =
    !available && reason.isNotBlank() && reason != "not_found" &&
        !vlcArchMismatchFrom(available, reason)

/** Clears the cached result and re-checks VLC availability. */
fun recheckVlcAvailability(): Boolean {
    _vlcAvailable = null
    vlcUnavailableReason = ""
    return isVlcAvailable
}

private fun checkVlcAvailable(): Boolean {
    return try {
        applyCustomVlcPath()
        if (!isVlcInstalledOnSystem()) {
            Log.warn("VLCJ", "VLC not found on this system. Skipping initialisation.")
            vlcUnavailableReason = "not_found"
            return false
        }
        // Try to actually load the native library by creating a player component.
        val component = createMediaPlayerComponent()
        if (component == null) {
            // createMediaPlayerComponent() already logged the error and set vlcUnavailableReason.
            return false
        }
        when (component) {
            is CallbackMediaPlayerComponent -> component.release()
            is EmbeddedMediaPlayerComponent -> component.release()
        }
        true
    } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
        // Loading libvlc fails with an Error (UnsatisfiedLinkError, NoClassDefFoundError), not an
        // Exception, and any of them means the same thing here: no VLC.
        vlcUnavailableReason = e.message ?: "unknown error"
        false
    }
}
