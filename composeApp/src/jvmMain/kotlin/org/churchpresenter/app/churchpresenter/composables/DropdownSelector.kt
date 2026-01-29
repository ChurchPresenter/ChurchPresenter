package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun DropdownSelector(
    label: String,
    items: List<String>,
    selected: String,
    onSelectedChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box {
        OutlinedTextField(
            modifier = modifier.clickable { expanded = true },
            value = selected,
            onValueChange = { /* read-only */ },
            readOnly = true,
            label = { Text(text = label) },
            trailingIcon = { Text("▾") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors().copy(
                unfocusedContainerColor = Color.White,
                focusedContainerColor = Color.White,
            )
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(text = { Text(item) }, onClick = {
                    onSelectedChange(item)
                    expanded = false
                })
            }
        }
    }
}