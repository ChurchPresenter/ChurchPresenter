package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.window.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.churchpresenter.settings.SettingsManager
import org.churchpresenter.sharedui.models.Tabs
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SignageTabInputTest {

    private val scope = CoroutineScope(SupervisorJob())
    private val settingsDir: File = Files.createTempDirectory("signage-tab-input").toFile()
    private val root = AppRootState(
        object : ApplicationScope {
            override fun exitApplication() = Unit
        },
        scope,
        secondaryDisplays = { emptyList() },
        SettingsManager(settingsDir),
    )

    @AfterTest
    fun cleanUp() {
        root.signageController?.stop()
        scope.cancel()
        SettingsManager.flushAllSaves()
        settingsDir.deleteRecursively()
    }

    @Test
    fun `a SmugMug key typed on the Signage tab is kept and saved`() = runComposeUiTest {
        root.appSettings = root.appSettings.copy(hiddenTabs = root.appSettings.hiddenTabs - Tabs.SIGNAGE.name)
        var input: SignageTabInput? = null
        setContent {
            root.SignageEffect()
            input = root.signageTabInput()
        }
        waitUntil(timeoutMillis = 5_000) { input != null }

        input?.onSmugMugApiKey?.invoke("smug-key")
        waitUntil(timeoutMillis = 5_000) {
            input?.smugMugApiKey == "smug-key" && root.signageSmugMugKey.value == "smug-key"
        }

        assertEquals("smug-key", root.appSettings.smugMugApiKey)
        assertEquals("smug-key", root.signageSmugMugKey.value, "and the controller is handed it")
        SettingsManager.flushAllSaves()
        assertEquals("smug-key", SettingsManager(settingsDir).loadSettings().smugMugApiKey)
    }
}
