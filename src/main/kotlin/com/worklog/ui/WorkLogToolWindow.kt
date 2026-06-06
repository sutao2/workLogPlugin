package com.worklog.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.worklog.services.WorkLogService
import com.worklog.settings.AppSettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.swing.Icon
import javax.swing.*

class WorkLogToolWindow(private val project: Project) : Disposable {
    companion object {
        const val ID = "WorkLog"
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val workLogService = project.getService(WorkLogService::class.java)

    private val dateComboBox = ComboBox<LocalDate>()
    private val editorArea = WorkLogUi.editorArea()
    private val statusLabel = JBLabel("就绪")
    private val commitCountLabel = JBLabel("提交: 0")
    private val wordCountLabel = JBLabel("字数: 0")

    private val panel = JPanel(BorderLayout(0, 8))

    init {
        panel.border = JBUI.Borders.empty(10, 10, 8, 10)
        initUI()
        loadAvailableDates()
    }

    fun getContent(): JComponent {
        return panel
    }

    override fun dispose() {
        scope.cancel()
    }

    private fun initUI() {
        panel.add(createToolbar(), BorderLayout.NORTH)
        panel.add(createEditorPanel(), BorderLayout.CENTER)
    }

    private fun createToolbar(): JPanel {
        val toolbar = JPanel(BorderLayout(0, 8))
        toolbar.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border()),
            JBUI.Borders.empty(0, 0, 6, 0)
        )

        val datePanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        datePanel.add(WorkLogUi.mutedLabel("日期"))
        dateComboBox.preferredSize = Dimension(JBUI.scale(164), dateComboBox.preferredSize.height)
        dateComboBox.isEditable = true
        dateComboBox.addActionListener { loadWorkLog() }

