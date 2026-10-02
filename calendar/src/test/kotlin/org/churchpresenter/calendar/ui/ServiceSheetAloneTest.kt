@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.theme.AppThemeWrapper
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The service sheet on its own, given no templates and none of its optional arguments: a new
 * service with nothing to start from, and an existing one whose stored date cannot be read.
 */
class ServiceSheetAloneTest {

    private fun ComposeUiTest.sheet(existing: PlannedService?, saved: MutableList<ServiceForm>) {
        setContent {
            AppThemeWrapper(theme = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalUse24HourClock provides true) {
                    ServiceSheet(
                        existing = existing,
                        defaultStartTime = "10:30",
                        date = TODAY,
                        seriesSize = 0,
                        templates = emptyList(),
                        templateLabel = { "" to "" },
                        onSave = { saved += it },
                        onDelete = null,
                        onDismiss = {},
                    )
                }
            }
        }
        waitForIdle()
    }

    @Test
    fun `with nothing to start from there is no start-from list, and the default time is the time`() =
        runComposeUiTest {
            val saved = mutableListOf<ServiceForm>()
            sheet(existing = null, saved = saved)
            assertTrue(!shows("Start from"))

            typeIntoFirstField("Vespers")
            clickLast("Add service")

            val form = saved.single()
            assertEquals("Vespers", form.name)
            assertEquals("10:30", form.startTime)
            assertEquals(TODAY, form.date)
        }

    @Test
    fun `a service whose stored date cannot be read is edited on the day the sheet was opened for`() =
        runComposeUiTest {
            val saved = mutableListOf<ServiceForm>()
            val odd = PlannedService(id = "s", date = "someday", name = "Evensong", startTime = "18:00")
            sheet(existing = odd, saved = saved)
            awaitText("Edit service")

            clickLast("Save")

            assertEquals(TODAY, saved.single().date)
            assertEquals("18:00", saved.single().startTime)
        }
}
