package org.churchpresenter.profiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.percent_suffix
import org.churchpresenter.strings.generated.resources.profile_caption_blank_line
import org.churchpresenter.strings.generated.resources.profile_caption_break_off
import org.churchpresenter.strings.generated.resources.profile_caption_break_phrase
import org.churchpresenter.strings.generated.resources.profile_caption_break_sentence
import org.churchpresenter.strings.generated.resources.profile_caption_clear
import org.churchpresenter.strings.generated.resources.profile_caption_clear_fade
import org.churchpresenter.strings.generated.resources.profile_caption_clear_sub
import org.churchpresenter.strings.generated.resources.profile_caption_dim
import org.churchpresenter.strings.generated.resources.profile_caption_dim_floor
import org.churchpresenter.strings.generated.resources.profile_caption_dim_step
import org.churchpresenter.strings.generated.resources.profile_caption_dim_sub
import org.churchpresenter.strings.generated.resources.profile_caption_line_breaks
import org.churchpresenter.strings.generated.resources.profile_caption_max_chars
import org.churchpresenter.strings.generated.resources.profile_caption_max_chars_sub
import org.churchpresenter.strings.generated.resources.profile_caption_reading_speed
import org.churchpresenter.strings.generated.resources.profile_caption_reading_speed_sub
import org.churchpresenter.strings.generated.resources.profile_caption_reading_speed_unit
import org.churchpresenter.strings.generated.resources.profile_caption_roll_up
import org.churchpresenter.strings.generated.resources.profile_caption_roll_up_sub
import org.churchpresenter.strings.generated.resources.profile_caption_roll_up_time
import org.churchpresenter.strings.generated.resources.profile_caption_style
import org.churchpresenter.strings.generated.resources.profile_caption_style_pop_on
import org.churchpresenter.strings.generated.resources.profile_caption_style_pop_on_sub
import org.churchpresenter.strings.generated.resources.profile_caption_style_roll_up
import org.churchpresenter.strings.generated.resources.profile_caption_style_roll_up_sub
import org.churchpresenter.strings.generated.resources.profile_caption_style_ticker
import org.churchpresenter.strings.generated.resources.profile_caption_bionic_sub
import org.churchpresenter.strings.generated.resources.profile_caption_bionic
import org.churchpresenter.strings.generated.resources.profile_caption_rsvp_phrase_sub
import org.churchpresenter.strings.generated.resources.profile_caption_rsvp_phrase
import org.churchpresenter.strings.generated.resources.profile_caption_rsvp_wpm
import org.churchpresenter.strings.generated.resources.profile_caption_rsvp_max_speed_sub
import org.churchpresenter.strings.generated.resources.profile_caption_rsvp_max_speed
import org.churchpresenter.strings.generated.resources.profile_caption_rsvp_speed
import org.churchpresenter.strings.generated.resources.profile_caption_rsvp_words
import org.churchpresenter.strings.generated.resources.profile_caption_style_rsvp_sub
import org.churchpresenter.strings.generated.resources.profile_caption_style_rsvp
import org.churchpresenter.strings.generated.resources.profile_caption_style_ticker_sub
import org.churchpresenter.strings.generated.resources.profile_caption_ticker_speed
import org.churchpresenter.strings.generated.resources.profile_caption_ticker_speed_unit
import org.churchpresenter.strings.generated.resources.profile_group_reading
import org.churchpresenter.strings.generated.resources.profile_ms
import org.churchpresenter.strings.generated.resources.seconds_suffix
import org.churchpresenter.settings.CAPTION_BREAK_NONE
import org.churchpresenter.settings.CAPTION_BREAK_SEGMENT
import org.churchpresenter.settings.CAPTION_BREAK_SENTENCE
import org.churchpresenter.settings.CAPTION_STYLE_POP_ON
import org.churchpresenter.settings.CAPTION_STYLE_ROLL_UP
import org.churchpresenter.settings.CAPTION_STYLE_RSVP
import org.churchpresenter.settings.CAPTION_STYLE_TICKER
import org.churchpresenter.settings.RSVP_FLASH_PHRASE
import org.churchpresenter.settings.CaptionReading
import org.jetbrains.compose.resources.stringResource

