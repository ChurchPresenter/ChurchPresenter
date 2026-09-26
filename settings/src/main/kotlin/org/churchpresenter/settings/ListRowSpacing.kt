package org.churchpresenter.settings

import kotlinx.serialization.Serializable

/** How much room the app's list rows leave around their text -- the Margin in Customize Theme. */
@Serializable
enum class ListRowSpacing {
    NORMAL,
    THIN,
    THINNER,
}
