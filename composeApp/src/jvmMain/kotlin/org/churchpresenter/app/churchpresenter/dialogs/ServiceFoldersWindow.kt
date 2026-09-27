package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import churchpresenter.composeapp.generated.resources.updater_choose_location
import churchpresenter.composeapp.generated.resources.updater_choose_repository
import churchpresenter.composeapp.generated.resources.updater_choose_service
import churchpresenter.composeapp.generated.resources.updater_commit_hint
import churchpresenter.composeapp.generated.resources.updater_commit_push
import churchpresenter.composeapp.generated.resources.updater_create_structure
import churchpresenter.composeapp.generated.resources.updater_date_hint
import churchpresenter.composeapp.generated.resources.updater_disconnect_repository
import churchpresenter.composeapp.generated.resources.updater_settings
import churchpresenter.composeapp.generated.resources.updater_settings_account
import churchpresenter.composeapp.generated.resources.updater_settings_disconnect_confirm
import churchpresenter.composeapp.generated.resources.updater_settings_signout
import churchpresenter.composeapp.generated.resources.updater_settings_signout_confirm
import churchpresenter.composeapp.generated.resources.updater_settings_title
import churchpresenter.composeapp.generated.resources.updater_repository_missing
import churchpresenter.composeapp.generated.resources.updater_reconnect_repository
import churchpresenter.composeapp.generated.resources.updater_choose_existing_repository
import churchpresenter.composeapp.generated.resources.updater_repository_missing_error
import churchpresenter.composeapp.generated.resources.updater_continue
import churchpresenter.composeapp.generated.resources.updater_enter_date
import churchpresenter.composeapp.generated.resources.updater_exit
import churchpresenter.composeapp.generated.resources.updater_help
import churchpresenter.composeapp.generated.resources.updater_help_question
import churchpresenter.composeapp.generated.resources.updater_help_what
import churchpresenter.composeapp.generated.resources.updater_help_what_text
import churchpresenter.composeapp.generated.resources.updater_help_why
import churchpresenter.composeapp.generated.resources.updater_help_why_text
import churchpresenter.composeapp.generated.resources.updater_help_how
import churchpresenter.composeapp.generated.resources.updater_help_how_intro
import churchpresenter.composeapp.generated.resources.updater_help_how_tree
import churchpresenter.composeapp.generated.resources.updater_help_how_text
import churchpresenter.composeapp.generated.resources.updater_load_failed
import churchpresenter.composeapp.generated.resources.updater_loaded_result_directory
import churchpresenter.composeapp.generated.resources.updater_loaded_result_next
import churchpresenter.composeapp.generated.resources.updater_loaded_result_plan
import churchpresenter.composeapp.generated.resources.updater_loaded_result_title
import churchpresenter.composeapp.generated.resources.updater_conflict_manual
import churchpresenter.composeapp.generated.resources.updater_conflict_remote
import churchpresenter.composeapp.generated.resources.updater_conflict_local
import churchpresenter.composeapp.generated.resources.updater_conflict_manual_help
import churchpresenter.composeapp.generated.resources.updater_conflict_title
import churchpresenter.composeapp.generated.resources.updater_invalid_date
import churchpresenter.composeapp.generated.resources.updater_no
import churchpresenter.composeapp.generated.resources.updater_operation_failed
import churchpresenter.composeapp.generated.resources.updater_no_repositories
import churchpresenter.composeapp.generated.resources.updater_repository_folder_dialog_title
import churchpresenter.composeapp.generated.resources.updater_gcm_required
import churchpresenter.composeapp.generated.resources.updater_signout_credentials_warning
import churchpresenter.composeapp.generated.resources.updater_progress_branches
import churchpresenter.composeapp.generated.resources.updater_progress_choose_location
import churchpresenter.composeapp.generated.resources.updater_progress_clone
import churchpresenter.composeapp.generated.resources.updater_progress_conflict
import churchpresenter.composeapp.generated.resources.updater_progress_create
import churchpresenter.composeapp.generated.resources.updater_progress_github
import churchpresenter.composeapp.generated.resources.updater_progress_load
import churchpresenter.composeapp.generated.resources.updater_progress_repositories
import churchpresenter.composeapp.generated.resources.updater_progress_save
import churchpresenter.composeapp.generated.resources.updater_progress_status
import churchpresenter.composeapp.generated.resources.updater_progress_sync
import churchpresenter.composeapp.generated.resources.updater_progress_signout
import churchpresenter.composeapp.generated.resources.updater_plan_ready
import churchpresenter.composeapp.generated.resources.updater_plan_missing
import churchpresenter.composeapp.generated.resources.updater_plan_service
import churchpresenter.composeapp.generated.resources.updater_plan_result_next
import churchpresenter.composeapp.generated.resources.updater_plan_result_plan
import churchpresenter.composeapp.generated.resources.updater_plan_result_title
import churchpresenter.composeapp.generated.resources.updater_load_service
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.data.ContentRepositoryManager
import org.churchpresenter.app.churchpresenter.data.ConflictEntry
import org.churchpresenter.app.churchpresenter.data.GitHubApi
import org.churchpresenter.app.churchpresenter.data.GitCredentialManagerUnavailableException
import org.churchpresenter.app.churchpresenter.data.GitHubRepository
import org.churchpresenter.app.churchpresenter.data.GitHubSession
import org.churchpresenter.app.churchpresenter.data.RepositoryStatus
import org.churchpresenter.app.churchpresenter.data.RepositoryState
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

