package org.churchpresenter.lottiegen.viewmodel

import kotlinx.coroutines.Job
import java.time.Instant

/**
 * What [StyleThumbnails] last did, for when a picture that should be in the Style menu is not.
 *
 * Read-only from outside: [StyleThumbnails] records into it as it builds, and [describe] says,
 * in one block of text, whether a config was ever asked for, which style it was asked with, the
 * state of the build job, which styles drew, which came back without a picture and why, and when
 * the last build finished. A host or a test that wants to read it creates one and hands it to
 * `App`; otherwise each [StyleThumbnails] keeps its own and nothing reads it.
 *
 * Written from the build coroutine and read from whatever thread asks, so every member is
 * synchronized. It records; it never changes what is drawn.
 */
class ThumbnailDiagnostics(private val clock: () -> Long = System::currentTimeMillis) {

    /** Why a style has, or lacks, a picture after a build. */
    enum class Outcome(val text: String) {
        DRAWN("drawn"),
        DRAWN_ON_RETRY("drawn on the second try (IllegalStateException first)"),
        DRAWN_AFTER_BLANK("drawn on the second try (blank still first)"),
        RENDER_NULL("render returned null"),
        BLANK("blank still (nothing to crop)"),
        ILLEGAL_STATE_TWICE("IllegalStateException twice"),
        ILLEGAL_STATE("IllegalStateException generating the Lottie"),
        ILLEGAL_ARGUMENT("IllegalArgumentException"),
    }

    private var requests = 0
    private var repeats = 0
    private var askedStyle: String? = null
    private var job: Job? = null
    private var builds = 0
    private var superseded = 0
    private val outcomes = LinkedHashMap<String, String>()
    private var published: List<String> = emptyList()
    private var finishedAt: Long? = null

    /**
     * A config was asked for with [style] selected. [job] is the build it started, or null when it
     * matched the key already drawn or being drawn and so started nothing.
     */
    @Synchronized
    internal fun requested(style: String, job: Job?) {
        requests++
        askedStyle = style
        if (job == null) repeats++ else this.job = job
    }

    /** A build began drawing, after its debounce: forget what the last one recorded per style. */
    @Synchronized
    internal fun buildStarted() {
        builds++
        outcomes.clear()
    }

    /** What happened to style [id] in this build; [detail] is an exception's message. */
    @Synchronized
    internal fun record(id: String, outcome: Outcome, detail: String? = null) {
        outcomes[id] = if (detail == null) outcome.text else "${outcome.text}: $detail"
    }

    /** As [record], unless something more specific was already recorded for [id] in this build. */
    @Synchronized
    internal fun recordIfAbsent(id: String, outcome: Outcome, detail: String? = null) {
        if (id !in outcomes) record(id, outcome, detail)
    }

    /** The styles the menu now has pictures for. */
    @Synchronized
    internal fun published(ids: Collection<String>) {
        published = ids.toList()
    }

    /** A build stopped because a newer config replaced it. */
    @Synchronized
    internal fun buildSuperseded() {
        superseded++
    }

    /** A build went through every style and published what it drew. */
    @Synchronized
    internal fun buildFinished() {
        finishedAt = clock()
    }

    /** Everything above, as text to print beside a thread dump. */
    @Synchronized
    fun describe(): String = buildString {
        appendLine("StyleThumbnails diagnostics")
        appendLine("  key present: ${requests > 0}, requests: $requests ($repeats for the key already held)")
        appendLine("  style asked for: ${askedStyle ?: "none"}")
        appendLine("  job: ${jobState(job)}")
        appendLine("  builds started: $builds, superseded: $superseded")
        val finished = finishedAt
        val ago = finished?.let { " (${clock() - it} ms ago)" } ?: ""
        appendLine("  last build finished: ${finished?.let { Instant.ofEpochMilli(it).toString() } ?: "never"}$ago")
        appendLine("  pictures published: ${published.size} ${published.joinToString(prefix = "[", postfix = "]")}")
        val asked = askedStyle
        if (asked != null) appendLine("  asked-for style $asked: ${outcomes[asked] ?: "no outcome this build"}")
        val drawn = outcomes.count { it.value.startsWith(Outcome.DRAWN.text) }
        appendLine("  drawn this build: $drawn of ${outcomes.size}")
        // Every style but the plainly drawn ones, a retry included: that is what a slow machine looks like.
        outcomes.filterValues { it != Outcome.DRAWN.text }.forEach { (id, why) -> appendLine("  style $id: $why") }
    }

    private fun jobState(job: Job?): String = when {
        job == null -> "never launched"
        job.isActive -> "active"
        job.isCancelled -> "cancelled"
        else -> "completed"
    }
}
