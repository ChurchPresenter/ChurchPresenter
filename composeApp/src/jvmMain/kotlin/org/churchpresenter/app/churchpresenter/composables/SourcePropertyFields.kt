package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.SourceTransform

@Composable
internal fun PropertyTextField(label: String, value: String, modifier: Modifier = Modifier, onValueChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    StyledTextField(
        value = text,
        onValueChange = {
            text = it
            onValueChange(it)
        },
        label = label,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
internal fun PropertyFloatField(label: String, value: Float, modifier: Modifier = Modifier, onValueChange: (Float) -> Unit) {
    var text by remember(value) { mutableStateOf("%.3f".format(value)) }
    var hasFocus by remember { mutableStateOf(false) }
    StyledTextField(
        value = text,
        onValueChange = { text = it },
        label = label,
        modifier = modifier.onFocusChanged { state ->
            if (hasFocus && !state.isFocused) {
                text.toFloatOrNull()?.let(onValueChange)
                    ?: run { text = "%.3f".format(value) }
            }
            hasFocus = state.isFocused
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            text.toFloatOrNull()?.let(onValueChange)
                ?: run { text = "%.3f".format(value) }
        })
    )
}

@Composable
internal fun PropertySlider(label: String, value: Float, min: Float, max: Float, onValueChange: (Float) -> Unit) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SlimSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = min..max,
            trailingLabel = if (min == 0f && max == 1f) "${(value * 100).toInt()}%" else "%.2f".format(value),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun PropertySliderWithInput(label: String, value: Float, min: Float, max: Float, suffix: String = "", onValueChange: (Float) -> Unit) {
    var textValue by remember(value) { mutableStateOf(value.toInt().toString()) }
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SlimSlider(
                value = value.coerceIn(min, max),
                onValueChange = {
                    textValue = it.toInt().toString()
                    onValueChange(it)
                },
                valueRange = min..max,
                modifier = Modifier.weight(1f)
            )
            var hasFocus by remember { mutableStateOf(false) }
            val commitValue = {
                textValue.toFloatOrNull()?.let { onValueChange(it.coerceIn(min, max)) }
                    ?: run { textValue = value.toInt().toString() }
            }
            StyledTextField(
                value = textValue,
                onValueChange = { textValue = it },
                modifier = Modifier.width(60.dp).onFocusChanged { state ->
                    if (hasFocus && !state.isFocused) commitValue()
                    hasFocus = state.isFocused
                },
                trailingIcon = if (suffix.isNotEmpty()) { {
                    Text(suffix, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.padding(end = 6.dp))
                } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commitValue() })
            )
        }
    }
}

internal fun updateName(source: SceneSource, name: String): SceneSource = when (source) {
    is SceneSource.ImageSource -> source.copy(name = name)
    is SceneSource.TextSource -> source.copy(name = name)
    is SceneSource.ColorSource -> source.copy(name = name)
    is SceneSource.VideoSource -> source.copy(name = name)
    is SceneSource.BrowserSource -> source.copy(name = name)
    is SceneSource.ShapeSource -> source.copy(name = name)
    is SceneSource.ClockSource -> source.copy(name = name)
    is SceneSource.QRCodeSource -> source.copy(name = name)
    is SceneSource.CameraSource -> source.copy(name = name)
    is SceneSource.ScreenCaptureSource -> source.copy(name = name)
    is SceneSource.BibleSource -> source.copy(name = name)
}

internal fun updateTransform(source: SceneSource, transform: SourceTransform): SceneSource = when (source) {
    is SceneSource.ImageSource -> source.copy(transform = transform)
    is SceneSource.TextSource -> source.copy(transform = transform)
    is SceneSource.ColorSource -> source.copy(transform = transform)
    is SceneSource.VideoSource -> source.copy(transform = transform)
    is SceneSource.BrowserSource -> source.copy(transform = transform)
    is SceneSource.ShapeSource -> source.copy(transform = transform)
    is SceneSource.ClockSource -> source.copy(transform = transform)
    is SceneSource.QRCodeSource -> source.copy(transform = transform)
    is SceneSource.CameraSource -> source.copy(transform = transform)
    is SceneSource.ScreenCaptureSource -> source.copy(transform = transform)
    is SceneSource.BibleSource -> source.copy(transform = transform)
}