        dateComboBox.editor.editorComponent.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    showDatePicker()
                }
            }
        })

        datePanel.add(dateComboBox)

        val actionRow = ResponsiveActionRow(
            datePanel,
            listOf(
                ToolbarItem("generate", "生成", "生成工作日志", AllIcons.Actions.Execute, showText = true, primary = true) {
                    showGenerateDialog()
                },
                ToolbarItem("save", "保存", "保存当前工作日志", AllIcons.Actions.MenuSaveall) {
                    saveWorkLog()
                },
                ToolbarItem("copy", "复制", "复制当前工作日志", AllIcons.Actions.Copy) {
                    copyWorkLog()
                },
                ToolbarItem("settings", "设置", "设置", AllIcons.General.Settings) {
                    openSettings()
                },
                ToolbarItem("more", "更多操作", "更多操作", AllIcons.Actions.More) { source ->
                    showMoreMenu(source)
                }
            )
        )
        toolbar.add(actionRow, BorderLayout.CENTER)

        return toolbar
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
    private fun showMoreMenu(button: Component) {
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

    private fun showOverflowMenu(button: Component, hiddenItems: List<ToolbarItem>) {
        val actionGroup = DefaultActionGroup().apply {
            hiddenItems.forEach { item ->
                if (item.id == "more") {
                    addSeparator()
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
                } else {
                    add(object : AnAction(item.text, item.tooltip, item.icon) {
                        override fun actionPerformed(e: AnActionEvent) {
                            item.action(button)
                        }
                    })
                }
            }
        }

        val popup = JBPopupFactory.getInstance()
            .createActionGroupPopup(
                null,
                actionGroup,
                DataContext.EMPTY_CONTEXT,
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                true
            )
        popup.showUnderneathOf(button)
    }

    private data class ToolbarItem(
        val id: String,
        val text: String,
        val tooltip: String,
        val icon: Icon,
        val showText: Boolean = false,
        val primary: Boolean = false,
        val action: (Component) -> Unit
    )

    private inner class ResponsiveActionRow(
        private val leadingComponent: JComponent,
        private val items: List<ToolbarItem>
    ) : JPanel(BorderLayout(8, 0)) {
        private val actionsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        private val overflowButton = WorkLogUi.iconButton(AllIcons.Actions.More, "更多操作") { button ->
            showOverflowMenu(button, hiddenItems)
        }
        private var hiddenItems: List<ToolbarItem> = emptyList()

        init {
            border = JBUI.Borders.empty()
            add(leadingComponent, BorderLayout.WEST)
            add(actionsPanel, BorderLayout.CENTER)
            add(overflowButton, BorderLayout.EAST)
            overflowButton.isVisible = false
            addComponentListener(object : java.awt.event.ComponentAdapter() {
                override fun componentResized(e: java.awt.event.ComponentEvent?) {
                    rebuild()
                }
            })
            rebuild()
        }

        override fun addNotify() {
            super.addNotify()
            SwingUtilities.invokeLater { rebuild() }
        }

        private fun rebuild() {
            val availableWidth = if (width <= 0) {
                Int.MAX_VALUE
            } else {
                (width - insets.left - insets.right - leadingComponent.preferredSize.width - JBUI.scale(8)).coerceAtLeast(0)
            }

            val mandatory = items.filter { it.primary }
            val optional = items.filterNot { it.primary }
            val mandatoryWidth = mandatory.sumOf { preferredButtonWidth(it) }
            val optionalWidth = optional.sumOf { preferredButtonWidth(it) }
            val gapWidth = JBUI.scale(4) * (items.size - 1).coerceAtLeast(0)

            val visibleItems = mutableListOf<ToolbarItem>()
            val hidden = mutableListOf<ToolbarItem>()

            if (mandatoryWidth + optionalWidth + gapWidth <= availableWidth) {
                visibleItems.addAll(items)
            } else {
                visibleItems.addAll(mandatory)
                var remaining = availableWidth - mandatoryWidth - preferredOverflowWidth() - JBUI.scale(12)
                optional.forEach { item ->
                    val itemWidth = preferredButtonWidth(item) + JBUI.scale(4)
                    if (remaining >= itemWidth) {
                        visibleItems.add(item)
                        remaining -= itemWidth
                    } else {
                        hidden.add(item)
                    }
                }
            }

            hiddenItems = hidden
            actionsPanel.removeAll()
            visibleItems.forEach { actionsPanel.add(createToolbarButton(it)) }
            overflowButton.isVisible = hiddenItems.isNotEmpty()
            revalidate()
            repaint()
        }

        private fun preferredButtonWidth(item: ToolbarItem): Int {
            return createToolbarButton(item).preferredSize.width
        }

        private fun preferredOverflowWidth(): Int {
            return overflowButton.preferredSize.width
        }

        private fun createToolbarButton(item: ToolbarItem): JButton {
            return if (item.showText) {
                WorkLogUi.button(item.text, primary = item.primary, icon = item.icon) {}.apply {
                    toolTipText = item.tooltip
                    addActionListener {
                        item.action(this)
                    }
                }
            } else {
                WorkLogUi.iconButton(item.icon, item.tooltip) { button ->
                    item.action(button)
                }
            }
        }
    }

    private fun createEditorPanel(): JPanel {
        val panel = JPanel(BorderLayout(0, 8))
        panel.border = JBUI.Borders.empty()

        val metrics = JPanel(FlowLayout(FlowLayout.RIGHT, 10, 0))
        statusLabel.foreground = JBColor.GRAY
        commitCountLabel.foreground = JBColor.GRAY
        wordCountLabel.foreground = JBColor.GRAY
        metrics.add(statusLabel)
        metrics.add(commitCountLabel)
        metrics.add(wordCountLabel)

        val header = JPanel(BorderLayout(12, 0))
        val titlePanel = JPanel(BorderLayout(0, 2))
        titlePanel.add(JBLabel("工作日志").apply {
            font = font.deriveFont(Font.BOLD, 15f)
        }, BorderLayout.NORTH)
        titlePanel.add(WorkLogUi.mutedLabel("Markdown 内容会保存到项目工作日志目录"), BorderLayout.CENTER)
        header.add(titlePanel, BorderLayout.CENTER)
        header.add(metrics, BorderLayout.EAST)
        panel.add(header, BorderLayout.NORTH)

        editorArea.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = updateWordCount()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = updateWordCount()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = updateWordCount()
        })

        editorArea.margin = JBUI.insets(12)
        editorArea.font = Font("Monospaced", Font.PLAIN, 13)
        panel.add(WorkLogUi.borderedScrollPane(editorArea), BorderLayout.CENTER)

        return panel
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
        val selectedDate = getSelectedDateFromComboBox()
        if (selectedDate == null) {
            statusLabel.text = "日期格式应为 yyyy-MM-dd"
            return
        }

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
        val selectedDate = getSelectedDateFromComboBox()
        if (selectedDate == null) {
            Messages.showWarningDialog(project, "日期格式应为 yyyy-MM-dd", "保存失败")
            return
        }
        val content = editorArea.text ?: ""

        if (content.isBlank()) {
            Messages.showWarningDialog(project, "日志内容不能为空", "保存失败")
            return
        }

        try {
            workLogService.updateWorkLogContent(selectedDate, content)
            statusLabel.text = "已保存: $selectedDate"
            loadAvailableDates()
        } catch (e: Exception) {
            statusLabel.text = "保存失败: ${e.message}"
            Messages.showErrorDialog(project, "保存失败: ${e.message}", "错误")
        }
    }

    private fun showGenerateDialog() {
        val dialog = GenerateWorkLogDialog(project, getSelectedDateFromComboBox() ?: LocalDate.now())
        if (dialog.showAndGet()) {
            generateWorkLog(dialog.getSelectedDate(), dialog.isIncludeCode(), dialog.isIncludeUncommitted())
        }
    }

    private fun generateWorkLog(date: LocalDate, includeCode: Boolean, includeUncommitted: Boolean) {
        statusLabel.text = "正在生成日志..."

        scope.launch {
            try {
                // Git操作必须在后台线程执行
                statusLabel.text = "正在获取Git提交记录..."
                val workLog = withContext(Dispatchers.IO) {
                    workLogService.createWorkLog(date, includeCode, includeUncommitted)
                }

                // 显示找到的提交数量
                val commitCount = workLog.gitCommits.size
                statusLabel.text = "找到 $commitCount 个提交记录"

                if (commitCount == 0) {
                    statusLabel.text = "无提交记录，将生成空白模板"
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
                            Messages.showWarningDialog(project, "AI 返回内容为空，将使用基础日志格式。", "AI 生成")
                            null
                        } else {
                            summary
                        }
                    } catch (e: Exception) {
                        val errorMsg = "AI 调用失败: ${e.message}"
                        statusLabel.text = errorMsg

                        Messages.showWarningDialog(
                            project,
                            "AI 生成失败，将使用基础日志格式。\n\n错误信息：\n${e.message}\n\n" +
                            "请检查：\n" +
                            "1. API URL 和 API Key 是否正确配置\n" +
                            "2. 网络连接是否正常\n" +
                            "3. 可以在设置页面使用\"测试 AI 连接\"功能验证配置",
                            "AI 调用失败"
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

            } catch (e: Exception) {
                statusLabel.text = "生成失败: ${e.message}"
                e.printStackTrace()
                Messages.showErrorDialog(
                    project,
                    "生成失败:\n\n${e.message}\n\n堆栈跟踪:\n${e.stackTraceToString().take(500)}",
                    "错误"
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

    private fun getSelectedDateFromComboBox(): LocalDate? {
        return when (val item = dateComboBox.selectedItem) {
            is LocalDate -> item
            is String -> runCatching { LocalDate.parse(item.trim()) }.getOrNull()
            else -> null
        }
    }

    private fun copyWorkLog() {
        val content = editorArea.text ?: ""

        if (content.isBlank()) {
            Messages.showWarningDialog(project, "日志内容为空，无法复制", "提示")
            return
        }

        try {
            CopyPasteManager.getInstance().setContents(StringSelection(content))
            statusLabel.text = "已复制到剪贴板"
        } catch (e: Exception) {
            statusLabel.text = "复制失败: ${e.message}"
            Messages.showErrorDialog(project, "复制失败: ${e.message}", "错误")
        }
    }
}
