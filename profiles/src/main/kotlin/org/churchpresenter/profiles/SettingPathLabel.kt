package org.churchpresenter.profiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.churchpresenter.settings.plainText
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.allStringResources
import org.jetbrains.compose.resources.getString
import java.util.Locale

/**
 * A setting's name read off its path: `bibleSettings.translations[kjv.spb].textFontSize` is
 * "Text font size · KJV", and a Bible box's `bibleSettings.textBoxes[REFERENCE#kjv.spb@LT].yPercent`
 * is "Y percent · Reference · KJV · LT". Named after the stored field, so it reads the way the
 * settings file does rather than the way a row labels it -- the card lists changes on every page,
 * not only this one's. [names] puts each field in the app's language.
 */
internal fun settingPathLabel(path: String, names: SettingNames = SettingNames()): String {
    val entry = ENTRY.find(path)?.groupValues?.get(1)?.replace(ESCAPED, "$1")?.let { entryLabel(it, names) }
    val leaf = path.replace(ENTRY, "").substringAfterLast('.')
    val field = names.field(leaf)
    return if (entry != null) "$field · $entry" else field
}

/**
 * The names a setting's field and a stored value are shown by: the `setting_field_*` and
 * `setting_value_*` strings in the app's language, and the field's own words where there is none.
 */
internal class SettingNames(private val strings: Map<String, String> = emptyMap()) {
    fun field(camel: String): String = strings[FIELD_PREFIX + snake(camel)] ?: words(camel)

    /** A box's item, such as `REFERENCE`. */
    fun item(name: String): String = strings[VALUE_PREFIX + name.lowercase()] ?: words(name.lowercase())

    /** A stored value in words: a known option by its name, anything else as [plainText] reads it. */
    fun value(element: JsonElement, on: String, off: String): String {
        val primitive = element as? JsonPrimitive
        val known = primitive?.takeIf { it.isString }?.let { strings[VALUE_PREFIX + it.content.lowercase()] }
        return known ?: element.plainText(on, off)
    }
}

/**
 * Every field and value name in the app's language, read off the event thread; English words until
 * then. A field a page already names, and a value its row already labels, reuse that string.
 */
@Composable
internal fun rememberSettingNames(): SettingNames {
    val names by produceState(SettingNames(), Locale.getDefault()) {
        value = SettingNames(
            withContext(Dispatchers.IO) {
                val own = Res.allStringResources.filterKeys { it.startsWith(FIELD_PREFIX) }
                val reused = SETTING_FIELD_STRINGS.mapKeys { FIELD_PREFIX + snake(it.key) }
                val values = SETTING_VALUE_STRINGS.mapKeys { VALUE_PREFIX + it.key.lowercase() }
                (own + reused + values).mapValues { getString(it.value) }
            },
        )
    }
    return names
}

/** A bracketed name as words: a file name without its extension, a box as its item and its translation. */
private fun entryLabel(name: String, names: SettingNames): String {
    val lowerThird = name.endsWith(LOWER_THIRD_SUFFIX)
    val key = name.removeSuffix(LOWER_THIRD_SUFFIX)
    val item = key.substringBefore('#', missingDelimiterValue = "")
    val file = key.substringAfter('#').substringBeforeLast('.').uppercase()
    return listOfNotNull(
        names.item(item).takeIf { item.isNotEmpty() },
        file,
        LOWER_THIRD_SUFFIX.removePrefix("@").takeIf { lowerThird },
    ).joinToString(" · ")
}

private fun words(camel: String): String =
    camel.replace(CAMEL_HUMP, " $1").lowercase().replaceFirstChar { it.uppercase() }

private fun snake(camel: String): String = camel.replace(CAMEL_HUMP, "_$1").lowercase()

private const val FIELD_PREFIX = "setting_field_"
private const val VALUE_PREFIX = "setting_value_"

/** How a box key names the lower third's box of an item -- `textBoxKey`'s suffix. */
private const val LOWER_THIRD_SUFFIX = "@LT"

/** A name in brackets -- a translation, a Bible box -- with a bracket or backslash in it escaped. */
private val ENTRY = Regex("""\[((?:\\.|[^]\\])*)]""")
private val ESCAPED = Regex("""\\(.)""")
private val CAMEL_HUMP = Regex("(?<=[a-z0-9])([A-Z])")
