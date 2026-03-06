package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.awt.Window
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.no_video_selected
import org.churchpresenter.app.churchpresenter.utils.createFileChooser
import org.jetbrains.compose.resources.stringResource

@Composable
fun FileVideoPicker(
    videoPath: String,
    onVideoPathChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(32.dp)
            .clickable {
                SwingUtilities.invokeLater {
                    val parentWindow = Window.getWindows().firstOrNull { it.isActive }
                    val fileChooser = createFileChooser {
                        fileSelectionMode = JFileChooser.FILES_ONLY
                        isMultiSelectionEnabled = false
                        fileFilter = FileNameExtensionFilter(
                            "Video Files (*.mp4, *.mov, *.avi, *.mkv, *.webm)",
                            "mp4", "mov", "avi", "mkv", "webm"
                        )
                        if (videoPath.isNotEmpty()) {
                            val file = java.io.File(videoPath)
                            if (file.exists()) {
                                selectedFile = file
                            }
                        }
                    }
                    val result = fileChooser.showOpenDialog(parentWindow)
                    if (result == JFileChooser.APPROVE_OPTION) {
                        onVideoPathChange(fileChooser.selectedFile.absolutePath)
                    }
                }
            }
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(4.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (videoPath.isEmpty()) stringResource(Res.string.no_video_selected) else videoPath.substringAfterLast('/').substringAfterLast('\\'),
            style = MaterialTheme.typography.bodySmall,
            color = if (videoPath.isEmpty())
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            else
                MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "🎬",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
