package org.churchpresenter.settingspages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.churchpresenter.settings.AtemSettings
import java.net.URLEncoder

/**
 * The URL that plays lower third [name]. Running defaults to keying, so the *unkeyed* variant is the
 * one that carries `key=0`.
 */
internal fun lowerThirdTriggerUrl(
    serverUrl: String,
    name: String,
    withKey: Boolean,
    apiKey: String = "",
): String {
    val params = buildList {
        if (!withKey) add("key=0")
        if (apiKey.isNotEmpty()) add("apiKey=" + URLEncoder.encode(apiKey, "UTF-8"))
    }
    val query = if (params.isEmpty()) "" else "?" + params.joinToString("&")
    return "$serverUrl/api/lowerthirds/${encodeUrlPathSegment(name)}/run$query"
}

/** The URL that uploads [name] to the ATEM as a [kind] ("still" or "clip"). */
internal fun atemMediaUrl(
    serverUrl: String,
    kind: String,
    name: String,
    keyTarget: String,
    apiKey: String = "",
): String = "$serverUrl/api/atem/$kind/${encodeUrlPathSegment(name)}" + apiQueryString(keyTarget, apiKey)

/** `keytype=dsk` or `keytype=usk`, per the configured key type. */
internal fun atemKeyTypeParam(atem: AtemSettings): String =
    if (atem.useDownstreamKey) "keytype=dsk" else "keytype=usk"

/**
 * The default key target for the "+ key" URLs, 1-based to match the switcher's own numbering. A DSK
 * ignores the M/E and names only the downstream key; an upstream key names both.
 */
internal fun atemKeyTarget(atem: AtemSettings): String =
    if (atem.useDownstreamKey) {
        "keytype=dsk&key=${atem.dskIndex + 1}"
    } else {
        "keytype=usk&me=${atem.keyMixEffect + 1}&key=${atem.keyIndex + 1}"
    }

/** The URL that turns the configured ATEM key on ([on]) or off. */
internal fun atemKeyUrl(serverUrl: String, on: Boolean, keyTypeParam: String, apiKey: String = ""): String =
    "$serverUrl/api/atem/key/${if (on) "on" else "off"}" + apiQueryString(keyTypeParam, apiKey)

/** The URL that takes the current lower third down, leaving other output alone. */
internal fun lowerThirdHideUrl(serverUrl: String, apiKey: String = ""): String =
    "$serverUrl/api/lowerthirds/hide" + apiQueryString(apiKey = apiKey)

/** The URL that clears every output — Bible, song, lower third and the rest. */
internal fun clearDisplayUrl(serverUrl: String, apiKey: String = ""): String =
    "$serverUrl/api/clear" + apiQueryString(apiKey = apiKey)
