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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.image_files_filter
import churchpresenter.composeapp.generated.resources.no_image_selected
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.jetbrains.compose.resources.stringResource
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path

@Composable
fun FileImagePicker(
    imagePath: String, // File path string
    onImagePathChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val imageFilesFilterStr = stringResource(Res.string.image_files_filter)
    val scope = rememberCoroutineScope()
    Row(
        modifier = modifier
            .height(32.dp)
            .clickable {
                scope.launch {
                    val path = if (imagePath.isNotEmpty()) Path(imagePath) else Path(System.getProperty(Constants.SystemProperties.USER_HOME))
                    val file = FileChooser.platformInstance.chooseSingle(
                        path = path,
                        filters = listOf(
                            FileNameExtensionFilter(
                                imageFilesFilterStr,
                                "jpg", "jpeg", "png", "gif", "bmp"
                            )
                        ),
                        title = "",
                        selectDirectory = false
                    )
                    if (file != null) {
                        onImagePathChange(file.toString())
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
            text = if (imagePath.isEmpty()) stringResource(Res.string.no_image_selected) else imagePath.substringAfterLast('/').substringAfterLast('\\'),
            style = MaterialTheme.typography.bodySmall,
            color = if (imagePath.isEmpty())
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            else
                MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "📁",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

