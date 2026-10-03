package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
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
import java.awt.image.BufferedImage
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
        modifier = modifier
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
        // Whatever is not boxed keeps the card it has always been drawn in.
        if (codeBox.enabled && messageBox.enabled) return@BoxWithConstraints
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(bgColor)
                .padding(48.dp)
        ) {
            if (qrBitmap != null && !codeBox.enabled) {
                if (isKey) {
                    Box(
                        modifier = Modifier
                            .width(qrBitmap.width.dp)
                            .height(qrBitmap.height.dp)
                            .padding(bottom = 24.dp)
                            .background(Color.White)
                    )
                } else {
                    Image(
                        bitmap = qrBitmap,
                        contentDescription = stringResource(Res.string.qr_code),
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                }
            }
            if (!messageBox.enabled) {
                Text(
                    text = message,
                    color = textColor,
                    fontSize = QR_MESSAGE_SIZE.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

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

fun generateQRCodeBitmap(
    content: String,
    size: Int,
    foregroundArgb: Int = 0xFF000000.toInt(),
    backgroundArgb: Int = 0xFFFFFFFF.toInt(),
): ImageBitmap? {
    return try {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        for (x in 0 until size) {
            for (y in 0 until size) {
                image.setRGB(x, y, if (bitMatrix.get(x, y)) foregroundArgb else backgroundArgb)
            }
        }
        image.toComposeImageBitmap()
    } catch (_: Exception) {
        null
    }
}
