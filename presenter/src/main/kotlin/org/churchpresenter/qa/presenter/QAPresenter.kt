package org.churchpresenter.qa.presenter

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.churchpresenter.sharedui.presenter.ReferenceScaledBox
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.qa_qr_message_default
import org.churchpresenter.strings.generated.resources.qr_code
import org.churchpresenter.sharedui.composables.rememberTextBackdropPainter
import org.churchpresenter.sharedui.composables.OutlinedText
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.settings.QASettings
import org.churchpresenter.settings.QA_QR_CODE_BOX
import org.churchpresenter.settings.QA_QR_MESSAGE_BOX
import org.churchpresenter.settings.QA_QUESTION_BOX
import org.churchpresenter.settings.TextBox
import org.churchpresenter.settings.boxAt
import org.churchpresenter.settings.textBoxKey
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.utils.Utils.parseHexColor
import org.churchpresenter.sharedui.utils.Utils.systemFontFamilyOrDefault
import org.churchpresenter.sharedui.utils.calculateAutoFitFontSize
import org.churchpresenter.sharedui.utils.generateQRCodeBitmap
import org.churchpresenter.sharedui.presenter.BoxFitText
import org.churchpresenter.sharedui.presenter.BoxedItem
import org.churchpresenter.sharedui.presenter.fitInBox
import org.churchpresenter.sharedui.presenter.rectIn

/** The card the question is drawn in, and so the space auto-fit measures against. */
private val CARD_PADDING = 64.dp
private val CARD_INNER_PADDING = 48.dp

/** How much of the output's height a question may take before auto-fit shrinks it. */
internal const val QUESTION_HEIGHT_FRACTION = 0.6f

@Composable
fun QAPresenter(
    modifier: Modifier = Modifier,
    question: Question?,
    qaSettings: QASettings = QASettings(),
    outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
    transitionAlpha: Float = 1f,
) = ReferenceScaledBox(modifier) {
    QAPresenterContent(
        question = question,
        qaSettings = qaSettings,
        outputRole = outputRole,
        transitionAlpha = transitionAlpha,
    )
}

/** [QAPresenter] as a 1920x1080-family output draws it; [ReferenceScaledBox] fits it to the real one. */
@Composable
private fun QAPresenterContent(
    question: Question?,
    qaSettings: QASettings,
    outputRole: String,
    transitionAlpha: Float,
) {
    val isKey = outputRole == Constants.OUTPUT_ROLE_KEY
    val textColor = if (isKey) Color.White else parseHexColor(qaSettings.textColor)
    val bgOpacity = (qaSettings.backgroundOpacity / 100f).coerceIn(0f, 1f)
    val cardBg = if (isKey) Color.White
                 else parseHexColor(
                     if (qaSettings.backgroundColor == Constants.COLOR_VALUE_TRANSPARENT) "#1E1E2E"
                     else qaSettings.backgroundColor
                 ).copy(alpha = bgOpacity)
    val fontFamily = systemFontFamilyOrDefault(qaSettings.fontType)

    val shadowColorBase = parseHexColor(qaSettings.shadowColor)
    val shadowSizeMul = qaSettings.shadowSize / 100f
    val shadowAlpha = (qaSettings.shadowOpacity / 100f).coerceIn(0f, 1f)
    val qaShadow = Shadow(
        color = shadowColorBase.copy(alpha = shadowAlpha),
        offset = Offset(6f * shadowSizeMul, 6f * shadowSizeMul),
        blurRadius = 12f * shadowSizeMul
    )

    val textStyle = TextStyle(
        fontFamily = fontFamily,
        fontWeight = if (qaSettings.bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (qaSettings.italic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (qaSettings.underline) TextDecoration.Underline else TextDecoration.None,
        shadow = if (qaSettings.shadow) qaShadow else null,
        textAlign = when (qaSettings.horizontalAlignment) {
            Constants.LEFT -> TextAlign.Left
            Constants.RIGHT -> TextAlign.Right
            else -> TextAlign.Center
        },
        color = textColor
    )

    val boxAlignment = positionToAlignment(qaSettings.position)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = transitionAlpha },
        contentAlignment = boxAlignment
    ) {
        val questionBox = qaSettings.textBoxes.boxAt(textBoxKey(QA_QUESTION_BOX, lowerThird = false))
        if (question != null && questionBox.enabled) {
            val area = Rect(0f, 0f, maxWidth.value, maxHeight.value)
            BoxedQuestion(question, qaSettings, questionBox, textStyle, cardBg, area)
        } else if (question != null) {
            val textMeasurer = rememberTextMeasurer()
            // Measured in dp, not in pixels. `calculateAutoFitFontSize` measures at `Density(1f)`
            // and returns a size that is then drawn as `.sp`, which the platform scales by the
            // output's density -- so fitting against a pixel width let a HiDPI output take a size
            // `density` times too large for the card it was fitted to, and the question overran it.
            val availableWidth = (maxWidth - (CARD_PADDING + CARD_INNER_PADDING) * 2).value.toInt()
            val availableHeight = (maxHeight.value * QUESTION_HEIGHT_FRACTION).toInt()

            val fontSize = remember(question.text, availableWidth, availableHeight, qaSettings.fontSize) {
                calculateAutoFitFontSize(textMeasurer, question.text, textStyle, availableWidth, availableHeight)
                    .coerceAtMost(qaSettings.fontSize)
            }

            Box(
                modifier = Modifier
                    .padding(CARD_PADDING)
                    .clip(RoundedCornerShape(24.dp))
                    .background(cardBg)
                    .padding(CARD_INNER_PADDING),
                contentAlignment = Alignment.Center
            ) {
                val painter = rememberTextBackdropPainter(qaSettings.backdrop)
                OutlinedText(
                    modifier = painter.modifier,
                    onTextLayout = painter::onTextLayout,
                    text = question.text,
                    outline = qaSettings.outline,
                    // As in the announcements presenter: the size drawn is fitted down from the
                    // configured one, and the stroke follows it.
                    scaleFactor = fontSize.toFloat() / qaSettings.fontSize.coerceAtLeast(1),
                    color = Color.Unspecified,
                    style = textStyle,
                    fontSize = fontSize.sp,
                    fillWidth = false,
                )
            }
        }
    }
}

