package org.churchpresenter.settings

import kotlinx.serialization.Serializable

/**
 * A named set of layers an operator clears together, e.g. *Clear text* = Slide + Messages
 * (`docs/SHOW_CONTROL.md`, Clear groups). [layers] are layer names as the live show spells them --
 * `SLIDE`, `MESSAGES` -- so a name this build does not know is skipped, not lost.
 */
@Serializable
data class ClearGroup(
    val id: String,
    val name: String,
    val layers: List<String> = emptyList(),
)
