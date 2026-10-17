package org.churchpresenter.server

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.churchpresenter.calendar.sync.Envelope
import org.churchpresenter.calendar.sync.Sanitize
import org.churchpresenter.settings.CalendarSyncSettings
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Base64

/**
 * What lets another computer join this calendar: the relay, the instance, that computer's own id
 * and token, and the instance key. Written by the main computer as a file into a folder the user
 * picks, or as a code they paste into an email of their own; the relay never sees either. It works
 * once, and for a day -- see the relay's `UNCLAIMED_DESKTOP_HOURS`.
 */
@Serializable
data class CalendarJoin(
    val v: Int = VERSION,
    val relay: String,
    val instance: String,
    val desktop: String,
    val token: String,
    val key: String,
    val role: String,
    val mainName: String = "",
    val expires: String,
) {
    /** `CPJ-` and the JSON as unpadded base64url: one word, safe to paste anywhere. */
    fun toCode(): String = CODE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(toJson().toByteArray())

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        const val VERSION = 1
        const val CODE_PREFIX = "CPJ-"
        const val FILE_NAME = "churchpresenter-calendar.cpjoin"
        const val FILE_EXTENSION = "cpjoin"
        private const val MIN_TOKEN_CHARS = 16
        private const val MAX_TOKEN_CHARS = 128
        private val TOKEN = Regex("^[A-Za-z0-9_-]+$")
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /**
         * [text] -- a join file's contents or a pasted code -- read and checked: every field the
         * shape it must be, the relay an https address, the key a key, and not past [now].
         */
        fun parse(text: String, now: Instant): JoinParse {
            val join = decode(text.trim())?.takeIf { it.isWellFormed() } ?: return JoinParse.Invalid
            val expires = expiry(join.expires) ?: return JoinParse.Invalid
            return if (expires.isBefore(now)) JoinParse.Expired else JoinParse.Ok(join)
        }

        /** A file's JSON or a code's, as a join; null when it is neither. */
        private fun decode(text: String): CalendarJoin? {
            val raw = if (text.startsWith(CODE_PREFIX)) decodeCode(text) ?: return null else text
            return try {
                json.decodeFromString(serializer(), raw)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        private fun expiry(text: String): Instant? = try {
            Instant.parse(text)
        } catch (_: DateTimeParseException) {
            null
        }

        private fun decodeCode(code: String): String? = try {
            val body = code.removePrefix(CODE_PREFIX).filterNot(Char::isWhitespace)
            String(Base64.getUrlDecoder().decode(body))
        } catch (_: IllegalArgumentException) {
            null
        }

        private fun CalendarJoin.isWellFormed(): Boolean =
            v == VERSION &&
                relay.startsWith("https://") &&
                Sanitize.isId(instance) &&
                Sanitize.isId(desktop) &&
                token.length in MIN_TOKEN_CHARS..MAX_TOKEN_CHARS && TOKEN.matches(token) &&
                Envelope.decodeKey(key) != null &&
                (role == CalendarSyncSettings.ROLE_FOLLOWER || role == CalendarSyncSettings.ROLE_VIEWER)
    }
}

/** What a join file or code came to. */
sealed class JoinParse {
    data class Ok(val join: CalendarJoin) : JoinParse()

    /** Not a join file or code at all, or one with a field out of shape. */
    data object Invalid : JoinParse()

    /** A real one whose day is up; the main computer has to make a new one. */
    data object Expired : JoinParse()
}
