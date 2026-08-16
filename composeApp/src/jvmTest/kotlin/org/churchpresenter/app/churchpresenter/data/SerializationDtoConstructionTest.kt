package org.churchpresenter.app.churchpresenter.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every DTO below is `@Serializable` and private-turned-`internal` to the client that owns it, so
 * kotlinx.serialization generates it a *second*, masked constructor purely for deserializing JSON.
 * Decoding a real response — which is all the rest of the suite ever does — only ever calls that
 * masked constructor, never the plain one a caller would use to build one by hand. Nothing in
 * either client actually builds one by hand today, but the plain constructor and its accessors are
 * still real, callable code, so it is worth pinning that a value put in comes back out unchanged.
 */
class SerializationDtoConstructionTest {

    @Test
    fun `pexels dtos round-trip their fields`() {
        val src = StockMediaClient.PexelsPhotoSrc(original = "https://img/orig.jpg", large2x = "https://img/large.jpg")
        assertEquals("https://img/orig.jpg", src.original)
        assertEquals("https://img/large.jpg", src.large2x)

        val photo = StockMediaClient.PexelsPhoto(id = 12345L, src = src)
        assertEquals(12345L, photo.id)
        assertEquals(src, photo.src)

        val photoResponse = StockMediaClient.PexelsPhotoResponse(photos = listOf(photo), nextPage = "p2")
        assertEquals(listOf(photo), photoResponse.photos)
        assertEquals("p2", photoResponse.nextPage)

        val file = StockMediaClient.PexelsVideoFile(link = "https://v/hd.mp4", quality = "hd", fileType = "video/mp4", width = 1920)
        assertEquals("https://v/hd.mp4", file.link)
        assertEquals("hd", file.quality)
        assertEquals("video/mp4", file.fileType)
        assertEquals(1920, file.width)

        val video = StockMediaClient.PexelsVideo(id = 77L, image = "https://img/thumb.jpg", videoFiles = listOf(file))
        assertEquals(77L, video.id)
        assertEquals("https://img/thumb.jpg", video.image)
        assertEquals(listOf(file), video.videoFiles)

        val videoResponse = StockMediaClient.PexelsVideoResponse(videos = listOf(video), nextPage = null)
        assertEquals(listOf(video), videoResponse.videos)
        assertEquals(null, videoResponse.nextPage)
    }

    @Test
    fun `pixabay dtos round-trip their fields`() {
        val photo = StockMediaClient.PixabayPhoto(id = 999L, previewURL = "https://img/prev.jpg", largeImageURL = "https://img/large.jpg")
        assertEquals(999L, photo.id)
        assertEquals("https://img/prev.jpg", photo.previewURL)
        assertEquals("https://img/large.jpg", photo.largeImageURL)

        val photoResponse = StockMediaClient.PixabayPhotoResponse(hits = listOf(photo), totalHits = 1)
        assertEquals(listOf(photo), photoResponse.hits)
        assertEquals(1, photoResponse.totalHits)

        val file = StockMediaClient.PixabayVideoFile(url = "https://v/medium.mp4", thumbnail = "https://img/medium.jpg")
        assertEquals("https://v/medium.mp4", file.url)
        assertEquals("https://img/medium.jpg", file.thumbnail)

        val files = StockMediaClient.PixabayVideoFiles(large = file, medium = file, small = null, tiny = null)
        assertEquals(file, files.large)
        assertEquals(file, files.medium)
        assertEquals(null, files.small)
        assertEquals(null, files.tiny)

        val video = StockMediaClient.PixabayVideo(id = 55L, videos = files, pictureId = "abc123")
        assertEquals(55L, video.id)
        assertEquals(files, video.videos)
        assertEquals("abc123", video.pictureId)

        val videoResponse = StockMediaClient.PixabayVideoResponse(hits = listOf(video), totalHits = 1)
        assertEquals(listOf(video), videoResponse.hits)
        assertEquals(1, videoResponse.totalHits)
    }

    @Test
    fun `planning center token and person dtos round-trip their fields`() {
        val token = PlanningCenterClient.TokenResponse(accessToken = "tok", refreshToken = "ref", expiresIn = 7200L)
        assertEquals("tok", token.accessToken)
        assertEquals("ref", token.refreshToken)
        assertEquals(7200L, token.expiresIn)

        val attrs = PlanningCenterClient.PersonAttributes(name = "Pat Ringer", firstName = "Pat", lastName = "Ringer")
        assertEquals("Pat Ringer", attrs.name)
        assertEquals("Pat", attrs.firstName)
        assertEquals("Ringer", attrs.lastName)

        val data = PlanningCenterClient.PersonData(id = "1", attributes = attrs)
        assertEquals("1", data.id)
        assertEquals(attrs, data.attributes)

        val response = PlanningCenterClient.PersonResponse(data = data)
        assertEquals(data, response.data)
    }

    @Test
    fun `dtos with an omitted field fall back to their default`() {
        assertEquals(PlanningCenterClient.PersonData(), PlanningCenterClient.PersonResponse().data)
        assertEquals("", PlanningCenterClient.PersonData().id)
        assertEquals(null, PlanningCenterClient.PersonAttributes().name)
        assertEquals("", PlanningCenterClient.TokenResponse().accessToken)

        assertEquals(null, StockMediaClient.PixabayVideoFile(url = "u").thumbnail)
        assertEquals(null, StockMediaClient.PixabayVideoFiles().large)
        assertEquals(null, StockMediaClient.PixabayVideo(id = 1L, videos = StockMediaClient.PixabayVideoFiles()).pictureId)
        assertEquals(null, StockMediaClient.PexelsVideoFile(link = "l").quality)
        assertEquals(emptyList(), StockMediaClient.PexelsVideo(id = 1L, image = "i").videoFiles)
        assertEquals(emptyList(), StockMediaClient.PexelsPhotoResponse().photos)
        assertEquals(emptyList(), StockMediaClient.PexelsVideoResponse().videos)
        assertEquals(0, StockMediaClient.PixabayPhotoResponse().totalHits)
        assertEquals(0, StockMediaClient.PixabayVideoResponse().totalHits)
    }
}
