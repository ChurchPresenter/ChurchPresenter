package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_app_icon
import churchpresenter.composeapp.generated.resources.service_folders_title
import churchpresenter.composeapp.generated.resources.updater_account
import churchpresenter.composeapp.generated.resources.updater_back
import churchpresenter.composeapp.generated.resources.updater_changes_question
import churchpresenter.composeapp.generated.resources.updater_choose_action
import churchpresenter.composeapp.generated.resources.updater_choose_branch
import churchpresenter.composeapp.generated.resources.updater_choose_option
import churchpresenter.composeapp.generated.resources.updater_choose_location
import churchpresenter.composeapp.generated.resources.updater_choose_repository
import churchpresenter.composeapp.generated.resources.updater_choose_service
import churchpresenter.composeapp.generated.resources.updater_commit_hint
import churchpresenter.composeapp.generated.resources.updater_commit_push
import churchpresenter.composeapp.generated.resources.updater_create_structure
import churchpresenter.composeapp.generated.resources.updater_date_hint
import churchpresenter.composeapp.generated.resources.updater_disconnect_repository
import churchpresenter.composeapp.generated.resources.updater_enter_date
import churchpresenter.composeapp.generated.resources.updater_exit
import churchpresenter.composeapp.generated.resources.updater_conflict_manual
import churchpresenter.composeapp.generated.resources.updater_conflict_remote
import churchpresenter.composeapp.generated.resources.updater_conflict_local
import churchpresenter.composeapp.generated.resources.updater_conflict_title
import churchpresenter.composeapp.generated.resources.updater_invalid_date
import churchpresenter.composeapp.generated.resources.updater_no
import churchpresenter.composeapp.generated.resources.updater_no_changes
import churchpresenter.composeapp.generated.resources.updater_operation_running
import churchpresenter.composeapp.generated.resources.updater_plan_ready
import churchpresenter.composeapp.generated.resources.updater_plan_missing
import churchpresenter.composeapp.generated.resources.updater_plan_service
import churchpresenter.composeapp.generated.resources.updater_load_service
import churchpresenter.composeapp.generated.resources.updater_repository_connected
import churchpresenter.composeapp.generated.resources.updater_save_sync
import churchpresenter.composeapp.generated.resources.updater_setup_repository
import churchpresenter.composeapp.generated.resources.updater_sign_in
import churchpresenter.composeapp.generated.resources.updater_git
import churchpresenter.composeapp.generated.resources.updater_sync_done
import churchpresenter.composeapp.generated.resources.updater_sync_question
import churchpresenter.composeapp.generated.resources.updater_sync_remote
import churchpresenter.composeapp.generated.resources.updater_synced
import churchpresenter.composeapp.generated.resources.updater_status_both
import churchpresenter.composeapp.generated.resources.updater_status_conflict
import churchpresenter.composeapp.generated.resources.updater_status_line
import churchpresenter.composeapp.generated.resources.updater_status_local
import churchpresenter.composeapp.generated.resources.updater_status_remote
import churchpresenter.composeapp.generated.resources.updater_status_synced
import churchpresenter.composeapp.generated.resources.updater_title_changes
import churchpresenter.composeapp.generated.resources.updater_title_conflict
import churchpresenter.composeapp.generated.resources.updater_title_plan
import churchpresenter.composeapp.generated.resources.updater_title_repository
import churchpresenter.composeapp.generated.resources.updater_title_services
import churchpresenter.composeapp.generated.resources.updater_title_status
import churchpresenter.composeapp.generated.resources.updater_title_sync_complete
import churchpresenter.composeapp.generated.resources.updater_yes
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.data.ContentRepositoryManager
import org.churchpresenter.app.churchpresenter.data.ConflictEntry
import org.churchpresenter.app.churchpresenter.data.GitHubApi
import org.churchpresenter.app.churchpresenter.data.GitHubRepository
import org.churchpresenter.app.churchpresenter.data.GitHubSession
import org.churchpresenter.app.churchpresenter.data.RepositoryStatus
import org.churchpresenter.app.churchpresenter.data.ServiceEntry
import org.churchpresenter.app.churchpresenter.data.ServiceFolders
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.utils.AppWindowRoot
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SettingsManager
import org.churchpresenter.theme.ThemeMode
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.nio.file.Path

