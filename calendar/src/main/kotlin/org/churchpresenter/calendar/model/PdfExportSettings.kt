package org.churchpresenter.calendar.model

import kotlinx.serialization.Serializable
import org.churchpresenter.core.models.schedule.ScheduleItem

/** Who an exported run of show is for. */
@Serializable
enum class PdfAudience { PUBLIC, STAFF }

/** The letterhead every exported run of show carries, and what the public copy leaves out. */
@Serializable
data class PdfExportSettings(
    val logoPath: String = "",
    val churchName: String = "",
    val churchAddress: String = "",
    /** Section headings, by name, whose rows only the staff copy shows. */
    val staffOnlySections: List<String> = listOf("Pre-Service", "Post-Service"),
    /** What the Export button does when clicked rather than picked from. */
    val lastAudience: PdfAudience = PdfAudience.STAFF,
) {
    fun isStaffOnly(section: String): Boolean = staffOnlySections.any { it.equals(section, ignoreCase = true) }

    fun withStaffOnly(section: String, staffOnly: Boolean): PdfExportSettings = copy(
        staffOnlySections = staffOnlySections.filterNot { it.equals(section, ignoreCase = true) } +
            listOfNotNull(section.takeIf { staffOnly }),
    )

    fun withSectionRenamed(from: String, to: String): PdfExportSettings = copy(
        staffOnlySections = staffOnlySections.map { if (it.equals(from, ignoreCase = true)) to else it },
    )
}

/**
 * The rows [audience] is shown. Staff get the whole run of show; the public copy drops cues and
 * every row under a staff-only section, heading included.
 */
fun PdfExportSettings.rowsFor(items: List<ScheduleItem>, audience: PdfAudience): List<ScheduleItem> {
    if (audience == PdfAudience.STAFF) return items
    var hidden = false
    return items.filter { item ->
        if (item is ScheduleItem.LabelItem) hidden = isStaffOnly(item.text)
        !hidden && item !is ScheduleItem.CueItem
    }
}
