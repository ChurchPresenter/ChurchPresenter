package org.churchpresenter.app.churchpresenter.models

/** Identifies one live Companion Satellite registration: a configured connection shown at one placement. */
data class CompanionSurfaceSlot(val connectionId: String, val placement: CompanionSurfacePlacement)
