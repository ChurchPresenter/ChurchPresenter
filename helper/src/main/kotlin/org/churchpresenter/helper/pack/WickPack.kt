package org.churchpresenter.helper.pack

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.churchpresenter.diagnostics.Log
import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.action.GuideStep
import org.churchpresenter.helper.action.GuideTour
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.intent.ResolveContext
import org.churchpresenter.helper.intent.Resolution
import org.churchpresenter.helper.intent.RuleIntentResolver
import org.churchpresenter.helper.intent.semantic.CatalogEntry
import org.churchpresenter.helper.intent.semantic.CatalogTarget
import org.churchpresenter.helper.intent.semantic.WickCatalog
import org.churchpresenter.helper.intent.semantic.profilePageLabel
import org.churchpresenter.helper.suggest.Tip
import org.churchpresenter.sharedui.guide.GuideTarget
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.ProfileFocus
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.allStringResources

/**
 * `wick-pack/pack.json` as it is written: what a Wick update can add without an app release. Data only —
 * phrases that lead to things Wick already does, tours over controls the app already tags, and tips.
 * Built from `wick-pack/source.json` by `./gradlew :helper:buildWickPack`.
 *
 * @property version this pack's own number, raised with every change
 * @property minApp the oldest app version it is for; an older app ignores it
 * @property phrases `catalog.tsv` lines: target, text, vector
 */
@Serializable
data class PackFile(
    val version: Int,
    val minApp: String,
    val phrases: List<String> = emptyList(),
    val tours: List<PackTourFile> = emptyList(),
    val tips: List<PackTipFile> = emptyList(),
)

/**
 * One guided tour: [title] is how "Did you mean …?" names it, [triggers] the requests that start it
 * (embedded into [PackFile.phrases] as `tour:<id>`), and [steps] what it rings, in order.
 */
@Serializable
data class PackTourFile(
    val id: String,
    val title: String,
    val triggers: List<String> = emptyList(),
    val steps: List<PackStepFile>,
)

/**
 * One step: the guide-target id it rings, what must be open first — `tab=SONGS`, `settings=PROJECTION`
 * or `profile=SONGS` (a Profiles page, optionally `:rowKey`) — and the English hint shown beside it.
 */
@Serializable
data class PackStepFile(val target: String, val before: String? = null, val hint: String)

/** A tip of the day in English; [tour] is a tour in the same pack that Show me runs. */
@Serializable
data class PackTipFile(val text: String, val tour: String? = null)

/** A pack, checked against this build: everything it names exists here, and nothing else survived. */
class WickPack internal constructor(
    val version: Int,
    internal val phrases: List<CatalogEntry>,
    internal val tours: Map<String, PackTour>,
    internal val tips: List<Tip>,
)

/** A tour from a pack, and how "Did you mean …?" names it. */
internal class PackTour(val title: String, val tour: GuideTour) {
    val action: HelperAction get() = HelperAction.Highlight(tour, HelperText.Plain(title))
}

/** The most a pack may weigh; anything bigger is not read at all. */
const val MAX_PACK_BYTES = 2 * 1024 * 1024

private const val MAX_TEXT = 300
private const val MAX_STEPS = 12
private const val MAX_TOURS = 300
private const val MAX_TIPS = 200
private const val MAX_PHRASES = 5000
private const val VECTOR_SIZE = 384

private val TOUR_ID = Regex("[a-z0-9][a-z0-9._-]{0,63}")

private val json = Json {
    ignoreUnknownKeys = false
    isLenient = false
    allowSpecialFloatingPointValues = false
}

/**
 * Reads [text] as a pack for app [appVersion], or null when it is too big, malformed, or for a newer
 * app. Whatever names a target, a place or a tour this build does not have is dropped, the rest kept.
 */
fun parseWickPack(text: String, appVersion: String): WickPack? {
    if (text.length > MAX_PACK_BYTES) return null
    val file = runCatching { json.decodeFromString(PackFile.serializer(), text) }
        .onFailure { Log.warn("Wick", "Ignoring a Wick pack that is not valid: ${it.message?.take(200)}") }
        .getOrNull() ?: return null
    if (isNewerVersion(file.minApp, appVersion)) {
        Log.info("Wick", "Ignoring Wick pack ${file.version}: it needs app ${file.minApp}, this is $appVersion")
        return null
    }
    return PackChecker().check(file)
}

/** Whether version [a] is newer than [b], read as dotted numbers: `2026.3.10` is newer than `2026.3.9`. */
internal fun isNewerVersion(a: String, b: String): Boolean {
    val left = a.split('.').map { it.toIntOrNull() ?: 0 }
    val right = b.split('.').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(left.size, right.size)) {
        val l = left.getOrElse(i) { 0 }
        val r = right.getOrElse(i) { 0 }
        if (l != r) return l > r
    }
    return false
}

/** Checks a pack's every name against this build. */
private class PackChecker {
    private val rules = RuleIntentResolver()
    private val bundledControls: Set<String> by lazy {
        WickCatalog.load().mapNotNull { (it.target as? CatalogTarget.Control)?.id }.toSet()
    }
    private val namedTargets: Set<String> by lazy { guideTargetConstants() }

