package org.churchpresenter.app.churchpresenter.composables

/**
 * What went wrong when a camera could not be opened, read off ffmpeg's own stderr.
 *
 * The capture loop used to know only that it had failed five times, which is not a diagnosis: a
 * camera another application is holding, a camera macOS is refusing on privacy grounds, and a
 * capture card that does not speak the pixel format asked of it all looked identical — a grey
 * placeholder on the canvas and a warning in Sentry with nothing in it.
 *
 * Each constant here names a different fix, and two of them are the operator's environment rather
 * than a defect in the app. [UNKNOWN] is not a failure of this enum: an unrecognised tail is still
 * reported verbatim, so the next occurrence names the marker that belongs here.
 */
internal enum class CameraFailure {
    /** macOS/Windows refused camera access. Retrying never resolves this. */
    PERMISSION_DENIED,

    /** The device rejected the pixel format asked of it, and usually lists what it does accept. */
    UNSUPPORTED_PIXEL_FORMAT,

    /** The device rejected the frame rate asked of it. */
    UNSUPPORTED_FRAMERATE,

    /** The device is open in another application. */
    DEVICE_BUSY,

    /** The device named on the command line is gone — unplugged since it was enumerated. */
    DEVICE_NOT_FOUND,

    /** ffmpeg ran and stayed running without ever producing a frame. */
    NO_FRAMES,

    /** A DeckLink card whose input cannot be opened because the app is using it for output. */
    DECKLINK_INPUT_IN_USE,

    /**
     * ffmpeg is not installed, so no camera can be opened at all.
     *
     * Decided before ffmpeg runs rather than classified from its stderr, which is why
     * [classifyCameraFfmpegStderr] has no marker for it: there is no stderr, because there is no
     * process. Not a defect in the app — but the operator has to be told, or the canvas is simply
     * black with no reason given.
     */
    FFMPEG_MISSING,

    /** ffmpeg failed for a reason not recognised here. The stderr tail is reported as-is. */
    UNKNOWN,
}

/** Where a redacted stderr tail is truncated. Sentry caps extras, and a long tail buys nothing. */
private const val MAX_REDACTED_STDERR_CHARS = 4000

/** How many trailing stderr lines are worth keeping. ffmpeg states its complaint at the end. */
private const val MAX_REDACTED_STDERR_LINES = 20

/** Shortest device name worth redacting — a one- or two-character name matches half of ffmpeg's output. */
private const val MIN_REDACTABLE_NAME_LENGTH = 3

/** Two or more path segments in a row: a real filesystem path rather than an ffmpeg option. */
private val ABSOLUTE_PATH = Regex("""(?:/[\w.@+-]+){2,}""")

/** `video=Some Camera` on a dshow command line, up to the end of the argument. */
private val DSHOW_DEVICE_ARG = Regex("""video=[^"']*""")

/**
 * The pixel formats this app would rather have, best first.
 *
 * All of these decode to BGRA without a colour-space guess, and the ordering is by how cheaply:
 * the packed 4:2:2 formats every USB capture stick speaks come before `nv12`, and `mjpeg` — which
 * is all a 1080p stick on a USB 2.0 link can offer — comes before the RGB variants that only
 * virtual cameras tend to expose.
 */
private val PREFERRED_PIXEL_FORMATS = listOf("uyvy422", "yuyv422", "nv12", "mjpeg", "0rgb", "bgr0")

/**
 * Classifies ffmpeg's stderr [stderrTail] into the one thing that went wrong.
 *
 * The order of the checks is the result, not an implementation detail: a tail routinely holds more
 * than one marker — a device that refuses the pixel format also prints an I/O error on the way out,
 * and one that has been unplugged prints both "could not find" and an I/O error — and the first
 * match wins, so the same output always classifies the same way. Permission comes first because it
 * makes every later complaint meaningless, and the specific causes come before the generic
 * "something went wrong on the wire" that each of them also produces.
 */
internal fun classifyCameraFfmpegStderr(stderrTail: List<String>): CameraFailure {
    val text = stderrTail.joinToString("\n").lowercase()
    return when {
        text.containsAny(
            "not authorized to use", "not permitted to use", "-11852", "operation not permitted",
            "permission denied", "access denied"
        ) -> CameraFailure.PERMISSION_DENIED

        text.contains("supported pixel formats") ||
            (text.contains("selected pixel format") && text.contains("not supported")) ->
            CameraFailure.UNSUPPORTED_PIXEL_FORMAT

        text.contains("supported framerates") ||
            (text.contains("selected framerate") && text.contains("not supported")) ->
            CameraFailure.UNSUPPORTED_FRAMERATE

        text.containsAny(
            "video device not found", "input device not found", "no such file or directory",
            "cannot find a device", "could not find video device"
        ) -> CameraFailure.DEVICE_NOT_FOUND

        text.containsAny(
            "device or resource busy", "could not run graph", "input/output error", "i/o error",
            "resource temporarily unavailable", "already in use"
        ) -> CameraFailure.DEVICE_BUSY

        else -> CameraFailure.UNKNOWN
    }
}

private fun String.containsAny(vararg needles: String): Boolean = needles.any { contains(it) }

/**
 * The pixel formats ffmpeg says the device accepts, in the order it listed them.
 *
 * ffmpeg prints them as an indented block under `Supported pixel formats are:`, one per line, and
 * ends the block by returning to an unindented line. A block that runs to the end of the tail is
 * kept — the tail is a window, so the terminator may simply not have been captured.
 */
