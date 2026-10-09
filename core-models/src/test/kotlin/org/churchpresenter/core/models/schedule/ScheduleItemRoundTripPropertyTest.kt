package org.churchpresenter.core.models.schedule

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.az
import io.kotest.property.arbitrary.cyrillic
import io.kotest.property.arbitrary.merge
import io.kotest.property.arbitrary.printableAscii
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Any schedule row, saved and read back through the polymorphic serializer a `.cps` file and the
 * wire both use, comes back equal -- over generated rows of seven kinds with text in two scripts,
 * quotes, backslashes and empty strings, not only the fixed rows [ScheduleItemSerializationTest]
 * pins. The seed is pinned, so every run checks the same rows.
 */
class ScheduleItemRoundTripPropertyTest {

    private val json = Json { encodeDefaults = true }

    private val text = Arb.string(0..24, Codepoint.printableAscii().merge(Codepoint.cyrillic()).merge(Codepoint.az()))
    private val count = Arb.int(0..500)

    private val item: Arb<ScheduleItem> = Arb.choice(
        Arb.bind(text, count, text, text, text) { id, number, title, book, songId ->
            ScheduleItem.SongItem(id = id, songNumber = number, title = title, songbook = book, songId = songId)
        },
        Arb.bind(text, text, count, count, text) { id, book, chapter, verse, body ->
            ScheduleItem.BibleVerseItem(
                id = id, bookName = book, chapter = chapter, verseNumber = verse, verseText = body,
            )
        },
        Arb.bind(text, text, text, text) { id, label, fg, bg ->
            ScheduleItem.LabelItem(id = id, text = label, textColor = fg, backgroundColor = bg)
        },
        Arb.bind(text, text, text, count) { id, path, name, images ->
            ScheduleItem.PictureItem(id = id, folderPath = path, folderName = name, imageCount = images)
        },
        Arb.bind(text, text, text, count, text) { id, path, name, slides, type ->
            ScheduleItem.PresentationItem(
                id = id, filePath = path, fileName = name, slideCount = slides, fileType = type,
            )
        },
        Arb.bind(text, text, text, text) { id, url, title, type ->
            ScheduleItem.MediaItem(id = id, mediaUrl = url, mediaTitle = title, mediaType = type)
        },
        Arb.bind(text, text, text, Arb.boolean(), Arb.long(0L..60_000L)) { id, preset, label, pause, pauseMs ->
            ScheduleItem.LowerThirdItem(
                id = id, presetId = preset, presetLabel = label, pauseAtFrame = pause, pauseDurationMs = pauseMs,
            )
        },
    )

    @Test
    fun `every row comes back from its saved form unchanged`() = runBlocking<Unit> {
        checkAll(PropTestConfig(seed = 20_261_008L, iterations = 500), item) { original ->
            val saved = json.encodeToString(ScheduleItem.serializer(), original)
            assertEquals(original, json.decodeFromString(ScheduleItem.serializer(), saved), saved)
        }
    }
}
