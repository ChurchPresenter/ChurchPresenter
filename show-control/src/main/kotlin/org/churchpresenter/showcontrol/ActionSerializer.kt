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

    private val known: Map<KClass<out Action>, KSerializer<out Action>> = mapOf(
        Action.GoLive::class to Action.GoLive.serializer(),
        Action.ToPreview::class to Action.ToPreview.serializer(),
        Action.Take::class to Action.Take.serializer(),
        Action.Clear::class to Action.Clear.serializer(),
        Action.ClearAll::class to Action.ClearAll.serializer(),
        Action.ClearGroup::class to Action.ClearGroup.serializer(),
        Action.Message::class to Action.Message.serializer(),
        Action.Prop::class to Action.Prop.serializer(),
        Action.LowerThird::class to Action.LowerThird.serializer(),
        Action.Timer::class to Action.Timer.serializer(),
        Action.Media::class to Action.Media.serializer(),
        Action.ObsScene::class to Action.ObsScene.serializer(),
        Action.AtemKey::class to Action.AtemKey.serializer(),
        Action.AtemMacro::class to Action.AtemMacro.serializer(),
        Action.CompanionPress::class to Action.CompanionPress.serializer(),
        Action.NextItem::class to Action.NextItem.serializer(),
        Action.PreviousItem::class to Action.PreviousItem.serializer(),
        Action.Wait::class to Action.Wait.serializer(),
        Action.RunMacro::class to Action.RunMacro.serializer(),
    )

    private val byName = known.values.associateBy { it.descriptor.serialName }

    /** The names actions are stored by, one per type -- what a newer build must keep. */
    val typeNames: Set<String> get() = byName.keys

    private val lenient = Json { ignoreUnknownKeys = true }

    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Action) {
        val json = (encoder as? JsonEncoder ?: throw SerializationException("Actions are written as JSON")).json
        val obj = when (value) {
            is Action.Unknown -> value.json
            else -> {
                @Suppress("UNCHECKED_CAST")
                val serializer = known.getValue(value::class) as KSerializer<Action>
                val fields = json.encodeToJsonElement(serializer, value).jsonObject
                JsonObject(mapOf(TYPE to JsonPrimitive(serializer.descriptor.serialName)) + fields)
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
}
