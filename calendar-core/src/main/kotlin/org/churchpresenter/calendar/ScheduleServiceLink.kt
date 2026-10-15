package org.churchpresenter.calendar

/**
 * The service the Schedule holds, by [serviceId] and [serviceName], and whether rows have been
 * added, removed or moved there since -- what the Schedule tab offers to save back.
 */
data class ScheduleServiceLink(val serviceId: String, val serviceName: String, val hasChanges: Boolean)
