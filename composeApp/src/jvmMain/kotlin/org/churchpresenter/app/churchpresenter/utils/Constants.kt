package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Rectangle

/** Polls for screen devices every 2 seconds so hot-plugged displays trigger recomposition. */
@Composable
fun rememberScreenDevices(): Array<GraphicsDevice> {
    var devices by remember {
        mutableStateOf(GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices)
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            val current = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
            if (current.size != devices.size) {
                devices = current
            }
        }
    }
    return devices
}

/** Returns the presenter screen bounds (first non-primary screen if available, else primary). */
fun presenterScreenBounds(): Rectangle {
    val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val screens = ge.screenDevices
    val primary = ge.defaultScreenDevice
    val nonPrimary = screens.firstOrNull { it != primary }
    return (nonPrimary ?: primary).defaultConfiguration.bounds
}

/** Find a screen index by stored bounds. Returns null if no match. */
fun findScreenIndexByBounds(screens: Array<GraphicsDevice>, x: Int, y: Int, w: Int, h: Int): Int? {
    if (x == Int.MIN_VALUE) return null  // bounds not set
    return screens.indexOfFirst { device ->
        val b = device.defaultConfiguration.bounds
        b.x == x && b.y == y && b.width == w && b.height == h
    }.takeIf { it >= 0 }
}

/** Returns the aspect ratio of the presenter screen. */
fun presenterAspectRatio(): Float {
    val bounds = presenterScreenBounds()
    return bounds.width.toFloat() / bounds.height.toFloat()
}

/** Formats an aspect ratio as a common name (e.g. "16:9") or decimal fallback (e.g. "1.78:1"). */
fun formatAspectRatio(width: Int, height: Int): String {
    val gcd = gcd(width, height)
    val w = width / gcd
    val h = height / gcd
    // Accept simplified ratios where both sides are reasonable (≤64)
    return if (w <= 64 && h <= 64) "$w:$h"
    else String.format("%.2f:1", width.toFloat() / height.toFloat())
}

private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

/** Any line wrapped in [] or {} is a section header. */
fun isHeaderLine(line: String): Boolean {
    val t = line.trim()
    return (t.startsWith("[") && t.endsWith("]")) ||
           (t.startsWith("{") && t.endsWith("}"))
}

/** {} = chorus, [] = verse/other */
fun isChorusHeader(line: String): Boolean {
    val t = line.trim()
    return t.startsWith("{") && t.endsWith("}")
}

object Constants {
    const val NONE = "None"
    const val FIRST_PAGE = "First Page"
    const val EVERY_PAGE = "Every Page"
    const val TOP = "Top"

    const val ABOVE_VERSE = "AboveVerse"
    const val BELOW_VERSE = "BelowVerse"
    const val MIDDLE = "Middle"
    const val BOTTOM = "Bottom"
    const val LEFT = "Left"
    const val CENTER = "Center"
    const val RIGHT = "Right"
    const val TOP_LEFT = "Top Left"
    const val TOP_CENTER = "Top Center"
    const val TOP_RIGHT = "Top Right"
    const val CENTER_LEFT = "Center Left"
    const val CENTER_RIGHT = "Center Right"
    const val BOTTOM_LEFT = "Bottom Left"
    const val BOTTOM_CENTER = "Bottom Center"
    const val BOTTOM_RIGHT = "Bottom Right"
    const val LIGHT = "LIGHT"

    const val DARK = "DARK"

    const val SYSTEM = "SYSTEM"

    const val OTHER = "Other"

    const val CONTAINS = "Contains"

    const val STARTS_WITH = "Starts with"

    const val EXACT_MATCH = "Exact match"

    // Background Types
    const val BACKGROUND_DEFAULT = "Default"
    const val BACKGROUND_COLOR = "Color"
    const val BACKGROUND_IMAGE = "Image"
    const val BACKGROUND_VIDEO = "Video"
    const val BACKGROUND_TRANSPARENT = "Transparent"
    const val BACKGROUND_GRADIENT = "Gradient"

