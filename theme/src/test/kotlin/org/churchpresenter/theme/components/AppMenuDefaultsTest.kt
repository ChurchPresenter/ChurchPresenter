package org.churchpresenter.theme.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.AppShape
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3Api::class)
class AppMenuDefaultsTest {

    @Test
    fun `menus are rounded, lifted and outlined in the scheme's outline variant`() = runComposeUiTest {
        val outline = Color(0xFF123456)
        var border: SolidColor? = null
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(outlineVariant = outline)) {
                border = AppMenuDefaults.border.brush as SolidColor
            }
        }
        waitForIdle()
        assertEquals(outline, border?.value)
        assertEquals(AppShape(14.dp), AppMenuDefaults.shape)
        assertEquals(12.dp, AppMenuDefaults.shadowElevation)
    }

    @Test
    fun `an open dropdown shows its items and a closed one does not`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                AppDropdownMenu(expanded = true, onDismissRequest = {}) { Text("Open item") }
                AppDropdownMenu(expanded = false, onDismissRequest = {}) { Text("Closed item") }
            }
        }
        onNodeWithText("Open item").assertExists()
        onNodeWithText("Closed item").assertDoesNotExist()
    }

    @Test
    fun `an open exposed dropdown shows its items`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ExposedDropdownMenuBox(expanded = true, onExpandedChange = {}) {
                    Text("Anchor", Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable))
                    AppExposedDropdownMenu(expanded = true, onDismissRequest = {}) { Text("Exposed item") }
                }
            }
        }
        onNodeWithText("Exposed item").assertExists()
    }

    @Test
    fun `every setting can be given and changed while open`() = runComposeUiTest {
        var step by mutableIntStateOf(0)
        setContent {
            MaterialTheme {
                AppDropdownMenu(
                    expanded = true,
                    onDismissRequest = { step + 0 },
                    modifier = if (step == 0) Modifier else Modifier.then(Modifier),
                    properties = PopupProperties(focusable = step == 0),
                    containerColor = if (step == 0) Color.Red else Color.Blue,
                ) { Text("Menu $step") }
                ExposedDropdownMenuBox(expanded = true, onExpandedChange = {}) {
                    Text("Anchor", Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable))
                    AppExposedDropdownMenu(
                        expanded = true,
                        onDismissRequest = { step + 0 },
                        modifier = if (step == 0) Modifier else Modifier.then(Modifier),
                        matchAnchorWidth = step == 0,
                    ) { Text("Exposed $step") }
                }
            }
        }
        onNodeWithText("Menu 0").assertExists()
        step = 1
        waitForIdle()
        onNodeWithText("Menu 1").assertExists()
        onNodeWithText("Exposed 1").assertExists()
        step = 2
        waitForIdle()
        onNodeWithText("Menu 2").assertExists()
    }

    @Test
    fun `an open menu stays open when what is around it recomposes`() = runComposeUiTest {
        var tick by mutableIntStateOf(0)
        val dismiss = {}
        setContent {
            MaterialTheme {
                Text("Tick $tick")
                AppDropdownMenu(expanded = true, onDismissRequest = dismiss) { Text("Steady item") }
                ExposedDropdownMenuBox(expanded = true, onExpandedChange = {}) {
                    Text("Tick $tick", Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable))
                    AppExposedDropdownMenu(expanded = true, onDismissRequest = dismiss) { Text("Steady exposed") }
                }
            }
        }
        tick = 1
        waitForIdle()
        onNodeWithText("Steady item").assertExists()
        onNodeWithText("Steady exposed").assertExists()
    }

    @Test
    fun `an open menu follows a theme switch`() = runComposeUiTest {
        var dark by mutableIntStateOf(0)
        setContent {
            MaterialTheme(colorScheme = if (dark == 1) darkColorScheme() else lightColorScheme()) {
                AppDropdownMenu(expanded = true, onDismissRequest = {}) { Text("Themed item") }
            }
        }
        dark = 1
        waitForIdle()
        onNodeWithText("Themed item").assertExists()
    }
}
