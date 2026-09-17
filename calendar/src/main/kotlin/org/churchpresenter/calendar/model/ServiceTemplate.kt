package org.churchpresenter.calendar.model

/**
 * What a new service starts out holding.
 *
 * In `model/` rather than beside the sheet that offers it: `CalendarState` acts on it, and a state
 * layer reaching up into `ui` for a type is the wrong way round.
 */
sealed interface ServiceTemplate {
    val id: String

    /** An empty run of show. */
    data object Blank : ServiceTemplate {
        override val id: String = "blank"
    }

    /** A copy of an earlier service's run of show, durations included. */
    data class CopyOf(val service: PlannedService) : ServiceTemplate {
        override val id: String get() = service.id
    }

    /** A template the user saved with **Template** in the run-of-show header. */
    data class Saved(val template: SavedTemplate) : ServiceTemplate {
        override val id: String get() = template.id
    }
}
