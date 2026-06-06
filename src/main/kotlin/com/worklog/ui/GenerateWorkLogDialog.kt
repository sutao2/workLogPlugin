package com.worklog.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.worklog.settings.AppSettingsState
import java.awt.BorderLayout
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
        val panel = JPanel(BorderLayout(0, 14))
        panel.border = JBUI.Borders.empty(16)
        panel.preferredSize = java.awt.Dimension(460, 230)

        val headerPanel = JPanel(BorderLayout(0, 4))
        val titleLabel = JBLabel("生成工作日志")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 17f)
        val descriptionLabel = JBLabel("根据 Git 提交记录生成 Markdown 日志，可选择加入代码变更内容。")
        descriptionLabel.foreground = JBColor.GRAY
        headerPanel.add(titleLabel, BorderLayout.NORTH)
        headerPanel.add(descriptionLabel, BorderLayout.CENTER)
        panel.add(headerPanel, BorderLayout.NORTH)

        val contentPanel = JPanel(BorderLayout(0, 12))
        contentPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor.border()),
            JBUI.Borders.empty(14)
        )

        // 日期选择
        val datePanel = JPanel(BorderLayout(10, 0))
        datePanel.add(JBLabel("日志日期").apply {
            font = font.deriveFont(Font.BOLD)
        }, BorderLayout.WEST)
        datePanel.add(dateSpinner, BorderLayout.CENTER)
        contentPanel.add(datePanel, BorderLayout.NORTH)

        val optionPanel = JPanel()
        optionPanel.layout = BoxLayout(optionPanel, BoxLayout.Y_AXIS)

        // 代码访问选项
        optionPanel.add(includeCodeCheckBox)
        optionPanel.add(Box.createVerticalStrut(7))
        optionPanel.add(rememberChoiceCheckBox)
        optionPanel.add(Box.createVerticalStrut(7))
        optionPanel.add(includeUncommittedCheckBox)
        contentPanel.add(optionPanel, BorderLayout.CENTER)

        // 说明文字
        val infoLabel = JBLabel(
            "<html><body style='width: 380px'>" +
                "开启代码读取后，AI 会使用 Git diff 生成更具体的工作总结。" +
                "</body></html>"
        )
        infoLabel.foreground = JBColor.GRAY
        panel.add(contentPanel, BorderLayout.CENTER)
        panel.add(infoLabel, BorderLayout.SOUTH)

        return panel
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
