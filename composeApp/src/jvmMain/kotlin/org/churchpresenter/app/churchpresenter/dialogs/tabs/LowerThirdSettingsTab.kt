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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bottom
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
import kotlinx.coroutines.delay
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.viewmodel.LowerThirdSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import java.awt.BorderLayout
import java.awt.CardLayout
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
import javax.swing.SwingUtilities
import netscape.javascript.JSObject

@Composable
fun LowerThirdSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    serverUrl: String = "",
    isDarkTheme: Boolean = true
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
                        SwingUtilities.invokeLater {
                            openLottieGeneratorDialog(
                                parentWindow = Window.getWindows().firstOrNull { it.isActive },
                                onFileSaved = { viewModel.onFileSavedFromGenerator() },
                                serverUrl = serverUrl,
                                isDarkTheme = isDarkTheme,
                                lowerThirdFolder = settings.streamingSettings.lowerThirdFolder
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
    parentWindow: Window?,
    onFileSaved: () -> Unit,
    serverUrl: String = "",
    isDarkTheme: Boolean = true,
    lowerThirdFolder: String = ""
) {
    val loadUrl: String = if (serverUrl.isNotEmpty()) {
        val port = java.net.URI(serverUrl).port.takeIf { it > 0 } ?: 8765
        "http://127.0.0.1:${port + 1}/lottie-generator.html"
    } else {
        javax.swing.JOptionPane.showMessageDialog(
            parentWindow,
            "Please start the API server to generate lower thirds.\n" +
                "Generated files will be saved to the folder configured in Settings.",
            "Server Not Running",
            javax.swing.JOptionPane.INFORMATION_MESSAGE
        )
        return
    }

    // Update mutable refs so the cached bridge always uses latest values
    LottieGeneratorCache.onFileSavedCallback = onFileSaved
    LottieGeneratorCache.lowerThirdFolderRef = lowerThirdFolder

    // Re-use existing dialog if the URL hasn't changed
    val cached = LottieGeneratorCache.dialog
    if (cached != null && LottieGeneratorCache.loadedUrl == loadUrl && cached.isDisplayable) {
        // Apply theme in case it changed
        Platform.runLater {
            try {
                val jfxPanel = cached.contentPane.getComponent(0) as? JFXPanel ?: return@runLater
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
        title = "Lower Third Generator"
        isModal = false
        preferredSize = Dimension(1200, 800)
        defaultCloseOperation = JDialog.HIDE_ON_CLOSE
    }

    val cards = CardLayout()
    val cardPanel = JPanel(cards)

    // Loading panel — centered progress bar + label
    val loadingPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        val glue1 = javax.swing.Box.createVerticalGlue()
        val label = JLabel("Loading generator...").apply {
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

    val jfxPanel = JFXPanel()
    cardPanel.add(loadingPanel, "loading")
    cardPanel.add(jfxPanel, "webview")
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
                    val folder = LottieGeneratorCache.lowerThirdFolderRef
                    val targetDir = folder.takeIf { it.isNotEmpty() }
                        ?.let { File(it) }
                        ?.takeIf { it.exists() && it.isDirectory }
                    if (targetDir != null) {
                        var num = 1
                        var targetFile = File(targetDir, "$baseName - %02d.json".format(num))
                        while (targetFile.exists()) {
                            num++
                            targetFile = File(targetDir, "$baseName - %02d.json".format(num))
                        }
                        targetFile.writeText(jsonContent)
                        LottieGeneratorCache.onFileSavedCallback?.invoke()
                    } else {
                        javax.swing.JOptionPane.showMessageDialog(
                            dialog,
                            "No folder configured.\nSet a Lower Third folder in Settings first.",
                            "No Folder",
                            javax.swing.JOptionPane.WARNING_MESSAGE
                        )
                    }
                }
            }
        }
        LottieGeneratorCache.bridgeRef = bridge

        val stateListener = ChangeListener<Worker.State> { _, _, newState ->
            if (newState == Worker.State.FAILED) {
                System.err.println("WebView FAILED to load: ${engine.loadWorker.exception}")
            }
            if (newState == Worker.State.SUCCEEDED) {
                val win = engine.executeScript("window") as? JSObject ?: return@ChangeListener
                win.setMember("_jvmBridge", bridge)

                engine.executeScript(
                    "var btn = document.getElementById('btnDownload');" +
                    "if (btn) { var newBtn = btn.cloneNode(true); btn.parentNode.replaceChild(newBtn, btn);" +
                    "  newBtn.textContent = 'Save Lower Third';" +
                    "  newBtn.addEventListener('click', function() { download(); savePreset(); }); }"
                )

                val themeMode = if (isDarkTheme) "dark" else "light"
                engine.executeScript(
                    "if(typeof applyUITheme==='function') applyUITheme('$themeMode');" +
                    "var s=document.createElement('style');s.textContent='#themeSwitcher{display:none!important}';" +
                    "document.head.appendChild(s);"
                )

                // Switch from loading to webview
                SwingUtilities.invokeLater { cards.show(cardPanel, "webview") }
            }
        }

        engine.loadWorker.stateProperty().addListener(stateListener)
        engine.load(loadUrl)

        jfxPanel.scene = Scene(StackPane(webView), 1180.0, 760.0)
    }

    LottieGeneratorCache.dialog = dialog
    LottieGeneratorCache.loadedUrl = loadUrl
    dialog.isVisible = true
}






