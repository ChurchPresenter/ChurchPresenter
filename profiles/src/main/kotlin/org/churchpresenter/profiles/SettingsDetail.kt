package org.churchpresenter.profiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * How much of a page is shown: the settings most operators touch, or every one of them.
 *
 * Read by [SettingsGroup] and [SettingsRow] through [LocalSettingsDetail], so a page lists every
 * row it has once and marks the advanced ones, rather than drawing two versions of itself.
 */
internal enum class SettingsDetail { BASIC, ADVANCED }

internal val LocalSettingsDetail = compositionLocalOf { SettingsDetail.ADVANCED }

/**
 * What the section search is looking for, or blank. A row whose label, sub-line and search terms do
 * not contain it is left out, and a group left with no rows is left out with it.
 */
internal val LocalSettingsQuery = compositionLocalOf { "" }

/** True when a row labelled with [text] passes the search in force. */
@Composable
internal fun matchesSettingsQuery(vararg text: String?): Boolean {
    val query = LocalSettingsQuery.current.trim()
    return query.isEmpty() || text.any { it != null && it.contains(query, ignoreCase = true) }
}
