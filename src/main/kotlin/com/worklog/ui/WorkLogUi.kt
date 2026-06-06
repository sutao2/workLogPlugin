package com.worklog.ui

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea

object WorkLogUi {
    fun page(content: JComponent, gap: Int = 12): JPanel {
        return JPanel(BorderLayout(0, JBUI.scale(gap))).apply {
            border = JBUI.Borders.empty(12)
            add(content, BorderLayout.CENTER)
        }
    }

    fun header(title: String, description: String? = null, trailing: JComponent? = null): JPanel {
        val panel = JPanel(BorderLayout(12, 0))
        val textPanel = JPanel(BorderLayout(0, 3))
        textPanel.add(JBLabel(title).apply {
            font = font.deriveFont(Font.BOLD, 16f)
        }, BorderLayout.NORTH)
        if (!description.isNullOrBlank()) {
            textPanel.add(mutedLabel(description), BorderLayout.CENTER)
        }
        panel.add(textPanel, BorderLayout.CENTER)
        trailing?.let { panel.add(it, BorderLayout.EAST) }
        return panel
    }

    fun section(content: JComponent, padding: Int = 12): JPanel {
        return JPanel(BorderLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border()),
                JBUI.Borders.empty(padding)
            )
            add(content, BorderLayout.CENTER)
        }
    }

    fun sectionPage(title: String, description: String, content: JComponent): JPanel {
        val panel = JPanel(BorderLayout(0, 14))
        panel.add(header(title, description), BorderLayout.NORTH)
        panel.add(section(content, 14), BorderLayout.CENTER)
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(14, 16)
            add(panel, BorderLayout.CENTER)
        }
    }

    fun mutedLabel(text: String): JBLabel {
        return JBLabel(text).apply {
            foreground = JBColor.GRAY
        }
    }

    fun helpLabel(text: String, width: Int = 560): JBLabel {
        return mutedLabel("<html><body style='width: ${JBUI.scale(width)}px'><small>$text</small></body></html>")
    }

    fun iconButton(icon: Icon, tooltip: String, action: (JButton) -> Unit): JButton {
        return JButton(icon).apply {
            toolTipText = tooltip
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusPainted = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            preferredSize = Dimension(JBUI.scale(28), JBUI.scale(28))
            addActionListener { action(this) }
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseEntered(e: java.awt.event.MouseEvent) {
                    isContentAreaFilled = true
                }

                override fun mouseExited(e: java.awt.event.MouseEvent) {
                    isContentAreaFilled = false
                }
            })
        }
    }

    fun button(text: String, primary: Boolean = false, icon: Icon? = null, action: () -> Unit): JButton {
        return JButton(text).apply {
            this.icon = icon
            margin = if (primary) JBUI.insets(4, 14) else JBUI.insets(4, 10)
            isFocusPainted = false
            isDefaultCapable = primary
            font = font.deriveFont(if (primary) Font.BOLD else Font.PLAIN)
            addActionListener { action() }
        }
    }

    fun editorArea(readOnly: Boolean = false): JTextArea {
        return JTextArea().apply {
            isEditable = !readOnly
            lineWrap = true
            wrapStyleWord = true
            tabSize = 4
            margin = JBUI.insets(14)
            font = Font("Monospaced", Font.PLAIN, 13)
            background = JBColor.namedColor("EditorPane.background", JBColor.PanelBackground)
        }
    }

    fun borderedScrollPane(component: JComponent): JBScrollPane {
        return JBScrollPane(component).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border()),
                JBUI.Borders.empty(1)
            )
        }
    }
}
