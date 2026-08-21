package org.churchpresenter.lottiegen.ui.components

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
    val hasLabel = label.isNotEmpty()
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
    val textColor = if (enabled) Tokens.InputText else Tokens.InputText.copy(alpha = 0.38f)

    val textStyle = MaterialTheme.typography.bodySmall.copy(
        fontSize = 13.sp,
        color = textColor
    )

    Column(modifier = modifier) {
        if (hasLabel) {
            Column(
                modifier = Modifier
                    .widthIn(min = 60.dp)
                    .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(IntrinsicSize.Max))
                    .height(Tokens.FieldHeight)
                    .clip(Tokens.FieldShape)
                    .background(Tokens.FieldBg)
                    .border(1.dp, borderColor, Tokens.FieldShape)
                    .hoverable(interactionSource)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = label.uppercase(),
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = Tokens.FieldLabelTracking,
                    color = if (enabled) Tokens.FieldLabel else Tokens.FieldLabel.copy(alpha = 0.5f),
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
                        textStyle = textStyle,
                        cursorBrush = SolidColor(Tokens.Accent),
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                        visualTransformation = visualTransformation,
                        interactionSource = interactionSource,
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (placeholder != null && value.isEmpty()) {
                                    CompositionLocalProvider(LocalContentColor provides Tokens.Placeholder) {
                                        placeholder()
                                    }
                                }
                                innerTextField()
                            }
                        }
                    )
                    if (trailingIcon != null) {
                        Box(modifier = Modifier.padding(start = 4.dp)) { trailingIcon() }
                    }
                }
            }
        } else {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (singleLine) Modifier.height(30.dp) else Modifier.fillMaxHeight())
                    .clip(Tokens.FieldShape)
                    .background(Tokens.FieldBg)
                    .border(1.dp, borderColor, Tokens.FieldShape)
                    .hoverable(interactionSource),
                enabled = enabled,
                readOnly = readOnly,
                singleLine = singleLine,
                maxLines = maxLines,
                textStyle = textStyle,
                cursorBrush = SolidColor(Tokens.Accent),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                visualTransformation = visualTransformation,
                interactionSource = interactionSource,
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (placeholder != null && value.isEmpty()) {
                                CompositionLocalProvider(LocalContentColor provides Tokens.Placeholder) {
                                    placeholder()
                                }
                            }
                            innerTextField()
                        }
                        if (trailingIcon != null) {
                            Box(modifier = Modifier.padding(start = 4.dp)) { trailingIcon() }
                        }
                    }
                }
            )
        }
    }
}
