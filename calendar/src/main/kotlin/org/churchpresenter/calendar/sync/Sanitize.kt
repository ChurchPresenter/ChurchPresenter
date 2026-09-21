package org.churchpresenter.calendar.sync

import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

/** Text hygiene for anything that arrives from the relay. */
object Sanitize {

    private val STRIPPED = Regex("[\\p{Cc}\\p{Cf}]")
    private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")
    private val UUID_LIKE = Regex("^[A-Za-z0-9_.:-]{1,64}$")

    /** NFC-normalized, control and format characters removed, whitespace collapsed, capped at [max]. */
    fun cleanText(text: String, max: Int): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFC)
        return STRIPPED.replace(normalized, "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(max)
    }

    /** `#RRGGBB` or the fallback. */
    fun hexColor(text: String, fallback: String): String = if (HEX_COLOR.matches(text)) text else fallback

    /** Whether an id is shaped like one this app or a phone would mint, and short enough to store. */
    fun isId(text: String): Boolean = UUID_LIKE.matches(text)

    /** `YYYY-MM-DD` inside the window the relay keeps, else null. */
    fun storedDate(text: String, today: LocalDate): LocalDate? {
        val date = try {
            LocalDate.parse(text)
        } catch (_: DateTimeParseException) {
            return null
        }
        val earliest = today.minusDays(WireLimits.RETENTION_DAYS)
        val latest = today.plusDays(WireLimits.HORIZON_DAYS)
        return date.takeIf { !it.isBefore(earliest) && !it.isAfter(latest) }
    }

    /** `HH:mm` else null. */
    fun storedTime(text: String): LocalTime? = try {
        LocalTime.parse(text).withSecond(0).withNano(0)
    } catch (_: DateTimeParseException) {
        null
    }
}