@Composable
fun QAQRCodePresenter(
    modifier: Modifier = Modifier,
    url: String,
    qaSettings: QASettings = QASettings(),
    outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
    transitionAlpha: Float = 1f,
) {
    val isKey = outputRole == Constants.OUTPUT_ROLE_KEY
    val textColor = if (isKey) Color.White else parseHexColor(qaSettings.textColor)
    val qrBgOpacity = (qaSettings.backgroundOpacity / 100f).coerceIn(0f, 1f)
    val bgColor = if (isKey) Color.Transparent
                  else parseHexColor(
                      if (qaSettings.backgroundColor == Constants.COLOR_VALUE_TRANSPARENT) "#1E1E2E"
                      else qaSettings.backgroundColor
                  ).copy(alpha = qrBgOpacity)

    val qrFgArgb = remember(qaSettings.qrForegroundColor) { parseHexColor(qaSettings.qrForegroundColor).toArgb() }
    val qrBgArgb = remember(qaSettings.qrBackgroundColor, qaSettings.qrBackgroundOpacity) {
        parseHexColor(qaSettings.qrBackgroundColor).copy(alpha = qaSettings.qrBackgroundOpacity / 100f).toArgb()
    }
    val qrBitmap = remember(url, qrFgArgb, qrBgArgb) { generateQRCodeBitmap(url, 512, qrFgArgb, qrBgArgb) }
    val codeBox = qaSettings.textBoxes.boxAt(textBoxKey(QA_QR_CODE_BOX, lowerThird = false))
    val messageBox = qaSettings.textBoxes.boxAt(textBoxKey(QA_QR_MESSAGE_BOX, lowerThird = false))
    val message = qaSettings.qrCodeMessage.ifEmpty { stringResource(Res.string.qa_qr_message_default) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = transitionAlpha },
        contentAlignment = Alignment.Center
    ) {
        val area = Rect(0f, 0f, maxWidth.value, maxHeight.value)
        if (codeBox.enabled && qrBitmap != null) {
            BoxedItem(codeBox.rectIn(area), codeBox, Constants.CENTER, textBoxKey(QA_QR_CODE_BOX, false)) {
                QRCodeImage(qrBitmap, isKey, Modifier.fillMaxSize())
            }
        }
        if (messageBox.enabled) {
            BoxedQRMessage(message, textColor, messageBox, messageBox.rectIn(area))
        }
        // Whatever is not boxed keeps the card it has always been drawn in -- sized to the output, so
        // the code and its message keep the proportions a 1080-line screen gives them on a window, a
        // portrait output or a preview tile alike, and never run off a small one.
        if (codeBox.enabled && messageBox.enabled) return@BoxWithConstraints
        val side = minOf(maxWidth * QR_CARD_WIDTH_FRACTION, maxHeight * QR_CARD_HEIGHT_FRACTION)
        val unit = side.value / QR_CARD_REFERENCE_SIDE
        val messageSize = with(LocalDensity.current) { (QR_MESSAGE_SIZE * unit).dp.toSp() }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape((QR_CARD_CORNER * unit).dp))
                .background(bgColor)
                .padding((QR_CARD_PADDING * unit).dp)
        ) {
            if (qrBitmap != null && !codeBox.enabled) {
                if (isKey) {
                    Box(modifier = Modifier.size(side).background(Color.White))
                } else {
                    Image(
                        bitmap = qrBitmap,
                        contentDescription = stringResource(Res.string.qr_code),
                        modifier = Modifier.size(side),
                    )
                }
                if (!messageBox.enabled) Spacer(Modifier.height((QR_CARD_GAP * unit).dp))
            }
            if (!messageBox.enabled) {
                Text(
                    text = message,
                    color = textColor,
                    fontSize = messageSize,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * The unboxed card at its reference size: on a 1920x1080 output its code is [QR_CARD_REFERENCE_SIDE]
 * across -- [QR_CARD_HEIGHT_FRACTION] of the height -- and the padding, gap, corner and message are
 * in the same units. A narrow (portrait) output lets the code take up to [QR_CARD_WIDTH_FRACTION] of
 * its width instead.
 */
private const val QR_CARD_REFERENCE_SIDE = 512f
private const val QR_CARD_HEIGHT_FRACTION = QR_CARD_REFERENCE_SIDE / 1080f
private const val QR_CARD_WIDTH_FRACTION = 0.7f
private const val QR_CARD_PADDING = 48f
private const val QR_CARD_GAP = 24f
private const val QR_CARD_CORNER = 24f

/** The QR code's message size, in the points the card draws it at. */
private const val QR_MESSAGE_SIZE = 32

/** The QR code filling [modifier]'s room at its own shape -- a white square on a key output. */
@Composable
private fun QRCodeImage(bitmap: ImageBitmap, isKey: Boolean, modifier: Modifier) {
    if (isKey) {
        Box(modifier.aspectRatio(1f).background(Color.White))
    } else {
        Image(bitmap = bitmap, contentDescription = stringResource(Res.string.qr_code), modifier = modifier)
    }
}

/** The QR code's message in its own box, fitted to it as the box says. */
@Composable
private fun BoxedQRMessage(message: String, color: Color, box: TextBox, rect: Rect) {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontWeight = FontWeight.Medium)
    val size = remember(message, rect, box) {
        fitInBox(
            measurer,
            BoxFitText(AnnotatedString(message), style, QR_MESSAGE_SIZE),
            box,
            IntSize(rect.width.toInt(), rect.height.toInt()),
        )
    }
    BoxedItem(rect, box, Constants.CENTER, textBoxKey(QA_QR_MESSAGE_BOX, false)) {
        Text(text = message, color = color, fontSize = size.sp, style = style, textAlign = TextAlign.Center)
    }
}

/**
 * The question's card in its own box: the text fitted to the box less the card's own padding, and
 * the card placed in the box by its vertical setting and the page's alignment.
 */
@Composable
private fun BoxedQuestion(
    question: Question,
    qaSettings: QASettings,
    box: TextBox,
    textStyle: TextStyle,
    cardBg: Color,
    area: Rect,
) {
    val rect = box.rectIn(area)
    val inner = CARD_INNER_PADDING.value * 2
    val measurer = rememberTextMeasurer()
    val fontSize = remember(question.text, rect, box, qaSettings.fontSize) {
        fitInBox(
            measurer,
            BoxFitText(AnnotatedString(question.text), textStyle, qaSettings.fontSize),
            box,
            IntSize((rect.width - inner).toInt(), (rect.height - inner).toInt()),
        )
    }
    BoxedItem(rect, box, qaSettings.horizontalAlignment, textBoxKey(QA_QUESTION_BOX, false)) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(cardBg)
                .padding(CARD_INNER_PADDING),
            contentAlignment = Alignment.Center,
        ) {
            val painter = rememberTextBackdropPainter(qaSettings.backdrop)
            OutlinedText(
                modifier = painter.modifier,
                onTextLayout = painter::onTextLayout,
                text = question.text,
                outline = qaSettings.outline,
                scaleFactor = fontSize.toFloat() / qaSettings.fontSize.coerceAtLeast(1),
                color = Color.Unspecified,
                style = textStyle,
                fontSize = fontSize.sp,
                fillWidth = false,
            )
        }
    }
}

private fun positionToAlignment(position: String): Alignment = when (position) {
    Constants.TOP_LEFT -> Alignment.TopStart
    Constants.TOP_CENTER -> Alignment.TopCenter
    Constants.TOP_RIGHT -> Alignment.TopEnd
    Constants.CENTER_LEFT -> Alignment.CenterStart
    Constants.CENTER -> Alignment.Center
    Constants.CENTER_RIGHT -> Alignment.CenterEnd
    Constants.BOTTOM_LEFT -> Alignment.BottomStart
    Constants.BOTTOM_CENTER -> Alignment.BottomCenter
    Constants.BOTTOM_RIGHT -> Alignment.BottomEnd
    else -> Alignment.Center
}
