package org.churchpresenter.theme.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import org.churchpresenter.theme.AppShape

object AppMenuDefaults {
    val shape: Shape = AppShape(14.dp)
    val shadowElevation = 12.dp

    val border: BorderStroke
        @Composable
        @ReadOnlyComposable
        get() = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    containerColor: Color = MenuDefaults.containerColor,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        scrollState = scrollState,
        properties = properties,
        shape = AppMenuDefaults.shape,
        containerColor = containerColor,
        shadowElevation = AppMenuDefaults.shadowElevation,
        border = AppMenuDefaults.border,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExposedDropdownMenuBoxScope.AppExposedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    matchAnchorWidth: Boolean = true,
    containerColor: Color = MenuDefaults.containerColor,
    content: @Composable ColumnScope.() -> Unit,
) {
    ExposedDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        matchAnchorWidth = matchAnchorWidth,
        shape = AppMenuDefaults.shape,
        containerColor = containerColor,
        shadowElevation = AppMenuDefaults.shadowElevation,
        border = AppMenuDefaults.border,
        content = content,
    )
}
