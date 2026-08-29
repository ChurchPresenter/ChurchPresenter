package org.churchpresenter.settings

import kotlinx.serialization.Serializable

/**
 * Where the Lottie lower thirds live. That is all this holds any more, and deliberately so.
 *
 * It used to carry four window insets that padded the animation on screen, plus the remains of a
 * preset feature the app no longer has. The insets are gone because **a Lottie file is
 * self-contained**: its margins belong inside the file, where whoever designed it put them. Three of
 * the four output paths honoured those insets and the ATEM one never did -- `LottieRenderCache`
 * renders through [LowerThirdOffscreenRenderer], which takes no settings at all -- so the same
 * animation was framed one way over NDI and another through the switcher. They were also raw dp
 * while every other presenter scales its insets against a 1920x1080 reference, so the same number
 * meant a different inset on a 4K screen than on a 1920 feed. Every output now draws the file edge
 * to edge, which is what the ATEM path always did.
 *
 * The preset remains were `lottiePresets`, `savedSearchStrings`, `savedReplaceStrings` and a
 * `lowerThirdListWidthDp` that duplicated the live one on [WindowLayoutSettings] -- four fields
 * serialized into every settings file with nothing anywhere reading them.
 */
@Serializable
data class StreamingSettings(
    val lowerThirdFolder: String = "",
)
