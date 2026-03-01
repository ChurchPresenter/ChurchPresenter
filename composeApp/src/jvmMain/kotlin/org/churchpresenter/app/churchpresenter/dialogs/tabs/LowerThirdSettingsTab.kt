package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.browse_directory
import churchpresenter.composeapp.generated.resources.bottom
import churchpresenter.composeapp.generated.resources.display_lower_third
import churchpresenter.composeapp.generated.resources.generate_lower_third
import churchpresenter.composeapp.generated.resources.import_lottie_file
import churchpresenter.composeapp.generated.resources.left
import churchpresenter.composeapp.generated.resources.lottie_files
import churchpresenter.composeapp.generated.resources.lottie_select_preset
import churchpresenter.composeapp.generated.resources.no_directory_selected
import churchpresenter.composeapp.generated.resources.no_lottie_files
import churchpresenter.composeapp.generated.resources.remove_lottie_file
import churchpresenter.composeapp.generated.resources.right
import churchpresenter.composeapp.generated.resources.storage_directory
import churchpresenter.composeapp.generated.resources.top
import churchpresenter.composeapp.generated.resources.window_position
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import kotlinx.coroutines.delay
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.viewmodel.LowerThirdSettingsViewModel
import org.churchpresenter.app.churchpresenter.utils.createFileChooser
import org.jetbrains.compose.resources.stringResource
import java.awt.Dimension
import java.awt.Window
import java.io.File
import javax.swing.JDialog
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.SwingUtilities