    // Position Options
    const val POSITION_ABOVE = "Above"
    const val POSITION_BELOW = "Below"

    // Language Options
    const val LANGUAGE_INTERFACE = "Interface"
    const val LANGUAGE_DATABASE = "Database"

    // File Extensions
    const val EXTENSION_SPS = "sps"
    const val EXTENSION_SPB = "spb"
    const val EXTENSION_SONG = "song"

    // Song Display Modes
    const val SONG_DISPLAY_MODE_VERSE = "verse"
    const val SONG_DISPLAY_MODE_LINE = "line"

    // Song Language Display
    const val SONG_LANG_BOTH = "both"
    const val SONG_LANG_PRIMARY = "primary"
    const val SONG_LANG_SECONDARY = "secondary"

    // Bilingual Layout
    const val BILINGUAL_SIDE_BY_SIDE = "side_by_side"
    const val BILINGUAL_TOP_BOTTOM = "top_bottom"

    // Section Types
    const val SECTION_TYPE_SONG = "song"
    const val SECTION_TYPE_VERSE = "verse"
    const val SECTION_TYPE_CHORUS = "chorus"

    // Fallback Resource
    const val FALLBACK_SONG_RESOURCE = "pv3300.sps"

    // Sort Columns
    const val SORT_NUMBER = "number"
    const val SORT_TITLE = "title"
    const val SORT_SONGBOOK = "songbook"
    const val SORT_TUNE = "tune"

    const val CURRENT_BOOK = "Current Book"

    const val ENTIRE_BIBLE = "Entire Bible"

    // Animation Types
    const val ANIMATION_CROSSFADE = "CROSSFADE"
    const val ANIMATION_FADE = "FADE"
    const val ANIMATION_SLIDE_LEFT = "SLIDE_LEFT"
    const val ANIMATION_SLIDE_RIGHT = "SLIDE_RIGHT"
    const val ANIMATION_SLIDE_UP = "SLIDE_UP"
    const val ANIMATION_SLIDE_TO_CENTER = "SLIDE_TO_CENTER"
    const val ANIMATION_NONE = "NONE"

    // Announcement directional animation types (full edge-to-edge)
    const val ANIMATION_SLIDE_FROM_LEFT   = "SLIDE_FROM_LEFT"
    const val ANIMATION_SLIDE_FROM_RIGHT  = "SLIDE_FROM_RIGHT"
    const val ANIMATION_SLIDE_FROM_TOP    = "SLIDE_FROM_TOP"
    const val ANIMATION_SLIDE_FROM_BOTTOM = "SLIDE_FROM_BOTTOM"
    const val ANIMATION_SLIDE_ALONG_TOP_LTR = "SLIDE_ALONG_TOP_LTR"
    const val ANIMATION_SLIDE_ALONG_TOP_RTL = "SLIDE_ALONG_TOP_RTL"
    const val ANIMATION_SLIDE_ALONG_BOTTOM_LTR = "SLIDE_ALONG_BOTTOM_LTR"
    const val ANIMATION_SLIDE_ALONG_BOTTOM_RTL = "SLIDE_ALONG_BOTTOM_RTL"

    // Media Types
    const val MEDIA_TYPE_LOCAL = "local"
    const val MEDIA_TYPE_AUDIO = "audio"
    const val MEDIA_TYPE_URL = "url"

    // Audio file extensions (VLC supports all common formats)
    val AUDIO_EXTENSIONS = setOf("mp3", "wav", "flac", "aac", "ogg", "wma", "m4a", "aiff", "opus")

    // Display Mode Types (for screen assignments)
    const val DISPLAY_MODE_FULLSCREEN = "fullscreen"
    const val DISPLAY_MODE_LOWER_THIRD = "lower_third"

