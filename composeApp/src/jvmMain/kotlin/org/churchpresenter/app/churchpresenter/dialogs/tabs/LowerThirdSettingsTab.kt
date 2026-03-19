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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.lower_third_generator
import churchpresenter.composeapp.generated.resources.loading_generator
import churchpresenter.composeapp.generated.resources.preview_will_appear_here
import churchpresenter.composeapp.generated.resources.server_not_running_title
import churchpresenter.composeapp.generated.resources.server_not_running_message
import churchpresenter.composeapp.generated.resources.no_folder_title
import churchpresenter.composeapp.generated.resources.no_folder_message
import churchpresenter.composeapp.generated.resources.choose_logo_image
import churchpresenter.composeapp.generated.resources.images_filter
import churchpresenter.composeapp.generated.resources.bottom
import churchpresenter.composeapp.generated.resources.browse_directory
import churchpresenter.composeapp.generated.resources.display_lower_third
import churchpresenter.composeapp.generated.resources.generate_lower_third
import churchpresenter.composeapp.generated.resources.left
import churchpresenter.composeapp.generated.resources.lottie_files
import churchpresenter.composeapp.generated.resources.lottie_select_preset
import churchpresenter.composeapp.generated.resources.no_directory_selected
import churchpresenter.composeapp.generated.resources.no_lottie_files
import churchpresenter.composeapp.generated.resources.remove_lottie_file
import churchpresenter.composeapp.generated.resources.right
import churchpresenter.composeapp.generated.resources.top
import churchpresenter.composeapp.generated.resources.window_position
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import javafx.application.Platform
import javafx.beans.value.ChangeListener
import javafx.embed.swing.JFXPanel
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.viewmodel.LowerThirdSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Window
import java.io.File
import javafx.application.Platform
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javafx.beans.value.ChangeListener
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.scene.web.WebView
import javax.swing.JDialog
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.writeText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.swing.JSplitPane
import javax.swing.SwingUtilities
import netscape.javascript.JSObject
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.awt.ComposePanel
import io.github.alexzhirkevich.compottie.LottieCompositionSpec as LottieSpec2

