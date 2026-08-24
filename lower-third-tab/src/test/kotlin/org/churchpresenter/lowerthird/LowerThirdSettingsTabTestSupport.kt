@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lowerthird

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import java.io.File

/**
 * Harness and fixtures shared by the `LowerThirdSettingsTab` test classes.
 *
 * The tab lists the Lottie animations in a folder, so its behaviour depends on what is actually on
 * disk. Every test that needs files gets its **own temporary folder**, created and deleted around
 * it, so nothing leaks between tests or touches the developer's real animation library.
 *
 * A file counts as a Lottie animation to this tab when it is a `.json` whose text contains both a
 * `"v"` and a `"layers"` key — [lottieJson] produces the smallest thing that satisfies that, and
 * [NOT_LOTTIE_JSON] the smallest thing that does not.
 */
@OptIn(ExperimentalTestApi::class)
internal fun lowerThirdSettingsTab(
    initial: AppSettings = AppSettings(),
    onOpenLottieGen: (outputDir: String, onFileSaved: (() -> Unit)?) -> Unit = { _, _ -> },
    block: ComposeUiTest.(get: () -> AppSettings) -> Unit,
) = runComposeUiTest {
    var current = initial
    setContent {
        MaterialTheme {
            var state by remember { mutableStateOf(current) }
            LowerThirdSettingsTab(
                settings = state,
                onSettingsChange = { transform -> state = transform(state); current = state },
                onOpenLottieGen = onOpenLottieGen,
            )
        }
    }
    awaitFolderScan()
    block { current }
}

/** Settings pointing the tab at [folder] as its animation library. */
internal fun settingsForFolder(folder: File): AppSettings = AppSettings().let {
    it.copy(streamingSettings = it.streamingSettings.copy(lowerThirdFolder = folder.absolutePath))
}

/** The smallest JSON this tab accepts as a Lottie animation. */
internal fun lottieJson(name: String = "clip"): String =
    """{"v":"5.7.4","fr":30,"ip":0,"op":30,"w":1920,"h":1080,"nm":"$name","layers":[]}"""

/** Valid JSON that is not a Lottie animation — no `"layers"` key. */
internal const val NOT_LOTTIE_JSON: String = """{"v":"5.7.4","nm":"not an animation"}"""

/**
 * Runs [block] against a fresh temporary folder, deleting it afterwards whatever happens.
 * [files] maps file name to contents.
 */
internal fun withLottieFolder(vararg files: Pair<String, String>, block: (File) -> Unit) {
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
 * Waits out the folder scan the tab now does off the UI thread (PR #259 moved it there, because
 * deciding whether a JSON file is a Lottie means reading the whole of it).
 *
 * Until that finishes the tab shows "Scanning folder…" rather than a verdict, so asserting straight
 * after composing sees an empty list. Waiting on the scanning row disappearing is a positive signal
 * that the read finished — it works for a folder with files and for an empty one alike.
 */
internal fun ComposeUiTest.awaitFolderScan() {
    waitUntil("the folder scan finished") {
        onAllNodesWithText("Scanning folder", substring = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
    }
}

/**
 * Waits for the lower-third rows that [settings]' folder should produce.
 *
 * The server tab loads them off the UI thread too, but unlike the lower-third tab it starts from an
 * empty list with no "Scanning folder" marker — so [awaitFolderScan] passes vacuously there and an
 * assertion would run against an empty list. Waiting for the names the folder actually holds is a
 * positive signal that the read landed, and it stays correct for an empty folder (nothing to wait
 * for).
 */
internal fun ComposeUiTest.awaitLowerThirdRows(settings: AppSettings) {
    val folder = File(settings.streamingSettings.lowerThirdFolder)
    val names = folder.listFiles()
        ?.filter { it.extension.equals("json", ignoreCase = true) && it.readText().contains("\"layers\"") }
        ?.map { it.nameWithoutExtension }
        ?: return
    if (names.isEmpty()) return
    waitUntil("the lower-third rows loaded") {
        names.all { onAllNodesWithText(it, substring = true).fetchSemanticsNodes(false).isNotEmpty() }
    }
}

