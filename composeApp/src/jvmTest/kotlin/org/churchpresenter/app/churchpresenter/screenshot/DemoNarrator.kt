package org.churchpresenter.app.churchpresenter.screenshot

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.security.MessageDigest
import javax.sound.sampled.AudioSystem

/**
 * The tour's narrator: speaks each line to a WAV, remembers the frame it starts on, and mixes them
 * under the frames into the finished MP4.
 *
 * The voice is ElevenLabs when `ELEVENLABS_API_KEY` is set, OpenAI when `OPENAI_API_KEY` is, and the
 * Mac's own `say` otherwise. Every line is cached by voice and text in `$CP_DEMO_OUT/voice-cache`, so
 * re-recording a tour pays only for the lines that changed. `CP_DEMO_VOICE` picks the voice.
 * `CP_DEMO_SILENT=1` paces every step as if its line were spoken, but records no voice.
 */
internal class DemoNarrator(root: File) {
    private val cache = File(root, "voice-cache").apply { mkdirs() }
    private val cues = mutableListOf<Pair<Int, File>>()
    private val silent = System.getenv("CP_DEMO_SILENT") == "1"
    private val http by lazy { HttpClient.newHttpClient() }
    private val elevenKey = System.getenv("ELEVENLABS_API_KEY")?.takeIf { it.isNotBlank() }
    private val openAiKey = System.getenv("OPENAI_API_KEY")?.takeIf { it.isNotBlank() }
    private val voice = System.getenv("CP_DEMO_VOICE")?.takeIf { it.isNotBlank() } ?: when {
        elevenKey != null -> ELEVEN_DEFAULT_VOICE
        openAiKey != null -> OPENAI_DEFAULT_VOICE
        else -> MAC_DEFAULT_VOICE
    }

    /** Speaks [text] from [frame]; returns how many seconds it takes. */
    fun say(text: String, frame: Int): Double {
        val spoken = spoken(text)
        if (spoken.isBlank()) return 0.0
        if (silent) return spoken.split(' ').size / WORDS_PER_SECOND
        val wav = File(cache, "${backend()}-${hash("$voice|$spoken")}.wav")
        if (!wav.isFile) synthesize(spoken, wav)
        cues += frame to wav
        return AudioSystem.getAudioFileFormat(wav).let { it.frameLength / it.format.frameRate.toDouble() }
    }

    /** The lines spoken since the last call, by the frame each starts on; forgotten here. */
    fun drainCues(): List<Pair<Int, File>> = cues.toList().also { cues.clear() }

    /**
     * Encodes a tour from its segment folders in `segments.txt` order (see [DemoTour.segment]): their
     * frames linked end to end into one numbering, each voice cue moved by where its segment starts.
     */
    fun encodeSegments(tour: File, fps: Int, video: File) {
        val order = File(tour, "segments.txt").readLines().filter { it.isNotBlank() }
        val all = File(tour, "all").apply {
            deleteRecursively()
            mkdirs()
        }
        var offset = 0
        val placed = mutableListOf<Pair<Int, File>>()
        for (name in order) {
            val folder = File(tour, "seg-$name")
            check(folder.isDirectory) { "segment $name was never recorded: run the whole tour once first" }
            val frames = folder.listFiles { f -> f.name.endsWith(".png") }.orEmpty().sortedBy { it.name }
            frames.forEachIndexed { i, f ->
                Files.createSymbolicLink(File(all, "f%05d.png".format(offset + i)).toPath(), f.absoluteFile.toPath())
            }
            File(folder, "cues.txt").takeIf { it.isFile }?.readLines()?.filter { it.isNotBlank() }?.forEach {
                placed += offset + it.substringBefore('\t').toInt() to File(it.substringAfter('\t'))
            }
            offset += frames.size
        }
        encode(all, fps, video, placed)
    }

