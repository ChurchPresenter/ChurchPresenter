@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.app.churchpresenter.viewmodel.ScheduleOpenFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A schedule file Open could not use is named in a dialog -- CHURCH-PRESENTER-DESKTOP-9N was a web
 * page saved under a schedule's name -- and OK puts it away, leaving the Schedule as it was.
 */
class ScheduleOpenFailedDialogTest {

    @Test
    fun `a file that is not a schedule is named, and OK puts the dialog away`() =
        scheduleTab(seed = { seedService() }) { vm, _ ->
            val before = vm.scheduleItems.toList()
            vm.openFailure = ScheduleOpenFailure("Sunday.cps", unreadable = false)
            waitForIdle()

            onNodeWithText("Sunday.cps isn't a ChurchPresenter schedule", substring = true).assertExists()
            onNodeWithText("OK").performClick()
            waitForIdle()

            assertNull(vm.openFailure)
            onNodeWithText("Couldn't open the schedule").assertDoesNotExist()
            assertEquals(before, vm.scheduleItems.toList())
        }

    @Test
    fun `a file that cannot be read says so`() = scheduleTab(seed = { seedService() }) { vm, _ ->
        vm.openFailure = ScheduleOpenFailure("Locked.cps", unreadable = true)
        waitForIdle()

        onNodeWithText("Locked.cps could not be read", substring = true).assertExists()
    }
}
