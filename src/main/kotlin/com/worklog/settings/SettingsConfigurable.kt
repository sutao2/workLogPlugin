package com.worklog.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.worklog.models.ApiConfig
import com.worklog.models.ApiFormat
import com.worklog.models.ExportFormat
import com.worklog.services.AIService
import com.worklog.services.PreCommitHookService
import com.worklog.services.ReminderService
import com.worklog.ui.ApiConfigDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.table.AbstractTableModel

/**
 * 插件设置配置页面
 */
class SettingsConfigurable : Configurable {

    private var settingsPanel: JPanel? = null

    // AI API 配置表格
    private val apiConfigTableModel = ApiConfigTableModel()
    private val apiConfigTable = JBTable(apiConfigTableModel)

    // 代码访问权限设置
    private val allowCodeAccessCheckBox = JBCheckBox("允许读取代码内容（用于AI总结）")
    private val rememberCodeAccessCheckBox = JBCheckBox("记住我的选择")

    // 提醒设置
    private val reminderEnabledCheckBox = JBCheckBox("启用定时提醒")
    private val reminderTimeField = JBTextField()
    private val closeReminderCheckBox = JBCheckBox("IDE 关闭时提醒填写日志")

    // 导出设置
    private val exportFormatComboBox = JComboBox(ExportFormat.values())

    // 存储路径设置
    private val storageLocationField = JBTextField()

    // 提示词模板
    private val systemPromptArea = JTextArea(5, 50)
    private val userPromptTemplateArea = JTextArea(10, 50)

    // 日志输出模板
    private val workLogOutputTemplateArea = JTextArea(15, 50)
    private val templateExamplesArea = JTextArea(20, 50)

    // 文件过滤设置
    private val excludedFileExtensionsArea = JTextArea(5, 50)
    private val excludedDirectoriesArea = JTextArea(5, 50)
    private val maxFileSizeField = JBTextField()

    // 代码评审设置
    private val reviewEnabledCheckBox = JBCheckBox("启用代码评审功能")
    private val reviewAutoRunCheckBox = JBCheckBox("提交时自动触发代码评审")
    private val reviewMaxDiffCharsField = JBTextField()
    private val reviewSystemPromptArea = JTextArea(5, 50)
    private val reviewUserPromptArea = JTextArea(10, 50)

    override fun getDisplayName(): String {
        return "WorkLog"
    }

    override fun createComponent(): JComponent {
        val settings = AppSettingsState.getInstance()

        // 加载当前设置
        loadSettings(settings)

        // 创建选项卡面板（使用延迟加载）
        val tabbedPane = JTabbedPane()

        // 缓存已创建的面板
        val panelCache = mutableMapOf<Int, JPanel>()

        // 添加标签页（不立即创建内容）
        tabbedPane.addTab("AI API 配置", null)
        tabbedPane.addTab("代码访问权限", null)
        tabbedPane.addTab("代码评审", null)
        tabbedPane.addTab("提醒设置", null)
        tabbedPane.addTab("存储和导出", null)
        tabbedPane.addTab("文件过滤", null)
        tabbedPane.addTab("提示词模板", null)
        tabbedPane.addTab("输出模板", null)

        // 添加标签页切换监听器，实现延迟加载
        tabbedPane.addChangeListener { _ ->
            val selectedIndex = tabbedPane.selectedIndex
            if (tabbedPane.getComponentAt(selectedIndex) == null) {
                // 只在标签页内容为空时创建
                val panel = when (selectedIndex) {
                    0 -> panelCache.getOrPut(0) { createApiConfigPanel() }
                    1 -> panelCache.getOrPut(1) { createCodeAccessPanel() }
                    2 -> panelCache.getOrPut(2) { createCodeReviewPanel() }
                    3 -> panelCache.getOrPut(3) { createReminderPanel() }
                    4 -> panelCache.getOrPut(4) { createStoragePanel() }
                    5 -> panelCache.getOrPut(5) { createFilterPanel() }
                    6 -> panelCache.getOrPut(6) { createPromptPanel() }
                    7 -> panelCache.getOrPut(7) { createTemplatePanel() }
                    else -> JPanel()
                }
                tabbedPane.setComponentAt(selectedIndex, panel)
            }
        }

        // 立即创建第一个标签页（用户最可能查看的）
        tabbedPane.setComponentAt(0, createApiConfigPanel())

        settingsPanel = JPanel(BorderLayout())
        settingsPanel?.border = JBUI.Borders.empty(8)
        settingsPanel?.add(tabbedPane, BorderLayout.CENTER)

        return settingsPanel!!
    }