private const val READING = "sttSettings.reading"
private val CLEAR_SECONDS_RANGE = 1..120
private val FADE_MS_RANGE = 0..5000
private const val FADE_MS_STEP = 100
private val READING_CPS_RANGE = 5..40
private val SLIDE_MS_RANGE = 50..2000
private const val SLIDE_MS_STEP = 50
private const val PERCENT_STEP = 5
private val MAX_CHARS_RANGE = 0..120
private val TICKER_SPEED_RANGE = 20..1000
private const val TICKER_SPEED_STEP = 10
private val RSVP_WORDS_RANGE = 1..3
private val RSVP_WPM_RANGE = 60..1000
private const val RSVP_WPM_STEP = 10

/**
 * READING: when captions leave the screen, how fast words may arrive, how the lines move and break,
 * and how the newest words stand out. The everyday switches are Basic; their fine values Advanced.
 */
@Composable
internal fun CaptionReadingGroup(
    reading: CaptionReading,
    /** Whether the profile's show speed follows the speaker -- RSVP's speed is then its ceiling. */
    matchSpeaker: Boolean,
    update: ((CaptionReading) -> CaptionReading) -> Unit,
) {
    val path = { field: String -> listOf("$READING.$field") }
    val ms = stringResource(Res.string.profile_ms)
    val percent = stringResource(Res.string.percent_suffix)
    SettingsGroup(stringResource(Res.string.profile_group_reading), key = "reading", paths = listOf(READING)) {
        StyleRows(reading, update, path)
        if (reading.style == CAPTION_STYLE_RSVP) RsvpRows(reading, matchSpeaker, update, path)
        SettingsSwitchRow(
            Res.string.profile_caption_bionic,
            reading.bionicReading,
            { v -> update { it.copy(bionicReading = v) } },
            sub = stringResource(Res.string.profile_caption_bionic_sub),
            paths = path("bionicReading"),
        )
        TimingRows(reading, update, path)
        if (reading.style == CAPTION_STYLE_ROLL_UP) {
            SettingsSwitchRow(
                Res.string.profile_caption_roll_up,
                reading.rollUp,
                { v -> update { it.copy(rollUp = v) } },
                sub = stringResource(Res.string.profile_caption_roll_up_sub),
                paths = path("rollUp"),
            )
        }
        if (reading.rollUp && reading.style == CAPTION_STYLE_ROLL_UP) {
            SettingsRow(
                Res.string.profile_caption_roll_up_time,
                advanced = true,
                paths = path("rollUpMillis"),
            ) {
                RowStepper(
                    reading.rollUpMillis,
                    { v -> update { it.copy(rollUpMillis = v) } },
                    SLIDE_MS_RANGE,
                    step = SLIDE_MS_STEP,
                    unit = ms,
                    fieldWidth = 76.dp,
                )
            }
        }
        // A flash has no older lines to dim and no lines to break
        if (reading.style != CAPTION_STYLE_RSVP) DimRows(reading, update, path, percent)
        if (reading.style != CAPTION_STYLE_TICKER && reading.style != CAPTION_STYLE_RSVP) {
            LineBreakRows(reading, update, path)
        }
    }
}

/** Dim older lines, and -- Advanced -- by how much each and how far at most. */
@Composable
private fun DimRows(
    reading: CaptionReading,
    update: ((CaptionReading) -> CaptionReading) -> Unit,
    path: (String) -> List<String>,
    percent: String,
) {
    SettingsSwitchRow(
        Res.string.profile_caption_dim,
        reading.dimOlderLines,
        { v -> update { it.copy(dimOlderLines = v) } },
        sub = stringResource(Res.string.profile_caption_dim_sub),
        paths = path("dimOlderLines"),
    )
    if (reading.dimOlderLines) {
        SettingsRow(
            Res.string.profile_caption_dim_step,
            advanced = true,
            paths = path("dimStepPercent"),
        ) {
            RowStepper(
                reading.dimStepPercent,
                { v -> update { it.copy(dimStepPercent = v) } },
                PERCENT_RANGE,
                step = PERCENT_STEP,
                unit = percent,
            )
        }
        SettingsRow(
            Res.string.profile_caption_dim_floor,
            advanced = true,
            paths = path("dimFloorPercent"),
        ) {
            RowStepper(
                reading.dimFloorPercent,
                { v -> update { it.copy(dimFloorPercent = v) } },
                PERCENT_RANGE,
                step = PERCENT_STEP,
                unit = percent,
            )
        }
    }
}

