@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.converter.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.converter.song.SongFormatConverter
import java.io.File
import java.nio.file.Files

internal class FakePickers(
    var files: List<File> = emptyList(),
    var directory: File? = null,
) : ConverterPickers {
    val requestedExtensions = mutableListOf<String>()
    val requestedSources = mutableListOf<String>()

    override fun files(description: String, vararg extensions: String, multiSelection: Boolean): List<File> {
        requestedExtensions += extensions.joinToString(",")
        return files
    }

    override fun directory(): File? = directory

    override fun sourceFiles(source: SongSource, format: SongFormatConverter): List<File> {
        requestedSources += source.id
        return files
    }
}

internal fun ComposeUiTest.setConverterContent(pickers: ConverterPickers, content: @Composable () -> Unit) {
    setContent {
        ConverterTheme {
            CompositionLocalProvider(LocalConverterPickers provides pickers) { content() }
        }
    }
}

internal fun ComposeUiTest.isShowing(text: String, substring: Boolean = false): Boolean =
    onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

internal fun ComposeUiTest.click(text: String, substring: Boolean = false) {
    val node = onAllNodesWithText(text, substring = substring).onFirst()
    runCatching { node.performScrollTo() }
    node.performClick()
    waitForIdle()
}

internal fun ComposeUiTest.awaitShowing(text: String, substring: Boolean = false) {
    waitUntil(timeoutMillis = UI_TIMEOUT_MS) { isShowing(text, substring) }
}

internal const val UI_TIMEOUT_MS = 5_000L

internal fun withTempDir(prefix: String, body: (File) -> Unit) {
    val dir = Files.createTempDirectory(prefix).toFile()
    try {
        body(dir)
    } finally {
        dir.deleteRecursively()
    }
}

internal fun sng(dir: File, name: String, title: String, vararg lines: String): File =
    File(dir, name).apply {
        parentFile.mkdirs()
        writeText("#Title=$title\n---\nVerse 1\n${lines.joinToString("\n")}\n", Charsets.UTF_8)
    }

internal fun ComposeUiTest.scrollAndClick(text: String) {
    onAllNodesWithText(text).onFirst().performScrollTo().performClick()
    waitForIdle()
}

internal fun ComposeUiTest.onEdt(action: ComposeUiTest.() -> Unit) {
    javax.swing.SwingUtilities.invokeAndWait { action() }
    waitForIdle()
}

internal fun ComposeUiTest.awaitInWindow(text: String, substring: Boolean = false) {
    val deadline = System.nanoTime() + UI_TIMEOUT_MS * 1_000_000
    while (!isShowing(text, substring)) {
        check(System.nanoTime() < deadline) { "\"$text\" never appeared" }
        Thread.yield()
    }
}
