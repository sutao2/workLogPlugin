package com.worklog.ui

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.worklog.models.ExportFormat
import com.worklog.services.WorkLogService
import com.worklog.settings.AppSettingsState
import com.worklog.utils.ExportUtil
import com.worklog.utils.StorageUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * 工作日志主界面 - 简洁版
 */
class WorkLogToolWindow(private val project: Project) {

    private val scope = CoroutineScope(Dispatchers.Main)
    private val workLogService = project.getService(WorkLogService::class.java)

    // UI 组件
    private val dateComboBox = ComboBox<LocalDate>()
    private val editorArea = JTextArea()
    private val statusLabel = JBLabel("就绪")
    private val commitCountLabel = JBLabel("提交: 0")
    private val wordCountLabel = JBLabel("字数: 0")

    private val panel = JPanel(BorderLayout(5, 5))

    init {
        panel.border = EmptyBorder(8, 8, 8, 8)
        initUI()
        loadAvailableDates()
    }

    fun getContent(): JComponent {
        return panel
    }

    private fun initUI() {
        panel.add(createToolbar(), BorderLayout.NORTH)
        panel.add(createEditorPanel(), BorderLayout.CENTER)
        panel.add(createStatusBar(), BorderLayout.SOUTH)
    }

    private fun createToolbar(): JPanel {
        val toolbar = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = Insets(2, 2, 2, 2)

        // 第一行
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.weightx = 0.0
        toolbar.add(JBLabel("日期:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        dateComboBox.addActionListener { loadWorkLog() }
        // 创建日期选择面板，包含下拉框和选择按钮
        val datePanel = JPanel(BorderLayout(2, 0))
        datePanel.add(dateComboBox, BorderLayout.CENTER)
        val selectDateBtn = JButton("...")
        selectDateBtn.toolTipText = "选择其他日期"
        selectDateBtn.preferredSize = Dimension(30, selectDateBtn.preferredSize.height)
        selectDateBtn.margin = Insets(2, 2, 2, 2)
        selectDateBtn.addActionListener { showDatePicker() }
        datePanel.add(selectDateBtn, BorderLayout.EAST)
        toolbar.add(datePanel, gbc)

        gbc.gridx = 2
        gbc.weightx = 0.0
        toolbar.add(createButton("生成日志") { showGenerateDialog() }, gbc)

        gbc.gridx = 3
        toolbar.add(createButton("保存") { saveWorkLog() }, gbc)

        gbc.gridx = 4
        toolbar.add(createButton("复制") { copyWorkLog() }, gbc)

        // 第二行
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.gridwidth = 1
        toolbar.add(createButton("历史记录") { showHistoryDialog() }, gbc)

        gbc.gridx = 1
        toolbar.add(createButton("导出") { exportWorkLog() }, gbc)

        gbc.gridx = 2
        toolbar.add(createButton("统计") { showStatistics() }, gbc)

        gbc.gridx = 3
        toolbar.add(createButton("快速插入") { showQuickInsertMenu() }, gbc)

        return toolbar
    }

    private fun createButton(text: String, action: () -> Unit): JButton {
        val button = JButton(text)
        button.addActionListener { action() }
        return button
    }

    private fun createEditorPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createTitledBorder("工作日志编辑器")

        editorArea.lineWrap = true
        editorArea.wrapStyleWord = true
        editorArea.font = Font("Monospaced", Font.PLAIN, 13)
        editorArea.tabSize = 4
        editorArea.margin = Insets(5, 5, 5, 5)

        // 字数统计
        editorArea.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = updateWordCount()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = updateWordCount()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = updateWordCount()
        })

        val scrollPane = JBScrollPane(editorArea)
        panel.add(scrollPane, BorderLayout.CENTER)