private enum class UpdaterScreen { Start, Help, HelpWhat, HelpWhy, HelpHow, Repository, Repositories, Branches, Main, MissingRepository, Settings, ConfirmDisconnect, ConfirmSignOut, Date, PlanCreated, Changes, SyncQuestion, SyncResult, Services, ServiceLoaded, Conflict, ManualConflict }
private val LocalUpdaterActionsEnabled = staticCompositionLocalOf { true }

@Composable
fun ServiceFoldersWindow(theme: ThemeMode, onClose: () -> Unit, onSettingsChanged: (AppSettings) -> Unit = {}, onLoadPlan: suspend (String) -> Boolean = { false }) {
    Window(onCloseRequest = onClose, title = stringResource(Res.string.service_folders_title), icon = painterResource(Res.drawable.ic_app_icon), state = rememberWindowState(width = 760.dp, height = 650.dp)) {
        AppWindowRoot(theme) { UpdaterContent(onClose, onSettingsChanged, onLoadPlan) }
    }
}

@Composable
private fun UpdaterContent(onClose: () -> Unit, onSettingsChanged: (AppSettings) -> Unit, onLoadPlan: suspend (String) -> Boolean) {
    var screen by remember { mutableStateOf(UpdaterScreen.Start) }
    var helpReturnScreen by remember { mutableStateOf(UpdaterScreen.Start) }
    var root by remember { mutableStateOf<Path?>(null) }
    var github by remember { mutableStateOf<GitHubSession?>(null) }
    var repositories by remember { mutableStateOf<List<GitHubRepository>>(emptyList()) }
    var repository by remember { mutableStateOf<GitHubRepository?>(null) }
    var branches by remember { mutableStateOf<List<String>>(emptyList()) }
    var branch by remember { mutableStateOf("main") }
    var status by remember { mutableStateOf<RepositoryStatus?>(null) }
    var missingRepositoryPath by remember { mutableStateOf<Path?>(null) }
    var conflicts by remember { mutableStateOf<List<ConflictEntry>>(emptyList()) }
    var manualConflictPath by remember { mutableStateOf<String?>(null) }
    var services by remember { mutableStateOf<List<ServiceEntry>>(emptyList()) }
    var date by remember { mutableStateOf("") }
    var commitMessage by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf<String?>(null) }
    var completedServiceDirectory by remember { mutableStateOf<Path?>(null) }
    var completedPlanPath by remember { mutableStateOf<Path?>(null) }
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val green = scheme.primary
    val gray = scheme.onSurface
    val dim = scheme.onSurfaceVariant
    val background = scheme.background
    val terminalStyle = TextStyle(fontFamily = FontFamily.Monospace, color = gray)
    val progressChooseLocation = stringResource(Res.string.updater_progress_choose_location)
    val progressStatus = stringResource(Res.string.updater_progress_status)
    val progressGithub = stringResource(Res.string.updater_progress_github)
    val progressRepositories = stringResource(Res.string.updater_progress_repositories)
    val progressBranches = stringResource(Res.string.updater_progress_branches)
    val progressClone = stringResource(Res.string.updater_progress_clone)
    val progressCreate = stringResource(Res.string.updater_progress_create)
    val progressSave = stringResource(Res.string.updater_progress_save)
    val progressSync = stringResource(Res.string.updater_progress_sync)
    val progressConflict = stringResource(Res.string.updater_progress_conflict)
    val progressLoad = stringResource(Res.string.updater_progress_load)
    val progressSignOut = stringResource(Res.string.updater_progress_signout)
    val missingRepositoryError = stringResource(Res.string.updater_repository_missing_error)
    val operationFailed = stringResource(Res.string.updater_operation_failed)
    val noRepositoriesMessage = stringResource(Res.string.updater_no_repositories)
    val repositoryFolderDialogTitle = stringResource(Res.string.updater_repository_folder_dialog_title)
    val gitCredentialManagerRequired = stringResource(Res.string.updater_gcm_required)
    val signOutCredentialsWarning = stringResource(Res.string.updater_signout_credentials_warning)

    fun action(progress: String, block: suspend () -> Unit) = scope.launch {
        busy = true; error = null; progressMessage = progress
        try { block() } catch (e: Exception) {
            error = when (e) {
                is GitCredentialManagerUnavailableException -> gitCredentialManagerRequired
                else -> e.message ?: operationFailed
            }
        } finally { busy = false; progressMessage = null }
    }
    suspend fun refresh() {
        val currentRoot = root ?: return
        if (withContext(Dispatchers.IO) {
                ContentRepositoryManager.savedConfiguration()?.let { !ContentRepositoryManager.isAvailable(it) } == true
            }) {
            missingRepositoryPath = currentRoot
            status = null
            conflicts = emptyList()
            services = emptyList()
            screen = UpdaterScreen.MissingRepository
            return
        }
        val snapshot = withContext(Dispatchers.IO) {
            val manager = ContentRepositoryManager(currentRoot)
            Triple(manager.status(), manager.conflicts(), manager.services())
        }
        status = snapshot.first; conflicts = snapshot.second; services = snapshot.third
    }
    fun chooseRoot(initial: Path? = root, after: suspend () -> Unit) = action(progressChooseLocation) {
        FileChooser.platformInstance.chooseSingle(initial, emptyList(), repositoryFolderDialogTitle, true)?.let { root = it; after() }
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
        missingRepositoryPath = null
        screen = UpdaterScreen.Start
        error = null
    }

    LaunchedEffect(Unit) {
        ContentRepositoryManager.savedConfiguration()?.let { configuration ->
            root = configuration.root; branch = configuration.branch
            if (!withContext(Dispatchers.IO) { ContentRepositoryManager.isAvailable(configuration) }) {
                missingRepositoryPath = configuration.root
                screen = UpdaterScreen.MissingRepository
            } else {
                action(progressStatus) { refresh(); screen = if (status?.kind == RepositoryState.CONFLICT) UpdaterScreen.Conflict else UpdaterScreen.Main }
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = background, contentColor = gray) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), Arrangement.spacedBy(18.dp)) {
            Text(
                screenTitle(screen),
                color = scheme.onBackground,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            )
            if (root != null) {
                Text(
                    root.toString(),
                    color = dim,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
            }
            Spacer(Modifier.height(4.dp))
            if (busy) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(progressMessage.orEmpty(), color = scheme.primary, style = MaterialTheme.typography.bodyMedium)
                }
            }
            CompositionLocalProvider(LocalUpdaterActionsEnabled provides !busy) {
            when (screen) {
                UpdaterScreen.Start -> ChoiceList(stringResource(Res.string.updater_setup_repository), listOf(stringResource(Res.string.updater_choose_location), stringResource(Res.string.updater_help), stringResource(Res.string.updater_exit))) { index ->
                    when (index) {
                        0 -> chooseRoot { screen = UpdaterScreen.Repository }
                        1 -> { helpReturnScreen = UpdaterScreen.Start; screen = UpdaterScreen.Help }
                        else -> onClose()
                    }
                }
                UpdaterScreen.Help -> ChoiceList(
                    stringResource(Res.string.updater_help_question),
                    listOf(stringResource(Res.string.updater_help_what), stringResource(Res.string.updater_help_why), stringResource(Res.string.updater_help_how), stringResource(Res.string.updater_back))
                ) { index ->
                    screen = when (index) {
                        0 -> UpdaterScreen.HelpWhat
                        1 -> UpdaterScreen.HelpWhy
                        2 -> UpdaterScreen.HelpHow
                    else -> helpReturnScreen
                    }
                }
                UpdaterScreen.HelpWhat -> {
                    Text(stringResource(Res.string.updater_help_what_text), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                    ChoiceList(stringResource(Res.string.updater_choose_action), listOf(stringResource(Res.string.updater_back))) { screen = UpdaterScreen.Help }
                }
                UpdaterScreen.HelpWhy -> {
                    Text(stringResource(Res.string.updater_help_why_text), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                    ChoiceList(stringResource(Res.string.updater_choose_action), listOf(stringResource(Res.string.updater_back))) { screen = UpdaterScreen.Help }
                }
                UpdaterScreen.HelpHow -> {
                    Text(stringResource(Res.string.updater_help_how_intro), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(Res.string.updater_help_how_tree),
                        color = scheme.primary,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                    )
                    Text(stringResource(Res.string.updater_help_how_text), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                    ChoiceList(stringResource(Res.string.updater_choose_action), listOf(stringResource(Res.string.updater_back))) { screen = UpdaterScreen.Help }
                }
                UpdaterScreen.Repository -> ChoiceList(stringResource(Res.string.updater_git), listOf(stringResource(Res.string.updater_sign_in), stringResource(Res.string.updater_back))) { index ->
                    if (index == 1) screen = UpdaterScreen.Start else action(progressGithub) {
                        val session = withContext(Dispatchers.IO) { GitHubApi.signIn() }
                        github = session
                        progressMessage = progressRepositories
                        repositories = withContext(Dispatchers.IO) { GitHubApi.repositories(session) }
                        if (repositories.isEmpty()) error = noRepositoriesMessage
                        else screen = UpdaterScreen.Repositories
                    }
                }
                UpdaterScreen.Repositories -> {
                    ChoiceList(
                        question = stringResource(Res.string.updater_choose_repository),
                        options = repositories.map { it.full_name } + stringResource(Res.string.updater_back),
                        supportingText = stringResource(Res.string.updater_account, github?.login.orEmpty())
                    ) { index ->
                        if (index == repositories.size) screen = UpdaterScreen.Repository else action(progressBranches) {
                            val selected = repositories[index]
                            val foundBranches = withContext(Dispatchers.IO) { GitHubApi.branches(github!!, selected) }
                            repository = selected
                            branches = foundBranches
                            branch = selected.default_branch
                            screen = UpdaterScreen.Branches
                        }
                    }
                }
                UpdaterScreen.Branches -> ChoiceList(stringResource(Res.string.updater_choose_branch), branches + stringResource(Res.string.updater_back)) { index ->
                    if (index == branches.size) screen = UpdaterScreen.Repositories else action(progressClone) {
                        branch = branches[index]
                        val selected = repository!!
                        val cloneRoot = withContext(Dispatchers.IO) { ContentRepositoryManager(root!!).clone("https://github.com/${selected.full_name}.git", branch) }
                        root = cloneRoot
                        progressMessage = progressStatus
                        val updated = withContext(Dispatchers.IO) {
                            val settingsManager = SettingsManager()
                            val current = settingsManager.loadSettings()
                            current.copy(
                                songSettings = current.songSettings.copy(storageDirectory = cloneRoot.resolve("Songs").toAbsolutePath().toString()),
                                bibleSettings = current.bibleSettings.copy(storageDirectory = cloneRoot.resolve("Bibles").toAbsolutePath().toString())
                            ).also(settingsManager::saveSettings)
                        }
                            onSettingsChanged(updated)
                            screen = UpdaterScreen.Main
                            refresh()
                    }
                }
                UpdaterScreen.Main -> {
                    val kind = status?.kind
                    status?.let { StatusBlock(it) }
                    when (kind) {
                        RepositoryState.SYNCED -> ChoiceList(stringResource(Res.string.updater_choose_action), listOf(stringResource(Res.string.updater_plan_service), stringResource(Res.string.updater_load_service), stringResource(Res.string.updater_help), stringResource(Res.string.updater_settings))) { index ->
                            when (index) {
                                0 -> screen = UpdaterScreen.Date
                                1 -> action(progressStatus) { services = withContext(Dispatchers.IO) { root?.let { ContentRepositoryManager(it).services() }.orEmpty() }; screen = UpdaterScreen.Services }
                                2 -> { helpReturnScreen = UpdaterScreen.Main; screen = UpdaterScreen.Help }
                                else -> screen = UpdaterScreen.Settings
                            }
                        }
                        RepositoryState.LOCAL -> ChoiceList(
                            stringResource(Res.string.updater_choose_action),
                            listOf(stringResource(Res.string.updater_plan_service), stringResource(Res.string.updater_load_service), stringResource(Res.string.updater_save_sync), stringResource(Res.string.updater_help), stringResource(Res.string.updater_settings))
                        ) { index ->
                            when (index) {
                                0 -> screen = UpdaterScreen.Date
                                1 -> action(progressStatus) { services = withContext(Dispatchers.IO) { root?.let { ContentRepositoryManager(it).services() }.orEmpty() }; screen = UpdaterScreen.Services }
                                2 -> screen = UpdaterScreen.Changes
                                3 -> { helpReturnScreen = UpdaterScreen.Main; screen = UpdaterScreen.Help }
                                else -> screen = UpdaterScreen.Settings
                            }
                        }
                        RepositoryState.REMOTE -> ChoiceList(
                            stringResource(Res.string.updater_choose_action),
                            listOf(stringResource(Res.string.updater_plan_service), stringResource(Res.string.updater_load_service), stringResource(Res.string.updater_sync_remote), stringResource(Res.string.updater_help), stringResource(Res.string.updater_settings))
                        ) { index ->
                            when (index) {
                                0 -> screen = UpdaterScreen.Date
                                1 -> action(progressStatus) { services = withContext(Dispatchers.IO) { root?.let { ContentRepositoryManager(it).services() }.orEmpty() }; screen = UpdaterScreen.Services }
                                2 -> screen = UpdaterScreen.SyncQuestion
                                3 -> { helpReturnScreen = UpdaterScreen.Main; screen = UpdaterScreen.Help }
                                else -> screen = UpdaterScreen.Settings
                            }
                        }
                        RepositoryState.BOTH -> {
                            val hasUncommittedChanges = (status?.staged ?: 0) + (status?.unstaged ?: 0) > 0
                            val options = buildList {
                                add(stringResource(Res.string.updater_plan_service))
                                add(stringResource(Res.string.updater_load_service))
                                if (hasUncommittedChanges) add(stringResource(Res.string.updater_save_sync))
                                add(stringResource(Res.string.updater_sync_remote))
                                add(stringResource(Res.string.updater_help))
                                add(stringResource(Res.string.updater_settings))
                            }
                            ChoiceList(stringResource(Res.string.updater_choose_action), options) { index ->
                                when {
                                    index == 0 -> screen = UpdaterScreen.Date
                                    index == 1 -> action(progressStatus) { services = withContext(Dispatchers.IO) { root?.let { ContentRepositoryManager(it).services() }.orEmpty() }; screen = UpdaterScreen.Services }
                                    hasUncommittedChanges && index == 2 -> screen = UpdaterScreen.Changes
                                    else -> {
                                        val syncIndex = if (hasUncommittedChanges) 3 else 2
                                        when (index - syncIndex) {
                                            0 -> screen = UpdaterScreen.SyncQuestion
                                            1 -> { helpReturnScreen = UpdaterScreen.Main; screen = UpdaterScreen.Help }
                                            else -> screen = UpdaterScreen.Settings
                                        }
                                    }
                                }
                            }
                        }
                        RepositoryState.CONFLICT -> { screen = UpdaterScreen.Conflict }
                        else -> ChoiceList(stringResource(Res.string.updater_choose_action), listOf(stringResource(Res.string.updater_plan_service), stringResource(Res.string.updater_settings), stringResource(Res.string.updater_exit))) { index -> when (index) { 0 -> screen = UpdaterScreen.Date; 1 -> screen = UpdaterScreen.Settings; else -> onClose() } }
                    }
                }
                UpdaterScreen.MissingRepository -> {
                    Text(stringResource(Res.string.updater_repository_missing), color = scheme.error, style = MaterialTheme.typography.titleMedium)
                    missingRepositoryPath?.let {
                        Text(it.toString(), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    }
                    ChoiceList(
                        stringResource(Res.string.updater_choose_action),
                        listOf(stringResource(Res.string.updater_choose_existing_repository), stringResource(Res.string.updater_reconnect_repository), stringResource(Res.string.updater_settings))
                    ) { index ->
                        when (index) {
                            0 -> chooseRoot(after = {
                                val selected = root
                                val config = ContentRepositoryManager.savedConfiguration()
                                if (selected == null || config == null || !ContentRepositoryManager.isAvailable(config.copy(root = selected))) {
                                    error = missingRepositoryError
                                    screen = UpdaterScreen.MissingRepository
                                } else {
                                    ContentRepositoryManager.updateSavedRoot(selected)
                                    branch = config.branch
                                    progressMessage = progressStatus
                                    refresh()
                                    screen = if (status?.kind == RepositoryState.CONFLICT) UpdaterScreen.Conflict else UpdaterScreen.Main
                                }
                            }, initial = null)
                            1 -> disconnectRepository()
                            else -> screen = UpdaterScreen.Settings
                        }
                    }
                }
                UpdaterScreen.Settings -> {
                    val login = GitHubApi.savedLogin()
                    Text(stringResource(Res.string.updater_settings_account, login ?: "—"), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    ChoiceList(stringResource(Res.string.updater_settings_title), listOf(stringResource(Res.string.updater_disconnect_repository), stringResource(Res.string.updater_settings_signout), stringResource(Res.string.updater_back))) { index ->
                        when (index) {
                            0 -> screen = UpdaterScreen.ConfirmDisconnect
                            1 -> screen = UpdaterScreen.ConfirmSignOut
                            else -> screen = UpdaterScreen.Main
                        }
                    }
                }
                UpdaterScreen.ConfirmDisconnect -> ChoiceList(stringResource(Res.string.updater_settings_disconnect_confirm), listOf(stringResource(Res.string.updater_yes), stringResource(Res.string.updater_no))) { index ->
                    if (index == 0) disconnectRepository() else screen = UpdaterScreen.Settings
                }
                UpdaterScreen.ConfirmSignOut -> ChoiceList(stringResource(Res.string.updater_settings_signout_confirm), listOf(stringResource(Res.string.updater_yes), stringResource(Res.string.updater_no))) { index ->
                    if (index == 0) action(progressSignOut) {
                        val credentialsRemoved = withContext(Dispatchers.IO) { GitHubApi.signOut() }
                        disconnectRepository()
                        if (!credentialsRemoved) error = signOutCredentialsWarning
                    } else screen = UpdaterScreen.Settings
                }
                UpdaterScreen.Date -> {
                    val invalidDateMessage = stringResource(Res.string.updater_invalid_date)
                    Text("${'$'} ${stringResource(Res.string.updater_enter_date)}", color = scheme.onBackground, fontFamily = FontFamily.Monospace)
                    OutlinedTextField(value = date, onValueChange = { date = it }, enabled = !busy, singleLine = true, placeholder = { Text(stringResource(Res.string.updater_date_hint), style = terminalStyle) }, textStyle = terminalStyle, colors = terminalFieldColors(), modifier = Modifier.fillMaxWidth())
                    ChoiceList(stringResource(Res.string.updater_create_structure), listOf(stringResource(Res.string.updater_create_structure), stringResource(Res.string.updater_back))) { index ->
                        if (index == 1) screen = UpdaterScreen.Main else if (!ServiceFolders.isValidDate(date)) error = invalidDateMessage else action(progressCreate) {
                            val planPath = withContext(Dispatchers.IO) { ContentRepositoryManager(root!!).createService(date) }
                            val serviceDirectory = requireNotNull(planPath.parent)
                            val updated = withContext(Dispatchers.IO) {
                                val settingsManager = SettingsManager()
                                val current = settingsManager.loadSettings()
                                current.copy(
                                    pictureSettings = current.pictureSettings.copy(storageDirectory = serviceDirectory.resolve("Pictures").toString()),
                                    presentationStorageDirectory = serviceDirectory.resolve("Presentations").toString(),
                                    mediaStorageDirectory = serviceDirectory.resolve("Media").toString()
                                ).also(settingsManager::saveSettings)
                            }
                            onSettingsChanged(updated)
                            completedServiceDirectory = serviceDirectory
                            completedPlanPath = planPath
                            screen = UpdaterScreen.PlanCreated
                            refresh()
                        }
                    }
                }
                UpdaterScreen.PlanCreated -> {
                    val serviceDirectory = requireNotNull(completedServiceDirectory)
                    val planPath = requireNotNull(completedPlanPath)
                    Text(stringResource(Res.string.updater_plan_result_title), color = scheme.primary, style = MaterialTheme.typography.titleLarge)
                    Text(
                        serviceDirectory.parent?.toString().orEmpty(),
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                    Column(
                        Modifier.fillMaxWidth().padding(start = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        FolderTreeLine(serviceDirectory.fileName.toString(), "├──", scheme.primary)
                        FolderTreeLine("Pictures", "│   ├──", scheme.primary)
                        FolderTreeLine("Presentations", "│   ├──", scheme.primary)
                        FolderTreeLine("Media", "│   └──", scheme.primary)
                        FolderTreeLine(planPath.fileName.toString(), "└──", scheme.primary)
                    }
                    Text(stringResource(Res.string.updater_plan_result_plan, planPath), color = scheme.onSurface, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    Text(stringResource(Res.string.updater_plan_result_next), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    ChoiceList(stringResource(Res.string.updater_choose_action), listOf(stringResource(Res.string.updater_continue))) { screen = UpdaterScreen.Main }
                }
                UpdaterScreen.Changes -> {
                    val commitHint = stringResource(Res.string.updater_commit_hint)
                    Text("${'$'} ${stringResource(Res.string.updater_changes_question)}", color = scheme.onBackground, fontFamily = FontFamily.Monospace)
                    OutlinedTextField(value = commitMessage, onValueChange = { commitMessage = it }, enabled = !busy, singleLine = true, placeholder = { Text(stringResource(Res.string.updater_commit_hint), style = terminalStyle) }, textStyle = terminalStyle, colors = terminalFieldColors(), modifier = Modifier.fillMaxWidth())
                    ChoiceList(stringResource(Res.string.updater_commit_push), listOf(stringResource(Res.string.updater_commit_push), stringResource(Res.string.updater_back))) { index ->
                        if (index == 1) screen = UpdaterScreen.Main else if (commitMessage.isBlank()) error = commitHint else action(progressSave) { withContext(Dispatchers.IO) { ContentRepositoryManager(root!!).synchronize(commitMessage.trim()) }; refresh(); screen = UpdaterScreen.SyncResult }
                    }
                }
                UpdaterScreen.SyncQuestion -> {
                    status?.let { StatusBlock(it) }
                    ChoiceList(stringResource(Res.string.updater_sync_question), listOf(stringResource(Res.string.updater_yes), stringResource(Res.string.updater_no))) { index ->
                        if (index == 1) screen = UpdaterScreen.Main else action(progressSync) { withContext(Dispatchers.IO) { ContentRepositoryManager(root!!).synchronize() }; refresh(); screen = if (conflicts.isEmpty()) UpdaterScreen.SyncResult else UpdaterScreen.Conflict }
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
                            when (choice) {
                                0, 1 -> action(progressConflict) {
                                    withContext(Dispatchers.IO) { ContentRepositoryManager(root!!).resolveConflict(conflict.path, choice == 1) }
                                    refresh()
                                    if (status?.kind != RepositoryState.CONFLICT) screen = UpdaterScreen.Main
                                }
                                else -> { manualConflictPath = conflict.path; screen = UpdaterScreen.ManualConflict }
                            }
                        }
                    }
                    ChoiceList(stringResource(Res.string.updater_choose_action), listOf(stringResource(Res.string.updater_settings))) { screen = UpdaterScreen.Settings }
                }
                UpdaterScreen.ManualConflict -> {
                    Text(stringResource(Res.string.updater_conflict_manual_help, manualConflictPath.orEmpty()), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                    ChoiceList(stringResource(Res.string.updater_choose_action), listOf(stringResource(Res.string.updater_yes), stringResource(Res.string.updater_no))) { index ->
                        if (index == 1) screen = UpdaterScreen.Conflict else action(progressConflict) {
                            val path = requireNotNull(manualConflictPath)
                            withContext(Dispatchers.IO) { ContentRepositoryManager(root!!).markConflictResolved(path) }
                            refresh()
                            screen = if (status?.kind == RepositoryState.CONFLICT) UpdaterScreen.Conflict else UpdaterScreen.Main
                        }
                    }
                }
                UpdaterScreen.Services -> {
                    val planReady = stringResource(Res.string.updater_plan_ready)
                    val planMissing = stringResource(Res.string.updater_plan_missing)
                    val loadFailed = stringResource(Res.string.updater_load_failed)
                    ChoiceList(stringResource(Res.string.updater_choose_service), services.map { "${it.date}  ·  ${if (it.plan != null) planReady else planMissing}" } + stringResource(Res.string.updater_back)) { index ->
                        if (index == services.size) screen = UpdaterScreen.Main else {
                            val service = services[index]
                            val planPath = service.plan
                            if (planPath == null) error = planMissing else action(progressLoad) {
                                val updated = withContext(Dispatchers.IO) {
                                    val settingsManager = SettingsManager()
                                    val current = settingsManager.loadSettings()
                                    var savedSettings: AppSettings? = null
                                    ContentRepositoryManager(root!!).connect(service, root!!) { songs, bibles, pictures, presentations, media ->
                                        savedSettings = current.copy(
                                            songSettings = current.songSettings.copy(storageDirectory = songs.toString()),
                                            bibleSettings = current.bibleSettings.copy(storageDirectory = bibles.toString()),
                                            pictureSettings = current.pictureSettings.copy(storageDirectory = pictures.toString()),
                                            presentationStorageDirectory = presentations.toString(),
                                            mediaStorageDirectory = media.toString()
                                        )
                                        settingsManager.saveSettings(requireNotNull(savedSettings))
                                    }
                                    requireNotNull(savedSettings)
                                }
                                onSettingsChanged(updated)
                                if (onLoadPlan(planPath.toString())) {
                                    completedServiceDirectory = service.directory
                                    completedPlanPath = planPath
                                    screen = UpdaterScreen.ServiceLoaded
                                } else {
                                    error = loadFailed
                                }
                            }
                        }
                    }
                }
                UpdaterScreen.ServiceLoaded -> {
                    val serviceDirectory = requireNotNull(completedServiceDirectory)
                    val planPath = requireNotNull(completedPlanPath)
                    Text(stringResource(Res.string.updater_loaded_result_title), color = scheme.primary, style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(Res.string.updater_loaded_result_directory, serviceDirectory), color = scheme.onSurface, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                    Text(stringResource(Res.string.updater_loaded_result_plan, planPath), color = scheme.onSurface, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                    Text(stringResource(Res.string.updater_loaded_result_next), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    ChoiceList(stringResource(Res.string.updater_choose_action), listOf(stringResource(Res.string.updater_continue))) { screen = UpdaterScreen.Main }
                }
            }
            }
            error?.let { Text("  ! $it", color = scheme.error, fontFamily = FontFamily.Monospace) }
        }
    }
}

@Composable
private fun FolderTreeLine(name: String, branch: String, color: Color) {
    Text(
        "$branch $name",
        color = color,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    )
}

@Composable
private fun ChoiceList(question: String, options: List<String>, onChoice: (Int) -> Unit) {
    ChoiceList(question = question, options = options, supportingText = null, onChoice = onChoice)
}

@Composable
private fun ChoiceList(question: String, options: List<String>, supportingText: String?, onChoice: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val enabled = LocalUpdaterActionsEnabled.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            question,
            color = scheme.onBackground,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
        )
        supportingText?.let {
            Text(it, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(4.dp))
        options.forEachIndexed { index, option ->
            Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { onChoice(index) }.padding(vertical = 10.dp, horizontal = 12.dp)) {
                Text(
                    "${(index + 1).toString().padStart(2, '0')}  ",
                    color = scheme.primary,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                )
                Text(
                    option,
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
                )
            }
        }
    }
}

@Composable
private fun terminalFieldColors() = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline, focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, cursorColor = MaterialTheme.colorScheme.primary)

@Composable
private fun StatusBlock(status: RepositoryStatus) {
    val scheme = MaterialTheme.colorScheme
    val color = when (status.kind) {
        RepositoryState.CONFLICT -> scheme.error
        RepositoryState.SYNCED -> scheme.primary
        else -> scheme.onSurfaceVariant
    }
    val kind = when (status.kind) {
        RepositoryState.SYNCED -> stringResource(Res.string.updater_status_synced)
        RepositoryState.LOCAL -> stringResource(Res.string.updater_status_local)
        RepositoryState.REMOTE -> stringResource(Res.string.updater_status_remote)
        RepositoryState.BOTH -> stringResource(Res.string.updater_status_both)
        RepositoryState.CONFLICT -> stringResource(Res.string.updater_status_conflict)
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(kind, color = color, style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium))
        Text(
            stringResource(Res.string.updater_status_line, kind, status.staged + status.unstaged, status.ahead, status.behind),
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )
    }
}

@Composable
private fun screenTitle(screen: UpdaterScreen): String = when (screen) {
    UpdaterScreen.Start, UpdaterScreen.Help, UpdaterScreen.HelpWhat, UpdaterScreen.HelpWhy, UpdaterScreen.HelpHow,
    UpdaterScreen.Repository, UpdaterScreen.Repositories, UpdaterScreen.Branches -> stringResource(Res.string.updater_title_repository)
    UpdaterScreen.Main, UpdaterScreen.SyncQuestion -> stringResource(Res.string.updater_title_status)
    UpdaterScreen.MissingRepository -> stringResource(Res.string.updater_repository_missing)
    UpdaterScreen.Settings, UpdaterScreen.ConfirmDisconnect, UpdaterScreen.ConfirmSignOut -> stringResource(Res.string.updater_settings_title)
    UpdaterScreen.Date, UpdaterScreen.PlanCreated -> stringResource(Res.string.updater_title_plan)
    UpdaterScreen.Changes -> stringResource(Res.string.updater_title_changes)
    UpdaterScreen.SyncResult -> stringResource(Res.string.updater_title_sync_complete)
    UpdaterScreen.Services, UpdaterScreen.ServiceLoaded -> stringResource(Res.string.updater_title_services)
    UpdaterScreen.Conflict, UpdaterScreen.ManualConflict -> stringResource(Res.string.updater_title_conflict)
}
