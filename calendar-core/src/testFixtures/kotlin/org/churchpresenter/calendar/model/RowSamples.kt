package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.ScheduleItem

/**
 * Sample rows for the suites that run a pass over every kind of row: the model's (`RowShapesTest`)
 * and the window's (`RowSubtitlesTest`). One list for both, so a kind added to `ScheduleItem` is
 * added once -- and `RowShapesTest` fails until it is.
 */
object RowSamples {
    private const val PICTURE_COUNT = 12
    private const val SLIDE_COUNT = 20

    fun song(id: String = "a") = ScheduleItem.SongItem(id, 1, "Song", "Hymns", "Hymns::1")
    fun heading() = ScheduleItem.LabelItem("h", "Worship", "#FFFFFF", "#5B9DF5")
    fun pictures() = ScheduleItem.PictureItem("p", "/pics", "Sunrise", PICTURE_COUNT)
    fun deck() = ScheduleItem.PresentationItem("d", "/deck.pptx", "Sermon", SLIDE_COUNT, "pptx")
    fun clip() = ScheduleItem.MediaItem("m", "/clip.mp4", "Welcome", "local")
    fun scene() = ScheduleItem.SceneItem("s", "scene-1", "Bible with Background")

    /** One of every kind of row, which is what each pass is run over. */
    val EVERY_KIND: List<ScheduleItem> = listOf(
        song(),
        ScheduleItem.BibleVerseItem("v", "John", 3, 16, "For God so loved"),
        heading(),
        pictures(),
        deck(),
        clip(),
        ScheduleItem.LowerThirdItem("l", "preset-1", "Pastor", false, 0),
        ScheduleItem.AnnouncementItem("a", "Welcome"),
        ScheduleItem.WebsiteItem("w", "https://example.org"),
        scene(),
        ScheduleItem.DictionaryItem("t", "G26", "agape", "agapē", "love"),
        ScheduleItem.CueItem("c", CueAction.BLANK),
        ScheduleItem.MinistryItem("o", "A poem", "Anna"),
    )
}