    /** Encodes [frames] at [fps] with every spoken line in place into [video]. */
    fun encode(frames: File, fps: Int, video: File, cues: List<Pair<Int, File>> = this.cues) {
        val ffmpeg = ffmpeg()
        video.parentFile?.mkdirs()
        val command = mutableListOf(ffmpeg, "-y", "-loglevel", "error")
        command += listOf("-framerate", "$fps", "-i", "${frames.path}/f%05d.png")
        cues.forEach { (_, wav) -> command += listOf("-i", wav.path) }
        if (cues.isNotEmpty()) {
            val delays = cues.mapIndexed { i, (frame, _) ->
                val ms = frame * 1000L / fps
                "[${i + 1}:a]aresample=44100,adelay=$ms|$ms[a$i]"
            }
            val mix = cues.indices.joinToString("") { "[a$it]" } +
                "amix=inputs=${cues.size}:normalize=0:duration=longest[voice]"
            command += listOf("-filter_complex", (delays + mix).joinToString(";"), "-map", "0:v", "-map", "[voice]")
            command += listOf("-c:a", "aac", "-b:a", "160k")
        }
        command += listOf("-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "18", "-movflags", "+faststart", video.path)
        run(*command.toTypedArray())
    }

    private fun ffmpeg() = listOf("/opt/homebrew/bin/ffmpeg", "/usr/local/bin/ffmpeg", "/usr/bin/ffmpeg")
        .firstOrNull { File(it).canExecute() } ?: error("ffmpeg not found — brew install ffmpeg")

    private fun run(vararg command: String) {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val log = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "${command.first()} failed: $log" }
    }

    private fun backend() = when {
        elevenKey != null -> "eleven"
        openAiKey != null -> "openai"
        else -> "mac"
    }

    private fun synthesize(text: String, wav: File) {
        when {
            elevenKey != null -> elevenLabs(text, elevenKey, wav)
            openAiKey != null -> openAi(text, openAiKey, wav)
            else -> mac(text, wav)
        }
    }

    private fun elevenLabs(text: String, key: String, wav: File) {
        val body = JsonObject(
            mapOf(
                "text" to JsonPrimitive(text),
                "model_id" to JsonPrimitive("eleven_multilingual_v2"),
            ),
        ).toString()
        val request = HttpRequest.newBuilder(
            URI("https://api.elevenlabs.io/v1/text-to-speech/$voice?output_format=mp3_44100_128"),
        )
            .header("xi-api-key", key)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        // MP3 is the format every ElevenLabs plan allows; ffmpeg turns it into a WAV that knows its length.
        val mp3 = File(wav.path.removeSuffix(".wav") + ".mp3").apply { writeBytes(post(request)) }
        run(ffmpeg(), "-y", "-loglevel", "error", "-i", mp3.path, "-ar", "44100", "-ac", "1", wav.path)
        mp3.delete()
    }

    private fun openAi(text: String, key: String, wav: File) {
        val body = JsonObject(
            mapOf(
                "model" to JsonPrimitive("gpt-4o-mini-tts"),
                "voice" to JsonPrimitive(voice),
                "input" to JsonPrimitive(text),
                "instructions" to JsonPrimitive(OPENAI_STYLE),
                "response_format" to JsonPrimitive("wav"),
            ),
        ).toString()
        val request = HttpRequest.newBuilder(URI("https://api.openai.com/v1/audio/speech"))
            .header("Authorization", "Bearer $key")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        wav.writeBytes(post(request))
    }

    private fun mac(text: String, wav: File) {
        val process = ProcessBuilder(
            "say", "-v", voice, "--file-format=WAVE", "--data-format=LEI16@44100", "-o", wav.path, text,
        ).redirectErrorStream(true).start()
        val log = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0 && wav.isFile) { "say failed: $log" }
    }

    private fun post(request: HttpRequest): ByteArray {
        val response = http.send(request, HttpResponse.BodyHandlers.ofByteArray())
        check(response.statusCode() == 200) {
            "speech request failed (${response.statusCode()}): ${String(response.body()).take(300)}"
        }
        return response.body()
    }

    private fun hash(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }.take(16)

    private companion object {
        // ElevenLabs' premade "Rachel": calm, clear, American English.
        const val ELEVEN_DEFAULT_VOICE = "21m00Tcm4TlvDq8ikWAM"
        const val OPENAI_DEFAULT_VOICE = "nova"
        const val MAC_DEFAULT_VOICE = "Samantha"

        /** A relaxed narrator, 150 words a minute — the pace a silent preview is timed to. */
        const val WORDS_PER_SECOND = 2.5
        const val OPENAI_STYLE = "A warm, friendly narrator walking a church volunteer through the app. " +
            "Unhurried and clear, smiling, never salesy."

        /** The callout text, made for speaking: no quote marks, symbols said as words. */
        fun spoken(text: String) = text
            // A list number ("1. ", "2) ") is for reading, never for saying.
            .replace(Regex("^\\s*\\d+[.)]\\s+"), "")
            .replace("\"", "")
            .replace("↓", "the down arrow")
            .replace("↑", "the up arrow")
            .replace(" · ", ", ")
            .replace(" — ", ", ")
            .replace("…", "")
            .replace("&", "and")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
