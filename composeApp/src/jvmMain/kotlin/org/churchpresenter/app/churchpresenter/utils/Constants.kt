package org.churchpresenter.app.churchpresenter.utils

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

    const val VERSE_RUS = "Куплет"

    const val VERSE_1_RUS = "Куплет 1"
    const val VERSE = "Verse"
    const val CHORUS = "Chorus"

    const val CHORUS_RUS = "Припев"
    const val OTHER = "Other"

    const val CONTAINS = "Contains"

    const val STARTS_WITH = "Starts with"

    const val EXACT_MATCH = "Exact match"

    // Background Types
    const val BACKGROUND_DEFAULT = "Default"
    const val BACKGROUND_COLOR = "Color"
    const val BACKGROUND_IMAGE = "Image"

    // Position Options
    const val POSITION_ABOVE = "Above"
    const val POSITION_BELOW = "Below"

    // Language Options
    const val LANGUAGE_INTERFACE = "Interface"
    const val LANGUAGE_DATABASE = "Database"

    // File Extensions
    const val EXTENSION_SPS = "sps"
    const val EXTENSION_SPB = "spb"

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

    // Display Mode Types (for screen assignments)
    const val DISPLAY_MODE_FULLSCREEN = "fullscreen"
    const val DISPLAY_MODE_LOWER_THIRD = "lower_third"

    // Seek amount in ms
    const val MEDIA_SEEK_MS = 10_000L

    // Companion Server (Ktor)
    const val SERVER_DEFAULT_PORT = 8765
    const val SERVER_APP_NAME = "ChurchPresenter"
    const val SERVER_VERSION = "1.0"

    // REST endpoints
    const val ENDPOINT_INFO      = "/api/info"
    const val ENDPOINT_SONGS     = "/api/songs"
    const val ENDPOINT_SCHEDULE  = "/api/schedule"
    const val ENDPOINT_WS        = "/ws"

    // WebSocket event types (server → client)
    const val WS_EVENT_SONGS_UPDATED    = "songs_updated"
    const val WS_EVENT_SCHEDULE_UPDATED = "schedule_updated"

    // WebSocket command types (client → server)
    const val WS_CMD_SELECT_SONG = "select_song"

    // API key authentication
    const val HEADER_API_KEY       = "X-Api-Key"
    const val QUERY_PARAM_API_KEY  = "apiKey"
    const val QUERY_PARAM_SONGBOOK = "songbook"
}