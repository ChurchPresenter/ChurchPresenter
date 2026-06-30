package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.foundation.shape.RoundedCornerShape
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.konami_dialog_blessings
import churchpresenter.composeapp.generated.resources.konami_dialog_button
import churchpresenter.composeapp.generated.resources.konami_dialog_headline
import churchpresenter.composeapp.generated.resources.konami_dialog_message
import churchpresenter.composeapp.generated.resources.konami_dialog_title
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.jetbrains.compose.resources.stringResource
import kotlin.math.sin
import kotlin.random.Random

private data class ConfettiParticle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    val size: Float,
    val rotation: Float,
    val rotationSpeed: Float,
    val wobble: Float
)

private val confettiColors = listOf(
    Color(0xFFE53935), Color(0xFF8E24AA), Color(0xFF1E88E5),
    Color(0xFF00ACC1), Color(0xFF43A047), Color(0xFFFDD835),
    Color(0xFFFB8C00), Color(0xFFFFB300)
)

@Composable
fun KonamiEasterEggDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    theme: ThemeMode = ThemeMode.SYSTEM
) {
    if (!isVisible) return

    val mainWindowState = LocalMainWindowState.current
    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(
            position = centeredOnMainWindow(mainWindowState, 420.dp, 340.dp),
            width = 420.dp,
            height = 340.dp
        ),
        title = stringResource(Res.string.konami_dialog_title),
        resizable = false
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                ConfettiOverlay()
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(Res.string.konami_dialog_headline),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.konami_dialog_blessings),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.konami_dialog_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(shape = RoundedCornerShape(6.dp), onClick = onDismiss) {
                        Text(stringResource(Res.string.konami_dialog_button))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfettiOverlay() {
    val rng = remember { Random(42) }
    var particles by remember {
        mutableStateOf(
            List(60) {
                ConfettiParticle(
                    x = rng.nextFloat(),
                    y = -rng.nextFloat() * 1.2f,
                    vx = (rng.nextFloat() - 0.5f) * 0.004f,
                    vy = 0.003f + rng.nextFloat() * 0.004f,
                    color = confettiColors[rng.nextInt(confettiColors.size)],
                    size = 6f + rng.nextFloat() * 6f,
                    rotation = rng.nextFloat() * 360f,
                    rotationSpeed = (rng.nextFloat() - 0.5f) * 4f,
                    wobble = rng.nextFloat() * 10f
                )
            }
        )
    }

    LaunchedEffect(Unit) {
        var lastFrame = 0L
        while (true) {
            withFrameNanos { nanos ->
                val dt = if (lastFrame == 0L) 16f else (nanos - lastFrame) / 1_000_000f
                lastFrame = nanos
                particles = particles.map { p ->
                    val newY = p.y + p.vy * dt
                    ConfettiParticle(
                        x = p.x + p.vx * dt + sin(newY * 20f + p.wobble) * 0.001f,
                        y = if (newY > 1.1f) -0.05f else newY,
                        vx = p.vx,
                        vy = p.vy,
                        color = p.color,
                        size = p.size,
                        rotation = p.rotation + p.rotationSpeed * dt,
                        rotationSpeed = p.rotationSpeed,
                        wobble = p.wobble
                    )
                }
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        particles.forEach { p ->
            val px = p.x * size.width
            val py = p.y * size.height
            rotate(degrees = p.rotation, pivot = Offset(px, py)) {
                drawRect(
                    color = p.color.copy(alpha = 0.85f),
                    topLeft = Offset(px - p.size / 2, py - p.size / 4),
                    size = Size(p.size, p.size / 2)
                )
            }
        }
    }
}