    // Output Role (fill+key for video mixers)
    const val OUTPUT_ROLE_NORMAL = "normal"
    const val OUTPUT_ROLE_FILL = "fill"
    const val OUTPUT_ROLE_KEY = "key"

    // Key output target sentinel: no key output configured
    const val KEY_TARGET_NONE = -2

    // Seek amount in ms
    const val MEDIA_SEEK_MS = 10_000L

    // Companion Server (Ktor)
    const val SERVER_DEFAULT_PORT = 8765
    const val SERVER_APP_NAME = "ChurchPresenter"
    const val SERVER_VERSION = "1.0"

    // REST endpoints
    const val ENDPOINT_INFO              = "/api/info"
    const val ENDPOINT_SONGS             = "/api/songs"
    const val ENDPOINT_SONG_DETAIL       = "/api/songs/{number}"
    const val ENDPOINT_BIBLE             = "/api/bible"
    const val ENDPOINT_BIBLE_SECONDARY   = "/api/bible/secondary"
    const val ENDPOINT_SCHEDULE          = "/api/schedule"
    const val ENDPOINT_SCHEDULE_ADD       = "/api/schedule/add"
    const val ENDPOINT_SCHEDULE_ADD_BATCH = "/api/schedule/add-batch"
    const val ENDPOINT_PROJECT            = "/api/project"
    const val ENDPOINT_PRESENTATIONS     = "/api/presentations"
    const val ENDPOINT_PICTURES          = "/api/pictures"
    const val ENDPOINT_WS                = "/ws"

    // Lottie Generator endpoints
    const val ENDPOINT_LOTTIE_PRESETS      = "/api/presets"
    const val ENDPOINT_LOTTIE_COLOR_THEMES = "/api/color-themes"
    const val ENDPOINT_LOTTIE_LOGOS        = "/api/logos"
    const val ENDPOINT_LOTTIE_GENERATOR    = "/lottie-generator.html"

    // WebSocket event types (server → client)
    const val WS_EVENT_SONGS_UPDATED              = "songs_updated"
    const val WS_EVENT_BIBLE_UPDATED              = "bible_updated"
    const val WS_EVENT_SECONDARY_BIBLE_UPDATED    = "secondary_bible_updated"
    const val WS_EVENT_SCHEDULE_UPDATED           = "schedule_updated"
    const val WS_EVENT_PRESENTATION_UPDATED       = "presentation_updated"
    const val WS_EVENT_PICTURES_UPDATED           = "pictures_updated"

    // WebSocket command types (client → server)
    const val WS_CMD_SELECT_SONG            = "select_song"
    const val WS_CMD_SELECT_PICTURE         = "select_picture"
    const val WS_CMD_ADD_TO_SCHEDULE        = "add_to_schedule"
    const val WS_CMD_ADD_BATCH_TO_SCHEDULE  = "add_batch_to_schedule"
    const val WS_CMD_PROJECT                = "project"

    // Item type strings shared by REST and WS payloads
    const val ITEM_TYPE_SONG         = "song"
    const val ITEM_TYPE_BIBLE        = "bible"
    const val ITEM_TYPE_PRESENTATION = "presentation"
    const val ITEM_TYPE_PICTURE      = "picture"
    const val ITEM_TYPE_MEDIA        = "media"

    // API key authentication
    const val HEADER_API_KEY        = "X-Api-Key"
    const val HEADER_DEVICE_ID      = "X-Device-Id"
    const val QUERY_PARAM_API_KEY   = "apiKey"
    const val QUERY_PARAM_SONGBOOK  = "songbook"
    const val QUERY_PARAM_BOOK      = "book"
    const val QUERY_PARAM_CHAPTER   = "chapter"

    // SSL / TLS (self-signed cert for companion server)
    const val SSL_KEYSTORE_TYPE     = "JKS"
    const val SSL_KEY_ALGORITHM     = "RSA"
    const val SSL_KEY_ALIAS         = "churchpresenter"
    const val SSL_KEYSTORE_PASSWORD = "churchpresenter_ssl"
}