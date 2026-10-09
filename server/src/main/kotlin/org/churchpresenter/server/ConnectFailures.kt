package org.churchpresenter.server

import io.ktor.client.network.sockets.ConnectTimeoutException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.io.IOException
import javax.net.ssl.SSLException

private const val FAILURE_LOG_INTERVAL = 10
private const val DECADE = 10
private const val REPORT_INTERVAL_WIDEN_AT_100 = 100
private const val REPORT_INTERVAL_WIDEN_AT_1000 = 1000

/**
 * The [classifyConnectFailure] buckets that mean "the primary is not up yet", not "something broke".
 *
 * `timeout` joined them after one stored address produced **767 reports from three churches in
 * seventeen days**, every one of them a first failure. Whether an absent primary refuses the
 * connection or never answers it is a property of the network between the two machines — a host
 * that is switched off times out where one that is merely not running the app refuses — and not
 * something the operator did differently. Filing the first of those as a defect and throttling the
 * other was a distinction with nothing behind it.
 *
 * It is also why the first-failure report was worth so little here: `consecutiveFailures` lives in
 * `connectLoop`, so every restart of the link begins a fresh count, and a follower pointed at an
 * address that never answers reports its "first" failure again on each one.
 */
private val BENIGN_CONNECT_FAILURES = setOf("refused", "dns", "ping_timeout", "timeout")

/**
 * How a failed connect to the primary is classified, redacted and rate-limited before it is
 * reported. None of it depends on a connection's state.
 */
internal object ConnectFailures {
    /**
     * The peer's address inside a connect failure's message, with the port kept.
     *
     * Group 1 is the scheme, group 2 the port and the rest of the URL, so the host between them
     * is what [redactedConnectFailure] replaces. Deliberately narrow: it matches an address in a
     * `ws://`/`wss://` URL and leaves every other word of ktor's message alone, because the rest
     * of it is the diagnosis.
     */
    private val PEER_URL = Regex("""(wss?://)[^/\s\]:]+(:\d+)?""")

    /**
     * Whether a connect failure this far into a run of them is worth a warning.
     *
     * A follower is configured once and then starts with the room, routinely before the primary
     * does, so "refused" and "dns" on the first attempt are the ordinary startup order rather than
     * a fault — and reporting them there made the follower's own boot sequence the single noisiest
     * signal in the project. Those two therefore wait for the run to persist through
     * [FAILURE_LOG_INTERVAL] attempts, by which point the backoff has carried it well past any
     * plausible "primary is still coming up" window and the link genuinely is not working.
     *
     * Once it has persisted, a benign run is reported **once**: the first warning says the link is
     * not coming up, and every later one from the same run says only that it still is not —
     * CHURCH-PRESENTER-DESKTOP-68 kept one follower pointed at an address that never answered
     * filing a warning at 10, 20 … 100, 200 … 1000 consecutive timeouts, about nineteen per
     * thousand, each telling Sentry nothing the first had not.
     *
     * The kinds that suggest a regression rather than an ordering — a certificate, or something
     * unrecognised — report on the first failure and then on the widening cadence of
     * [reportIntervalFor], because those are worth seeing even if they never recur and worth
     * seeing again while they do.
     */
    internal fun shouldReportConnectFailure(kind: String, consecutiveFailures: Int): Boolean {
        if (kind in BENIGN_CONNECT_FAILURES) return consecutiveFailures == FAILURE_LOG_INTERVAL
        return consecutiveFailures == 1 || consecutiveFailures % reportIntervalFor(consecutiveFailures) == 0
    }

    /**
     * The reporting cadence for [shouldReportConnectFailure]: every 10th failure through the first
     * 99, every 100th through the first 999, every 1000th beyond that — applied to how often a
     * still-failing streak is worth telling Sentry about, separate from [MAX_RECONNECT_DELAY_MS]'s
     * own backoff on how often a reconnect is actually retried.
     */
    internal fun reportIntervalFor(consecutiveFailures: Int): Int = when {
        consecutiveFailures < REPORT_INTERVAL_WIDEN_AT_100 -> FAILURE_LOG_INTERVAL
        consecutiveFailures < REPORT_INTERVAL_WIDEN_AT_1000 -> FAILURE_LOG_INTERVAL * DECADE
        else -> FAILURE_LOG_INTERVAL * DECADE * DECADE
    }

    /**
     * A connect failure's message with the peer's address taken out of it.
     *
     * The message used to be interpolated into the report's *title*, which did two things. It put
     * the address of a church's own machine — `ws://192.168.1.100:8765/ws` — into an issue title,
     * where nothing scrubs it: `CrashReporter` redacts home directories and the OS username, not
     * private addresses. And because Sentry groups on the title, one failure arrived as **fourteen
     * separate issues**, one per address and port, none of which looked related to the others.
     *
     * `PicturesViewModel.reportThumbnailFailures` fixed the same shape for file names and says why:
     * a constant title so the class of failure is one issue, and what distinguishes an occurrence in
     * the detail.
     *
     * The port is kept. It is not anyone's address, and a follower pointed at the wrong port is a
     * real misconfiguration worth being able to see.
     */
    internal fun redactedConnectFailure(message: String?): String =
        message?.replace(PEER_URL, "$1<peer>$2") ?: "none"

    /**
     * Buckets a connect failure so Sentry can be filtered/grouped by cause. "refused", "dns",
     * "timeout" and "ping_timeout" are the primary not being there — not started, switched off, or
     * gone from the network — and are [BENIGN_CONNECT_FAILURES]; "tls" and "other" are more likely
     * a real regression (a protocol or certificate bug) and stay on the reporting cadence.
     */
    internal fun classifyConnectFailure(e: Exception): String = when {
        // ktor's own pinger raises this when the primary misses the keepalive window, which is what
        // the heartbeat is for: the link drops, the backoff reconnects, and the operator sees the
        // status change. On a hall's wifi that is ordinary churn — five churches filed it — so it
        // belongs with "refused" and "dns" rather than being reported the first time it happens.
        e is IOException && e.message?.contains("Ping timeout", ignoreCase = true) == true -> "ping_timeout"
        else -> classifyConnectFailureByType(e)
    }

    private fun classifyConnectFailureByType(e: Exception): String = when (e) {
        // Ktor's ConnectTimeoutException extends java.net.ConnectException, so it must be matched
        // first or every connect timeout is filed as "refused" — the one bucket that says the
        // operator simply has not started the primary yet.
        is ConnectTimeoutException -> "timeout"
        is ConnectException -> "refused"
        is UnknownHostException -> "dns"
        is SocketTimeoutException -> "timeout"
        is SSLException -> "tls"
        else -> "other"
    }
}
