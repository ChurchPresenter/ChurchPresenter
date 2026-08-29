package org.churchpresenter.app.churchpresenter.viewmodel

import java.io.File

/**
 * Quick check whether a JSON file looks like a Lottie animation (has "v" and "layers" keys).
 *
 * The whole of what is left of `LowerThirdSettingsViewModel`, which drove a Lower Third *settings*
 * tab that duplicated the Lower Third content tab and has been removed. This function was never
 * part of that: it is what tells a Lottie from any other `.json` in the folder, and the content tab,
 * the render cache, the ATEM bridge and the Server tab all ask it.
 *
 * It reads the whole file, so keep it off the composition thread -- every caller does.
 */
fun isLottieFile(file: File): Boolean {
    return try {
        val text = file.readText()
        text.contains("\"v\"") && text.contains("\"layers\"")
    } catch (_: Exception) { false }
}
