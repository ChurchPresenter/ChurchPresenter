@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lowerthird.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.lowerthird.awaitFolderScan
import org.churchpresenter.settings.AppSettings
import java.io.File

/**
 * Harness for the `LowerThirdSettingsTab` test classes.
 *
 * The tab lists the Lottie animations in a folder, so its behaviour depends on what is actually on
 * disk. Every test that needs files gets its **own temporary folder**, created and deleted around
 * it, so nothing leaks between tests or touches the developer's real animation library — that is
 * `withLottieFolder`, which along with `lottieJson`, `NOT_LOTTIE_JSON` and `awaitFolderScan` lives
 * in `:lower-third-tab`'s test fixtures because the server tab's suite needs the same ones.
 */
@OptIn(ExperimentalTestApi::class)
internal fun lowerThirdTab(
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
