package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
    var expanded = rememberSaveable { mutableStateOf(false) }

    Box {
        OutlinedTextField(
            modifier = modifier,
            interactionSource = remember { MutableInteractionSource() }
                .also { interactionSource ->
                    LaunchedEffect(interactionSource) {
                        interactionSource.interactions.collect {
                            if (it is PressInteraction.Release) {
                                expanded.value = true
                            }
                        }
                    }
                },
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
            containerColor = Color.White,
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item) },
                    onClick = {
                        onSelectedChange(item)
                        expanded.value = false
                    }
                )
            }
        }
    }
}