package org.churchpresenter.settings

import kotlinx.serialization.json.JsonElement

/**
 * The last few profiles' [settingPaths], by identity.
 *
 * The profile pages read one value at a time -- a row's master value, a change's default -- and
 * every read flattened the whole profile through JSON to find it, dozens of times a composition.
 * A profile is immutable, so one instance always flattens to the same map, and a page reads only a
 * handful of profiles -- the one edited, its masters, its defaults -- so a few are kept.
 */
internal object RecentSettingPaths {
    private const val KEPT = 8
    private val recent = ArrayDeque<Pair<OutputProfile, Map<String, JsonElement>>>(KEPT)

    /** [profile]'s paths: the ones [flatten] gave for this very instance, or [flatten]'s answer now. */
    @Synchronized
    fun of(profile: OutputProfile, flatten: (OutputProfile) -> Map<String, JsonElement>): Map<String, JsonElement> {
        recent.firstOrNull { it.first === profile }?.let { return it.second }
        val paths = flatten(profile)
        if (recent.size == KEPT) recent.removeLast()
        recent.addFirst(profile to paths)
        return paths
    }
}
