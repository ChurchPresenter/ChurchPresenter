@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.churchpresenter.calendar.CalendarHost
import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.ui.CalendarApp
import org.churchpresenter.theme.ChurchPresenterTheme
import java.io.File
import java.nio.file.Files
import kotlin.test.Test

class CalProbeTest {
    @Test
    fun probe() = runComposeUiTest {
        val folder = Files.createTempDirectory("calprobe").toFile()
        File(folder, "calendar.json").writeText(Json { encodeDefaults = true }.encodeToString(CalendarDocument.serializer(), month()))
        setContent { ChurchPresenterTheme { Box(Modifier.size(1300.dp, 850.dp)) {
            CalendarApp(storeFolder = folder, songFolder = null, host = CalendarHost(), today = TODAY, now = { NOW }, watchStoreFolder = false, io = Dispatchers.Unconfined)
        } } }
        waitForIdle()
        val out = StringBuilder()
        fun roots() = onAllNodes(isRoot()).fetchSemanticsNodes().size
        out.append("start roots=${roots()}\n")
        onAllNodes(hasText("Add song, verse or section"))[0].performClick(); waitForIdle()
        out.append("picker roots=${roots()}\n")
        val r = onAllNodes(isRoot()); r[r.fetchSemanticsNodes().size - 1].performKeyInput { pressKey(Key.Escape) }; waitForIdle()
        out.append("after esc roots=${roots()}\n")
        val ann = onAllNodes(hasText("Sunday announcements", substring = true)).fetchSemanticsNodes()
        out.append("ann matches=${ann.size} ${ann.map { it.boundsInWindow }}\n")
        onAllNodes(hasText("Sunday announcements", substring = true))[0].performClick(); waitForIdle()
        out.append("after row click roots=${roots()} starts=${onAllNodes(hasText("Starts", ignoreCase = true)).fetchSemanticsNodes().size}\n")
        File(System.getenv("CP_DEMO_OUT"), "calprobe.txt").writeText(out.toString())
    }
}
