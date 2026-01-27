package org.churchpresenter.app.churchpresenter

import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.ItemEvent
import java.util.concurrent.atomic.AtomicReference
import javax.swing.*

/**
 * Show a simple Swing-based font chooser (modal) that lets the user pick:
 * - font family
 * - font size (8..120)
 * - weight (Regular or Bold)
 *
 * Returns a java.awt.Font instance or null if the user cancelled.
 *
 * Usage:
 *   val chosen = showSwingFontChooser(initialFamily = "Arial", initialSize = 24, initialWeight = "Bold")
 */
fun showSwingFontChooser(
    initialFamily: String? = null,
    initialSize: Int = 14,
    initialWeight: String? = "Regular"
): Font? {
    val result = AtomicReference<Font?>(null)

    // Build the dialog/show logic as a Runnable so we can run it directly on the EDT
    val showDialog = Runnable {
        val families = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames
        val dialog = JDialog()
        dialog.title = "Font Chooser"
        dialog.isModal = true
        dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
        dialog.layout = BorderLayout()

        // Family selector
        val familyBox = JComboBox(families)
        if (initialFamily != null) {
            familyBox.selectedItem = initialFamily
        }

        // Size selector (8..120)
        val sizes = (8..120).map { it as Integer }.toTypedArray()
        val sizeBox = JComboBox(sizes)
        sizeBox.selectedItem = Integer(initialSize.coerceIn(8, 120))

        // Weight selector
        val weightBox = JComboBox(arrayOf("Regular", "Bold"))
        weightBox.selectedItem = if (initialWeight?.equals("Bold", ignoreCase = true) == true) "Bold" else "Regular"

        // Preview label
        val preview = JLabel("The quick brown fox jumps over the lazy dog")
        preview.horizontalAlignment = SwingConstants.CENTER
        fun updatePreview() {
            val fam = familyBox.selectedItem as? String ?: families.firstOrNull() ?: "SansSerif"
            val sz = (sizeBox.selectedItem as Integer).toInt()
            val style = if ((weightBox.selectedItem as String).equals("Bold", ignoreCase = true)) Font.BOLD else Font.PLAIN
            preview.font = Font(fam, style, sz)
        }
        updatePreview()

        // Listeners to update preview
        familyBox.addItemListener { e -> if (e.stateChange == ItemEvent.SELECTED) updatePreview() }
        sizeBox.addItemListener { e -> if (e.stateChange == ItemEvent.SELECTED) updatePreview() }
        weightBox.addItemListener { e -> if (e.stateChange == ItemEvent.SELECTED) updatePreview() }

        // Controls layout
        val controls = JPanel(FlowLayout(FlowLayout.LEFT))
        controls.add(JLabel("Family:"))
        controls.add(familyBox)
        controls.add(JLabel("Size:"))
        controls.add(sizeBox)
        controls.add(JLabel("Weight:"))
        controls.add(weightBox)

        // Buttons
        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT))
        val cancelBtn = JButton("Cancel")
        val okBtn = JButton("OK")
        buttons.add(cancelBtn)
        buttons.add(okBtn)

        okBtn.addActionListener {
            val fam = familyBox.selectedItem as? String ?: families.firstOrNull() ?: "SansSerif"
            val sz = (sizeBox.selectedItem as Integer).toInt()
            val style = if ((weightBox.selectedItem as String).equals("Bold", ignoreCase = true)) Font.BOLD else Font.PLAIN
            result.set(Font(fam, style, sz))
            dialog.dispose()
        }
        cancelBtn.addActionListener {
            dialog.dispose()
        }

        val previewPanel = JPanel(BorderLayout())
        previewPanel.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        previewPanel.add(preview, BorderLayout.CENTER)

        dialog.add(controls, BorderLayout.NORTH)
        dialog.add(previewPanel, BorderLayout.CENTER)
        dialog.add(buttons, BorderLayout.SOUTH)
        dialog.pack()
        dialog.setLocationRelativeTo(null)
        dialog.isVisible = true
    }

    try {
        // If already on the EDT, run directly. Otherwise invokeAndWait to run on the EDT.
        if (SwingUtilities.isEventDispatchThread()) {
            showDialog.run()
        } else {
            SwingUtilities.invokeAndWait(showDialog)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }

    return result.get()
}
