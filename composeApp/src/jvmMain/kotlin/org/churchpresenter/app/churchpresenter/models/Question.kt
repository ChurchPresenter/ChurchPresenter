package org.churchpresenter.app.churchpresenter.models

import kotlinx.serialization.Serializable

enum class QuestionStatus { PENDING, APPROVED, DENIED, DONE }

data class Question(
    val id: String,
    val text: String,
    val submitterName: String = "",
    val timestamp: Long,
    val status: QuestionStatus = QuestionStatus.PENDING,
    val voteCount: Int = 0,
    val upvotes: Int = 0,
    val downvotes: Int = 0,
)

@Serializable
data class QuestionDto(
    val id: String,
    val text: String,
    val submitterName: String = "",
    val timestamp: Long,
    val status: String,
    val voteCount: Int = 0,
    val upvotes: Int = 0,
    val downvotes: Int = 0,
)

@Serializable
data class SubmitQuestionRequest(
    val text: String,
    val name: String = ""
)

@Serializable
data class VoteRequest(
    val questionId: String,
    val direction: String = "up" // "up" or "down"
)

fun Question.toDto() = QuestionDto(
    id = id,
    text = text,
    submitterName = submitterName,
    timestamp = timestamp,
    status = status.name,
    voteCount = voteCount,
    upvotes = upvotes,
    downvotes = downvotes,
)
