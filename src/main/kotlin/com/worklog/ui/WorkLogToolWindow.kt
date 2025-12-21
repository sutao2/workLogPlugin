package com.worklog.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.worklog.services.WorkLogService
import com.worklog.settings.AppSettingsState
import com.worklog.utils.StorageUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        val toolbar = JPanel(BorderLayout())

        // 左侧面板 - 主要操作按钮
        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 3, 2))

        // 日期标签
        leftPanel.add(JBLabel("日期:"))

        // 日期下拉框（可编辑，点击可弹出日期选择器）
        dateComboBox.preferredSize = Dimension(150, dateComboBox.preferredSize.height)
        dateComboBox.isEditable = true
        dateComboBox.addActionListener { loadWorkLog() }

        // 添加鼠标点击监听，双击弹出日期选择器
        dateComboBox.editor.editorComponent.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    showDatePicker()
                }
            }
        })

        leftPanel.add(dateComboBox)

        // 分隔符
        leftPanel.add(Box.createHorizontalStrut(5))

        // 生成日志按钮
        leftPanel.add(createButton("生成日志") { showGenerateDialog() })

        // 保存按钮
        leftPanel.add(createButton("保存") { saveWorkLog() })

        // 复制按钮
        leftPanel.add(createButton("复制") { copyWorkLog() })

        toolbar.add(leftPanel, BorderLayout.WEST)

        // 右侧面板 - 设置和更多按钮
        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 2, 2))

        // 设置按钮（使用 IDEA 标准齿轮图标）
        val settingsButton = JButton(AllIcons.General.Settings)
        settingsButton.toolTipText = "设置"
        settingsButton.isBorderPainted = false
        settingsButton.isContentAreaFilled = false
        settingsButton.isFocusPainted = false
        settingsButton.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        settingsButton.preferredSize = Dimension(28, 28)
        settingsButton.addActionListener { openSettings() }

        // 添加鼠标悬停效果
        settingsButton.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseEntered(e: java.awt.event.MouseEvent) {
                settingsButton.isContentAreaFilled = true
            }
            override fun mouseExited(e: java.awt.event.MouseEvent) {
                settingsButton.isContentAreaFilled = false
            }
        })
        rightPanel.add(settingsButton)

        // 更多操作按钮（使用 IDEA 标准三点图标）
        val moreButton = JButton(AllIcons.Actions.More)
        moreButton.toolTipText = "更多操作"
        moreButton.isBorderPainted = false
        moreButton.isContentAreaFilled = false
        moreButton.isFocusPainted = false
        moreButton.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        moreButton.preferredSize = Dimension(28, 28)
        moreButton.addActionListener { showMoreMenu(moreButton) }

        // 添加鼠标悬停效果
        moreButton.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseEntered(e: java.awt.event.MouseEvent) {
                moreButton.isContentAreaFilled = true
            }
            override fun mouseExited(e: java.awt.event.MouseEvent) {
                moreButton.isContentAreaFilled = false
            }
        })
        rightPanel.add(moreButton)

        toolbar.add(rightPanel, BorderLayout.EAST)

        return toolbar
    }

    private fun createButton(text: String, action: () -> Unit): JButton {
        val button = JButton(text)
        button.addActionListener { action() }
        return button
    }

    /**
     * 打开插件设置页面
     */
    private fun openSettings() {
        val settingsDialog = com.intellij.openapi.options.ShowSettingsUtil.getInstance()
        settingsDialog.showSettingsDialog(project, "WorkLog")
    }

    /**
     * 显示更多操作菜单（IDEA 标准样式）
     */
    private fun showMoreMenu(button: JButton) {
        // 创建 Action Group
        val actionGroup = DefaultActionGroup().apply {
            add(object : AnAction("历史记录", "查看历史工作日志", AllIcons.Vcs.History) {
                override fun actionPerformed(e: AnActionEvent) {
                    showHistoryDialog()
                }
            })
            add(object : AnAction("统计报告", "查看工作日志统计", AllIcons.Toolwindows.ToolWindowStructure) {
                override fun actionPerformed(e: AnActionEvent) {
                    showStatistics()
                }
            })
        }

        // 使用 JBPopupFactory 创建 IDEA 风格的弹出菜单
        val popup = JBPopupFactory.getInstance()
            .createActionGroupPopup(
                null,  // 不显示标题
                actionGroup,
                DataContext.EMPTY_CONTEXT,
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                true
            )

        // 在按钮下方显示
        popup.showUnderneathOf(button)
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
                // Git操作必须在后台线程执行
                statusLabel.text = "正在获取Git提交记录..."
                val workLog = withContext(Dispatchers.IO) {
                    workLogService.createWorkLog(date, includeCode)
                }

                // 显示找到的提交数量
                val commitCount = workLog.gitCommits.size
                statusLabel.text = "找到 $commitCount 个提交记录"

                if (commitCount == 0) {
                    // 没有提交记录
                    JOptionPane.showMessageDialog(
                        panel,
                        "在 $date 没有找到Git提交记录。\n将生成空白日志模板。",
                        "提示",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                }

                val settings = AppSettingsState.getInstance()

                // 检查是否需要调用AI
                val shouldCallAI = settings.apiUrlCompat.isNotBlank() &&
                                  settings.apiKeyCompat.isNotBlank() &&
                                  workLog.gitCommits.isNotEmpty()

                val aiSummary = if (shouldCallAI) {
                    try {
                        statusLabel.text = "正在调用 AI（提交数：$commitCount）..."
                        // AI调用也在后台线程
                        val summary = withContext(Dispatchers.IO) {
                            val aiService = project.getService(com.worklog.services.AIService::class.java)
                            aiService.summarizeWork(workLog.gitCommits, includeCode)
                        }
                        statusLabel.text = "AI 生成完成（字数：${summary.length}）"

                        if (summary.isBlank()) {
                            JOptionPane.showMessageDialog(
                                panel,
                                "AI 返回的内容为空。\n将使用基础日志格式。",
                                "警告",
                                JOptionPane.WARNING_MESSAGE
                            )
                            null
                        } else {
                            summary
                        }
                    } catch (e: Exception) {
                        val errorMsg = "AI 调用失败: ${e.message}"
                        statusLabel.text = errorMsg

                        // 显示详细错误信息
                        JOptionPane.showMessageDialog(
                            panel,
                            "AI 生成失败，将使用基础日志格式。\n\n错误信息：\n${e.message}\n\n" +
                            "请检查：\n" +
                            "1. API URL 和 API Key 是否正确配置\n" +
                            "2. 网络连接是否正常\n" +
                            "3. 可以在设置页面使用\"测试 AI 连接\"功能验证配置",
                            "AI 调用失败",
                            JOptionPane.WARNING_MESSAGE
                        )
                        null
                    }
                } else {
                    if (settings.apiUrlCompat.isBlank() || settings.apiKeyCompat.isBlank()) {
                        statusLabel.text = "未配置 AI（将使用基础日志）"
                    } else if (workLog.gitCommits.isEmpty()) {
                        statusLabel.text = "无提交记录（将使用基础日志）"
                    }
                    null
                }

                // 在后台线程生成和保存内容
                statusLabel.text = "正在生成日志内容..."
                withContext(Dispatchers.IO) {
                    // 清空模板内容，避免重复添加
                    workLog.content = ""

                    val fullContent = com.worklog.utils.MarkdownUtil.generateFullWorkLog(
                        workLog = workLog,
                        aiSummary = aiSummary,
                        includeCodeDiff = includeCode
                    )

                    workLog.content = fullContent
                    workLogService.saveWorkLog(workLog)
                }

                // UI更新必须在主线程
                if (!dateComboBox.getItemAt(0).equals(date)) {
                    dateComboBox.insertItemAt(date, 0)
                }
                dateComboBox.selectedItem = date
                loadWorkLog()

                statusLabel.text = if (aiSummary != null) {
                    "日志生成成功（含 AI 总结，$commitCount 个提交）"
                } else if (commitCount > 0) {
                    "日志生成成功（$commitCount 个提交，无 AI）"
                } else {
                    "日志生成成功（空白模板）"
                }

                val message = if (aiSummary != null) {
                    "工作日志已生成\n\n包含：\n- AI 工作总结\n- $commitCount 个 Git 提交记录" +
                    if (includeCode) "\n- 代码变更摘要" else ""
                } else if (commitCount > 0) {
                    "工作日志已生成（基础格式）\n\n包含：\n- $commitCount 个 Git 提交记录" +
                    if (includeCode) "\n- 代码变更摘要" else "" +
                    "\n\n提示：未包含 AI 总结"
                } else {
                    "已生成空白日志模板\n\n未找到 Git 提交记录"
                }

                JOptionPane.showMessageDialog(
                    panel,
                    message,
                    "成功",
                    JOptionPane.INFORMATION_MESSAGE
                )
            } catch (e: Exception) {
                statusLabel.text = "生成失败: ${e.message}"
                e.printStackTrace()
                JOptionPane.showMessageDialog(
                    panel,
                    "生成失败:\n\n${e.message}\n\n堆栈跟踪:\n${e.stackTraceToString().take(500)}",
                    "错误",
                    JOptionPane.ERROR_MESSAGE
                )
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