@Composable
fun LowerThirdSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
) {
    val viewModel = remember { LowerThirdSettingsViewModel() }

    // Keep viewModel folder in sync with settings
    LaunchedEffect(settings.streamingSettings.lowerThirdFolder) {
        val folder = settings.streamingSettings.lowerThirdFolder
        if (viewModel.lottieFolder != folder) viewModel.setFolder(folder)
    }

    val lottieFolder = viewModel.lottieFolder
    val lottieFilesInDirectory = remember(lottieFolder, viewModel.refreshTrigger) {
        viewModel.filesInDirectory()
    }
    val selectedFile = viewModel.selectedFile

    // Preview — debounced so we don't re-read on every keystroke
    var debouncedPath by remember { mutableStateOf(viewModel.importSourcePath()) }
    LaunchedEffect(selectedFile, lottieFolder) {
        delay(400)
        debouncedPath = viewModel.importSourcePath()
    }
    val previewJsonContent = remember(debouncedPath) { viewModel.previewJsonContent() }

    val composition by rememberLottieComposition(key = previewJsonContent) {
        LottieCompositionSpec.JsonString(previewJsonContent.ifBlank { "{}" })
    }
    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = previewJsonContent.isNotBlank(),
        iterations = Int.MAX_VALUE
    )

    val noDirectorySelectedStr = stringResource(Res.string.no_directory_selected)
    val noLottieFilesStr = stringResource(Res.string.no_lottie_files)

    Row(modifier = Modifier.fillMaxSize()) {

        // ── Left panel ──────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 600.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(start = 15.dp, end = 15.dp, top = 8.dp, bottom = 15.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Storage Directory
            SectionHeader(stringResource(Res.string.storage_directory))
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = lottieFolder.ifEmpty { noDirectorySelectedStr },
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ModernButton(
                    text = stringResource(Res.string.browse_directory),
                    backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    onClick = {
                        SwingUtilities.invokeLater {
                            val parentWindow = Window.getWindows().firstOrNull { it.isActive }
                            val fileManager = org.churchpresenter.app.churchpresenter.viewmodel.FileManager()
                            val selectedDir = fileManager.chooseDirectory(
                                currentDirectory = lottieFolder,
                                parentWindow = parentWindow
                            )
                            selectedDir?.let { dir ->
                                viewModel.setFolder(dir)
                                onSettingsChange { s ->
                                    s.copy(streamingSettings = s.streamingSettings.copy(lowerThirdFolder = dir))
                                }
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(15.dp))

            // Lottie Files list
            SectionHeader(stringResource(Res.string.lottie_files))
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(8.dp)
                ) {
                    if (lottieFilesInDirectory.isEmpty()) {
                        Text(
                            text = if (lottieFolder.isEmpty()) noDirectorySelectedStr else noLottieFilesStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        lottieFilesInDirectory.forEach { fileName ->
                            val isSelected = fileName == selectedFile
                            Text(
                                text = fileName,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else Color.Transparent
                                    )
                                    .clickable { viewModel.selectFile(fileName) }
                                    .padding(vertical = 4.dp, horizontal = 8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Import / Remove buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModernButton(
                    text = stringResource(Res.string.import_lottie_file),
                    backgroundColor = MaterialTheme.colorScheme.inverseSurface,
                    onClick = {
                        SwingUtilities.invokeLater {
                            val parentWindow = Window.getWindows().firstOrNull { it.isActive }
                            if (lottieFolder.isEmpty()) return@invokeLater
                            val chooser = createFileChooser {
                                fileSelectionMode = JFileChooser.FILES_ONLY
                                dialogTitle = "Select Lottie JSON File"
                                fileFilter = FileNameExtensionFilter("Lottie JSON (*.json)", "json")
                            }
                            if (chooser.showOpenDialog(parentWindow) == JFileChooser.APPROVE_OPTION) {
                                viewModel.importFile(chooser.selectedFile.absolutePath)
                            }
                        }
                    }
                )
                ModernButton(
                    text = stringResource(Res.string.remove_lottie_file),
                    backgroundColor = MaterialTheme.colorScheme.errorContainer,
                    onClick = { viewModel.removeSelectedFile() }
                )
                ModernButton(
                    text = stringResource(Res.string.generate_lower_third),
                    backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                    onClick = {
                        SwingUtilities.invokeLater {
                            openLottieGeneratorDialog(
                                parentWindow = Window.getWindows().firstOrNull { it.isActive },
                                outputFolder = lottieFolder.ifEmpty { null },
                                onFileSaved = { viewModel.onFileSavedFromGenerator() }
                            )
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // ── Right panel — live preview ───────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = selectedFile ?: stringResource(Res.string.lottie_select_preset),
                style = MaterialTheme.typography.bodyMedium,
                color = if (previewJsonContent.isNotBlank()) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            )

            // Lottie preview box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black, RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (previewJsonContent.isNotBlank()) {
                    Image(
                        painter = rememberLottiePainter(
                            composition = composition,
                            progress = { progress }
                        ),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = stringResource(Res.string.lottie_select_preset),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }
            }

            // ── Window Position ──────────────────────────────────────
            HorizontalDivider()
            Text(
                text = stringResource(Res.string.window_position),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            )

            val streaming = settings.streamingSettings

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.top), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                NumberSettingsTextField(
                    initialText = streaming.windowTop,
                    onValueChange = { v -> onSettingsChange { s -> s.copy(streamingSettings = s.streamingSettings.copy(windowTop = v)) } },
                    range = 0..10000
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(Res.string.left), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    NumberSettingsTextField(
                        initialText = streaming.windowLeft,
                        onValueChange = { v -> onSettingsChange { s -> s.copy(streamingSettings = s.streamingSettings.copy(windowLeft = v)) } },
                        range = 0..10000
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .height(80.dp)
                        .background(Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.333f)
                            .align(Alignment.BottomCenter)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                    ) {
                        Text(
                            text = stringResource(Res.string.display_lower_third),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(Res.string.right), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    NumberSettingsTextField(
                        initialText = streaming.windowRight,
                        onValueChange = { v -> onSettingsChange { s -> s.copy(streamingSettings = s.streamingSettings.copy(windowRight = v)) } },
                        range = 0..10000
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.bottom), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                NumberSettingsTextField(
                    initialText = streaming.windowBottom,
                    onValueChange = { v -> onSettingsChange { s -> s.copy(streamingSettings = s.streamingSettings.copy(windowBottom = v)) } },
                    range = 0..10000
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Column {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ModernButton(
    text: String,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * Opens the Lottie Generator HTML tool in a JavaFX WebView dialog.
 * Intercepts download anchor clicks from the page and saves the file
 * directly to [outputFolder] instead of the system Downloads folder.
 */
private fun openLottieGeneratorDialog(
    parentWindow: Window?,
    outputFolder: String?,
    onFileSaved: () -> Unit
) {
    // Locate lottie-generator.html — check multiple locations:
    // 1. Bundled inside the packaged app's resources (Contents/app/resources/Lottie-Gen on macOS)
    // 2. Relative to the app executable (for DMG installs)
    // 3. Dev working directory
    // 4. Paths relative to the JAR / class location
    val appResourcesDir = System.getProperty("compose.application.resources.dir")
    val executablePath = ProcessHandle.current().info().command().orElse(null)
        ?.let { File(it).parentFile }
    // Walk up from working dir to find project root containing Lottie-Gen/
    fun findProjectRoot(): File? {
        var dir = File(System.getProperty("user.dir"))
        repeat(6) {
            val candidate = File(dir, "Lottie-Gen/lottie-generator.html")
            if (candidate.exists()) return dir
            dir = dir.parentFile ?: return null
        }
        return null
    }
    val projectRoot = findProjectRoot()
    val htmlFile = listOfNotNull(
        appResourcesDir?.let { File(it, "Lottie-Gen/lottie-generator.html") },
        executablePath?.let { File(it, "../app/resources/Lottie-Gen/lottie-generator.html") },
        executablePath?.let { File(it, "Lottie-Gen/lottie-generator.html") },
        executablePath?.let { File(it, "../../Lottie-Gen/lottie-generator.html") },
        projectRoot?.let { File(it, "Lottie-Gen/lottie-generator.html") },
        File("Lottie-Gen/lottie-generator.html"),
        File(System.getProperty("user.dir"), "Lottie-Gen/lottie-generator.html"),
        // Resolve relative to this class's code source (works in packaged JARs)
        try {
            val src = object {}.javaClass.protectionDomain?.codeSource?.location?.toURI()
            src?.let { File(File(it).parentFile, "Lottie-Gen/lottie-generator.html") }
        } catch (_: Exception) { null },
        try {
            val src = object {}.javaClass.protectionDomain?.codeSource?.location?.toURI()
            src?.let { File(File(it).parentFile?.parentFile, "Lottie-Gen/lottie-generator.html") }
        } catch (_: Exception) { null }
    ).firstOrNull { it.exists() }

    if (htmlFile == null) {
        javax.swing.JOptionPane.showMessageDialog(
            parentWindow,
            "Lottie Generator not found.\nExpected at: Lottie-Gen/lottie-generator.html",
            "Generator Not Found",
            javax.swing.JOptionPane.ERROR_MESSAGE
        )
        return
    }

    // Ensure JavaFX toolkit is running
    try {
        javafx.application.Platform.setImplicitExit(false)
        javafx.application.Platform.startup { }
    } catch (_: IllegalStateException) {
        // Already initialised — fine
    }

    // Build the dialog on the Swing EDT
    val dialog = JDialog().apply {
        title = "Lower Third Generator"
        isModal = false
        preferredSize = Dimension(1200, 800)
        defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
    }

    // JFXPanel bridges Swing ↔ JavaFX
    val jfxPanel = javafx.embed.swing.JFXPanel()
    dialog.contentPane.add(jfxPanel, java.awt.BorderLayout.CENTER)
    dialog.pack()
    dialog.setLocationRelativeTo(parentWindow)

    // Build the JavaFX scene on the FX thread
    javafx.application.Platform.runLater {
        val webView = javafx.scene.web.WebView()
        val engine = webView.engine

        // After page loads, inject a JS bridge that intercepts <a download> clicks
        engine.loadWorker.stateProperty().addListener(
            javafx.beans.value.ChangeListener { _, _, newState ->
                if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                    val win = engine.executeScript("window") as? netscape.javascript.JSObject ?: return@ChangeListener

                    // Kotlin object exposed to JavaScript
                    val bridge = object {
                        @Suppress("unused")
                        fun save(filename: String, jsonContent: String) {
                            SwingUtilities.invokeLater {
                                val dest = outputFolder
                                    ?.let { File(it) }
                                    ?.takeIf { it.exists() }
                                    ?.let { File(it, filename) }
                                    ?: run {
                                        val chooser = JFileChooser().apply {
                                            dialogTitle = "Save Lottie File"
                                            fileFilter = FileNameExtensionFilter("Lottie JSON (*.json)", "json")
                                            selectedFile = File(filename)
                                        }
                                        if (chooser.showSaveDialog(dialog) != JFileChooser.APPROVE_OPTION) return@invokeLater
                                        chooser.selectedFile
                                    }
                                dest.writeText(jsonContent)
                                onFileSaved()
                                javax.swing.JOptionPane.showMessageDialog(
                                    dialog,
                                    "Saved: ${dest.name}",
                                    "Saved",
                                    javax.swing.JOptionPane.INFORMATION_MESSAGE
                                )
                            }
                        }
                    }

                    win.setMember("_jvmBridge", bridge)
                    // Bridge is now live — the HTML's download() checks window._jvmBridge directly
                }
            }
        )

        engine.load(htmlFile.toURI().toString())

        val scene = javafx.scene.Scene(
            javafx.scene.layout.StackPane(webView),
            1180.0, 760.0
        )
        jfxPanel.scene = scene
    }

    dialog.isVisible = true
}