@Composable
fun LowerThirdSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    serverUrl: String = "",
    isDarkTheme: Boolean = true
) {
    val scope = rememberCoroutineScope()
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
    val generatorTitle = stringResource(Res.string.lower_third_generator)
    val generatorLoading = stringResource(Res.string.loading_generator)
    val serverNotRunningTitleStr = stringResource(Res.string.server_not_running_title)
    val serverNotRunningMessageStr = stringResource(Res.string.server_not_running_message)
    val noFolderTitleStr = stringResource(Res.string.no_folder_title)
    val noFolderMessageStr = stringResource(Res.string.no_folder_message)
    val chooseLogoTitleStr = stringResource(Res.string.choose_logo_image)
    val imagesFilterStr = stringResource(Res.string.images_filter)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {

        // ── Left panel ──────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(0.48f)
                .widthIn(min = 400.dp, max = 450.dp)
                .heightIn(min = 600.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(start = 15.dp, end = 15.dp, top = 8.dp, bottom = 15.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModernButton(
                    text = stringResource(Res.string.remove_lottie_file),
                    backgroundColor = MaterialTheme.colorScheme.errorContainer,
                    onClick = { viewModel.removeSelectedFile() }
                )
                ModernButton(
                    text = stringResource(Res.string.generate_lower_third),
                    backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                    onClick = {
                        val genTitle = generatorTitle
                        val genLoading = generatorLoading
                        val srvTitle = serverNotRunningTitleStr
                        val srvMsg = serverNotRunningMessageStr
                        val nfTitle = noFolderTitleStr
                        val nfMsg = noFolderMessageStr
                        val logoTitle = chooseLogoTitleStr
                        val imgFilter = imagesFilterStr
                        SwingUtilities.invokeLater {
                            openLottieGeneratorDialog(
                                parentScope = scope,
                                parentWindow = Window.getWindows().firstOrNull { it.isActive },
                                onFileSaved = { viewModel.onFileSavedFromGenerator() },
                                serverUrl = serverUrl,
                                isDarkTheme = isDarkTheme,
                                lowerThirdFolder = settings.streamingSettings.lowerThirdFolder,
                                generatorDialogTitle = genTitle,
                                loadingText = genLoading,
                                serverNotRunningTitle = srvTitle,
                                serverNotRunningMessage = srvMsg,
                                noFolderTitle = nfTitle,
                                noFolderMessage = nfMsg,
                                chooseLogoTitle = logoTitle,
                                imagesFilterText = imgFilter
                            )
                        }
                    }
                )
            }
        }

        // ── Right panel — live preview ───────────────────────────────
        Column(
            modifier = Modifier
                .weight(0.48f)
                .widthIn(min = 400.dp, max = 450.dp)
                .heightIn(min = 600.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(start = 15.dp, end = 15.dp, top = 8.dp, bottom = 15.dp),
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
                            .fillMaxHeight(settings.projectionSettings.lowerThirdHeightPercent / 100f)
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

/** Shared state for the Compose-based Lottie preview in the generator dialog. */
private object LottiePreviewState {
    val jsonContent = mutableStateOf("")
    val statusText = mutableStateOf("")
    val isDark = mutableStateOf(true)
}

/**
 * Cached generator dialog — keeps the WebView alive between uses so that
 * re-opening is near-instant after the first (slow) JavaFX cold-start.
 */
private object LottieGeneratorCache {
    var dialog: JDialog? = null
    var loadedUrl: String? = null
    @Volatile var onFileSavedCallback: (() -> Unit)? = null
    @Volatile var lowerThirdFolderRef: String = ""
    var bridgeRef: Any? = null // prevent GC
}

internal fun openLottieGeneratorDialog(
    parentScope: CoroutineScope,
    parentWindow: Window?,
    onFileSaved: () -> Unit,
    serverUrl: String = "",
    isDarkTheme: Boolean = true,
    lowerThirdFolder: String = "",
    generatorDialogTitle: String = "Lower Third Generator",
    loadingText: String = "Loading generator...",
    serverNotRunningTitle: String = "Server Not Running",
    serverNotRunningMessage: String = "Please start the API server to generate lower thirds.\nGenerated files will be saved to the folder configured in Settings.",
    noFolderTitle: String = "No Folder",
    noFolderMessage: String = "No folder configured.\nSet a Lower Third folder in Settings first.",
    chooseLogoTitle: String = "Choose Logo Image",
    imagesFilterText: String = "Images"
) {
    val loadUrl: String = if (serverUrl.isNotEmpty()) {
        val port = java.net.URI(serverUrl).port.takeIf { it > 0 } ?: 8765
        "http://127.0.0.1:${port + 1}/lottie-generator.html"
    } else {
        JOptionPane.showMessageDialog(
            parentWindow,
            serverNotRunningMessage,
            serverNotRunningTitle,
            JOptionPane.INFORMATION_MESSAGE
        )
        return
    }

    // Update mutable refs so the cached bridge always uses latest values
    LottieGeneratorCache.onFileSavedCallback = onFileSaved
    LottieGeneratorCache.lowerThirdFolderRef = lowerThirdFolder
    LottiePreviewState.isDark.value = isDarkTheme

    // Re-use existing dialog if the URL hasn't changed
    val cached = LottieGeneratorCache.dialog
    if (cached != null && LottieGeneratorCache.loadedUrl == loadUrl && cached.isDisplayable) {
        // Apply theme in case it changed
        Platform.runLater {
            try {
                // Navigate: CardLayout panel → JSplitPane → JFXPanel (left component)
                val cardPanel = cached.contentPane.getComponent(0) as? JPanel ?: return@runLater
                val splitPane = cardPanel.components.filterIsInstance<JSplitPane>().firstOrNull() ?: return@runLater
                val jfxPanel = splitPane.leftComponent as? JFXPanel ?: return@runLater
                val webView = (jfxPanel.scene?.root as? StackPane)?.children?.firstOrNull() as? WebView ?: return@runLater
                val themeMode = if (isDarkTheme) "dark" else "light"
                webView.engine.executeScript(
                    "if(typeof applyUITheme==='function') applyUITheme('$themeMode');"
                )
            } catch (_: Exception) {}
        }
        cached.setLocationRelativeTo(parentWindow)
        cached.isVisible = true
        cached.toFront()
        return
    }

    // Ensure JavaFX toolkit is initialised
    try {
        Platform.setImplicitExit(false)
        Platform.startup { }
    } catch (_: IllegalStateException) {}

    val dialog = JDialog().apply {
        title = generatorDialogTitle
        isModal = false
        preferredSize = Dimension(1200, 800)
        defaultCloseOperation = JDialog.HIDE_ON_CLOSE
        // Inherit the app icon from the parent window or load from app resources
        try {
            val icons = parentWindow?.iconImages
            if (!icons.isNullOrEmpty()) {
                iconImages = icons
            } else {
                val resDir = System.getProperty("compose.application.resources.dir")
                if (resDir != null) {
                    val iconFile = File(resDir, "icon-128.png").takeIf { it.exists() }
                        ?: File(resDir, "icon-64.png").takeIf { it.exists() }
                    if (iconFile != null) {
                        iconImages = listOf(javax.imageio.ImageIO.read(iconFile))
                    }
                }
            }
        } catch (_: Exception) {}
    }

    val cards = CardLayout()
    val cardPanel = JPanel(cards)

    // Loading panel — centered progress bar + label
    val loadingPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        val glue1 = javax.swing.Box.createVerticalGlue()
        val label = JLabel(loadingText).apply {
            alignmentX = JLabel.CENTER_ALIGNMENT
            font = font.deriveFont(16f)
        }
        val progress = JProgressBar().apply {
            isIndeterminate = true
            alignmentX = JProgressBar.CENTER_ALIGNMENT
            maximumSize = Dimension(300, 20)
        }
        val spacer = javax.swing.Box.createRigidArea(Dimension(0, 12))
        val glue2 = javax.swing.Box.createVerticalGlue()
        add(glue1); add(label); add(spacer); add(progress); add(glue2)
    }

    // Content panel: JFXPanel (config sidebar) + ComposePanel (Lottie preview)
    val jfxPanel = JFXPanel()
    val composePreviewPanel = ComposePanel().apply {
        setContent {
            GeneratorPreviewContent()
        }
    }

    val contentPanel = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, jfxPanel, composePreviewPanel).apply {
        dividerLocation = 440
        isOneTouchExpandable = false
        dividerSize = 4
    }

    cardPanel.add(loadingPanel, "loading")
    cardPanel.add(contentPanel, "content")
    cards.show(cardPanel, "loading")

    dialog.contentPane.add(cardPanel, BorderLayout.CENTER)
    dialog.pack()
    dialog.setLocationRelativeTo(parentWindow)

    Platform.runLater {
        val webView = WebView()
        val engine = webView.engine

        engine.setOnError { e -> System.err.println("WebView error: ${e.message}") }
        engine.loadWorker.exceptionProperty().addListener { _, _, ex ->
            if (ex != null) System.err.println("WebView load exception: $ex")
        }

        val bridge = object {
            @Suppress("unused")
            fun save(baseName: String, jsonContent: String) {
                SwingUtilities.invokeLater {
                    try {
                        val folder = LottieGeneratorCache.lowerThirdFolderRef
                        val targetDir = folder.takeIf { it.isNotEmpty() }
                            ?.let { File(it) }
                            ?.takeIf { it.exists() && it.isDirectory }
                        if (targetDir != null) {
                            // Sanitise base name: remove characters invalid in Windows file names
                            val safeName = baseName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "lower-third" }
                            var num = 1
                            var targetFile = File(targetDir, "$safeName - %02d.json".format(num))
                            while (targetFile.exists()) {
                                num++
                                targetFile = File(targetDir, "$safeName - %02d.json".format(num))
                            }
                            targetFile.writeText(jsonContent, Charsets.UTF_8)
                            LottieGeneratorCache.onFileSavedCallback?.invoke()
                        } else {
                            javax.swing.JOptionPane.showMessageDialog(
                                dialog,
                                noFolderMessage,
                                noFolderTitle,
                                javax.swing.JOptionPane.WARNING_MESSAGE
                            )
                        }
                    } catch (e: Exception) {
                        System.err.println("Lower third save error: $e")
                        javax.swing.JOptionPane.showMessageDialog(
                            dialog,
                            "Failed to save: ${e.message}",
                            "Save Error",
                            javax.swing.JOptionPane.ERROR_MESSAGE
                        )
                    }
                }
            }

            @Suppress("unused")
            fun preview(jsonStr: String) {
                LottiePreviewState.jsonContent.value = jsonStr
            }

            @Suppress("unused")
            fun status(msg: String) {
                LottiePreviewState.statusText.value = msg
            }

            @Suppress("unused")
            fun chooseLogo() {
                SwingUtilities.invokeLater {
                    val folder = LottieGeneratorCache.lowerThirdFolderRef
                    if (folder.isEmpty()) {
                        javax.swing.JOptionPane.showMessageDialog(
                            dialog,
                            noFolderMessage,
                            noFolderTitle,
                            javax.swing.JOptionPane.WARNING_MESSAGE
                        )
                        Platform.runLater {
                            engine.executeScript("document.getElementById('logoSelect').value='';")
                        }
                        return@invokeLater
                    }
                    val chooser = javax.swing.JFileChooser().apply {
                        dialogTitle = chooseLogoTitle
                        fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
                            imagesFilterText, "png", "jpg", "jpeg", "gif", "svg", "webp"
                        )
                    }
                    if (chooser.showOpenDialog(dialog) == javax.swing.JFileChooser.APPROVE_OPTION) {
                        val file = chooser.selectedFile
                        val bytes = file.readBytes()
                        val mime = when (file.extension.lowercase()) {
                            "png" -> "image/png"
                            "jpg", "jpeg" -> "image/jpeg"
                            "gif" -> "image/gif"
                            "svg" -> "image/svg+xml"
                            "webp" -> "image/webp"
                            else -> "image/png"
                        }
                        val dataUrl = "data:$mime;base64,${java.util.Base64.getEncoder().encodeToString(bytes)}"
                        Platform.runLater {
                            engine.executeScript(
                                "if(typeof onLogoChosen==='function') onLogoChosen(${escapeJs(file.name)}, ${escapeJs(dataUrl)});"
                            )
                        }
                    }
                }
            }
        }
        LottieGeneratorCache.bridgeRef = bridge

        val stateListener = ChangeListener<Worker.State> { _, _, newState ->
            if (newState == Worker.State.FAILED) {
                System.err.println("WebView FAILED to load: ${engine.loadWorker.exception}")
                SwingUtilities.invokeLater {
                    cards.show(cardPanel, "content")
                    javax.swing.JOptionPane.showMessageDialog(
                        dialog,
                        "Failed to load the generator.\n${engine.loadWorker.exception?.message ?: "Unknown error"}",
                        generatorDialogTitle,
                        javax.swing.JOptionPane.ERROR_MESSAGE
                    )
                }
            }
            if (newState == Worker.State.SUCCEEDED) {
                val win = engine.executeScript("window") as? JSObject ?: return@ChangeListener
                win.setMember("_jvmBridge", bridge)

                engine.executeScript(
                    "var btn = document.getElementById('btnDownload');" +
                    "if (btn) { var newBtn = btn.cloneNode(true); btn.parentNode.replaceChild(newBtn, btn);" +
                    "  newBtn.textContent = 'Save Lower Third';" +
                    "  newBtn.addEventListener('click', function() { try { download(); } catch(e) { console.error(e); } try { savePreset(); } catch(e) { console.error(e); } }); }"
                )

                // Hide the WebView preview area and expand config panel to fill
                engine.executeScript(
                    "var pa=document.querySelector('.preview-area');if(pa)pa.style.display='none';" +
                    "var pn=document.querySelector('.panel');if(pn){pn.style.width='100%';pn.style.minWidth='100%';}"
                )

                val themeMode = if (isDarkTheme) "dark" else "light"
                engine.executeScript(
                    "if(typeof applyUITheme==='function') applyUITheme('$themeMode');" +
                    "var s=document.createElement('style');s.textContent='#themeSwitcher{display:none!important}';" +
                    "document.head.appendChild(s);"
                )

                // Switch from loading to content
                SwingUtilities.invokeLater { cards.show(cardPanel, "content") }
            }
        }

        engine.loadWorker.stateProperty().addListener(stateListener)
        engine.load(loadUrl)

        jfxPanel.scene = Scene(StackPane(webView), 440.0, 760.0)
    }

    LottieGeneratorCache.dialog = dialog
    LottieGeneratorCache.loadedUrl = loadUrl
    dialog.isVisible = true
}

