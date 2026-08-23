@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.ui.LabeledCheckbox
import org.churchpresenter.ui.LabeledRadioButton
import org.churchpresenter.ui.LabeledSwitch
import kotlin.test.Test

/**
 * The checkbox / radio button / switch rows where the label is part of the control.
 *
 * Shot here rather than only through the tabs that use them: they are shared by the ATEM upload
 * dialog, the settings tabs and the Q&A panel, so one image per state beats the same row appearing
 * inside a dozen tab screenshots. Each of the three has the same set of knobs — selected, disabled,
 * a quieter supporting line, and the control pushed to the right edge — so each is shot with the
 * same set of states, which is what makes them comparable at a glance.
 */
class LabeledControlsScreenshotTest {

    private fun row(name: String, content: @androidx.compose.runtime.Composable () -> Unit) =
        captureComponent(SECTION, name) { Box(Modifier.width(320.dp)) { content() } }

    // ── Checkbox ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `checkbox off`() = row("checkbox_off") {
        LabeledCheckbox(checked = false, onCheckedChange = {}, label = LABEL)
    }

    @Test
    fun `checkbox on`() = row("checkbox_on") {
        LabeledCheckbox(checked = true, onCheckedChange = {}, label = LABEL)
    }

    @Test
    fun `checkbox disabled`() = row("checkbox_disabled") {
        LabeledCheckbox(checked = true, onCheckedChange = {}, label = LABEL, enabled = false)
    }

    @Test
    fun `checkbox with a supporting line`() = row("checkbox_supporting") {
        LabeledCheckbox(
            checked = true,
            onCheckedChange = {},
            label = LABEL,
            supporting = SUPPORTING,
        )
    }

    @Test
    fun `checkbox with the control at the end`() = row("checkbox_control_at_end") {
        LabeledCheckbox(
            checked = true,
            onCheckedChange = {},
            label = LABEL,
            supporting = SUPPORTING,
            controlAtEnd = true,
        )
    }

    // ── Radio button ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `radio unselected`() = row("radio_unselected") {
        LabeledRadioButton(selected = false, onClick = {}, label = LABEL)
    }

    @Test
    fun `radio selected`() = row("radio_selected") {
        LabeledRadioButton(selected = true, onClick = {}, label = LABEL)
    }

    @Test
    fun `radio disabled`() = row("radio_disabled") {
        LabeledRadioButton(selected = true, onClick = {}, label = LABEL, enabled = false)
    }

    @Test
    fun `radio with a supporting line`() = row("radio_supporting") {
        LabeledRadioButton(selected = true, onClick = {}, label = LABEL, supporting = SUPPORTING)
    }

    @Test
    fun `radio with the control at the end`() = row("radio_control_at_end") {
        LabeledRadioButton(
            selected = true,
            onClick = {},
            label = LABEL,
            supporting = SUPPORTING,
            controlAtEnd = true,
        )
    }

    // ── Switch ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `switch off`() = row("switch_off") {
        LabeledSwitch(checked = false, onCheckedChange = {}, label = LABEL)
    }

    @Test
    fun `switch on`() = row("switch_on") {
        LabeledSwitch(checked = true, onCheckedChange = {}, label = LABEL)
    }

    @Test
    fun `switch disabled`() = row("switch_disabled") {
        LabeledSwitch(checked = true, onCheckedChange = {}, label = LABEL, enabled = false)
    }

    @Test
    fun `switch with a supporting line`() = row("switch_supporting") {
        LabeledSwitch(checked = true, onCheckedChange = {}, label = LABEL, supporting = SUPPORTING)
    }

    @Test
    fun `switch with the control at the end`() = row("switch_control_at_end") {
        LabeledSwitch(
            checked = true,
            onCheckedChange = {},
            label = LABEL,
            supporting = SUPPORTING,
            controlAtEnd = true,
        )
    }

    private companion object {
        const val SECTION = "labeledControls"
        const val LABEL = "Clip (full animation)"
        const val SUPPORTING = "Uploads every frame to the switcher's clip pool"
    }
}
