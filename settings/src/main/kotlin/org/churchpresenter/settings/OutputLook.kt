package org.churchpresenter.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * What an output draws of the live stack, layer by layer (`docs/LAYER_MODEL.md`): each layer the
 * output can leave out, and within a layer the content it can skip -- a stage screen showing songs
 * but not pictures, a stream key without the background.
 *
 * Scripture and songs are not here: whether an output draws them is its [OutputProfile.bibleMode]
 * and [OutputProfile.songMode], which also say in which languages. Everything defaults to drawn.
 */
@Serializable
data class OutputLook(
    val background: BackgroundLook = BackgroundLook(),
    val media: MediaLook = MediaLook(),
    val slide: SlideLook = SlideLook(),
    /** Live captions. */
    val captions: Boolean = true,
    /** Lower thirds. */
    val graphics: Boolean = true,
    val announcements: Boolean = true,
    /** Operator messages, e.g. a nursery call. */
    val messages: Boolean = true,
) {
    companion object {
        /** Nothing drawn at all -- see [BLANK_OUTPUT_PROFILE]. */
        val NOTHING = OutputLook(
            background = BackgroundLook(fullscreen = false, lowerThird = false, bible = false, songs = false),
            media = MediaLook(pictures = false, video = false, subtitles = false),
            slide = SlideLook(web = false, canvas = false, qa = false, dictionary = false),
            captions = false,
            graphics = false,
            announcements = false,
            messages = false,
        )
    }
}

/**
 * The Background layer: the full-screen background, a lower third's band background, and the
 * backgrounds Bible and songs put up with them.
 */
@Serializable
data class BackgroundLook(
    val fullscreen: Boolean = true,
    val lowerThird: Boolean = true,
    val bible: Boolean = true,
    val songs: Boolean = true,
)

/** The Media layer: pictures (and presentation slides, drawn as pictures), video, and video's subtitles. */
@Serializable
data class MediaLook(
    val pictures: Boolean = true,
    val video: Boolean = true,
    val subtitles: Boolean = true,
)

/** The Slide layer's content other than scripture and songs. */
@Serializable
data class SlideLook(
    val web: Boolean = true,
    val canvas: Boolean = true,
    val qa: Boolean = true,
    val dictionary: Boolean = true,
)

/** [this] with its look changed by [edit]. */
fun OutputProfile.withLook(edit: OutputLook.() -> OutputLook): OutputProfile = copy(look = look.edit())

/** [this] with its Background layer changed by [edit]. */
fun OutputLook.withBackground(edit: BackgroundLook.() -> BackgroundLook): OutputLook =
    copy(background = background.edit())

/** [this] with its Media layer changed by [edit]. */
fun OutputLook.withMedia(edit: MediaLook.() -> MediaLook): OutputLook = copy(media = media.edit())

/** [this] with its Slide layer changed by [edit]. */
fun OutputLook.withSlide(edit: SlideLook.() -> SlideLook): OutputLook = copy(slide = slide.edit())

/**
 * Where each of the flat `show*` switches a profile carried before looks now lives in
 * [OutputProfile.look], by path -- what the settings migration moves, and what a linked profile's
 * overrides are renamed to.
 */
val LEGACY_LOOK_PATHS: Map<String, String> = mapOf(
    "showFullscreenBackground" to "look.background.fullscreen",
    "showLowerThirdBackground" to "look.background.lowerThird",
    "showBibleBackground" to "look.background.bible",
    "showSongsBackground" to "look.background.songs",
    "showPictures" to "look.media.pictures",
    "showMedia" to "look.media.video",
    "showSubtitles" to "look.media.subtitles",
    "showWebsite" to "look.slide.web",
    "showCanvas" to "look.slide.canvas",
    "showQA" to "look.slide.qa",
    "showDictionary" to "look.slide.dictionary",
    "showSTT" to "look.captions",
    "showStreaming" to "look.graphics",
    "showAnnouncements" to "look.announcements",
)

/**
 * [profile], a profile as stored before looks, with each of its flat `show*` switches moved to its
 * place in `look` and a linked profile's overrides of them renamed to match -- see
 * [LEGACY_LOOK_PATHS]. A switch the profile never stored is left to its default, which is drawn.
 */
internal fun moveShowSwitchesIntoLook(profile: JsonObject): JsonObject {
    val moved = LEGACY_LOOK_PATHS.keys.filter { it in profile }
    val overrides = profile["overrides"] as? JsonArray
    if (moved.isEmpty() && overrides?.none { it.pathString() in LEGACY_LOOK_PATHS } != false) return profile
    var look = profile["look"] as? JsonObject ?: JsonObject(emptyMap())
    for (key in moved) {
        look = look.withPath(LEGACY_LOOK_PATHS.getValue(key).removePrefix("look.").split('.'), profile.getValue(key))
    }
    val out = profile.filterKeys { it !in LEGACY_LOOK_PATHS }.toMutableMap()
    out["look"] = look
    if (overrides != null) {
        out["overrides"] = JsonArray(
            overrides.map { path -> LEGACY_LOOK_PATHS[path.pathString()]?.let(::JsonPrimitive) ?: path },
        )
    }
    return JsonObject(out)
}

private fun JsonElement.pathString(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/** [this] with [value] at [path], each object on the way made if it is not there. */
private fun JsonObject.withPath(path: List<String>, value: JsonElement): JsonObject {
    val key = path.first()
    val inner = if (path.size == 1) {
        value
    } else {
        (this[key] as? JsonObject ?: JsonObject(emptyMap())).withPath(path.drop(1), value)
    }
    return JsonObject(this + (key to inner))
}
