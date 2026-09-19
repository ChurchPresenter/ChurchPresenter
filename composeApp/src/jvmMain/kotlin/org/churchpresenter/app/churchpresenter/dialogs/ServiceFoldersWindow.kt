package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_app_icon
import churchpresenter.composeapp.generated.resources.service_folders_browse
import churchpresenter.composeapp.generated.resources.service_folders_create
import churchpresenter.composeapp.generated.resources.service_folders_date
import churchpresenter.composeapp.generated.resources.service_folders_description
import churchpresenter.composeapp.generated.resources.service_folders_error
import churchpresenter.composeapp.generated.resources.service_folders_invalid_date
import churchpresenter.composeapp.generated.resources.service_folders_picker_error
import churchpresenter.composeapp.generated.resources.service_folders_ready
import churchpresenter.composeapp.generated.resources.service_folders_root
import churchpresenter.composeapp.generated.resources.service_folders_title
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.data.ServiceFolders
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.utils.AppWindowRoot
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.utils.ServiceFolderConstants
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.nio.file.Path
import java.time.LocalDate

/** Standalone preparation utility, available from the Help menu. */
@Composable
fun ServiceFoldersWindow(theme: ThemeMode, onClose: () -> Unit) {
    Window(
        onCloseRequest = onClose,
        title = stringResource(Res.string.service_folders_title),
        icon = painterResource(Res.drawable.ic_app_icon),
        state = rememberWindowState(width = 620.dp, height = 560.dp)
    ) {
        AppWindowRoot(theme = theme) {
            ServiceFoldersContent()
        }
    }
}

@Composable
private fun ServiceFoldersContent() {
    var root by remember { mutableStateOf<Path?>(null) }
    var date by remember { mutableStateOf(ServiceFolders.formatDate(LocalDate.now())) }
    var plan by remember { mutableStateOf<Path?>(null) }
    var error by remember { mutableStateOf<StringResource?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val validDate = ServiceFolders.isValidDate(date)
    val pickerTitle = stringResource(Res.string.service_folders_root)

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(Res.string.service_folders_description))
            OutlinedTextField(
                value = root?.toString().orEmpty(),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(Res.string.service_folders_root)) },
                modifier = Modifier.fillMaxWidth()
            )
            Button(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    error = null
                    try {
                        FileChooser.platformInstance.chooseSingle(root, emptyList(), pickerTitle, true)?.let {
                            root = it
                            plan = null
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        error = Res.string.service_folders_picker_error
                    } finally {
                        busy = false
                    }
                }
            }) { Text(stringResource(Res.string.service_folders_browse)) }
            OutlinedTextField(
                value = date,
                onValueChange = { date = it; plan = null; error = null },
                enabled = !busy,
                singleLine = true,
                isError = !validDate,
                label = { Text(stringResource(Res.string.service_folders_date)) },
                supportingText = {
                    if (!validDate) Text(stringResource(Res.string.service_folders_invalid_date))
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (validDate) {
                root?.let { selectedRoot ->
                    SelectionContainer {
                        Text(ServiceFolderConstants.MATERIAL_DIRECTORIES.joinToString("\n") {
                            selectedRoot.resolve(date).resolve(it).toString()
                        })
                    }
                }
            }
            Button(enabled = root != null && validDate && !busy, onClick = {
                val selectedRoot = root ?: return@Button
                val selectedDate = date
                scope.launch {
                    busy = true
                    error = null
                    plan = null
                    try {
                        plan = withContext(Dispatchers.IO) { ServiceFolders.create(selectedRoot, selectedDate) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        error = Res.string.service_folders_error
                    } finally {
                        busy = false
                    }
                }
            }) { Text(stringResource(Res.string.service_folders_create)) }
            error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            plan?.let {
                Text(stringResource(Res.string.service_folders_ready))
                SelectionContainer { Text(it.toString()) }
            }
        }
    }
}
