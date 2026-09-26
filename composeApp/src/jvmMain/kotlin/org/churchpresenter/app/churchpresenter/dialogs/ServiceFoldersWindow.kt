package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import churchpresenter.composeapp.generated.resources.service_folders_description
import churchpresenter.composeapp.generated.resources.service_folders_ready
import churchpresenter.composeapp.generated.resources.service_folders_root
import churchpresenter.composeapp.generated.resources.service_folders_title
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.data.ContentRepositoryManager
import org.churchpresenter.app.churchpresenter.data.GitHubApi
import org.churchpresenter.app.churchpresenter.data.GitHubRepository
import org.churchpresenter.app.churchpresenter.data.GitHubSession
import org.churchpresenter.app.churchpresenter.data.RepositoryStatus
import org.churchpresenter.app.churchpresenter.data.ServiceEntry
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.utils.AppWindowRoot
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SettingsManager
import org.churchpresenter.theme.ThemeMode
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.nio.file.Path

@Composable
fun ServiceFoldersWindow(theme: ThemeMode, onClose: () -> Unit, onSettingsChanged: (AppSettings) -> Unit = {}) {
    Window(onCloseRequest = onClose, title = stringResource(Res.string.service_folders_title), icon = painterResource(Res.drawable.ic_app_icon), state = rememberWindowState(width = 760.dp, height = 650.dp)) {
        AppWindowRoot(theme) { ContentRepositoryContent(onSettingsChanged) }
    }
}

@Composable
private fun ContentRepositoryContent(onSettingsChanged: (AppSettings) -> Unit) {
    var root by remember { mutableStateOf<Path?>(null) }
    var github by remember { mutableStateOf<GitHubSession?>(null) }
    var repositories by remember { mutableStateOf<List<GitHubRepository>>(emptyList()) }
    var selectedRepository by remember { mutableStateOf<GitHubRepository?>(null) }
    var branches by remember { mutableStateOf<List<String>>(emptyList()) }
    var branch by remember { mutableStateOf("main") }
    var status by remember { mutableStateOf<RepositoryStatus?>(null) }
    var services by remember { mutableStateOf<List<ServiceEntry>>(emptyList()) }
    var selected by remember { mutableStateOf<ServiceEntry?>(null) }
    var commitMessage by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    fun runAction(action: suspend () -> Unit) = scope.launch {
        busy = true; error = null
        try { action() } catch (e: Exception) { error = e.message ?: "Operation failed." } finally { busy = false }
    }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.service_folders_description))
            TabRow(tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Repository") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Connect service") })
            }
            OutlinedTextField(root?.toString().orEmpty(), {}, readOnly = true, label = { Text(stringResource(Res.string.service_folders_root)) }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = { runAction { FileChooser.platformInstance.chooseSingle(root, emptyList(), "Repository folder", true)?.let { root = it } } }) { Text(stringResource(Res.string.service_folders_browse)) }
                Button(enabled = !busy, onClick = { runAction { github = GitHubApi.signIn(); repositories = GitHubApi.repositories(github!!); selectedRepository = repositories.firstOrNull(); branches = selectedRepository?.let { GitHubApi.branches(github!!, it) }.orEmpty(); branch = selectedRepository?.default_branch ?: "main" } }) { Text("Sign in with GitHub") }
            }
            if (tab == 0) {
                github?.let { session ->
                    Text("GitHub: ${session.login}")
                    repositories.forEach { repository -> Button(enabled = !busy, onClick = { selectedRepository = repository; runAction { branches = GitHubApi.branches(session, repository); branch = repository.default_branch } }, modifier = Modifier.fillMaxWidth()) { Text(repository.full_name) } }
                    branches.forEach { availableBranch -> Button(enabled = !busy, onClick = { branch = availableBranch }, modifier = Modifier.fillMaxWidth()) { Text(if (availableBranch == branch) "✓ $availableBranch" else availableBranch) } }
                }
                status?.let { Text("${it.kind}: ${it.branch}: ${it.staged} staged, ${it.unstaged} changed, ahead ${it.ahead}, behind ${it.behind}\n${it.message}") }
                Button(enabled = !busy && root != null && selectedRepository != null, onClick = { val r = root!!; val repository = selectedRepository!!; runAction { val manager = ContentRepositoryManager(r); manager.initialize("https://github.com/${repository.full_name}.git", branch); status = manager.status(); services = manager.services() } }) { Text("Connect selected repository") }
                OutlinedTextField(commitMessage, { commitMessage = it }, enabled = !busy, label = { Text("Commit message") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy && root != null && commitMessage.isNotBlank(), onClick = { val manager = ContentRepositoryManager(root!!); runAction { manager.commit(commitMessage); status = manager.status() } }) { Text("Commit") }
                    Button(enabled = !busy && root != null, onClick = { val manager = ContentRepositoryManager(root!!); runAction { manager.synchronize(); status = manager.status(); services = manager.services() } }) { Text("Synchronize") }
                }
            } else {
                services.forEach { service -> Button(enabled = !busy, onClick = { selected = service }, modifier = Modifier.fillMaxWidth()) { Text("${service.date}  ${if (service.plan != null) "plan ready" else "plan missing"}") } }
                selected?.let { service ->
                    HorizontalDivider(); Text("Selected: ${service.date}")
                    Button(enabled = !busy && root != null && service.plan != null, onClick = {
                        val repository = root!!; runAction {
                            val current = SettingsManager().loadSettings()
                            ContentRepositoryManager(repository).connect(service, repository) { songs, bibles, pictures, presentations, media ->
                                val updated = current.copy(songSettings = current.songSettings.copy(storageDirectory = songs.toString()), bibleSettings = current.bibleSettings.copy(storageDirectory = bibles.toString()), pictureSettings = current.pictureSettings.copy(storageDirectory = pictures.toString()), presentationStorageDirectory = presentations.toString(), mediaStorageDirectory = media.toString())
                                SettingsManager().saveSettings(updated); onSettingsChanged(updated)
                            }
                        }
                    }) { Text("Connect service and open plan") }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (status != null) Text(stringResource(Res.string.service_folders_ready))
        }
    }
}