internal fun parseSupportedPixelFormats(stderrTail: List<String>): List<String> =
    parseIndentedBlock(stderrTail, "Supported pixel formats")
        .flatMap { it.split(' ', ',', '\t') }
        .map { it.trim() }
        .filter { it.isNotEmpty() && it.all { c -> c.isLetterOrDigit() } }

/**
 * The frame rates ffmpeg says the device accepts.
 *
 * Both forms it prints are read: a discrete list (`30.000000 60.000000`) and a range
 * (`{29.970030-30.000000}`), where the range's own bounds are the only rates worth asking for.
 */
internal fun parseSupportedFramerates(stderrTail: List<String>): List<Double> =
    parseIndentedBlock(stderrTail, "Supported framerates")
        .flatMap { Regex("""\d+(?:\.\d+)?""").findAll(it).map { m -> m.value }.toList() }
        .mapNotNull { it.toDoubleOrNull() }
        .filter { it > 0 }
        .distinct()

/** The indented lines following the first line naming [header], up to the next unindented line. */
private fun parseIndentedBlock(stderrTail: List<String>, header: String): List<String> {
    val start = stderrTail.indexOfFirst { it.contains(header, ignoreCase = true) }
    if (start < 0) return emptyList()

    // ffmpeg sometimes puts the first entry on the header line itself, after the colon.
    val onHeaderLine = stderrTail[start].substringAfter(':', missingDelimiterValue = "").trim()
    val block = mutableListOf<String>()
    if (onHeaderLine.isNotEmpty()) block += onHeaderLine

    for (line in stderrTail.drop(start + 1)) {
        val body = line.substringAfter("] ", missingDelimiterValue = line)
        if (body.isBlank() || !body.first().isWhitespace()) break
        block += body.trim()
    }
    return block
}

/** The best of the [supported] formats for this app, or the first one offered when none is known. */
internal fun preferredPixelFormat(supported: List<String>): String? =
    PREFERRED_PIXEL_FORMATS.firstOrNull { it in supported } ?: supported.firstOrNull()

/**
 * ffmpeg's [stderrTail], trimmed and stripped of anything naming the operator's machine.
 *
 * This is what makes a camera warning answerable, so the redaction is deliberately narrow: it
 * removes the device name and absolute paths — which name the user's hardware and their home
 * directory — and leaves every ffmpeg diagnostic intact. **A redactor that eats the diagnosis is
 * worse than no report at all**, which is what this replaced.
 */
internal fun redactedFfmpegStderr(stderrTail: List<String>, deviceName: String): String =
    stderrTail.takeLast(MAX_REDACTED_STDERR_LINES)
        .joinToString("\n") { redactDeviceName(it, deviceName) }
        .take(MAX_REDACTED_STDERR_CHARS)

/**
 * The ffmpeg [command] with the device it names replaced, so the argv can be read in a report.
 *
 * Everything that explains the attempt survives — the capture backend, `-pixel_format`,
 * `-framerate`, `-video_size`, and the avfoundation index, which is a position in a list and not a
 * description of anyone's hardware. The dshow/v4l2 device name is not.
 */
internal fun redactedFfmpegCommand(command: List<String>): String =
    command.joinToString(" ") { arg ->
        ABSOLUTE_PATH.replace(DSHOW_DEVICE_ARG.replace(arg, "video=<device>"), "<path>")
    }

private fun redactDeviceName(line: String, deviceName: String): String {
    val withoutPaths = ABSOLUTE_PATH.replace(line, "<path>")
    return if (deviceName.length >= MIN_REDACTABLE_NAME_LENGTH) {
        withoutPaths.replace(deviceName, "<device>", ignoreCase = true)
    } else {
        withoutPaths
    }
}

/**
 * Explicit input flags for a capture attempt, filled in when the device has refused the defaults.
 *
 * Empty means "let ffmpeg and the device negotiate", which is what every attempt used to do — and
 * kept doing, identically, five times over, after the device had already said no.
 */
internal data class CaptureOverride(
    val pixelFormat: String? = null,
    val framerate: String? = null,
) {
    companion object {
        val NONE = CaptureOverride()
    }
}

/**
 * The next set of input flags to try after [failure], or `null` when retrying cannot help.
 *
 * ffmpeg names what the device accepts in the very message rejecting the request, so the first
 * source is [stderrTail] itself; [knownFormats] — what `listCameraFormats` enumerated — is the
 * fallback for devices that complain without listing. An override already in [alreadyTried] yields
 * `null` rather than a second identical attempt.
 */
internal fun nextCaptureOverride(
    failure: CameraFailure,
    stderrTail: List<String>,
    knownFormats: List<CameraFormat>,
    alreadyTried: Set<CaptureOverride>,
): CaptureOverride? {
    val candidate = when (failure) {
        CameraFailure.UNSUPPORTED_PIXEL_FORMAT ->
            preferredPixelFormat(parseSupportedPixelFormats(stderrTail))
                ?.let { CaptureOverride(pixelFormat = it) }

        CameraFailure.UNSUPPORTED_FRAMERATE ->
            (parseSupportedFramerates(stderrTail).firstOrNull()?.toInt()
                ?: knownFormats.firstOrNull()?.fps)
                ?.let { CaptureOverride(framerate = it.toString()) }

        else -> null
    }
    return candidate?.takeIf { it !in alreadyTried }
}
