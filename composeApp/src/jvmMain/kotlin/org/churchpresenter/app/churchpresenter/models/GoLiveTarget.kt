package org.churchpresenter.app.churchpresenter.models

/**
 * Which output(s) a Go Live action targets. [All] projects to every connected output (the historical
 * behavior); [Display] targets a single output slot by its index; [Multi] targets a specific set of
 * output slots by index (all index values share the same space as ProjectionSettings.screenAssignments
 * / PresenterManager screen locks).
 */
sealed class GoLiveTarget {
    object All : GoLiveTarget()
    data class Display(val index: Int) : GoLiveTarget()
    data class Multi(val indices: Set<Int>) : GoLiveTarget()
}
