package org.churchpresenter.lottiegen.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.lottiegen.ui.Tokens

/** Disabled text and captions are dimmed rather than hidden. */
private const val DISABLED_ALPHA = 0.38f
private const val LABEL_DISABLED_ALPHA = 0.5f

/**
 * The chrome a field draws itself with, computed once by [LottieTextField] and handed to whichever
 * of the two variants renders it. Not parameters, because the two variants need all of it.
 */
private class FieldChrome(
    val interactionSource: MutableInteractionSource,
    val borderColor: Color,
    val textStyle: TextStyle,
)

/**
 * A text field styled as a field card, matching [LottieDropdown]: a tiny uppercase label above
 * the editable value. Without a label it degrades to a plain bordered box (used by the batch
 * import dialog's multiline area).
 */
@Composable
fun LottieTextField(
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
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    fillWidth: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()

    val borderColor by animateColorAsState(
        when {
            isError -> MaterialTheme.colorScheme.error
            focused || hovered -> Tokens.FieldBorderHover
            else -> Tokens.FieldBorder
        },
        label = "fieldBorder"
    )
    val textColor = if (enabled) Tokens.InputText else Tokens.InputText.copy(alpha = DISABLED_ALPHA)
    val chrome = FieldChrome(
        interactionSource = interactionSource,
        borderColor = borderColor,
        textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, color = textColor),
    )

    Column(modifier = modifier) {
        if (label.isNotEmpty()) {
            LabelledField(
                value, onValueChange, label, enabled, readOnly, singleLine, maxLines,
                placeholder, trailingIcon, keyboardOptions, keyboardActions, visualTransformation,
                fillWidth, chrome,
            )
        } else {
            PlainField(
                value, onValueChange, enabled, readOnly, singleLine, maxLines,
                placeholder, trailingIcon, keyboardOptions, keyboardActions, visualTransformation,
                chrome,
            )
        }
    }
}

/** The field-card form: a tiny uppercase caption above the editable value. */
@Composable
private fun LabelledField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    readOnly: Boolean,
    singleLine: Boolean,
    maxLines: Int,
    placeholder: @Composable (() -> Unit)?,
    trailingIcon: @Composable (() -> Unit)?,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    visualTransformation: VisualTransformation,
    fillWidth: Boolean,
    chrome: FieldChrome,
) {
    Column(
        modifier = Modifier
            .widthIn(min = 60.dp)
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(IntrinsicSize.Max))
            .height(Tokens.FieldHeight)
            .clip(Tokens.FieldShape)
            .background(Tokens.FieldBg)
            .border(1.dp, chrome.borderColor, Tokens.FieldShape)
            .hoverable(chrome.interactionSource)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 9.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = Tokens.FieldLabelTracking,
            color = if (enabled) Tokens.FieldLabel else Tokens.FieldLabel.copy(alpha = LABEL_DISABLED_ALPHA),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                readOnly = readOnly,
                singleLine = singleLine,
                maxLines = maxLines,
                textStyle = chrome.textStyle,
                cursorBrush = SolidColor(Tokens.Accent),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                visualTransformation = visualTransformation,
                interactionSource = chrome.interactionSource,
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        FieldPlaceholder(placeholder, value)
                        innerTextField()
                    }
                }
            )
            if (trailingIcon != null) {
                Box(modifier = Modifier.padding(start = 4.dp)) { trailingIcon() }
            }
        }
    }
}

/** The plain bordered box, used where there is no caption -- the batch import dialog's text area. */
@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    readOnly: Boolean,
    singleLine: Boolean,
    maxLines: Int,
    placeholder: @Composable (() -> Unit)?,
    trailingIcon: @Composable (() -> Unit)?,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    visualTransformation: VisualTransformation,
    chrome: FieldChrome,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (singleLine) Modifier.height(30.dp) else Modifier.fillMaxHeight())
            .clip(Tokens.FieldShape)
            .background(Tokens.FieldBg)
            .border(1.dp, chrome.borderColor, Tokens.FieldShape)
            .hoverable(chrome.interactionSource),
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        maxLines = maxLines,
        textStyle = chrome.textStyle,
        cursorBrush = SolidColor(Tokens.Accent),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        interactionSource = chrome.interactionSource,
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    FieldPlaceholder(placeholder, value)
                    innerTextField()
                }
                if (trailingIcon != null) {
                    Box(modifier = Modifier.padding(start = 4.dp)) { trailingIcon() }
                }
            }
        }
    )
}

/** The placeholder, shown only while the field is empty. */
@Composable
private fun FieldPlaceholder(placeholder: @Composable (() -> Unit)?, value: String) {
    if (placeholder != null && value.isEmpty()) {
        CompositionLocalProvider(LocalContentColor provides Tokens.Placeholder) { placeholder() }
    }
}
