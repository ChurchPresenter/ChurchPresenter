package org.churchpresenter.lottiegen.editor.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.churchpresenter.lottiegen.ui.Strings

private val ButtonShape = RoundedCornerShape(6.dp)

/**
 * The Style Editor's live preview — the generator PreviewPanel's Compottie mechanics
 * with play/seek state hoisted so the timeline scrub can drive it. (Deliberate copy:
 * the user-facing PreviewPanel must stay untouched.)
 */
@Composable
fun EditorPreview(
    jsonString: String?,
    aspectRatio: Float,
    isPlaying: Boolean,
    seek: Float,
    onPlayingChange: (Boolean) -> Unit,
    onSeekChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(aspectRatio)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0xFF2A2D35), RoundedCornerShape(4.dp))
                    .background(Color(0xFF10131A)),
                contentAlignment = Alignment.Center
            ) {
                if (jsonString != null) {
                    val composition by rememberLottieComposition(key = jsonString) {
                        LottieCompositionSpec.JsonString(jsonString)
                    }
                    val progress by animateLottieCompositionAsState(
                        composition = composition,
                        isPlaying = isPlaying,
                        iterations = Int.MAX_VALUE
                    )

                    LaunchedEffect(progress) {
                        if (isPlaying) onSeekChange(progress)
                    }

                    composition?.let {
                        Image(
                            painter = rememberLottiePainter(
                                composition = it,
                                progress = { if (isPlaying) progress else seek }
                            ),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Text(
                        Strings.generating,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { onPlayingChange(!isPlaying) }, shape = ButtonShape) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) Strings.editorPause else Strings.editorPlay,
                    modifier = Modifier.size(16.dp)
                )
            }

            Slider(
                value = seek,
                onValueChange = {
                    onSeekChange(it)
                    onPlayingChange(false)
                },
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f)
            )

            Text(
                "%.0f%%".format(seek * 100),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
