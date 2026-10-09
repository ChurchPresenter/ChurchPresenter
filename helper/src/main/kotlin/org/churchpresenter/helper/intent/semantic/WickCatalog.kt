package org.churchpresenter.helper.intent.semantic

import org.churchpresenter.helper.suggest.SuggestedRequest
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.models.Tabs
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/**
 * What one catalog entry leads to. Written into `wick/catalog.tsv` by [format] and read back by
 * [parse], so the file names everything by its stable name (an enum constant, a guide-target id, a
 * string key), never by position.
 */
internal sealed interface CatalogTarget {
    fun format(): String

    /** One of the chips the helper offers; the rules turn its [request] into the action. */
    data class Suggested(val request: SuggestedRequest) : CatalogTarget {
        override fun format() = "suggested:${request.name}"
    }

    /** A phrase the rules already understand; they turn it into the action. */
    data class Request(val text: String) : CatalogTarget {
        override fun format() = "request:$text"
    }

    /** A tagged control, named by the string [labelKey] it shows; [before] opens where it lives. */
    data class Control(val id: String, val labelKey: String, val before: Before?) : CatalogTarget {
        override fun format() = "control:$id:$labelKey:${before?.format() ?: "-"}"
    }

    /**
     * A row of the Profiles settings page, named by its label's string [labelKey], on the profile page
     * [page] (`SONGS`, `BIBLE`, `GENERAL`, … — see `ProfileFocus.page`).
     */
    data class ProfileRow(val labelKey: String, val page: String) : CatalogTarget {
        override fun format() = "profileRow:$labelKey:$page"
    }

    /** A labelled row of the Settings page [page], named by its label's string [labelKey]. */
    data class PageRow(val labelKey: String, val page: SettingsPage) : CatalogTarget {
        override fun format() = "pageRow:$labelKey:${page.name}"
    }

    data class Settings(val page: SettingsPage) : CatalogTarget {
        override fun format() = "settings:${page.name}"
    }

    data class Tab(val tab: Tabs) : CatalogTarget {
        override fun format() = "tab:${tab.name}"
    }

    data class Shortcut(val action: ShortcutAction) : CatalogTarget {
        override fun format() = "shortcut:${action.name}"
    }

    /** A guided tour that came in a downloaded Wick pack, by its [id] there. */
    data class PackTour(val id: String) : CatalogTarget {
        override fun format() = "tour:$id"
    }

    /** What has to be open before a [Control] can be seen. */
    sealed interface Before {
        fun format(): String

        data class OnTab(val tab: Tabs) : Before {
            override fun format() = "tab=${tab.name}"
        }

        data class OnSettings(val page: SettingsPage) : Before {
            override fun format() = "settings=${page.name}"
        }
    }

    companion object {
        /** The target [text] names, or null when it names something this build no longer has. */
        fun parse(text: String): CatalogTarget? {
            val kind = text.substringBefore(':')
            val rest = text.substringAfter(':')
            return when (kind) {
                "suggested" -> SuggestedRequest.entries.find { it.name == rest }?.let(::Suggested)
                "request" -> Request(rest)
                "control" -> parseControl(rest)
                "profileRow" -> rest.split(':').takeIf { it.size == 2 }?.let { (key, page) -> ProfileRow(key, page) }
                "pageRow" -> rest.split(':').takeIf { it.size == 2 }?.let { (key, page) ->
                    SettingsPage.entries.find { it.name == page }?.let { PageRow(key, it) }
                }
                "settings" -> SettingsPage.entries.find { it.name == rest }?.let(::Settings)
                "tab" -> Tabs.entries.find { it.name == rest }?.let(::Tab)
                "shortcut" -> ShortcutAction.entries.find { it.name == rest }?.let(::Shortcut)
                "tour" -> rest.takeIf { it.isNotBlank() }?.let(::PackTour)
                else -> null
            }
        }

        private fun parseControl(rest: String): Control? {
            val parts = rest.split(':')
            if (parts.size != CONTROL_PARTS) return null
            val (id, labelKey, before) = parts
            return Control(id, labelKey, parseBefore(before))
        }

        private fun parseBefore(text: String): Before? = when {
            text.startsWith("tab=") -> Tabs.entries.find { it.name == text.removePrefix("tab=") }?.let(Before::OnTab)
            text.startsWith("settings=") ->
                SettingsPage.entries.find { it.name == text.removePrefix("settings=") }?.let(Before::OnSettings)
            else -> null
        }

        private const val CONTROL_PARTS = 3
    }
}

/** One thing the helper can match a request against: some [text] that describes [target], and its meaning. */
internal class CatalogEntry(val target: CatalogTarget, val text: String, val vector: FloatArray)

/**
 * `wick/catalog.tsv`: one entry per line — target, text, and the text's vector as base64 fp16. The
 * file is generated from the codebase by `./gradlew :helper:updateWickCatalog` and checked against it on
 * every test run, so it is never edited by hand.
 */
internal object WickCatalog {
    const val RESOURCE = "wick/catalog.tsv"

    fun read(stream: InputStream): List<CatalogEntry> = stream.bufferedReader().useLines { lines ->
        lines.filter { it.isNotBlank() }.mapNotNull(::parseLine).toList()
    }

    fun load(): List<CatalogEntry> =
        WickCatalog::class.java.classLoader.getResourceAsStream(RESOURCE)?.let(::read) ?: emptyList()

    fun line(target: CatalogTarget, text: String, vector: FloatArray): String =
        "${target.format()}\t$text\t${encodeVector(vector)}"

    /** One `catalog.tsv` line, or null when it is malformed or names something this build does not have. */
    fun parseLine(line: String): CatalogEntry? {
        val (target, text, vector) = line.split('\t').takeIf { it.size == FIELDS } ?: return null
        return CatalogTarget.parse(target)?.let { CatalogEntry(it, text, decodeVector(vector)) }
    }

    fun encodeVector(vector: FloatArray): String {
        val buffer = ByteBuffer.allocate(vector.size * Short.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        for (v in vector) buffer.putShort(java.lang.Float.floatToFloat16(v))
        return Base64.getEncoder().encodeToString(buffer.array())
    }

    fun decodeVector(text: String): FloatArray {
        val buffer = ByteBuffer.wrap(Base64.getDecoder().decode(text)).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(buffer.remaining() / Short.SIZE_BYTES) { java.lang.Float.float16ToFloat(buffer.short) }
    }

    private const val FIELDS = 3
}
