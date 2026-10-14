package org.churchpresenter.canvas

import java.io.IOException
import java.util.concurrent.TimeUnit

/** Where Windows keeps its camera privacy switches, under both HKLM and HKCU. */
private const val CAMERA_CONSENT_KEY =
    "Software\\Microsoft\\Windows\\CurrentVersion\\CapabilityAccessManager\\ConsentStore\\webcam"

/**
 * The three switches that can stop a desktop app opening a camera: camera access for the whole
 * device (HKLM), camera access for this user (HKCU), and "Let desktop apps access your camera"
 * (HKCU, `NonPackaged`). Each holds `Value` = `Allow` or `Deny`.
 */
internal val CAMERA_CONSENT_KEYS = listOf(
    "HKLM\\$CAMERA_CONSENT_KEY",
    "HKCU\\$CAMERA_CONSENT_KEY",
    "HKCU\\$CAMERA_CONSENT_KEY\\NonPackaged",
)

private const val REG_QUERY_TIMEOUT_S = 3L

private val DENY_VALUE =
    Regex("""^\s*Value\s+REG_SZ\s+Deny\s*$""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

/** Whether one `reg query <key> /v Value` answer says Deny. */
internal fun consentDenied(regQueryOutput: String): Boolean = DENY_VALUE.containsMatchIn(regQueryOutput)

/**
 * Whether Windows' camera privacy settings are blocking desktop apps, asking [query] for each of
 * [CAMERA_CONSENT_KEYS]. A key [query] cannot read (`null`) is not taken as a block.
 */
internal fun windowsCameraBlocked(query: (key: String) -> String?): Boolean =
    CAMERA_CONSENT_KEYS.any { key -> query(key)?.let(::consentDenied) == true }

/** `reg query [key] /v Value`'s output, or `null` when it could not be run or read. */
internal fun queryRegistryValue(key: String): String? =
    try {
        val process = ProcessBuilder("reg", "query", key, "/v", "Value").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (process.waitFor(REG_QUERY_TIMEOUT_S, TimeUnit.SECONDS)) output else null.also { process.destroyForcibly() }
    } catch (_: IOException) {
        null
    }

/**
 * [failure], read in the light of Windows' own camera privacy switch.
 *
 * With "Let desktop apps access your camera" off, ffmpeg can still list the cameras and then fails
 * to open one with "could not find video device", which reads as an unplugged or renamed camera.
 * Found by the Windows acceptance pass. ffmpeg's "Unable to BindToObject" line was the first thing
 * tried here, but a capture driver with no card behind it prints exactly the same line, so the
 * setting itself is what decides. Only DirectShow is asked, and only when the device was not found.
 */
internal fun refineForWindowsPrivacy(failure: CameraFailure, scheme: String, blocked: () -> Boolean): CameraFailure =
    if (failure == CameraFailure.DEVICE_NOT_FOUND && scheme == DSHOW_SCHEME && blocked()) {
        CameraFailure.PERMISSION_DENIED
    } else {
        failure
    }

/** The Windows capture scheme, the only one Windows' privacy switch applies to. */
internal const val DSHOW_SCHEME = "dshow"
