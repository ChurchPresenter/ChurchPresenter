package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.churchpresenter.app.churchpresenter.data.QASettings
import org.churchpresenter.app.churchpresenter.models.Question
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault
import org.churchpresenter.app.churchpresenter.utils.calculateAutoFitFontSize
import java.awt.image.BufferedImage

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
    val cardBg = if (isKey) Color.White
                 else if (qaSettings.backgroundColor == "transparent") Color.Transparent
                 else parseHexColor(qaSettings.backgroundColor)
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
        if (question != null) {
            val textMeasurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val cardPaddingPx = with(density) { (64.dp * 2).roundToPx() }
            val innerPaddingPx = with(density) { (48.dp * 2).roundToPx() }
            val availableWidthPx = constraints.maxWidth - cardPaddingPx - innerPaddingPx
            val availableHeightPx = (constraints.maxHeight * 0.6f).toInt()

            val fontSize = remember(question.text, availableWidthPx, availableHeightPx, qaSettings.fontSize) {
                calculateAutoFitFontSize(textMeasurer, question.text, textStyle, availableWidthPx, availableHeightPx)
                    .coerceAtMost(qaSettings.fontSize)
            }

            Box(
                modifier = Modifier
                    .padding(64.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(cardBg)
                    .padding(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = question.text,
                    style = textStyle,
                    fontSize = fontSize.sp,
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
    val bgColor = if (isKey) Color.Transparent
                  else if (qaSettings.backgroundColor == "transparent") Color.Transparent
                  else parseHexColor(qaSettings.backgroundColor)

    val qrBitmap = remember(url) { generateQRCodeBitmap(url, 512) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = transitionAlpha },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(bgColor)
                .padding(48.dp)
        ) {
            if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap,
                    contentDescription = "QR Code",
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
            Text(
                text = "Scan to ask a question",
                color = textColor,
                fontSize = 32.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
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

fun generateQRCodeBitmap(content: String, size: Int): ImageBitmap? {
    return try {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until size) {
            for (y in 0 until size) {
                image.setRGB(x, y, if (bitMatrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        image.toComposeImageBitmap()
    } catch (_: Exception) {
        null
    }
}
