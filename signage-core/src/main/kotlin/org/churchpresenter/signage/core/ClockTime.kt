package org.churchpresenter.signage.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalTime

/** A wall-clock time of day to the minute, written `"HH:mm"` on the wire. */
@Serializable(with = ClockTimeSerializer::class)
@JvmInline
value class ClockTime(val minuteOfDay: Int) : Comparable<ClockTime> {
    init {
        require(minuteOfDay in 0 until MINUTES_PER_DAY) { "minute of day out of range: $minuteOfDay" }
    }

    val hour: Int get() = minuteOfDay / MINUTES_PER_HOUR
    val minute: Int get() = minuteOfDay % MINUTES_PER_HOUR

    fun toLocalTime(): LocalTime = LocalTime.of(hour, minute)

    override fun compareTo(other: ClockTime): Int = minuteOfDay.compareTo(other.minuteOfDay)

    override fun toString(): String = "%02d:%02d".format(hour, minute)

    companion object {
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

        fun of(hour: Int, minute: Int): ClockTime = ClockTime(hour * MINUTES_PER_HOUR + minute)

        /** Parses `"HH:mm"`; throws [IllegalArgumentException] on anything else. */
        fun parse(text: String): ClockTime {
            val parts = text.split(':')
            require(parts.size == 2) { "expected HH:mm, got '$text'" }
            val hour = requireNotNull(parts[0].toIntOrNull()) { "bad hour in '$text'" }
            val minute = requireNotNull(parts[1].toIntOrNull()) { "bad minute in '$text'" }
            require(hour in 0..23 && minute in 0..59) { "time out of range: '$text'" }
            return of(hour, minute)
        }
    }
}

internal object ClockTimeSerializer : KSerializer<ClockTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("org.churchpresenter.signage.ClockTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ClockTime) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): ClockTime = ClockTime.parse(decoder.decodeString())
}