    private fun createCodeAccessPanel(): JPanel {
        return wrapSettingsPage(
            FormBuilder.createFormBuilder()
            .addComponent(allowCodeAccessCheckBox)
            .addComponent(rememberCodeAccessCheckBox)
            .addComponent(helpLabel("允许读取代码后，AI 可以分析代码变更内容以生成更详细的总结。"))
            .addComponentFillVertically(JPanel(), 0)
            .panel
        )
    }

    private fun createCodeReviewPanel(): JPanel {
        reviewSystemPromptArea.lineWrap = true
        reviewSystemPromptArea.wrapStyleWord = true
        reviewUserPromptArea.lineWrap = true
        reviewUserPromptArea.wrapStyleWord = true

        return wrapSettingsPage(
            FormBuilder.createFormBuilder()
            .addComponent(reviewEnabledCheckBox)
            .addComponent(reviewAutoRunCheckBox)
            .addComponent(helpLabel("启用后，提交代码时将自动触发 AI 评审，评审完成后可选择继续提交或取消。"))
            .addLabeledComponent(JBLabel("最大 diff 字符数:"), reviewMaxDiffCharsField, 1, false)
            .addComponent(helpLabel("超过此长度的 diff 会被截断，默认 50000。"))
            .addLabeledComponent(JBLabel("评审系统提示词:"), JBScrollPane(reviewSystemPromptArea), 1, true)
            .addLabeledComponent(JBLabel("评审用户提示词模板:"), JBScrollPane(reviewUserPromptArea), 1, true)
            .addComponent(helpLabel("可用变量：{{files}}, {{diff}}, {{commit_message}}"))
            .addComponentFillVertically(JPanel(), 0)
            .panel
        )
    }

    private fun createReminderPanel(): JPanel {
        return wrapSettingsPage(
            FormBuilder.createFormBuilder()
            .addComponent(reminderEnabledCheckBox)
            .addLabeledComponent(JBLabel("提醒时间 (HH:mm):"), reminderTimeField, 1, false)
            .addComponent(closeReminderCheckBox)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        )
    }