private fun escapeJs(s: String): String {
    val escaped = s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")
    return "'$escaped'"
}

@Composable
private fun GeneratorPreviewContent() {
    val jsonContent by LottiePreviewState.jsonContent

    val composition by rememberLottieComposition(key = jsonContent) {
        LottieSpec2.JsonString(jsonContent.ifBlank { "{}" })
    }

    val progress = remember(jsonContent) { Animatable(0f) }
    var isPlaying by remember { mutableStateOf(true) }
    var seekTarget by remember { mutableStateOf(-1f) }

    LaunchedEffect(composition, jsonContent, isPlaying) {
        val comp = composition ?: return@LaunchedEffect
        if (jsonContent.isBlank()) return@LaunchedEffect
        if (!isPlaying) return@LaunchedEffect
        val totalDurMs = ((comp.durationFrames / comp.frameRate) * 1000f).toLong().coerceAtLeast(1L)
        while (isPlaying) {
            val startFrom = progress.value
            val remainMs = (totalDurMs * (1f - startFrom)).toInt().coerceAtLeast(1)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = remainMs, easing = LinearEasing)
            )
            if (isPlaying) progress.snapTo(0f)
        }
    }

    // Handle seek
    LaunchedEffect(seekTarget) {
        if (seekTarget >= 0f) {
            progress.snapTo(seekTarget)
            seekTarget = -1f
        }
    }

    val comp = composition
    val totalFrames = comp?.durationFrames ?: 0f
    val currentFrame = progress.value * totalFrames

    val isDark by LottiePreviewState.isDark
    val bgColor = if (isDark) Color(0xFF10131A) else Color(0xFFE7E0EC)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.2f)
    val textColor = if (isDark) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.4f)
    val btnBg = if (isDark) Color(0xFF49454F) else Color(0xFFE0E0E0)
    val btnText = if (isDark) Color.White else Color.Black
    val accentColor = if (isDark) Color(0xFF90CAF9) else Color(0xFF1976D2)
    val trackColor = if (isDark) Color(0xFF49454F) else Color(0xFFCAC4D0)
    val frameTextColor = if (isDark) Color(0xFF888888) else Color(0xFF666666)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // Preview area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (jsonContent.isNotBlank() && comp != null) {
                Box(
                    modifier = Modifier
                        .aspectRatio(comp.width / comp.height)
                        .fillMaxSize()
                        .border(1.dp, borderColor)
                ) {
                    Image(
                        painter = rememberLottiePainter(
                            composition = composition,
                            progress = { progress.value }
                        ),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Text(
                    text = stringResource(Res.string.preview_will_appear_here),
                    color = textColor
                )
            }
        }

        // Playback controls
        if (jsonContent.isNotBlank()) {
            Row(
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .align(Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Play/Pause button
                Button(
                    onClick = { isPlaying = !isPlaying },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = btnBg,
                        contentColor = btnText
                    ),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(if (isPlaying) "\u23F8" else "\u25B6")
                }

                // Seek bar
                androidx.compose.material3.Slider(
                    value = progress.value,
                    onValueChange = {
                        isPlaying = false
                        seekTarget = it
                    },
                    onValueChangeFinished = { isPlaying = true },
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = trackColor
                    )
                )

                // Frame display
                Text(
                    text = "${currentFrame.toInt()} / ${totalFrames.toInt()}",
                    color = frameTextColor,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            // Status line
            val statusText by LottiePreviewState.statusText
            if (statusText.isNotBlank()) {
                Text(
                    text = statusText,
                    color = frameTextColor,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}


