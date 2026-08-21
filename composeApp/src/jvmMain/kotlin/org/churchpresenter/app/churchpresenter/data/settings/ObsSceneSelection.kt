package org.churchpresenter.app.churchpresenter.data.settings

import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.settings.OBSSettings

/**
 * The OBS scene to switch to when the live content type becomes [mode], or **null** to leave OBS
 * alone.
 *
 * Order: the scene mapped to this content type, else [OBSSettings.defaultScene], else no switch.
 *
 * **Blank is treated as unset at every step, and that is not a nicety.** `OBSSettingsTab` writes each
 * mapping straight from a text field, so a user who types a scene name and then clears it leaves
 * `""` in [OBSSettings.sceneMappings] rather than removing the key. Taking that literally would ask
 * OBS to switch to a scene called "" the moment that content goes live — mid-service, on the stream.
 * The same applies to a blank default, which means "no default", not "a scene with no name".
 *
 * Returns null when integration is disabled, so the caller has one condition to check rather than
 * two.
 */
fun obsSceneFor(mode: Presenting, settings: OBSSettings): String? {
    if (!settings.enabled) return null
    return settings.sceneMappings[mode.name]?.takeIf { it.isNotBlank() }
        ?: settings.defaultScene.takeIf { it.isNotBlank() }
}