/**
 * RSVP: how many words each flash shows, and its words a minute -- the speed at a fixed show speed,
 * the ceiling when it follows the speaker.
 */
@Composable
private fun RsvpRows(
    reading: CaptionReading,
    matchSpeaker: Boolean,
    update: ((CaptionReading) -> CaptionReading) -> Unit,
    path: (String) -> List<String>,
) {
    SettingsRow(
        Res.string.profile_caption_rsvp_words,
        sub = if (reading.rsvpWordsPerFlash == RSVP_FLASH_PHRASE) {
            stringResource(Res.string.profile_caption_rsvp_phrase_sub)
        } else {
            null
        },
        paths = path("rsvpWordsPerFlash"),
    ) {
        RowSegmented(
            options = RSVP_WORDS_RANGE.map { RowOption(it, it.toString()) } +
                RowOption(RSVP_FLASH_PHRASE, stringResource(Res.string.profile_caption_rsvp_phrase)),
            selected = reading.rsvpWordsPerFlash,
            onSelect = { v -> update { it.copy(rsvpWordsPerFlash = v) } },
        )
    }
    SettingsRow(
        stringResource(
            if (matchSpeaker) Res.string.profile_caption_rsvp_max_speed else Res.string.profile_caption_rsvp_speed,
        ),
        sub = if (matchSpeaker) stringResource(Res.string.profile_caption_rsvp_max_speed_sub) else null,
        paths = path("rsvpWpm"),
    ) {
        RowStepper(
            reading.rsvpWpm,
            { v -> update { it.copy(rsvpWpm = v) } },
            RSVP_WPM_RANGE,
            step = RSVP_WPM_STEP,
            unit = stringResource(Res.string.profile_caption_rsvp_wpm),
            fieldWidth = 76.dp,
        )
    }
}

/** How captions are put on screen -- rolling, popping on, or crawling -- and how fast a ticker crawls. */
@Composable
private fun StyleRows(
    reading: CaptionReading,
    update: ((CaptionReading) -> CaptionReading) -> Unit,
    path: (String) -> List<String>,
) {
    SettingsRow(
        Res.string.profile_caption_style,
        sub = when (reading.style) {
            CAPTION_STYLE_POP_ON -> stringResource(Res.string.profile_caption_style_pop_on_sub)
            CAPTION_STYLE_TICKER -> stringResource(Res.string.profile_caption_style_ticker_sub)
            CAPTION_STYLE_RSVP -> stringResource(Res.string.profile_caption_style_rsvp_sub)
            else -> stringResource(Res.string.profile_caption_style_roll_up_sub)
        },
        paths = path("style"),
    ) {
        RowSegmented(
            options = listOf(
                RowOption(CAPTION_STYLE_ROLL_UP, stringResource(Res.string.profile_caption_style_roll_up)),
                RowOption(CAPTION_STYLE_POP_ON, stringResource(Res.string.profile_caption_style_pop_on)),
                RowOption(CAPTION_STYLE_TICKER, stringResource(Res.string.profile_caption_style_ticker)),
                RowOption(CAPTION_STYLE_RSVP, stringResource(Res.string.profile_caption_style_rsvp)),
            ),
            selected = reading.style,
            onSelect = { v -> update { it.copy(style = v) } },
        )
    }
    if (reading.style == CAPTION_STYLE_TICKER) {
        SettingsRow(Res.string.profile_caption_ticker_speed, paths = path("tickerSpeed")) {
            RowStepper(
                reading.tickerSpeed,
                { v -> update { it.copy(tickerSpeed = v) } },
                TICKER_SPEED_RANGE,
                step = TICKER_SPEED_STEP,
                unit = stringResource(Res.string.profile_caption_ticker_speed_unit),
                fieldWidth = 76.dp,
            )
        }
    }
}

