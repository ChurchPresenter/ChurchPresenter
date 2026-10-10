@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsRowKitTest {

    @Test
    fun `a group draws its header, footer, action and rows, and an advanced one is left out in Basic`() =
        runComposeUiTest {
            var detail by mutableStateOf(SettingsDetail.ADVANCED)
            var query by mutableStateOf("")
            var tick by mutableIntStateOf(0)
            var actions = 0
            setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalSettingsDetail provides detail, LocalSettingsQuery provides query) {
                        Column(Modifier.testTag("page_$tick")) {
                            SettingsGroup(
                                caption = "Look",
                                key = "look",
                                modifier = Modifier.testTag("group"),
                                advanced = true,
                                action = { GroupCaptionAction("Reset", onClick = { actions++ }, Icons.Filled.Delete, Modifier.testTag("reset")) },
                                header = { Text("the header") },
                                footer = { Text("the footer") },
                                paths = listOf("bibleSettings.textColor"),
                                summary = { "folded summary" },
                            ) {
                                SettingsRow(
                                    label = "Shadow",
                                    modifier = Modifier.testTag("shadow"),
                                    sub = "behind the letters",
                                    advanced = false,
                                    searchTerms = "drop",
                                    leading = { Text("lead") },
                                    paths = listOf("bibleSettings.shadow"),
                                ) { Text("control") }
                                SettingsSwitchRow(
                                    label = "Bold",
                                    checked = true,
                                    onCheckedChange = {},
                                    modifier = Modifier.testTag("bold"),
                                    sub = "heavier",
                                    advanced = true,
                                    leading = { Text("dot") },
                                    paths = listOf("bibleSettings.bold"),
                                    extra = { Text("extra") },
                                )
                                SettingsWideRow(modifier = Modifier.testTag("wide"), advanced = true, searchTerms = "wide note") {
                                    Text("a wide note")
                                }
                            }
                            SettingsGroup(caption = "Plain", key = "plain") {
                                SettingsSwitchRow("Italic", checked = false, onCheckedChange = {})
                                SettingsWideRow { Text("plain note") }
                            }
                        }
                    }
                }
            }
            listOf("the header", "the footer", "behind the letters", "lead", "heavier", "dot", "extra", "a wide note")
                .forEach { onNodeWithText(it).assertExists() }
            onNodeWithTag("reset").performClick()
            assertEquals(1, actions)
            tick++
            waitForIdle()
            onNodeWithText("the header").assertExists()

            query = "drop"
            waitForIdle()
            onNodeWithText("Shadow").assertExists()
            onNodeWithText("Bold").assertDoesNotExist()
            onNodeWithText("a wide note").assertDoesNotExist()
            onNodeWithText("Italic").assertDoesNotExist()

            query = "wide"
            waitForIdle()
            onNodeWithText("a wide note").assertExists()
            onNodeWithText("Shadow").assertDoesNotExist()

            query = ""
            detail = SettingsDetail.BASIC
            waitForIdle()
            onNodeWithText("the header").assertDoesNotExist()
            onNodeWithText("Shadow").assertDoesNotExist()
            onNodeWithText("Italic").assertExists()
            onNodeWithText("plain note").assertExists()
        }

    @Test
    fun `an advanced row inside a plain group is left out in Basic`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalSettingsDetail provides SettingsDetail.BASIC) {
                    Column {
                        SettingsGroup(caption = "Plain", key = "plain") {
                            SettingsRow("Shown", advanced = false) { Text("a") }
                            SettingsRow("Hidden", advanced = true) { Text("b") }
                            SettingsWideRow(advanced = true) { Text("hidden note") }
                        }
                    }
                }
            }
        }
        onNodeWithText("Shown").assertExists()
        onNodeWithText("Hidden").assertDoesNotExist()
        onNodeWithText("hidden note").assertDoesNotExist()
    }
}