    fun check(file: PackFile): WickPack {
        val tours = file.tours.take(MAX_TOURS)
            .filter { TOUR_ID.matches(it.id) && it.title.length in 1..MAX_TEXT }
            .mapNotNull { tour -> tourOf(tour)?.let { tour.id to it } }
            .toMap()
        val phrases = file.phrases.take(MAX_PHRASES).mapNotNull { line -> phraseOf(line, tours.keys) }
        val tips = file.tips.take(MAX_TIPS).filter { it.text.length in 1..MAX_TEXT }.map { tip ->
            Tip(HelperText.Plain(tip.text), tip.tour?.let { tours[it] }?.action)
        }
        val dropped = file.tours.size - tours.size + file.phrases.size - phrases.size
        if (dropped > 0) Log.info("Wick", "Wick pack ${file.version}: dropped $dropped entries this build cannot use")
        return WickPack(file.version, phrases, tours, tips)
    }

    private fun tourOf(tour: PackTourFile): PackTour? {
        val steps = tour.steps.take(MAX_STEPS).mapNotNull(::stepOf)
        // A tour that lost a step would skip part of the way: all of it or none.
        if (steps.isEmpty() || steps.size != tour.steps.size) return null
        return PackTour(tour.title, GuideTour(steps))
    }

    private fun stepOf(step: PackStepFile): GuideStep? {
        if (step.hint.length !in 1..MAX_TEXT || !isKnownTarget(step.target)) return null
        val before = step.before?.let { beforeOf(it) ?: return null }
        return GuideStep(GuideTarget(step.target), HelperText.Plain(step.hint), before)
    }

    private fun beforeOf(text: String): HelperAction? {
        val kind = text.substringBefore('=')
        val rest = text.substringAfter('=', "")
        return when (kind) {
            "tab" -> Tabs.entries.find { it.name == rest }?.let(HelperAction::SelectTab)
            "settings" -> SettingsPage.entries.find { it.name == rest }?.let { HelperAction.OpenSettings(it) }
            "profile" -> {
                val page = rest.substringBefore(':')
                val row = rest.substringAfter(':', "").ifEmpty { null }
                if (profilePageLabel(page) == null || (row != null && row !in Res.allStringResources)) return null
                HelperAction.OpenSettings(SettingsPage.PROFILES, ProfileFocus(page = page, rowKey = row))
            }
            else -> null
        }
    }

    private fun phraseOf(line: String, tours: Set<String>): CatalogEntry? {
        val fields = line.split('\t')
        val entry = line.takeIf { fields.size == PHRASE_FIELDS && fields[1].length in 1..MAX_TEXT }
            ?.let { runCatching { WickCatalog.parseLine(it) }.getOrNull() }
            ?: return null
        val wellFormed = entry.vector.size == VECTOR_SIZE && entry.vector.all { it.isFinite() }
        val target = entry.target
        val tourHere = target !is CatalogTarget.PackTour || target.id in tours
        return entry.takeIf { wellFormed && tourHere && isKnown(target, fields[0]) }
    }

    private fun isKnown(target: CatalogTarget, raw: String): Boolean = when (target) {
        is CatalogTarget.Request -> rules.resolveNow(target.text, CONTEXT) != Resolution.Unknown
        is CatalogTarget.Control ->
            isKnownTarget(target.id) && target.labelKey in Res.allStringResources &&
                (target.before != null || raw.substringAfterLast(':') == "-")
        is CatalogTarget.ProfileRow ->
            target.labelKey in Res.allStringResources && profilePageLabel(target.page) != null
        is CatalogTarget.PageRow -> target.labelKey in Res.allStringResources
        else -> true
    }

    /** Whether the app tags a control as [id]: a named target, one the bundled catalog knows, or a row, tab or page. */
    private fun isKnownTarget(id: String): Boolean = id in namedTargets || id in bundledControls || when {
        id.startsWith("tab.") -> Tabs.entries.any { it.name == id.removePrefix("tab.") }
        id.startsWith("settings.") && SettingsPage.entries.any { it.name == id.removePrefix("settings.") } -> true
        id.startsWith("settingsRow.") -> id.removePrefix("settingsRow.") in Res.allStringResources
        id.startsWith("shortcuts.") -> ShortcutAction.entries.any { it.name == id.removePrefix("shortcuts.") }
        else -> false
    }

    private companion object {
        const val PHRASE_FIELDS = 3
        val CONTEXT = ResolveContext(language = "en")
    }
}

/** Every id `GuideTargets` names, read off its getters — a value class's getter returns the id itself. */
private fun guideTargetConstants(): Set<String> = GuideTargets::class.java.declaredMethods
    .filter { it.parameterCount == 0 && it.returnType == String::class.java && it.name.startsWith("get") }
    .mapNotNull { method -> runCatching { method.invoke(GuideTargets) as? String }.getOrNull() }
    .toSet()
