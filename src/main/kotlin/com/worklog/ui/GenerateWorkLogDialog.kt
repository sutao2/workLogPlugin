package com.worklog.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.worklog.settings.AppSettingsState
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Font
import java.time.LocalDate
import javax.swing.*

/**
 * 生成工作日志对话框
 */
class GenerateWorkLogDialog(private val project: Project) : DialogWrapper(project) {

    private val dateSpinner: JSpinner
    private val includeCodeCheckBox: JBCheckBox
    private val rememberChoiceCheckBox: JBCheckBox
    private val includeUncommittedCheckBox: JBCheckBox

    init {
        title = "生成工作日志"

        // 初始化日期选择器
        val today = LocalDate.now()
        val dateModel = SpinnerDateModel(
            java.sql.Date.valueOf(today),
            null,
            java.sql.Date.valueOf(today),
            java.util.Calendar.DAY_OF_MONTH
        )
        dateSpinner = JSpinner(dateModel)
        val editor = JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd")
        dateSpinner.editor = editor

        // 加载设置
        val settings = AppSettingsState.getInstance()
        includeCodeCheckBox = JBCheckBox("允许读取代码内容（用于AI生成更详细的总结）", settings.allowCodeAccess)
        rememberChoiceCheckBox = JBCheckBox("记住我的选择", settings.rememberCodeAccessChoice)
        includeUncommittedCheckBox = JBCheckBox("包含未提交的更改", false)
        includeUncommittedCheckBox.isEnabled = includeCodeCheckBox.isSelected
        includeCodeCheckBox.addActionListener {
            includeUncommittedCheckBox.isEnabled = includeCodeCheckBox.isSelected
            if (!includeCodeCheckBox.isSelected) {
                includeUncommittedCheckBox.isSelected = false
            }
        }

        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 12))
        panel.border = JBUI.Borders.empty(12, 16)
        panel.preferredSize = Dimension(430, 230)

        panel.add(
            WorkLogUi.mutedLabel("根据 Git 提交记录生成 Markdown 日志，可选择是否读取代码变更。"),
            BorderLayout.NORTH
        )

        val content = JPanel(BorderLayout(0, 12))
        content.add(createDateSection(), BorderLayout.NORTH)
        content.add(createOptionsSection(), BorderLayout.CENTER)
        panel.add(content, BorderLayout.CENTER)

        panel.add(
            JBLabel("开启代码读取后，AI 会使用 Git diff 生成更具体的工作总结。").apply {
                foreground = JBColor.GRAY
            },
            BorderLayout.SOUTH
        )

        return panel
    }

    private fun createDateSection(): JComponent {
        val section = JPanel(BorderLayout(0, 6))
        section.add(sectionTitle("生成范围"), BorderLayout.NORTH)

        val row = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        row.add(JBLabel("日志日期").apply {
            preferredSize = Dimension(JBUI.scale(72), preferredSize.height)
        })
        dateSpinner.preferredSize = Dimension(JBUI.scale(170), dateSpinner.preferredSize.height)
        row.add(dateSpinner)
        section.add(row, BorderLayout.CENTER)
        return section
    }

    private fun createOptionsSection(): JComponent {
        val section = JPanel(BorderLayout(0, 6))
        section.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.border()),
            JBUI.Borders.empty(10, 0, 0, 0)
        )
        section.add(sectionTitle("内容选项"), BorderLayout.NORTH)

        val form = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(0, 0, JBUI.scale(6), 0)
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
        }

        gbc.gridx = 0
        gbc.gridy = 0
        form.add(includeCodeCheckBox, gbc)
        gbc.gridy = 1
        form.add(rememberChoiceCheckBox, gbc)
        gbc.gridy = 2
        form.add(includeUncommittedCheckBox, gbc)

        section.add(form, BorderLayout.CENTER)
        return section
    }

    private fun sectionTitle(text: String): JBLabel {
        return JBLabel(text).apply {
            font = font.deriveFont(Font.BOLD, 13f)
        }
    }

    override fun doOKAction() {
        // 如果选择了记住选择，保存到设置
        if (rememberChoiceCheckBox.isSelected) {
            val settings = AppSettingsState.getInstance()
            settings.allowCodeAccess = includeCodeCheckBox.isSelected
            settings.rememberCodeAccessChoice = true
        }

        super.doOKAction()
    }

    /**
     * 获取选择的日期
     */
    fun getSelectedDate(): LocalDate {
        val date = dateSpinner.value as java.util.Date
        return date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    }

    /**
     * 是否包含代码内容
     */
    fun isIncludeCode(): Boolean {
        return includeCodeCheckBox.isSelected
    }

    /**
     * 是否包含未提交的更改
     */
    fun isIncludeUncommitted(): Boolean {
        return includeUncommittedCheckBox.isSelected
    }
}
