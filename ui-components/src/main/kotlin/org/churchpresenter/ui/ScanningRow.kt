package org.churchpresenter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Tags every one of these rows, so a test can wait for "no scan is still running" in one place
 * rather than per-caller by the label each one happens to show.
 */
const val SCANNING_ROW_TAG = "scanningRow"

/**
 * Spinner plus label, sized to sit inline with the body-small text it replaces.
 *
 * Stands in for a list that is still being read off disk — the System settings tab's detected-files
 * lines and the Bible tab's translation picker both show it. Saying "scanning" rather than showing
 * an empty list matters: an empty list reads as a verdict about the folder, and someone who has just
 * picked the right one would be told it holds nothing.
 *
 * `internal` so it can be composed directly in a test: the state it represents is transient by
 * nature, and there is no seam to hold a real scan open long enough to catch it on screen without
 * racing it.
 */
@Composable
fun ScanningRow(scanningText: String) {
    Row(
        modifier = Modifier.testTag(SCANNING_ROW_TAG).padding(top = 4.dp, start = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            strokeWidth = 1.5.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = scanningText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
