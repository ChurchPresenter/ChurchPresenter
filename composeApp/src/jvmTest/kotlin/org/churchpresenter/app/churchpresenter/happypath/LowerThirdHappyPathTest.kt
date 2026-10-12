@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.happypath

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasText
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.StreamingSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.models.Tabs
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

private val LOWER_THIRDS = listOf("Guest Speaker", "Welcome")

class LowerThirdHappyPathTest : HappyPathHarness() {

    private fun library(): AppSettings {
        val folder = File(dir, "Lower Thirds").apply { mkdirs() }
        LOWER_THIRDS.forEach { name ->
            val json = checkNotNull(javaClass.getResource("/app-preview/lower-thirds/$name.json")).readText()
            File(folder, "$name.json").writeText(json)
        }
        return settings(Tabs.LOWER_THIRD)
            .copy(streamingSettings = StreamingSettings(lowerThirdFolder = folder.absolutePath))
    }

    private fun ComposeUiTest.listed(name: String): Boolean =
        onAllNodes(hasText(name, substring = true)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    private fun ComposeUiTest.awaitLowerThird(app: HappyApp) {
        await("the lower third is on air") {
            app.presenter.isLive(Presenting.LOWER_THIRD) && app.presenter.lottieJsonContent.value.isNotEmpty()
        }
    }

    @Test
    fun `every lottie file in the folder is listed`() = happyPath(library()) { _ ->
        await("both lower thirds are listed") { LOWER_THIRDS.all { listed(it) } }
    }

    @Test
    fun `a lower third goes live`() = happyPath(library()) { app ->
        await("the list is loaded") { listed("Guest Speaker") }
        clickText("Guest Speaker")
        goLive()

        awaitLowerThird(app)
    }

    @Test
    fun `clearing takes the lower third down`() = happyPath(library()) { app ->
        await("the list is loaded") { listed("Guest Speaker") }
        clickText("Guest Speaker")
        goLive()
        awaitLowerThird(app)

        press(Key.Escape)

        await("nothing is on air") { !app.presenter.isLive(Presenting.LOWER_THIRD) }
    }

    @Test
    fun `a lower third added to the schedule becomes a row`() = happyPath(library()) { app ->
        await("the list is loaded") { listed("Welcome") }
        clickText("Welcome")
        clickTabControl("Add to Schedule")

        await("the schedule holds the lower third") { scheduleShows("Welcome") }
        assertTrue(app.schedule().isNotEmpty())
    }
}
