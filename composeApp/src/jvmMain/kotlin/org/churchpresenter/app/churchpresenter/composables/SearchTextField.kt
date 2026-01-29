package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp

@Composable
fun SearchTextField(
    modifier: Modifier = Modifier,
    initialText: String = "",
    label: String,
    onValueChange: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    OutlinedTextField(
        maxLines = 1,
        textStyle = MaterialTheme.typography.bodyMedium,
        label = { Text(text = label) },
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 36.dp)
            .padding(bottom = 4.dp),
        value = text,
        onValueChange = {
            text = it
            onValueChange.invoke(it)
        },
        colors = OutlinedTextFieldDefaults.colors().copy(
            unfocusedContainerColor = Color.White,
            focusedContainerColor = Color.White,
        )
    )
}