private enum class UpdaterScreen { Start, Repository, Repositories, Branches, Main, Date, Changes, SyncQuestion, SyncResult, Services, Conflict }

@Composable
fun ServiceFoldersWindow(theme: ThemeMode, onClose: () -> Unit, onSettingsChanged: (AppSettings) -> Unit = {}, onLoadPlan: (String) -> Unit = {}) {
    Window(onCloseRequest = onClose, title = stringResource(Res.string.service_folders_title), icon = painterResource(Res.drawable.ic_app_icon), state = rememberWindowState(width = 760.dp, height = 650.dp)) {
        AppWindowRoot(theme) { UpdaterContent(onClose, onSettingsChanged, onLoadPlan) }
    }
}

@Composable
private fun UpdaterContent(onClose: () -> Unit, onSettingsChanged: (AppSettings) -> Unit, onLoadPlan: (String) -> Unit) {
    var screen by remember { mutableStateOf(UpdaterScreen.Start) }
    var root by remember { mutableStateOf<Path?>(null) }
    var github by remember { mutableStateOf<GitHubSession?>(null) }
    var repositories by remember { mutableStateOf<List<GitHubRepository>>(emptyList()) }
    var repository by remember { mutableStateOf<GitHubRepository?>(null) }
    var branches by remember { mutableStateOf<List<String>>(emptyList()) }
    var branch by remember { mutableStateOf("main") }
    var status by remember { mutableStateOf<RepositoryStatus?>(null) }
    var conflicts by remember { mutableStateOf<List<ConflictEntry>>(emptyList()) }
    var services by remember { mutableStateOf<List<ServiceEntry>>(emptyList()) }
    var date by remember { mutableStateOf("") }
    var commitMessage by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val green = scheme.primary
    val gray = scheme.onSurface
    val dim = scheme.onSurfaceVariant
    val background = scheme.background
    val terminalStyle = TextStyle(fontFamily = FontFamily.Monospace, color = gray)

    fun action(block: suspend () -> Unit) = scope.launch {
        busy = true; error = null
        try { block() } catch (e: Exception) { error = e.message ?: "Operation failed." } finally { busy = false }
    }
    fun refresh() {
        val manager = root?.let(::ContentRepositoryManager) ?: return
        status = manager.status(); conflicts = manager.conflicts(); services = manager.services()
    }
    fun chooseRoot(after: () -> Unit) = action {
        FileChooser.platformInstance.chooseSingle(root, emptyList(), "Repository folder", true)?.let { root = it; after() }
    }
    fun startAfterSync() {
        refresh()
        screen = when (status?.kind) { "conflict" -> UpdaterScreen.Conflict; "remote", "both" -> UpdaterScreen.SyncQuestion; "local" -> UpdaterScreen.Changes; else -> UpdaterScreen.Main }
    }
    fun disconnectRepository() {
        ContentRepositoryManager.clearSavedConfiguration()
        root = null
        github = null
        repositories = emptyList()
        repository = null
        branches = emptyList()
        status = null
        conflicts = emptyList()
        services = emptyList()
        screen = UpdaterScreen.Start
        error = null
    }

    LaunchedEffect(Unit) {
        ContentRepositoryManager.savedConfiguration()?.let { configuration ->
            root = configuration.root; branch = configuration.branch
            action { refresh(); screen = if (status?.kind == "conflict") UpdaterScreen.Conflict else UpdaterScreen.Main }
        }
    }

    Surface(Modifier.fillMaxSize(), color = background, contentColor = gray) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), Arrangement.spacedBy(14.dp)) {
            Text("CHURCH PRESENTER", color = green, fontFamily = FontFamily.Monospace)
            Text("──────────────────────────────────────────────────────", color = dim, fontFamily = FontFamily.Monospace)
            Text(screenTitle(screen), color = scheme.onBackground, fontFamily = FontFamily.Monospace)
            if (root != null) Text("  repository: $root", color = dim, fontFamily = FontFamily.Monospace)
            HorizontalDivider(color = scheme.outlineVariant)
            when (screen) {
                UpdaterScreen.Start -> ChoiceList(stringResource(Res.string.updater_setup_repository), listOf(stringResource(Res.string.updater_choose_location), stringResource(Res.string.updater_exit))) { index ->
                    if (index == 1) onClose() else chooseRoot { screen = UpdaterScreen.Repository }
                }
                UpdaterScreen.Repository -> ChoiceList(stringResource(Res.string.updater_git), listOf(stringResource(Res.string.updater_sign_in), stringResource(Res.string.updater_back))) { index ->
                    if (index == 1) screen = UpdaterScreen.Start else action { github = GitHubApi.signIn(); repositories = GitHubApi.repositories(github!!); screen = UpdaterScreen.Repositories }
                }
                UpdaterScreen.Repositories -> {
                    Text(stringResource(Res.string.updater_account, github?.login.orEmpty()), color = dim, fontFamily = FontFamily.Monospace)
                    ChoiceList(stringResource(Res.string.updater_choose_repository), repositories.map { it.full_name } + stringResource(Res.string.updater_back)) { index ->
                        if (index == repositories.size) screen = UpdaterScreen.Repository else action { repository = repositories[index]; branches = GitHubApi.branches(github!!, repository!!); branch = repository!!.default_branch; screen = UpdaterScreen.Branches }
                    }
                }
                UpdaterScreen.Branches -> ChoiceList(stringResource(Res.string.updater_choose_branch), branches + stringResource(Res.string.updater_back)) { index ->
                    if (index == branches.size) screen = UpdaterScreen.Repositories else action {
                        branch = branches[index]
                        val selected = repository!!
                        val cloneRoot = ContentRepositoryManager(root!!).clone("https://github.com/${selected.full_name}.git", branch)
                        root = cloneRoot
                        val settingsManager = SettingsManager()
                        val current = settingsManager.loadSettings()
                        val updated = current.copy(
                            songSettings = current.songSettings.copy(storageDirectory = cloneRoot.resolve("Songs").toAbsolutePath().toString()),
                            bibleSettings = current.bibleSettings.copy(storageDirectory = cloneRoot.resolve("Bibles").toAbsolutePath().toString())
                        )
                        settingsManager.saveSettings(updated)
                        onSettingsChanged(updated)
                        refresh()
                        screen = UpdaterScreen.Main
                    }
                }
                UpdaterScreen.Main -> {
                    val kind = status?.kind
                    status?.let { StatusBlock(it) }
                    when (kind) {
                        "synced" -> ChoiceList(stringResource(Res.string.updater_choose_action), listOf(stringResource(Res.string.updater_plan_service), stringResource(Res.string.updater_load_service), stringResource(Res.string.updater_disconnect_repository))) { index ->
                            when (index) {
                                0 -> screen = UpdaterScreen.Date
                                1 -> { services = root?.let { ContentRepositoryManager(it).services() }.orEmpty(); screen = UpdaterScreen.Services }
                                else -> disconnectRepository()
                            }
                        }
                        "local" -> ChoiceList(stringResource(Res.string.updater_choose_action), listOf(stringResource(Res.string.updater_save_sync), stringResource(Res.string.updater_disconnect_repository))) { index -> if (index == 0) screen = UpdaterScreen.Changes else disconnectRepository() }
                        "remote", "both" -> ChoiceList(stringResource(Res.string.updater_choose_action), listOf(stringResource(Res.string.updater_sync_remote), stringResource(Res.string.updater_disconnect_repository))) { index -> if (index == 0) screen = UpdaterScreen.SyncQuestion else disconnectRepository() }
                        "conflict" -> { screen = UpdaterScreen.Conflict }
                        else -> ChoiceList(stringResource(Res.string.updater_choose_action), listOf(stringResource(Res.string.updater_plan_service), stringResource(Res.string.updater_disconnect_repository), stringResource(Res.string.updater_exit))) { index -> when (index) { 0 -> screen = UpdaterScreen.Date; 1 -> disconnectRepository(); else -> onClose() } }
                    }
                }
                UpdaterScreen.Date -> {
                    val invalidDateMessage = stringResource(Res.string.updater_invalid_date)
                    Text("${'$'} ${stringResource(Res.string.updater_enter_date)}", color = scheme.onBackground, fontFamily = FontFamily.Monospace)
                    OutlinedTextField(value = date, onValueChange = { date = it }, singleLine = true, placeholder = { Text(stringResource(Res.string.updater_date_hint), style = terminalStyle) }, textStyle = terminalStyle, colors = terminalFieldColors(), modifier = Modifier.fillMaxWidth())
                    ChoiceList(stringResource(Res.string.updater_create_structure), listOf(stringResource(Res.string.updater_create_structure), stringResource(Res.string.updater_back))) { index ->
                        if (index == 1) screen = UpdaterScreen.Main else if (!ServiceFolders.isValidDate(date)) error = invalidDateMessage else action { ContentRepositoryManager(root!!).createService(date); refresh(); screen = UpdaterScreen.Main }
                    }
                }
                UpdaterScreen.Changes -> {
                    val commitHint = stringResource(Res.string.updater_commit_hint)
                    Text("${'$'} ${stringResource(Res.string.updater_changes_question)}", color = scheme.onBackground, fontFamily = FontFamily.Monospace)
                    OutlinedTextField(value = commitMessage, onValueChange = { commitMessage = it }, singleLine = true, placeholder = { Text(stringResource(Res.string.updater_commit_hint), style = terminalStyle) }, textStyle = terminalStyle, colors = terminalFieldColors(), modifier = Modifier.fillMaxWidth())
                    ChoiceList(stringResource(Res.string.updater_commit_push), listOf(stringResource(Res.string.updater_commit_push), stringResource(Res.string.updater_back))) { index ->
                        if (index == 1) screen = UpdaterScreen.Main else if (commitMessage.isBlank()) error = commitHint else action { ContentRepositoryManager(root!!).synchronize(commitMessage.trim()); refresh(); screen = UpdaterScreen.SyncResult }
                    }
                }
                UpdaterScreen.SyncQuestion -> {
                    status?.let { StatusBlock(it) }
                    ChoiceList(stringResource(Res.string.updater_sync_question), listOf(stringResource(Res.string.updater_yes), stringResource(Res.string.updater_no))) { index ->
                        if (index == 1) screen = UpdaterScreen.Main else action { ContentRepositoryManager(root!!).synchronize(); refresh(); screen = if (conflicts.isEmpty()) UpdaterScreen.SyncResult else UpdaterScreen.Conflict }
                    }
                }
                UpdaterScreen.SyncResult -> {
                    Text(stringResource(Res.string.updater_sync_done), color = green, fontFamily = FontFamily.Monospace)
                    Text(stringResource(Res.string.updater_synced), color = green, fontFamily = FontFamily.Monospace)
                    ChoiceList(stringResource(Res.string.updater_choose_action), listOf(stringResource(Res.string.updater_back))) { screen = UpdaterScreen.Main }
                }
                UpdaterScreen.Conflict -> {
                    Text("! ${stringResource(Res.string.updater_conflict_title)}", color = scheme.error, fontFamily = FontFamily.Monospace)
                    conflicts.forEachIndexed { index, conflict ->
                        ChoiceList("${index + 1}/${conflicts.size}: ${conflict.path}", listOf(stringResource(Res.string.updater_conflict_local), stringResource(Res.string.updater_conflict_remote), stringResource(Res.string.updater_conflict_manual))) { choice ->
                            if (choice < 2) action { ContentRepositoryManager(root!!).resolveConflict(conflict.path, choice == 1); refresh(); if (conflicts.isEmpty()) screen = UpdaterScreen.Main }
                        }
                    }
                    ChoiceList(stringResource(Res.string.updater_choose_action), listOf(stringResource(Res.string.updater_disconnect_repository))) { disconnectRepository() }
                }
                UpdaterScreen.Services -> {
                    val planReady = stringResource(Res.string.updater_plan_ready)
                    val planMissing = stringResource(Res.string.updater_plan_missing)
                    ChoiceList(stringResource(Res.string.updater_choose_service), services.map { "${it.date}  ·  ${if (it.plan != null) planReady else planMissing}" } + stringResource(Res.string.updater_back)) { index ->
                        if (index == services.size) screen = UpdaterScreen.Main else { val service = services[index]; if (service.plan == null) error = planMissing else action { val current = SettingsManager().loadSettings(); ContentRepositoryManager(root!!).connect(service, root!!) { songs, bibles, pictures, presentations, media -> val updated = current.copy(songSettings = current.songSettings.copy(storageDirectory = songs.toString()), bibleSettings = current.bibleSettings.copy(storageDirectory = bibles.toString()), pictureSettings = current.pictureSettings.copy(storageDirectory = pictures.toString()), presentationStorageDirectory = presentations.toString(), mediaStorageDirectory = media.toString()); SettingsManager().saveSettings(updated); onSettingsChanged(updated) }; onLoadPlan(service.plan.toString()) } }
                    }
                }
            }
            if (busy) Text(stringResource(Res.string.updater_operation_running), color = green, fontFamily = FontFamily.Monospace)
            error?.let { Text("  ! $it", color = scheme.error, fontFamily = FontFamily.Monospace) }
        }
    }
}

