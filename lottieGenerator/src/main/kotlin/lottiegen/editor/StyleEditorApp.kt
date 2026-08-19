package lottiegen.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import lottiegen.editor.ui.EditorPreview
import lottiegen.editor.ui.EditorTooltip
import lottiegen.editor.ui.ElementInspector
import lottiegen.editor.ui.ElementListPanel
import lottiegen.editor.ui.ProjectToolbarActions
import lottiegen.editor.ui.TestConfigPanel
import lottiegen.editor.ui.TestMatrixView
import lottiegen.editor.ui.TimelinePanel
import lottiegen.ui.LottieGenTheme
import lottiegen.ui.Strings
import java.awt.Cursor

/**
 * Root composable of the developer-only Animation Style Editor. Opened from the main
 * app's Developer menu (embedded — inherits the host theme) or standalone via
 * `--editor` (wraps itself in LottieGenTheme). The user-facing generator App is
 * untouched by any of this.
 */
@Composable
fun StyleEditorApp(standalone: Boolean = false) {
    val scope = rememberCoroutineScope()
    val viewModel = remember(scope) { EditorViewModel(scope, EditorViewModel.templateSpec()) }

    val content: @Composable () -> Unit = { EditorContent(viewModel) }

    if (standalone) {
        LottieGenTheme { content() }
    } else {
        content()
    }
}

@Composable
private fun EditorContent(state: EditorState) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            EditorToolbar(state)

            var leftPaneWidth by remember { mutableStateOf(380f) }
            val density = LocalDensity.current

            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LeftPane(state, leftPaneWidth)

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                        .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                        .pointerInput(Unit) {
                            detectDragGestures { _, dragAmount ->
                                val deltaDp = with(density) { dragAmount.x.toDp().value }
                                leftPaneWidth = (leftPaneWidth + deltaDp).coerceIn(300f, 700f)
                            }
                        }
                )

                RightPane(state)
            }

            if (state.statusText.isNotEmpty()) {
                Text(
                    state.statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun EditorToolbar(state: EditorState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = state.projectName + if (state.dirty) " *" else "",
            style = MaterialTheme.typography.titleMedium
        )
        ProjectToolbarActions(state)
        Row(verticalAlignment = Alignment.CenterVertically) {
            EditorTooltip(Strings.editorTipModePreview) {
                FilterChip(
                    selected = !state.matrixMode,
                    onClick = { state.setMatrixModeEnabled(false) },
                    label = { Text(Strings.editorModePreview) }
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            EditorTooltip(Strings.editorTipModeMatrix) {
                FilterChip(
                    selected = state.matrixMode,
                    onClick = { state.setMatrixModeEnabled(true) },
                    label = { Text(Strings.editorModeMatrix) }
                )
            }
        }
    }
}

@Composable
private fun LeftPane(state: EditorState, widthDp: Float) {
    Column(
        modifier = Modifier
            .width(widthDp.dp)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        ElementListPanel(state)
        ElementInspector(state)
    }
}

@Composable
private fun RightPane(state: EditorState) {
    var isPlaying by remember { mutableStateOf(true) }
    var seek by remember { mutableStateOf(0f) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (state.matrixMode) {
            TestMatrixView(state, modifier = Modifier.weight(1f).fillMaxWidth())
        } else {
            EditorPreview(
                jsonString = state.generatedJson,
                aspectRatio = state.testConfig.canvasW.toFloat() / state.testConfig.canvasH.toFloat(),
                isPlaying = isPlaying,
                seek = seek,
                onPlayingChange = { isPlaying = it },
                onSeekChange = { seek = it },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
            TimelinePanel(
                state = state,
                seek = seek,
                onSeekChange = { seek = it },
                onPlayingChange = { isPlaying = it }
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            TestConfigPanel(state)
        }
    }
}
