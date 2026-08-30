@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.FixedViewport
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.ViewportProbe
import org.churchpresenter.app.churchpresenter.data.RemoteClientManager
import org.churchpresenter.settings.SettingsManager
import org.churchpresenter.app.churchpresenter.horizontalOverflow
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The settings tabs, checked for content that runs off the side.
 *
 * The dialog is resizable and every tab scrolls vertically, but **nothing scrolls it sideways** —
 * only `CompanionSatelliteSettingsTab` has a `horizontalScroll` of its own. A row too wide for the
 * dialog therefore has no recourse, in the same way the fixed-height dialogs in
 * `DialogViewportTest` have none downwards.
 *
 * Unlike vertical overflow, this one is visible in the layout: a `Column` given a `maxHeight`
 * squeezes its children to fit, but a `Row` does not shrink unweighted children — it places them
 * past the edge. So the containment check that is vacuous for height is the correct one here.
 *
 * **This currently finds nothing, and that is the result.** Every tab was measured at 400, 500, 600
 * and 700dp and none overflowed at any of them, so the concern that these tabs clip laterally when
 * the dialog is narrowed does not reproduce. The test is kept as the regression guard for that,
 * pinned at the narrowest width worth supporting rather than at the widths it was explored with.
 */
class SettingsTabWidthTest {

    /**
     * Narrow enough to be a real constraint, wide enough to be a size anyone would use. The dialog
     * now opens at most `screen - 48dp` wide, so the small end of that is a laptop panel, and a user
     * dragging it narrower than this is past what any layout here promises.
     */
    private val narrow = 700.dp

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        // As in OptionsContentTest: pin the JVM-wide log path before swapping user.home, because
        // PresenterManager and CompanionServer log through InstanceLinkLogger, which keeps whatever
        // user.home pointed at the first time anything logged.
        TestSingletons.latchToTestHome()
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-tab-width-test").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    // Built once and shared across the ten compositions. Constructing them per tab is most of
    // what this test costs, and none of them carry state that one tab's layout could affect.
    private val settingsManager by lazy { SettingsManager() }
    private val companionServer by lazy { CompanionServer() }
    private val remoteClientManager by lazy { RemoteClientManager() }
    private val presenterManager by lazy { PresenterManager() }

    private fun overflowOfTab(tab: Int): Float {
        var overflow = 0f
        runComposeUiTest {
            val probe = ViewportProbe()
            setContent {
                FixedViewport(narrow, 700.dp, probe) {
                    OptionsDialogContent(
                        theme = ThemeMode.LIGHT,
                        settingsManager = settingsManager,
                        companionServer = companionServer,
                        remoteClientManager = remoteClientManager,
                        presenterManager = presenterManager,
                        onDismiss = {},
                        initialTab = tab,
                        detectScreens = { emptyList() },
                    )
                }
            }
            overflow = horizontalOverflow(probe).value
        }
        return overflow
    }

    /**
     * One tab per test rather than a loop over all ten: each composition of the settings dialog
     * costs about a quarter of a second, so the loop version ran to 2.8s — over the bar a unit test
     * here is held to — and named only "some tab" when it failed.
     */
    private fun assertTabFits(tab: Int, name: String) {
        val over = overflowOfTab(tab)
        assertTrue(
            over <= 1f,
            "at ${narrow.value.toInt()}dp wide the $name tab draws ${over.toInt()}dp past the edge, " +
                "and nothing scrolls it back",
        )
    }

    @Test fun `the Appearance tab fits a narrowed dialog`() = assertTabFits(0, "Appearance")

    @Test fun `the Bible tab fits a narrowed dialog`() = assertTabFits(1, "Bible")

    @Test fun `the Song tab fits a narrowed dialog`() = assertTabFits(2, "Song")

    @Test fun `the Background tab fits a narrowed dialog`() = assertTabFits(3, "Background")

    @Test fun `the Projection tab fits a narrowed dialog`() = assertTabFits(4, "Projection")

    @Test fun `the Server tab fits a narrowed dialog`() = assertTabFits(5, "Server")

    @Test fun `the Stage Monitor tab fits a narrowed dialog`() = assertTabFits(6, "Stage Monitor")

    @Test fun `the ATEM tab fits a narrowed dialog`() = assertTabFits(7, "ATEM")

    @Test fun `the Dictionary tab fits a narrowed dialog`() = assertTabFits(8, "Dictionary")

    @Test
    fun `the Companion Satellite tab fits a narrowed dialog`() = assertTabFits(9, "Companion Satellite")
}
