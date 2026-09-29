package org.churchpresenter.planningcenter

/** The `item_type` values Planning Center gives a plan item, as [PlanningCenterClient] reads them. */
object PcoItemType {
    const val SONG = "song"
    const val HEADER = "header"
    const val MEDIA = "media"
    /** A generic item: anything that is not a song, a header or media. Also the fallback when PCO omits it. */
    const val ITEM = "item"
}
