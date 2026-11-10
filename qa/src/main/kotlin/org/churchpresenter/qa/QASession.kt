package org.churchpresenter.qa

import kotlinx.serialization.Serializable
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.core.models.qa.QuestionDto
import org.churchpresenter.core.models.qa.QuestionStatus
import org.churchpresenter.core.models.qa.toDto
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * One finished Q&A session, kept so it can be looked back over, exported or resumed on its own.
 *
 * [name] is what the operator called it when it started; [startedAt] and [endedAt] are wall-clock
 * millis.
 */
data class QASession(
    val id: String,
    val name: String,
    val startedAt: Long,
    val endedAt: Long,
    val questions: List<Question>,
)

/** What [QASession] is on disk, inside `qa_state.json`. */
@Serializable
internal data class QASessionDto(
    val id: String,
    val name: String,
    val startedAt: Long = 0L,
    val endedAt: Long = 0L,
    val questions: List<QuestionDto> = emptyList(),
)

internal fun QASession.toDto() = QASessionDto(
    id = id,
    name = name,
    startedAt = startedAt,
    endedAt = endedAt,
    questions = questions.map { it.toDto() },
)

internal fun QASessionDto.toSession() = QASession(
    id = id,
    name = name,
    startedAt = startedAt,
    endedAt = endedAt,
    questions = questions.map { it.toQuestion() },
)

internal fun QuestionDto.toQuestion() = Question(
    id = id,
    text = text,
    submitterName = submitterName,
    submitterDeviceId = submitterDeviceId,
    timestamp = timestamp,
    status = try { QuestionStatus.valueOf(status) } catch (_: Exception) { QuestionStatus.PENDING },
    voteCount = voteCount,
    upvotes = upvotes,
    downvotes = downvotes,
)

/**
 * The name a session gets when the operator gives none: the moment it started, in the machine's
 * own date and time format ("Nov 10, 2026, 9:30 AM").
 */
fun defaultSessionName(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(zone)
        .format(Instant.ofEpochMilli(timestamp))

/**
 * The name the questions kept before sessions had names are filed under. It is stored data, not a
 * label drawn by the UI, so it is not a string resource.
 */
internal const val LEGACY_HISTORY_SESSION_NAME = "Earlier questions"