    private fun createStoragePanel(): JPanel {
        return wrapSettingsPage(
            FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("默认导出格式:"), exportFormatComboBox, 1, false)
            .addLabeledComponent(JBLabel("存储路径:"), storageLocationField, 1, false)
            .addComponent(helpLabel("相对于项目根目录的路径。"))
            .addComponentFillVertically(JPanel(), 0)
            .panel
        )
    }

    private fun createPromptPanel(): JPanel {
        val systemPromptScrollPane = JBScrollPane(systemPromptArea)
        val userPromptScrollPane = JBScrollPane(userPromptTemplateArea)
        return wrapSettingsPage(
            FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("系统提示词:"), systemPromptScrollPane, 1, true)
            .addLabeledComponent(JBLabel("用户提示词模板:"), userPromptScrollPane, 1, true)
            .addComponent(helpLabel("可用变量：{{commits}}, {{code_diff}}, {{#if hasCodeAccess}}...{{/if}}"))
            .addComponentFillVertically(JPanel(), 0)
            .panel
        )
    }

    private fun createTemplatePanel(): JPanel {
        val outputTemplateScrollPane = JBScrollPane(workLogOutputTemplateArea)
        val examplesScrollPane = JBScrollPane(templateExamplesArea)
        return wrapSettingsPage(
            FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("工作日志输出模板:"), outputTemplateScrollPane, 1, true)
            .addComponent(helpLabel("可用变量：{{date}}, {{ai_summary}}, {{git_commits}}, {{code_changes}}。条件语法：{{#if hasCodeAccess}}...{{/if}}"))
            .addLabeledComponent(JBLabel("模板示例（仅供参考）:"), examplesScrollPane, 1, true)
            .addComponent(helpLabel("可以从示例中复制模板格式到上方的输出模板中。"))
            .addComponentFillVertically(JPanel(), 0)
            .panel
        )
    }

    private fun createFilterPanel(): JPanel {
        excludedFileExtensionsArea.lineWrap = true
        excludedFileExtensionsArea.wrapStyleWord = true
        excludedDirectoriesArea.lineWrap = true
        excludedDirectoriesArea.wrapStyleWord = true

        val extensionsScrollPane = JBScrollPane(excludedFileExtensionsArea)
        val directoriesScrollPane = JBScrollPane(excludedDirectoriesArea)

        return wrapSettingsPage(
            FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("排除的文件扩展名:"), extensionsScrollPane, 1, true)
            .addComponent(helpLabel("用逗号分隔，例如：ckpt,pth,bin,pb。这些类型不会被包含在 Git diff 中。"))
            .addLabeledComponent(JBLabel("排除的目录:"), directoriesScrollPane, 1, true)
            .addComponent(helpLabel("用逗号分隔，例如：/node_modules/,/dist/,/build/。"))
            .addLabeledComponent(JBLabel("文件大小限制 (KB):"), maxFileSizeField, 1, false)
            .addComponent(helpLabel("超过此大小的文件不会获取 diff，默认 1024KB。"))
            .addComponentFillVertically(JPanel(), 0)
            .panel
        )
    }

    private fun loadSettings(settings: AppSettingsState) {
        // 加载 API 配置列表
        apiConfigTableModel.configs.clear()
        apiConfigTableModel.configs.addAll(settings.apiConfigs.map { config ->
            config.copy(apiKey = ApiKeyStore.getApiKey(config.id, config.apiKey))
        })
        apiConfigTableModel.fireTableDataChanged()

        allowCodeAccessCheckBox.isSelected = settings.allowCodeAccess
        rememberCodeAccessCheckBox.isSelected = settings.rememberCodeAccessChoice

        reviewEnabledCheckBox.isSelected = settings.reviewEnabled
        reviewAutoRunCheckBox.isSelected = settings.reviewAutoRunBeforeCommit
        reviewMaxDiffCharsField.text = settings.reviewMaxDiffChars.toString()
        reviewSystemPromptArea.text = settings.reviewSystemPrompt
        reviewUserPromptArea.text = settings.reviewUserPromptTemplate

        reminderEnabledCheckBox.isSelected = settings.reminderEnabled
        reminderTimeField.text = settings.reminderTime
        closeReminderCheckBox.isSelected = settings.closeReminderEnabled

        exportFormatComboBox.selectedItem = settings.defaultExportFormat
        storageLocationField.text = settings.storageLocation

        systemPromptArea.text = settings.systemPrompt
        userPromptTemplateArea.text = settings.userPromptTemplate

        workLogOutputTemplateArea.text = settings.workLogOutputTemplate
        templateExamplesArea.text = settings.templateExamples

        excludedFileExtensionsArea.text = settings.excludedFileExtensions
        excludedDirectoriesArea.text = settings.excludedDirectories
        maxFileSizeField.text = settings.maxFileSizeKb.toString()
    }

    override fun isModified(): Boolean {
        val settings = AppSettingsState.getInstance()

        // 检查 API 配置是否被修改
        if (apiConfigTableModel.configs.size != settings.apiConfigs.size) {
            return true
        }

        for (i in apiConfigTableModel.configs.indices) {
            val tableConfig = apiConfigTableModel.configs[i]
            val settingsConfig = settings.apiConfigs.getOrNull(i)
            if (settingsConfig == null || tableConfig != settingsConfig) {
                return true
            }
        }

        return allowCodeAccessCheckBox.isSelected != settings.allowCodeAccess ||
                rememberCodeAccessCheckBox.isSelected != settings.rememberCodeAccessChoice ||
                reviewEnabledCheckBox.isSelected != settings.reviewEnabled ||
                reviewAutoRunCheckBox.isSelected != settings.reviewAutoRunBeforeCommit ||
                reviewMaxDiffCharsField.text != settings.reviewMaxDiffChars.toString() ||
                reviewSystemPromptArea.text != settings.reviewSystemPrompt ||
                reviewUserPromptArea.text != settings.reviewUserPromptTemplate ||
                reminderEnabledCheckBox.isSelected != settings.reminderEnabled ||
                reminderTimeField.text != settings.reminderTime ||
                closeReminderCheckBox.isSelected != settings.closeReminderEnabled ||
                exportFormatComboBox.selectedItem != settings.defaultExportFormat ||
                storageLocationField.text != settings.storageLocation ||
                systemPromptArea.text != settings.systemPrompt ||
                userPromptTemplateArea.text != settings.userPromptTemplate ||
                workLogOutputTemplateArea.text != settings.workLogOutputTemplate ||
                templateExamplesArea.text != settings.templateExamples ||
                excludedFileExtensionsArea.text != settings.excludedFileExtensions ||
                excludedDirectoriesArea.text != settings.excludedDirectories ||
                maxFileSizeField.text != settings.maxFileSizeKb.toString()
    }

    override fun apply() {
        val settings = AppSettingsState.getInstance()

        // 保存 API 配置列表（使用 data class 的 copy 保持状态）
        settings.apiConfigs.clear()
        settings.apiConfigs.addAll(apiConfigTableModel.configs.map { config ->
            ApiKeyStore.setApiKey(config.id, config.apiKey)
            config.copy(
                id = config.id,
                name = config.name,
                apiUrl = config.apiUrl,
                apiKey = "",
                modelName = config.modelName,
                apiFormat = config.apiFormat,
                customRequestTemplate = config.customRequestTemplate,
                customResponseJsonPath = config.customResponseJsonPath,
                isEnabled = config.isEnabled  // 保持原始的启用状态！
            )
        })

        settings.allowCodeAccess = allowCodeAccessCheckBox.isSelected
        settings.rememberCodeAccessChoice = rememberCodeAccessCheckBox.isSelected

        settings.reviewEnabled = reviewEnabledCheckBox.isSelected
        settings.reviewAutoRunBeforeCommit = reviewAutoRunCheckBox.isSelected
        settings.reviewMaxDiffChars = (reviewMaxDiffCharsField.text.toIntOrNull() ?: 50000).coerceAtLeast(1000)
        settings.reviewSystemPrompt = reviewSystemPromptArea.text
        settings.reviewUserPromptTemplate = reviewUserPromptArea.text

        settings.reminderEnabled = reminderEnabledCheckBox.isSelected
        settings.reminderTime = reminderTimeField.text
        settings.closeReminderEnabled = closeReminderCheckBox.isSelected

        settings.defaultExportFormat = exportFormatComboBox.selectedItem as ExportFormat
        settings.storageLocation = storageLocationField.text

        settings.systemPrompt = systemPromptArea.text
        settings.userPromptTemplate = userPromptTemplateArea.text

        settings.workLogOutputTemplate = workLogOutputTemplateArea.text
        settings.templateExamples = templateExamplesArea.text

        settings.excludedFileExtensions = excludedFileExtensionsArea.text
        settings.excludedDirectories = excludedDirectoriesArea.text
        try {
            settings.maxFileSizeKb = maxFileSizeField.text.toIntOrNull() ?: 1024
        } catch (e: Exception) {
            settings.maxFileSizeKb = 1024
        }

        ProjectManager.getInstance().openProjects
            .filter { !it.isDisposed }
            .forEach {
                it.getService(ReminderService::class.java).restart()
                try {
                    it.getService(PreCommitHookService::class.java).installOrUpdateHookIfNeeded()
                } catch (e: Exception) {
                    Messages.showWarningDialog(
                        it,
                        "Pre-commit hook 同步失败：${e.message}",
                        "WorkLog"
                    )
                }
            }
    }

    override fun reset() {
        loadSettings(AppSettingsState.getInstance())
    }

    private fun wrapSettingsPage(content: JPanel): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(8)
        content.border = JBUI.Borders.empty()
        panel.add(content, BorderLayout.CENTER)
        return panel
    }

    private fun helpLabel(text: String): JBLabel {
        return JBLabel("<html><small>$text</small></html>").apply {
            foreground = JBColor.GRAY
        }
    }

    /**
     * 创建 API 配置面板（表格形式）
     */
    private fun createApiConfigPanel(): JPanel {
        val panel = JPanel(BorderLayout(0, 8))

        // 配置表格
        apiConfigTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        apiConfigTable.rowHeight = JBUI.scale(26)
        val scrollPane = JBScrollPane(apiConfigTable)
        scrollPane.preferredSize = Dimension(600, 200)

        // 按钮面板
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))

        val addButton = JButton("添加")
        addButton.addActionListener { addApiConfig() }

        val editButton = JButton("编辑")
        editButton.addActionListener { editApiConfig() }

        val deleteButton = JButton("删除")
        deleteButton.addActionListener { deleteApiConfig() }

        val copyButton = JButton("复制")
        copyButton.addActionListener { copyApiConfig() }

        val enableButton = JButton("启用")
        enableButton.addActionListener { enableApiConfig() }

        val testButton = JButton("测试连接")
        testButton.addActionListener { testSelectedApiConfig() }

        buttonPanel.add(addButton)
        buttonPanel.add(editButton)
        buttonPanel.add(copyButton)
        buttonPanel.add(enableButton)
        buttonPanel.add(deleteButton)
        buttonPanel.add(testButton)

        panel.add(buttonPanel, BorderLayout.NORTH)
        panel.add(scrollPane, BorderLayout.CENTER)

        panel.add(helpLabel("双击表格行可以编辑配置。每次只能启用一个 API 配置。"), BorderLayout.SOUTH)

        // 双击编辑
        apiConfigTable.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    editApiConfig()
                }
            }
        })

        return wrapSettingsPage(panel)
    }

    private fun addApiConfig() {
        val dialog = ApiConfigDialog(null)
        if (dialog.showAndGet()) {
            val newConfig = dialog.getApiConfig()
            apiConfigTableModel.addConfig(newConfig)
        }
    }

    private fun editApiConfig() {
        val selectedRow = apiConfigTable.selectedRow
        if (selectedRow < 0) {
            Messages.showWarningDialog("请先选择一个配置", "提示")
            return
        }

        val config = apiConfigTableModel.configs[selectedRow]
        val dialog = ApiConfigDialog(null, config)
        if (dialog.showAndGet()) {
            val updatedConfig = dialog.getApiConfig()
            apiConfigTableModel.updateConfig(selectedRow, updatedConfig)
        }
    }

    private fun deleteApiConfig() {
        val selectedRow = apiConfigTable.selectedRow
        if (selectedRow < 0) {
            Messages.showWarningDialog("请先选择一个配置", "提示")
            return
        }

        val result = Messages.showYesNoDialog(
            "确定要删除选中的 API 配置吗？",
            "确认删除",
            Messages.getQuestionIcon()
        )

        if (result == Messages.YES) {
            apiConfigTableModel.removeConfig(selectedRow)
        }
    }

    private fun copyApiConfig() {
        val selectedRow = apiConfigTable.selectedRow
        if (selectedRow < 0) {
            Messages.showWarningDialog("请先选择一个配置", "提示")
            return
        }

        val config = apiConfigTableModel.configs[selectedRow]
        val copiedConfig = config.duplicate()  // 使用 duplicate() 创建副本
        apiConfigTableModel.addConfig(copiedConfig)
    }

    private fun enableApiConfig() {
        val selectedRow = apiConfigTable.selectedRow
        if (selectedRow < 0) {
            Messages.showWarningDialog("请先选择一个配置", "提示")
            return
        }

        // 设置选中的配置为启用状态
        apiConfigTableModel.setActiveConfig(selectedRow)

        // 提示用户记得保存
        Messages.showInfoMessage(
            "已将该配置设置为启用状态。\n请点击\"应用\"或\"确定\"按钮保存设置。",
            "提示"
        )
    }

    private fun testSelectedApiConfig() {
        val selectedRow = apiConfigTable.selectedRow
        if (selectedRow < 0) {
            Messages.showWarningDialog("请先选择一个配置", "提示")
            return
        }

        val project = ProjectManager.getInstance().openProjects.firstOrNull()
        if (project == null) {
            Messages.showErrorDialog("请先打开一个项目", "测试失败")
            return
        }

        // 先保存当前表格中的配置到 settings（临时）
        val tempSettings = AppSettingsState.getInstance()
        val originalConfigs = tempSettings.apiConfigs.map { it.copy() }
        val originalApiKeys = originalConfigs.associate { config ->
            config.id to ApiKeyStore.getApiKey(config.id, config.apiKey)
        }
        val testedConfigIds = apiConfigTableModel.configs.map { it.id }.toSet()
        fun restoreApiKeys() {
            testedConfigIds.forEach { configId ->
                ApiKeyStore.setApiKey(configId, originalApiKeys[configId].orEmpty())
            }
            originalApiKeys.forEach { (configId, apiKey) ->
                ApiKeyStore.setApiKey(configId, apiKey)
            }
        }

        try {
            // 应用当前表格的配置进行测试（保持启用状态）
            tempSettings.apiConfigs.clear()
            tempSettings.apiConfigs.addAll(apiConfigTableModel.configs.map { config ->
                config.copy(
                    id = config.id,
                    name = config.name,
                    apiUrl = config.apiUrl,
                    apiKey = config.apiKey,
                    modelName = config.modelName,
                    apiFormat = config.apiFormat,
                    customRequestTemplate = config.customRequestTemplate,
                    customResponseJsonPath = config.customResponseJsonPath,
                    isEnabled = config.isEnabled
                )
            })
            tempSettings.setActiveApiConfig(apiConfigTableModel.configs[selectedRow].id)
            apiConfigTableModel.configs.forEach { config ->
                ApiKeyStore.setApiKey(config.id, config.apiKey)
            }

            // 异步测试连接
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val aiService = project.getService(AIService::class.java)
                    withContext(Dispatchers.IO) {
                        aiService.testConnection()
                    }
                    Messages.showInfoMessage(
                        "AI 接口测试成功！配置正确。\n\n提示：请点击\"应用\"或\"确定\"按钮保存设置。",
                        "测试成功"
                    )
                } catch (e: Exception) {
                    Messages.showErrorDialog(
                        "AI 接口测试失败：\n\n${e.message}\n\n请检查配置是否正确",
                        "测试失败"
                    )
                } finally {
                    tempSettings.apiConfigs.clear()
                    tempSettings.apiConfigs.addAll(originalConfigs.map { it.copy() })
                    restoreApiKeys()
                }
            }
        } catch (e: Exception) {
            // 发生异常，恢复原始配置
            tempSettings.apiConfigs.clear()
            tempSettings.apiConfigs.addAll(originalConfigs.map { it.copy() })
            restoreApiKeys()
            Messages.showErrorDialog("测试失败：${e.message}", "错误")
        }
    }

    /**
     * API 配置表格模型
     */
    private class ApiConfigTableModel : AbstractTableModel() {
        val configs = mutableListOf<ApiConfig>()

        private val columnNames = arrayOf("状态", "配置名称", "API 地址", "API Key", "模型名称")

        override fun getRowCount(): Int = configs.size

        override fun getColumnCount(): Int = columnNames.size

        override fun getColumnName(column: Int): String = columnNames[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val config = configs[rowIndex]
            return when (columnIndex) {
                0 -> if (config.isEnabled) "✓" else ""
                1 -> config.name
                2 -> config.apiUrl
                3 -> config.getMaskedApiKey()
                4 -> config.modelName
                else -> ""
            }
        }

        fun addConfig(config: ApiConfig) {
            configs.add(config)
            fireTableRowsInserted(configs.size - 1, configs.size - 1)
        }

        fun updateConfig(row: Int, config: ApiConfig) {
            configs[row] = config
            fireTableRowsUpdated(row, row)
        }

        fun removeConfig(row: Int) {
            configs.removeAt(row)
            fireTableRowsDeleted(row, row)
        }

        fun setActiveConfig(row: Int) {
            configs.forEachIndexed { index, config ->
                config.isEnabled = (index == row)
            }
            fireTableDataChanged()
        }
    }
}
