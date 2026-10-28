package org.churchpresenter.theme.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import org.churchpresenter.theme.AppShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.theme.ElevationPalette
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.hoverTint
import org.churchpresenter.theme.sunken

private val FieldShape = AppShape(8.dp)

@Composable
fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    placeholder: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    fillWidth: Boolean = false,
    /** The fill of the sunken well behind the text; unspecified is the theme's own well. */
    containerColor: Color = Color.Unspecified,
) = SettingsTextFieldBody(
    FieldValue.Plain(value, onValueChange), modifier, label, isError, supportingText, fillWidth, containerColor,
    FieldOptions(
        FieldInput(enabled, readOnly, keyboardOptions, keyboardActions, visualTransformation),
        singleLine, maxLines, placeholder, trailingIcon,
    ),
)

/**
 * [SettingsTextField] holding its own [TextFieldValue], for a caller that places the cursor: a field
 * that starts with text in it and is typed after it, say.
 */
@Composable
fun SettingsTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    enabled: Boolean = true,
    singleLine: Boolean = true,
    placeholder: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    fillWidth: Boolean = false,
) = SettingsTextFieldBody(
    FieldValue.Rich(value, onValueChange), modifier, label, isError, supportingText, fillWidth, Color.Unspecified,
    FieldOptions(
        FieldInput(enabled, false, keyboardOptions, KeyboardActions.Default, VisualTransformation.None),
        singleLine, if (singleLine) 1 else Int.MAX_VALUE, placeholder, null,
    ),
)

/** What a field holds: plain text, or text with its cursor and selection. */
private sealed interface FieldValue {
    val text: String

    class Plain(override val text: String, val onChange: (String) -> Unit) : FieldValue

    class Rich(val value: TextFieldValue, val onChange: (TextFieldValue) -> Unit) : FieldValue {
        override val text: String get() = value.text
    }
}

@Suppress("LongParameterList") // the public fields' own parameters, passed through as they were given
@Composable
private fun SettingsTextFieldBody(
    value: FieldValue,
    modifier: Modifier,
    label: String,
    isError: Boolean,
    supportingText: @Composable (() -> Unit)?,
    fillWidth: Boolean,
    containerColor: Color,
    options: FieldOptions,
) {
    val enabled = options.enabled
    val hasLabel = label.isNotEmpty()
    val interactionSource = remember { MutableInteractionSource() }
    // The one thing on this field that says which of the two dozen boxes on a settings screen a
    // keystroke is about to land in. Both branches below draw the same 1dp outline whatever the
    // state, so without this, tabbing through a settings tab moved an invisible caret: the
    // interaction source was built and handed to BasicTextField, and then never read.
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        focused -> MaterialTheme.colorScheme.primary
        else -> Color.Unspecified
    }
    val textColor = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    val chrome = FieldChrome(interactionSource, borderColor, textColor, containerColor, elevationPalette())
    Column(modifier = modifier) {
        if (hasLabel) {
            LabelledField(value, label, fillWidth, chrome, options)
        } else {
            PlainField(value, chrome, options)
        }
        if (supportingText != null) {
            CompositionLocalProvider(
                LocalContentColor provides if (isError) MaterialTheme.colorScheme.error
                                          else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Box(modifier = Modifier.padding(start = 4.dp, top = 3.dp)) {
                    supportingText()
                }
            }
        }
    }
}

/** The colours a field is drawn with this frame, resolved once by [SettingsTextField]. */
private class FieldChrome(
    val interactionSource: MutableInteractionSource,
    val borderColor: Color,
    val textColor: Color,
    val containerColor: Color,
    val palette: ElevationPalette,
)

/** How the field takes typing: passed to `BasicTextField` as it was given. */
private class FieldInput(
    val enabled: Boolean,
    val readOnly: Boolean,
    val keyboardOptions: KeyboardOptions,
    val keyboardActions: KeyboardActions,
    val visualTransformation: VisualTransformation,
)

/** What [SettingsTextField] was asked for beyond its value and label, passed down as one thing. */
private class FieldOptions(
    val input: FieldInput,
    val singleLine: Boolean,
    val maxLines: Int,
    val placeholder: @Composable (() -> Unit)?,
    val trailingIcon: @Composable (() -> Unit)?,
) {
    val enabled: Boolean get() = input.enabled
}

