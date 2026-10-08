package org.churchpresenter.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.showcontrol.Action

/**
 * A named list of show-control actions an operator runs in one go (`docs/SHOW_CONTROL.md`,
 * Macros), from a button, a key, the HTTP/WebSocket API or another macro. [name] is what
 * `Action.RunMacro` and the API call it by; [actions] run in order.
 */
@Serializable
data class Macro(
    val id: String,
    val name: String,
    val actions: List<Action> = emptyList(),
)

/** The macro [wanted] names, by id or by name in any case, or null for one there is none of. */
fun List<Macro>.macroNamed(wanted: String): Macro? =
    firstOrNull { it.id == wanted } ?: firstOrNull { it.name.equals(wanted.trim(), ignoreCase = true) }
