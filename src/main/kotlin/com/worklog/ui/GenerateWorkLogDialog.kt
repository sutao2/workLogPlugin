package com.worklog.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.worklog.settings.AppSettingsState
import java.awt.BorderLayout
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
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = JBUI.Borders.empty(12)
        panel.preferredSize = java.awt.Dimension(420, 180)

        // 日期选择
        val datePanel = JPanel(BorderLayout(8, 0))
        datePanel.add(JBLabel("选择日期: "), BorderLayout.WEST)
        datePanel.add(dateSpinner, BorderLayout.CENTER)
        panel.add(datePanel)
        panel.add(Box.createVerticalStrut(8))

        // 代码访问选项
        panel.add(includeCodeCheckBox)
        panel.add(Box.createVerticalStrut(5))
        panel.add(rememberChoiceCheckBox)
        panel.add(Box.createVerticalStrut(5))
        panel.add(includeUncommittedCheckBox)
        panel.add(Box.createVerticalStrut(8))

        // 说明文字
        val infoLabel = JBLabel(
            "<html><body style='width: 360px'>" +
                "允许读取代码后，AI 会基于代码变更生成更详细的总结。" +
                "</body></html>"
        )
        infoLabel.foreground = JBColor.GRAY
        panel.add(infoLabel)

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