        return panel
    }

    private fun createStatusBar(): JPanel {
        val statusBar = JPanel(FlowLayout(FlowLayout.LEFT, 10, 2))
        statusBar.add(statusLabel)
        statusBar.add(JSeparator(SwingConstants.VERTICAL))
        statusBar.add(commitCountLabel)
        statusBar.add(JSeparator(SwingConstants.VERTICAL))
        statusBar.add(wordCountLabel)
        return statusBar
    }

    private fun updateWordCount() {
        val text = editorArea.text ?: ""
        wordCountLabel.text = "字数: ${text.length} | 行数: ${text.split("\n").size}"
    }

    private fun showQuickInsertMenu() {
        val popup = JPopupMenu()

        val templates = mapOf(
            "任务项" to "- [ ] ",
            "完成项" to "- [x] ",
            "重要提示" to "> **重要**: ",
            "Bug修复" to "修复: ",
            "新功能" to "新增: ",
            "优化" to "优化: ",
            "学习笔记" to "### 学习笔记\n\n",
            "今日目标" to "### 今日目标\n\n",
            "明日计划" to "### 明日计划\n\n"
        )

        templates.forEach { (name, template) ->
            val item = JMenuItem(name)
            item.addActionListener {
                editorArea.insert(template, editorArea.caretPosition)
                editorArea.requestFocus()
            }
            popup.add(item)
        }

        val component = panel.components.firstOrNull { it is JButton } as? JButton
        component?.let { popup.show(it, 0, it.height) }
    }

    private fun loadAvailableDates() {
        dateComboBox.removeAllItems()
        dateComboBox.addItem(LocalDate.now())

        workLogService.getAllWorkLogDates().forEach { date ->
            if (date != LocalDate.now()) {
                dateComboBox.addItem(date)
            }
        }

        dateComboBox.renderer = object : DefaultListCellRenderer() {
            private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd (EEEE)")
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int,
                isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                if (value is LocalDate) {
                    text = value.format(formatter)
                }
                return component
            }
        }

        if (dateComboBox.itemCount > 0) {
            dateComboBox.selectedIndex = 0
            loadWorkLog()
        }
    }

    private fun loadWorkLog() {
        val selectedDate = dateComboBox.selectedItem as? LocalDate ?: return

        val workLog = workLogService.loadWorkLog(selectedDate)
        if (workLog != null) {
            editorArea.text = workLog.content
            commitCountLabel.text = "提交: ${workLog.gitCommits.size}"
            statusLabel.text = "已加载: $selectedDate"
        } else {
            editorArea.text = createDefaultTemplate(selectedDate)
            commitCountLabel.text = "提交: 0"
            statusLabel.text = "新建日志: $selectedDate"
        }
        updateWordCount()
    }

    private fun createDefaultTemplate(date: LocalDate): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日 EEEE")
        return """
# 工作日志 - ${date.format(formatter)}

## 今日目标


## 完成事项


## 详细内容


## 问题和思考


## 明日计划


        """.trimIndent()
    }

    private fun saveWorkLog() {
        val selectedDate = dateComboBox.selectedItem as? LocalDate ?: return
        val content = editorArea.text ?: ""

        if (content.isBlank()) {
            JOptionPane.showMessageDialog(panel, "日志内容不能为空", "保存失败", JOptionPane.WARNING_MESSAGE)
            return
        }

        try {
            StorageUtil.writeWorkLog(project, selectedDate, content)
            statusLabel.text = "已保存: $selectedDate"
            JOptionPane.showMessageDialog(panel, "工作日志已保存", "保存成功", JOptionPane.INFORMATION_MESSAGE)
            loadAvailableDates()
        } catch (e: Exception) {
            statusLabel.text = "保存失败: ${e.message}"
            JOptionPane.showMessageDialog(panel, "保存失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun showGenerateDialog() {
        val dialog = GenerateWorkLogDialog(project)
        if (dialog.showAndGet()) {
            generateWorkLog(dialog.getSelectedDate(), dialog.isIncludeCode())
        }
    }

    private fun generateWorkLog(date: LocalDate, includeCode: Boolean) {
        statusLabel.text = "正在生成日志..."

        scope.launch {
            try {
                val workLog = workLogService.createWorkLog(date, includeCode)
                val settings = AppSettingsState.getInstance()

                val aiSummary = if (settings.apiKey.isNotBlank() && workLog.gitCommits.isNotEmpty()) {
                    try {
                        val aiService = project.getService(com.worklog.services.AIService::class.java)
                        statusLabel.text = "正在调用 AI..."
                        aiService.summarizeWork(workLog.gitCommits, includeCode)
                    } catch (e: Exception) {
                        statusLabel.text = "AI 调用失败: ${e.message}"
                        null
                    }
                } else null

                val fullContent = com.worklog.utils.MarkdownUtil.generateFullWorkLog(
                    workLog = workLog,
                    aiSummary = aiSummary,
                    includeCodeDiff = includeCode
                )

                workLog.content = fullContent
                workLogService.saveWorkLog(workLog)

                if (!dateComboBox.getItemAt(0).equals(date)) {
                    dateComboBox.insertItemAt(date, 0)
                }
                dateComboBox.selectedItem = date
                loadWorkLog()

                statusLabel.text = "日志生成成功"
                JOptionPane.showMessageDialog(panel, "工作日志已生成", "成功", JOptionPane.INFORMATION_MESSAGE)
            } catch (e: Exception) {
                statusLabel.text = "生成失败: ${e.message}"
                JOptionPane.showMessageDialog(panel, "生成失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
            }
        }
    }

    private fun showHistoryDialog() {
        val dialog = HistoryViewDialog(project)
        dialog.show()
        dialog.getSelectedDate()?.let { selectedDate ->
            dateComboBox.selectedItem = selectedDate
            loadWorkLog()
        }
    }

    private fun exportWorkLog() {
        val selectedDate = dateComboBox.selectedItem as? LocalDate ?: return
        val workLog = workLogService.loadWorkLog(selectedDate)

        if (workLog == null) {
            JOptionPane.showMessageDialog(panel, "请先生成或加载工作日志", "导出失败", JOptionPane.WARNING_MESSAGE)
            return
        }

        val formats = ExportFormat.values()
        val selectedFormat = JOptionPane.showInputDialog(
            panel, "选择导出格式:", "导出",
            JOptionPane.QUESTION_MESSAGE, null, formats,
            AppSettingsState.getInstance().defaultExportFormat
        ) as? ExportFormat ?: return

        val fileChooser = JFileChooser()
        fileChooser.dialogTitle = "选择导出位置"
        fileChooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY

        if (fileChooser.showSaveDialog(panel) == JFileChooser.APPROVE_OPTION) {
            try {
                val exportedFile = ExportUtil.export(workLog, selectedFormat, fileChooser.selectedFile.toPath())
                statusLabel.text = "已导出: ${exportedFile.name}"

                val result = JOptionPane.showConfirmDialog(
                    panel,
                    "导出成功: ${exportedFile.absolutePath}\n\n是否打开文件?",
                    "导出成功", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE
                )

                if (result == JOptionPane.YES_OPTION) {
                    Desktop.getDesktop().open(exportedFile)
                }
            } catch (e: Exception) {
                statusLabel.text = "导出失败: ${e.message}"
                JOptionPane.showMessageDialog(panel, "导出失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
            }
        }
    }

    private fun showStatistics() {
        val dialog = StatisticsDialog(project)
        dialog.show()
    }

    private fun showDatePicker() {
        val dialog = DatePickerDialog(project)
        if (dialog.showAndGet()) {
            val selectedDate = dialog.getSelectedDate()

            // 检查日期是否已在下拉列表中
            var found = false
            for (i in 0 until dateComboBox.itemCount) {
                if (dateComboBox.getItemAt(i) == selectedDate) {
                    dateComboBox.selectedIndex = i
                    found = true
                    break
                }
            }

            // 如果不在列表中，添加到列表
            if (!found) {
                dateComboBox.addItem(selectedDate)
                dateComboBox.selectedItem = selectedDate
            }

            loadWorkLog()
        }
    }

    private fun copyWorkLog() {
        val content = editorArea.text ?: ""

        if (content.isBlank()) {
            JOptionPane.showMessageDialog(panel, "日志内容为空，无法复制", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }

        try {
            CopyPasteManager.getInstance().setContents(StringSelection(content))
            statusLabel.text = "已复制到剪贴板"
            JOptionPane.showMessageDialog(panel, "工作日志已复制到剪贴板", "复制成功", JOptionPane.INFORMATION_MESSAGE)
        } catch (e: Exception) {
            statusLabel.text = "复制失败: ${e.message}"
            JOptionPane.showMessageDialog(panel, "复制失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
        }
    }
}
