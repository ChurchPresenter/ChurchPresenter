package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.runtime.Composable
import churchpresenter.composeapp.generated.resources.*
import org.churchpresenter.app.churchpresenter.data.SettingsManager
import org.churchpresenter.app.churchpresenter.dialogs.tabs.createNativeSongSettingsPanel
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Frame
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.ExperimentalResourceApi

// Helper function to get string resources in Swing context
@OptIn(ExperimentalResourceApi::class)
private fun getStringResource(resource: org.jetbrains.compose.resources.StringResource): String {
    return runBlocking {
        org.jetbrains.compose.resources.getString(resource)
    }
}

fun showOptionsDialog(
    parent: Frame? = null,
    onSave: () -> Unit = {}
) {
    SwingUtilities.invokeLater {
        val settingsManager = SettingsManager()
        var currentSettings = settingsManager.loadSettings()

        val dialog = JDialog(parent, getStringResource(Res.string.options), true).apply {
            defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
            setSize(800, 600)
            setLocationRelativeTo(parent)
            isResizable = false

            val tabbedPane = JTabbedPane()

            // Song Settings Tab - Using native Swing components directly
            val songPanel = createNativeSongSettingsPanel(
                settings = currentSettings.songSettings,
                onSettingsChange = { newSongSettings ->
                    currentSettings = currentSettings.copy(songSettings = newSongSettings)
                }
            )
            tabbedPane.addTab(getStringResource(Res.string.song), songPanel)

            // Placeholder tabs
            tabbedPane.addTab(getStringResource(Res.string.bible), JLabel(getStringResource(Res.string.bible_tab_content)))
            tabbedPane.addTab(getStringResource(Res.string.text_settings_and_colors), JLabel(getStringResource(Res.string.text_settings_tab_content)))
            tabbedPane.addTab(getStringResource(Res.string.background), JLabel(getStringResource(Res.string.background_tab_content)))
            tabbedPane.addTab(getStringResource(Res.string.background_images), JLabel(getStringResource(Res.string.background_images_tab_content)))
            tabbedPane.addTab(getStringResource(Res.string.folders), JLabel(getStringResource(Res.string.folders_tab_content)))
            tabbedPane.addTab(getStringResource(Res.string.projection), JLabel(getStringResource(Res.string.projection_tab_content)))
            tabbedPane.addTab(getStringResource(Res.string.other), JLabel(getStringResource(Res.string.other_tab_content)))

            // Button panel
            val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
                val okButton = JButton("✓ ${getStringResource(Res.string.ok)}").apply {
                    addActionListener {
                        settingsManager.saveSettings(currentSettings)
                        onSave()
                        dispose()
                    }
                }

                val cancelButton = JButton("✗ ${getStringResource(Res.string.cancel)}").apply {
                    addActionListener { dispose() }
                }

                add(okButton)
                add(cancelButton)
            }

            layout = BorderLayout()
            add(tabbedPane, BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)

            addWindowListener(object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent?) {
                    dispose()
                }
            })
        }

        dialog.isVisible = true
    }
}


@Composable
fun OptionsDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    // Legacy compatibility - now triggers JDialog
    if (isVisible) {
        showOptionsDialog(
            parent = null,
            onSave = {
                onSave()
                onDismiss()
            }
        )
    }
}

