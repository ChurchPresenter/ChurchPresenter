package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.tabs.Tabs
import org.churchpresenter.app.churchpresenter.tabs.getStringName

@Composable
fun TabVisibilitySettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val visibleCount = Tabs.entries.count { it.name !in settings.hiddenTabs }

        Tabs.entries.forEach { tab ->
            val isVisible = tab.name !in settings.hiddenTabs
            val isOnlyVisible = isVisible && visibleCount == 1

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isVisible,
                    onCheckedChange = { checked ->
                        onSettingsChange { s ->
                            val newHidden = if (checked) {
                                s.hiddenTabs - tab.name
                            } else {
                                s.hiddenTabs + tab.name
                            }
                            s.copy(hiddenTabs = newHidden)
                        }
                    },
                    enabled = !isOnlyVisible
                )
                Text(
                    text = getStringName(tab),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}