/** When the captions leave the screen after a silence, and how fast words may arrive. */
@Composable
private fun TimingRows(
    reading: CaptionReading,
    update: ((CaptionReading) -> CaptionReading) -> Unit,
    path: (String) -> List<String>,
) {
    val ms = stringResource(Res.string.profile_ms)
    SettingsSwitchRow(
        Res.string.profile_caption_clear,
        reading.clearAfterSilence,
        { v -> update { it.copy(clearAfterSilence = v) } },
        sub = stringResource(Res.string.profile_caption_clear_sub),
        paths = path("clearAfterSilence") + path("clearAfterSeconds"),
        extra = {
            if (reading.clearAfterSilence) {
                RowStepper(
                    reading.clearAfterSeconds,
                    { v -> update { it.copy(clearAfterSeconds = v) } },
                    CLEAR_SECONDS_RANGE,
                    unit = stringResource(Res.string.seconds_suffix),
                )
            }
        },
    )
    if (reading.clearAfterSilence) {
        SettingsRow(
            Res.string.profile_caption_clear_fade,
            advanced = true,
            paths = path("clearFadeMillis"),
        ) {
            RowStepper(
                reading.clearFadeMillis,
                { v -> update { it.copy(clearFadeMillis = v) } },
                FADE_MS_RANGE,
                step = FADE_MS_STEP,
                unit = ms,
                fieldWidth = 76.dp,
            )
        }
    }
    SettingsSwitchRow(
        Res.string.profile_caption_reading_speed,
        reading.readingSpeedLimit,
        { v -> update { it.copy(readingSpeedLimit = v) } },
        sub = stringResource(Res.string.profile_caption_reading_speed_sub),
        paths = path("readingSpeedLimit") + path("readingSpeedCps"),
        extra = {
            if (reading.readingSpeedLimit) {
                RowStepper(
                    reading.readingSpeedCps,
                    { v -> update { it.copy(readingSpeedCps = v) } },
                    READING_CPS_RANGE,
                    unit = stringResource(Res.string.profile_caption_reading_speed_unit),
                    fieldWidth = 76.dp,
                )
            }
        },
    )
}

/** Where lines break: a new line per phrase or sentence, a blank line between, and a width in characters. */
@Composable
private fun LineBreakRows(
    reading: CaptionReading,
    update: ((CaptionReading) -> CaptionReading) -> Unit,
    path: (String) -> List<String>,
) {
    SettingsRow(Res.string.profile_caption_line_breaks, paths = path("lineBreaks")) {
        RowSegmented(
            options = listOf(
                RowOption(CAPTION_BREAK_NONE, stringResource(Res.string.profile_caption_break_off)),
                RowOption(CAPTION_BREAK_SEGMENT, stringResource(Res.string.profile_caption_break_phrase)),
                RowOption(CAPTION_BREAK_SENTENCE, stringResource(Res.string.profile_caption_break_sentence)),
            ),
            selected = reading.lineBreaks,
            onSelect = { v -> update { it.copy(lineBreaks = v) } },
        )
    }
    if (reading.lineBreaks != CAPTION_BREAK_NONE) {
        SettingsSwitchRow(
            Res.string.profile_caption_blank_line,
            reading.blankLineBetween,
            { v -> update { it.copy(blankLineBetween = v) } },
            advanced = true,
            paths = path("blankLineBetween"),
        )
    }
    SettingsRow(
        Res.string.profile_caption_max_chars,
        sub = stringResource(Res.string.profile_caption_max_chars_sub),
        advanced = true,
        paths = path("maxCharsPerLine"),
    ) {
        RowStepper(reading.maxCharsPerLine, { v -> update { it.copy(maxCharsPerLine = v) } }, MAX_CHARS_RANGE)
    }
}
