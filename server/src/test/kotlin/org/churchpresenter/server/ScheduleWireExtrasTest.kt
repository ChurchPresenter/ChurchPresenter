package org.churchpresenter.server

import kotlinx.serialization.json.Json
import org.churchpresenter.core.models.schedule.ScheduleItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The two optional fields a follower's schedule adds carry: where a row goes (`index`) and which one
 * picture a single-picture row is (`imagePath`). Both are absent from what an older build sends and
 * ignored by an older build that receives them.
 */
class ScheduleWireExtrasTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ── index ───────────────────────────────────────────────────────────────────

    @Test
    fun `an add says where it goes`() {
        assertEquals(2, requestedIndex("""{"item":{"songNumber":42},"index":2}""", json))
    }

    @Test
    fun `an add that does not say, or says nonsense, goes on the end`() {
        assertNull(requestedIndex("""{"item":{"songNumber":42}}""", json))
        assertNull(requestedIndex("""{"item":{"songNumber":42},"index":-1}""", json))
        assertNull(requestedIndex("""{"item":{"songNumber":42},"index":"top"}""", json))
        assertNull(requestedIndex("""{"item":{"songNumber":42},"index":null}""", json))
        assertNull(requestedIndex("not json", json))
    }

    @Test
    fun `a request without an index reads as it always did`() {
        val label = ScheduleItem.LabelItem("l1", "Offering", "#FFFFFF", "#000000")
        val sent = Json.encodeToString(AddToScheduleRequest.serializer(), AddToScheduleRequest(label))
        assertFalse("index" in sent, "a client's own Json leaves the default out")
        val read = json.decodeFromString(AddToScheduleRequest.serializer(), sent)
        assertEquals(label, read.item)
        assertNull(read.index)
    }

    // ── imagePath ───────────────────────────────────────────────────────────────

    @Test
    fun `a picture row with no picture named is the whole folder`() {
        val row = pictureItem("p1", "/pics/Welcome", "Welcome", 4, imagePath = null)
        assertEquals("", row.imagePath)
        assertEquals("Welcome (4 images)", row.displayText)
        assertEquals(row, pictureItem("p1", "/pics/Welcome", "Welcome", 4, imagePath = ""))
    }

    @Test
    fun `a picture row naming one picture is that picture`() {
        val row = pictureItem("p1", "/pics/Welcome", "Welcome", 4, imagePath = "/pics/Welcome/sunrise.jpg")
        assertEquals("/pics/Welcome/sunrise.jpg", row.imagePath)
        assertEquals("sunrise.jpg", row.displayText)
    }

    @Test
    fun `a follower's single picture reaches the primary as one picture`() {
        val dto = RemoteItemDto(folderPath = "/pics/Welcome", folderName = "Welcome", imageCount = 4,
            imagePath = "/pics/Welcome/sunrise.jpg")
        val row = dto.toScheduleItem() as ScheduleItem.PictureItem
        assertEquals("/pics/Welcome/sunrise.jpg", row.imagePath)
    }

    @Test
    fun `the schedule feed names the picture only for a single-picture row`() {
        val single = ScheduleItem.PictureItem("p1", "/pics/Welcome", "Welcome", 4,
            displayText = "sunrise.jpg", imagePath = "/pics/Welcome/sunrise.jpg")
        val folder = ScheduleItem.PictureItem("p2", "/pics/Welcome", "Welcome", 4)

        assertEquals("/pics/Welcome/sunrise.jpg", single.toDto().imagePath)
        assertNull(folder.toDto().imagePath)

        val folderWire = json.encodeToString(ScheduleItemDto.serializer(), folder.toDto())
        assertFalse("imagePath" in folderWire, "a folder row's wire form is what it always was")
        assertTrue("imagePath" in json.encodeToString(ScheduleItemDto.serializer(), single.toDto()))
    }
}
