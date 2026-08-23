package org.churchpresenter.companionserver

import java.io.File

/** Quick check whether a JSON file looks like a Lottie animation (has "v" and "layers" keys). */
fun isLottieFile(file: File): Boolean {
    return try {
        val text = file.readText()
        text.contains("\"v\"") && text.contains("\"layers\"")
    } catch (_: Exception) { false }
}
