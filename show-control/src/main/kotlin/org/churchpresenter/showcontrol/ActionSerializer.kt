package org.churchpresenter.showcontrol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.reflect.KClass

/**
 * Writes an [Action] as a JSON object whose `type` is its `@SerialName`, and reads one back. A
 * `type` this build does not know -- or an object it cannot read -- comes back as
 * [Action.Unknown], which writes itself out unchanged, so a newer file survives an older build.
 * Fields an action does not have are ignored.
 */
object ActionSerializer : KSerializer<Action> {

    private val known: Map<KClass<out Action>, Known<out Action>> = listOf(
        entry(Action.GoLive.serializer()),
        entry(Action.ToPreview.serializer()),
        entry(Action.Take.serializer()),
        entry(Action.Clear.serializer()),
        entry(Action.ClearAll.serializer()),
        entry(Action.ClearGroup.serializer()),
        entry(Action.Message.serializer()),
        entry(Action.Prop.serializer()),
        entry(Action.LowerThird.serializer()),
        entry(Action.Timer.serializer()),
        entry(Action.Media.serializer()),
        entry(Action.ObsScene.serializer()),
        entry(Action.AtemKey.serializer()),
        entry(Action.AtemMacro.serializer()),
        entry(Action.CompanionPress.serializer()),
        entry(Action.NextItem.serializer()),
        entry(Action.PreviousItem.serializer()),
        entry(Action.Wait.serializer()),
        entry(Action.RunMacro.serializer()),
    ).associateBy { it.type }

    private val byName = known.values.map { it.serializer }.associateBy { it.descriptor.serialName }

    /** The names actions are stored by, one per type -- what a newer build must keep. */
    val typeNames: Set<String> get() = byName.keys

    private val lenient = Json { ignoreUnknownKeys = true }

    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Action) {
        val json = (encoder as? JsonEncoder ?: throw SerializationException("Actions are written as JSON")).json
        val obj = when (value) {
            is Action.Unknown -> value.json
            else -> {
                val entry = known.getValue(value::class)
                val type = JsonPrimitive(entry.serializer.descriptor.serialName)
                JsonObject(mapOf(TYPE to type) + entry.encode(json, value))
            }
        }
        encoder.encodeJsonElement(obj)
    }

    override fun deserialize(decoder: Decoder): Action {
        val input = decoder as? JsonDecoder ?: throw SerializationException("Actions are read from JSON")
        val obj = input.decodeJsonElement() as? JsonObject ?: throw SerializationException("An action is an object")
        return try {
            // A `type` that is not a string -- an object, an array -- is as unknown as a name this
            // build has never heard of; reading it throws, so it is read inside the catch.
            val serializer = (obj[TYPE] as? JsonPrimitive)?.contentOrNull?.let(byName::get)
                ?: return Action.Unknown(obj)
            lenient.decodeFromJsonElement(serializer, JsonObject(obj - TYPE))
        } catch (_: SerializationException) {
            Action.Unknown(obj)
        } catch (_: IllegalArgumentException) {
            Action.Unknown(obj)
        }
    }

    private const val TYPE = "type"

    /** One action type and its serializer, kept together so encoding needs no unchecked cast. */
    private class Known<A : Action>(val type: KClass<A>, val serializer: KSerializer<A>) {
        fun encode(json: Json, value: Action): JsonObject =
            json.encodeToJsonElement(serializer, type.java.cast(value)).jsonObject
    }

    private inline fun <reified A : Action> entry(serializer: KSerializer<A>) = Known(A::class, serializer)
}
