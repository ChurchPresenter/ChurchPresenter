package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.runtime.*
import androidx.compose.ui.awt.ComposePanel
import churchpresenter.composeapp.generated.resources.*
import org.churchpresenter.app.churchpresenter.data.SettingsManager
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SongSettingsTab
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Frame
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*
import kotlinx.coroutines.runBlocking
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.ui.theme.AppThemeWrapper
import org.churchpresenter.app.churchpresenter.ui.theme.setTheme
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
    theme: String,
    settingsManager: SettingsManager,
    onSave: (AppSettings) -> Unit = {}
) {
    SwingUtilities.invokeLater {
        var currentSettings = settingsManager.loadSettings()
        val dialog = JDialog(parent, getStringResource(Res.string.options), true).apply {
            defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
            setSize(1000, 700)
            setLocationRelativeTo(parent)
            isResizable = true
            minimumSize = Dimension(900, 650)

            val tabbedPane = JTabbedPane()

            // Song Settings Tab - Using Compose
            val songPanel = ComposePanel().apply {
                setContent {
                    AppThemeWrapper {
                        var appSettings by remember { mutableStateOf(currentSettings) }
                        setTheme(theme)
                        SongSettingsTab(
                            settings = appSettings,
                            onSettingsChange = { updateFn ->
                                appSettings = AppSettings(updateFn.songSettings)
                                currentSettings = appSettings
                            }
                        )
                    }
                }
            }
            tabbedPane.addTab(getStringResource(Res.string.song), songPanel)

            // Placeholder tabs
            tabbedPane.addTab(
                getStringResource(Res.string.bible),
                JLabel(getStringResource(Res.string.bible_tab_content))
            )
            tabbedPane.addTab(
                getStringResource(Res.string.text_settings_and_colors),
                JLabel(getStringResource(Res.string.text_settings_tab_content))
            )
            tabbedPane.addTab(
                getStringResource(Res.string.background),
                JLabel(getStringResource(Res.string.background_tab_content))
            )
            tabbedPane.addTab(
                getStringResource(Res.string.background_images),
                JLabel(getStringResource(Res.string.background_images_tab_content))
            )
            tabbedPane.addTab(
                getStringResource(Res.string.folders),
                JLabel(getStringResource(Res.string.folders_tab_content))
            )
            tabbedPane.addTab(
                getStringResource(Res.string.projection),
                JLabel(getStringResource(Res.string.projection_tab_content))
            )
            tabbedPane.addTab(
                getStringResource(Res.string.other),
                JLabel(getStringResource(Res.string.other_tab_content))
            )

            // Button panel
            val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
                val okButton = JButton("✓ ${getStringResource(Res.string.ok)}").apply {
                    addActionListener {
                        settingsManager.saveSettings(currentSettings)
                        onSave(currentSettings)
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
