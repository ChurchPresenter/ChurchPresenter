package org.churchpresenter.server

import kotlinx.serialization.Serializable
import org.churchpresenter.settings.Macro

/** One macro as `GET /api/macros` lists it. */
@Serializable
data class MacroDto(val id: String, val name: String, val actions: Int)

/** The macros a remote client may run, with how many actions each holds. */
internal fun macrosListing(macros: List<Macro>): List<MacroDto> =
    macros.map { MacroDto(it.id, it.name, it.actions.size) }
