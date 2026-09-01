package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.setup_wizard_next
import churchpresenter.composeapp.generated.resources.setup_wizard_skip
import churchpresenter.composeapp.generated.resources.setup_step_count
import churchpresenter.composeapp.generated.resources.setup_wizard_back
import churchpresenter.composeapp.generated.resources.setup_wizard_step
import churchpresenter.composeapp.generated.resources.setup_wizard_title
import org.jetbrains.compose.resources.stringResource

/*
 * The wizard's chrome: the left step rail and the right panel's header and footer.
 *
 * Held apart from SetupWizardDialog.kt because that file is already the eight steps' content and
 * little else; the shell is shared by all of them and changes for different reasons.
 *
 * Every colour here comes from `MaterialTheme.colorScheme`, never a literal. The wizard is the one
 * window that can be looked at in all ten themes in a row — its own Appearance step invites exactly
 * that — so a hardcoded palette would be visible as a bug within seconds of opening step 2.
 */

private val WIZARD_RAIL_WIDTH = 268.dp
private val RAIL_ROW_SHAPE = RoundedCornerShape(9.dp)
private val PANEL_FOOTER_HEIGHT = 72.dp
private val STEP_MARKER_SIZE = 26.dp
private const val PENDING_ALPHA = 0.45f
private const val SELECTED_ROW_TINT = 0.14f

/** The tag a rail row carries, so a test can press step N without matching on its label. */
internal fun setupRailTag(index: Int): String = "setup_rail_$index"

/** One row of the rail: what the step is called, and what the user has chosen on it so far. */
internal data class WizardRailStep(
    val title: String,
    /** The choice made on this step, shown under its title. Null for steps that store nothing. */
    val chosen: String? = null,
)

/**
 * The left rail: a progress bar, then every step as its own row.
 *
 * Rows are clickable. The old wizard could only be walked with Back and Next, which made revisiting
 * a choice five steps back a matter of pressing Back five times; the rail makes the whole sequence
 * addressable, and doubles as the record of what has been chosen.
 */
@Composable
internal fun WizardRail(
    steps: List<WizardRailStep>,
    currentStep: Int,
    onSelectStep: (Int) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(WIZARD_RAIL_WIDTH)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 20.dp)) {
            Text(
                text = stringResource(Res.string.setup_wizard_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.setup_wizard_step, currentStep + 1, steps.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(14.dp))
            WizardProgressBar(current = currentStep, total = steps.size)
            Spacer(modifier = Modifier.height(16.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            steps.forEachIndexed { index, step ->
                WizardRailRow(
                    index = index,
                    step = step,
                    selected = index == currentStep,
                    complete = index < currentStep,
                    onClick = { onSelectStep(index) },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(PANEL_FOOTER_HEIGHT)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(shape = RoundedCornerShape(6.dp), onClick = onSkip) {
                Text(
                    text = stringResource(Res.string.setup_wizard_skip),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** How far through the wizard the user is, as one continuous bar rather than eight dots. */
@Composable
private fun WizardProgressBar(current: Int, total: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction = (current + 1).toFloat() / total.toFloat())
                .fillMaxHeight()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun WizardRailRow(
    index: Int,
    step: WizardRailStep,
    selected: Boolean,
    complete: Boolean,
    onClick: () -> Unit,
) {
    val titleColor = when {
        selected -> MaterialTheme.colorScheme.onSurface
        complete -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = PENDING_ALPHA)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RAIL_ROW_SHAPE)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_ROW_TINT)
                } else {
                    Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag(setupRailTag(index)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        WizardStepMarker(index = index, selected = selected, complete = complete)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = step.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = titleColor,
            )
            if (step.chosen != null) {
                Text(
                    text = step.chosen,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The numbered disc beside a rail row — a tick once the step is behind the user. */
@Composable
private fun WizardStepMarker(index: Int, selected: Boolean, complete: Boolean) {
    val filled = selected || complete
    Box(
        modifier = Modifier
            .size(STEP_MARKER_SIZE)
            .clip(CircleShape)
            .background(
                if (filled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (complete) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(
                text = "${index + 1}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (filled) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = PENDING_ALPHA)
                },
            )
        }
    }
}

/**
 * The panel's title block: the step's icon, its name and its one-line description.
 *
 * [instructionCount] draws the pill on the right that says how many numbered instructions follow,
 * so a step that is a list announces its own length before it is read. Steps that are not lists
 * pass null.
 */
@Composable
internal fun WizardPanelHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    instructionCount: Int? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(25.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (instructionCount != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(Res.string.setup_step_count, instructionCount),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * The bar under every step: Back on the left, the step's own status in the middle, Continue on the
 * right. [status] is what that step wants to report — a language count, a theme count, whether VLC
 * was found — and is empty on steps with nothing to say.
 */
@Composable
internal fun WizardPanelFooter(
    canGoBack: Boolean,
    isLastStep: Boolean,
    status: String,
    continueLabel: String,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PANEL_FOOTER_HEIGHT)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canGoBack) {
            OutlinedButton(shape = RoundedCornerShape(8.dp), onClick = onBack) {
                Text(stringResource(Res.string.setup_wizard_back))
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        if (status.isNotEmpty()) {
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 14.dp),
            )
        }
        Button(shape = RoundedCornerShape(8.dp), onClick = onContinue) {
            Text(
                text = if (isLastStep) continueLabel else stringResource(Res.string.setup_wizard_next),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** A bordered block that mimics a piece of the real UI the instructions are pointing at. */
@Composable
internal fun WizardMockPanel(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(3.dp).height(13.dp).background(MaterialTheme.colorScheme.primary))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (trailing != null) {
                Spacer(modifier = Modifier.weight(1f))
                trailing()
            }
        }
        Box(modifier = Modifier.fillMaxWidth().padding(12.dp)) { content() }
    }
}
