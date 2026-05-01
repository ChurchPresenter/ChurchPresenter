package org.churchpresenter.app.churchpresenter.models

import kotlinx.serialization.Serializable

enum class QuestionStatus { PENDING, APPROVED, DENIED, DONE }

data class Question(
    val id: String,
    val text: String,
    val submitterName: String = "",
    val timestamp: Long,
    val status: QuestionStatus = QuestionStatus.PENDING
)

@Serializable
data class QuestionDto(
    val id: String,
    val text: String,
    val submitterName: String = "",
    val timestamp: Long,
    val status: String
)

@Serializable
data class SubmitQuestionRequest(
    val text: String,
    val name: String = ""
)

fun Question.toDto() = QuestionDto(
    id = id,
    text = text,
    submitterName = submitterName,
    timestamp = timestamp,
    status = status.name
)
