@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lowerthird

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import java.io.File

/**
 * Lottie-folder fixtures, shared by every suite that puts animations on disk and points a tab at
 * them.
 *
 * Three modules need these and they must agree: `:lower-third-settings-tab` drives the options page
 * that lists a folder, `:composeApp` drives the server tab's lower-third triggers, and this module
 * drives the tab itself. What counts as a Lottie animation is decided by
 * `companionserver.isLottieFile`, so a fixture that drifts from it stops testing the same thing the
 * app does — which is why these live here, beside the module that owns the rendering, rather than
 * being copied into each suite.
 */

/** The smallest JSON the folder scan accepts as a Lottie animation. */
fun lottieJson(name: String = "clip"): String =
    """{"v":"5.7.4","fr":30,"ip":0,"op":30,"w":1920,"h":1080,"nm":"$name","layers":[]}"""

/** Valid JSON that is not a Lottie animation — no `"layers"` key. */
const val NOT_LOTTIE_JSON: String = """{"v":"5.7.4","nm":"not an animation"}"""

/**
 * Runs [block] against a fresh temporary folder, deleting it afterwards whatever happens.
 * [files] maps file name to contents.
 */
fun withLottieFolder(vararg files: Pair<String, String>, block: (File) -> Unit) {
    val folder = File.createTempFile("churchpresenter-lottie", "").let {
        it.delete()
        it.mkdirs()
        it
    }
    try {
        files.forEach { (name, contents) -> File(folder, name).writeText(contents) }
        block(folder)
    } finally {
        folder.deleteRecursively()
    }
}

/**
 * Waits out the folder scan the tabs do off the UI thread (PR #259 moved it there, because deciding
 * whether a JSON file is a Lottie means reading the whole of it).
 *
 * Until that finishes the tab shows "Scanning folder…" rather than a verdict, so asserting straight
 * after composing sees an empty list. Waiting on the scanning row disappearing is a positive signal
 * that the read finished — it works for a folder with files and for an empty one alike.
 */
fun ComposeUiTest.awaitFolderScan() {
    waitUntil("the folder scan finished") {
        onAllNodesWithText("Scanning folder", substring = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
    }
}
