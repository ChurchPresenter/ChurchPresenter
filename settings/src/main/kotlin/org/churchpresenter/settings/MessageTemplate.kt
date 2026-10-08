package org.churchpresenter.settings

import kotlinx.serialization.Serializable

/**
 * A message saved to go live again: its [text], with `{tokens}` the operator fills in each time --
 * "Parent of child #{number}, please come to the nursery" -- and how long it stays up, or null to
 * stay until cleared. See `docs/SHOW_CONTROL.md`, Messages.
 */
@Serializable
data class MessageTemplate(
    val id: String,
    val name: String,
    val text: String,
    val durationSeconds: Int? = null,
)

private val TOKEN = Regex("""\{([^{}]+)\}""")

/** The tokens [text] asks for, each once, in the order they first appear. */
fun messageTokens(text: String): List<String> =
    TOKEN.findAll(text).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.distinct().toList()

/** [text] with each `{token}` replaced by its value in [values]; a token with no value is left as written. */
fun fillMessage(text: String, values: Map<String, String>): String =
    TOKEN.replace(text) { match -> values[match.groupValues[1].trim()] ?: match.value }
