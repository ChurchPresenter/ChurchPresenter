package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.window.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SettingsManager
import org.churchpresenter.settings.StartupPromptPacing
import org.churchpresenter.settings.StoryPromptState
import org.churchpresenter.settings.utils.AppDataDir
import org.churchpresenter.updater.UpdateCheckResult
import org.churchpresenter.updater.UpdateInfo
import java.io.File
import java.util.concurrent.Executor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The dialogs a new install meets at startup: one a start at most, and a quiet start after it.
 *
 * Each start is a fresh [AppRootState] over the settings the last one saved, run through the real
 * startup sequence, after which every dialog flag the app has is read — so a dialog added later that
 * opens on its own without going through [claimStartupPrompt] fails here, not only one that does.
 * Runs in dev mode, where Wick's intro is due on its own too.
 */
class StartupPromptsTest {

    // Launched work is never run: nothing here reaches the network.
    private val scope = CoroutineScope(SupervisorJob() + Executor { }.asCoroutineDispatcher())
    private val settingsFile = File(AppDataDir.resolve(), "settings.json")
    private var savedBefore: ByteArray? = null

    private val application = object : ApplicationScope {
        override fun exitApplication() = Unit
    }

    @BeforeTest
    fun setUp() {
        TestSingletons.latchSkikoHostOs()
        TestSingletons.latchToTestHome()
        savedBefore = settingsFile.takeIf { it.exists() }?.readBytes()
        settingsFile.delete()
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        savedBefore?.let { settingsFile.writeBytes(it) } ?: settingsFile.delete()
    }

    /** One start: the app comes up, its startup prompts run, and what is left open is read. */
    private fun start(update: UpdateCheckResult = UpdateCheckResult.UpToDate): AppRootState {
        val root = AppRootState(application, scope, secondaryDisplays = { emptyList() }, SettingsManager())
        root.beginStartupPrompts()
        root.appReady = true
        runBlocking { root.openStartupPrompts(checkForUpdate = { update }, storyDelayMs = 0) }
        return root
    }

    /** Every dialog up: each `show…` flag the app state has, and the two that are not flags. The licence is apart. */
    private fun AppRootState.openDialogs(): List<String> {
        val flags = AppRootState::class.java.methods
            .filter { it.name.startsWith("getShow") && it.parameterCount == 0 }
            .filter { it.returnType == Boolean::class.javaPrimitiveType && it.invoke(this) == true }
            .map { it.name.removePrefix("get") }
        // The wizard waits behind the licence; what counts is that it is due.
        return flags + listOfNotNull(
            "update".takeIf { pendingUpdateResult != null },
            "wickIntro".takeIf { wickIntroShowing },
        )
    }

    /** The operator closes everything this start opened, and the app saves what that leaves. */
    private fun AppRootState.closeEverything() {
        appSettings = appSettings.copy(
            eulaAcceptedVersion = CURRENT_EULA_VERSION,
            setupWizardShown = appSettings.setupWizardShown || showSetupWizard,
            helper = if (wickIntroShowing) appSettings.helper.copy(introSeen = true) else appSettings.helper,
        )
        showSetupWizard = false
        pendingUpdateResult = null
        showStoryPrompt = false
        settingsManager.saveSettings(appSettings)
    }

    /** Starts once per [updates] entry, each answered with it, closing what each start opened before the next. */
    private fun launchesAnswering(updates: List<UpdateCheckResult>): List<List<String>> =
        updates.map { update -> start(update).run { openDialogs().also { closeEverything() } } }

    private fun launches(count: Int): List<List<String>> = launchesAnswering(List(count) { UpdateCheckResult.UpToDate })

    @Test
    fun `no start of a new install opens more than one dialog unasked`() {
        launches(8).forEachIndexed { index, open ->
            assertTrue(open.size <= 1, "start ${index + 1} opened $open")
        }
    }

    @Test
    fun `a new install meets its dialogs every other start`() {
        assertEquals(
            listOf(
                listOf("ShowSetupWizard"), emptyList(), listOf("update"), emptyList(), listOf("wickIntro"), emptyList(),
            ),
            launches(6),
        )
    }

    @Test
    fun `the licence and the wizard are the first start's, and the first update check waits`() {
        var checks = 0
        val root = AppRootState(application, scope, secondaryDisplays = { emptyList() }, SettingsManager())
        root.beginStartupPrompts()
        root.appReady = true
        runBlocking { root.openStartupPrompts(checkForUpdate = { checks++; UpdateCheckResult.UpToDate }) }

        assertEquals(false, root.eulaAccepted)
        assertEquals(true, root.showSetupWizard)
        assertEquals(0, checks, "the first check ever waits for a free start, so it stays the first")
        assertEquals(0L, root.appSettings.lastUpdateCheckTimestamp)
    }

    @Test
    fun `an available update shows on a start the pacing keeps quiet, and takes it`() {
        // Past its first update check, and the start before this one showed a dialog.
        SettingsManager().saveSettings(
            AppSettings(
                eulaAcceptedVersion = CURRENT_EULA_VERSION,
                setupWizardShown = true,
                lastUpdateCheckTimestamp = 1L,
                startupPrompts = StartupPromptPacing(launches = 1, lastPromptLaunch = 1),
            ),
        )
        val available = UpdateCheckResult.Available(UpdateInfo("99.0.0", releaseUrl = "", releaseNotes = ""))

        val opened = launchesAnswering(listOf(available, UpdateCheckResult.UpToDate, UpdateCheckResult.UpToDate))

        assertEquals(listOf(listOf("update"), emptyList(), listOf("wickIntro")), opened)
    }

    @Test
    fun `a due story prompt waits for a start no other dialog took`() {
        val now = System.currentTimeMillis()
        val week = now / WEEK_MS
        // Set up as a month-old install used every week, past its first-run dialogs.
        SettingsManager().saveSettings(
            AppSettings(
                eulaAcceptedVersion = CURRENT_EULA_VERSION,
                setupWizardShown = true,
                lastUpdateCheckTimestamp = now,
                storyPrompt = StoryPromptState(
                    installedAtMillis = now - 2 * MONTH_MS,
                    activeWeeks = (0L until 4L).map { week - it }.toSet(),
                ),
            ),
        )

        val opened = launches(4)

        assertEquals(listOf("wickIntro"), opened[0], "Wick's intro takes the first free start")
        assertEquals(emptyList(), opened[1])
        assertEquals(listOf("ShowStoryPrompt"), opened[2])
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
        const val WEEK_MS = 7 * DAY_MS
        const val MONTH_MS = 30 * DAY_MS
    }
}