@Composable
private fun ChoiceList(question: String, options: List<String>, onChoice: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("${'$'} $question", color = MaterialTheme.colorScheme.onBackground, fontFamily = FontFamily.Monospace)
        options.forEachIndexed { index, option ->
            Row(Modifier.fillMaxWidth().clickable { onChoice(index) }.padding(vertical = 9.dp, horizontal = 8.dp)) {
                Text("   ${index + 1}. ", color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
                Text(option, color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace)
            }
        }
        Text("  ${stringResource(Res.string.updater_choose_option)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun terminalFieldColors() = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline, focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, cursorColor = MaterialTheme.colorScheme.primary)

@Composable
private fun StatusBlock(status: RepositoryStatus) {
    val color = if (status.kind == "conflict") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val kind = when (status.kind) {
        "synced" -> stringResource(Res.string.updater_status_synced)
        "local" -> stringResource(Res.string.updater_status_local)
        "remote" -> stringResource(Res.string.updater_status_remote)
        "both" -> stringResource(Res.string.updater_status_both)
        else -> stringResource(Res.string.updater_status_conflict)
    }
    Text(stringResource(Res.string.updater_status_line, kind, status.staged, status.ahead, status.behind), color = color, fontFamily = FontFamily.Monospace)
}

@Composable
private fun screenTitle(screen: UpdaterScreen): String = when (screen) {
    UpdaterScreen.Start, UpdaterScreen.Repository, UpdaterScreen.Repositories, UpdaterScreen.Branches -> stringResource(Res.string.updater_title_repository)
    UpdaterScreen.Main, UpdaterScreen.SyncQuestion -> stringResource(Res.string.updater_title_status)
    UpdaterScreen.Date -> stringResource(Res.string.updater_title_plan)
    UpdaterScreen.Changes -> stringResource(Res.string.updater_title_changes)
    UpdaterScreen.SyncResult -> stringResource(Res.string.updater_title_sync_complete)
    UpdaterScreen.Services -> stringResource(Res.string.updater_title_services)
    UpdaterScreen.Conflict -> stringResource(Res.string.updater_title_conflict)
}