/**
 * Labeled: by default the inner Column self-sizes to label text width via IntrinsicSize.Max, so
 * callers passing a bare Modifier get wrap-content behaviour. Pass fillWidth = true when the
 * caller's modifier (e.g. weight() inside a Row) should determine the width instead.
 */
@Composable
private fun LabelledField(
    value: FieldValue,
    label: String,
    fillWidth: Boolean,
    chrome: FieldChrome,
    o: FieldOptions,
) {
    Column(
        modifier = Modifier
            .widthIn(min = 60.dp)
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(IntrinsicSize.Max))
            .then(if (o.singleLine) Modifier.height(42.dp) else Modifier.heightIn(min = 42.dp))
            .sunken(FieldShape, chrome.palette, chrome.containerColor, chrome.borderColor)
            .hoverTint(FieldShape)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (o.enabled) 0.5f else 0.3f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val minLines = if (o.singleLine) 1 else MULTI_LINE_MIN_LINES
            FieldText(value, Modifier.weight(1f), chrome, o, minLines) { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    Placeholder(o.placeholder, value.text)
                    innerTextField()
                }
            }
            if (o.trailingIcon != null) {
                Box(modifier = Modifier.padding(start = 4.dp)) { o.trailingIcon.invoke() }
            }
        }
    }
}

/** The plain bordered box, used where there is no caption. */
@Composable
private fun PlainField(value: FieldValue, chrome: FieldChrome, o: FieldOptions) {
    val modifier = Modifier
        .fillMaxWidth()
        .then(if (o.singleLine) Modifier.height(28.dp) else Modifier.padding(vertical = 5.dp))
        .sunken(FieldShape, chrome.palette, chrome.containerColor, chrome.borderColor)
        .hoverTint(FieldShape)
    FieldText(value, modifier, chrome, o, minLines = 1) { innerTextField ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                Placeholder(o.placeholder, value.text)
                innerTextField()
            }
            if (o.trailingIcon != null) {
                Box(modifier = Modifier.padding(start = 4.dp)) { o.trailingIcon.invoke() }
            }
        }
    }
}

/** The `BasicTextField` for [value]'s kind, styled as every settings field is. */
@Composable
private fun FieldText(
    value: FieldValue,
    modifier: Modifier,
    chrome: FieldChrome,
    o: FieldOptions,
    minLines: Int,
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit,
) {
    val textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = chrome.textColor)
    val cursor = SolidColor(MaterialTheme.colorScheme.primary)
    when (value) {
        is FieldValue.Plain -> BasicTextField(
            value = value.text,
            onValueChange = value.onChange,
            modifier = modifier,
            enabled = o.input.enabled,
            readOnly = o.input.readOnly,
            singleLine = o.singleLine,
            maxLines = o.maxLines,
            minLines = minLines,
            textStyle = textStyle,
            cursorBrush = cursor,
            keyboardOptions = o.input.keyboardOptions,
            keyboardActions = o.input.keyboardActions,
            visualTransformation = o.input.visualTransformation,
            interactionSource = chrome.interactionSource,
            decorationBox = decorationBox,
        )
        is FieldValue.Rich -> BasicTextField(
            value = value.value,
            onValueChange = value.onChange,
            modifier = modifier,
            enabled = o.input.enabled,
            readOnly = o.input.readOnly,
            singleLine = o.singleLine,
            maxLines = o.maxLines,
            minLines = minLines,
            textStyle = textStyle,
            cursorBrush = cursor,
            keyboardOptions = o.input.keyboardOptions,
            keyboardActions = o.input.keyboardActions,
            visualTransformation = o.input.visualTransformation,
            interactionSource = chrome.interactionSource,
            decorationBox = decorationBox,
        )
    }
}

/** The dimmed placeholder, shown while the field is empty. */
@Composable
private fun Placeholder(placeholder: @Composable (() -> Unit)?, value: String) {
    if (placeholder != null && value.isEmpty()) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSurface.copy(alpha = PLACEHOLDER_ALPHA)
        ) {
            placeholder()
        }
    }
}

private const val PLACEHOLDER_ALPHA = 0.38f

/** A multi-line field shows at least this many lines, so a wrapped value is seen to wrap. */
private const val MULTI_LINE_MIN_LINES = 2
