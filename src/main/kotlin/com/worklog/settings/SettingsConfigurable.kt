package com.worklog.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.FormBuilder
import com.worklog.models.ApiConfig
import com.worklog.models.ApiFormat
import com.worklog.models.ExportFormat
import com.worklog.services.AIService
import com.worklog.ui.ApiConfigDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Dimension
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

    override fun getDisplayName(): String {
        return "WorkLog"
    }

    override fun createComponent(): JComponent {
        val settings = AppSettingsState.getInstance()

        // 加载当前设置
        loadSettings(settings)

        // AI API 设置面板（使用表格形式）
        val aiApiPanel = createApiConfigPanel()

        // 代码访问权限面板
        val codeAccessPanel = FormBuilder.createFormBuilder()
            .addComponent(allowCodeAccessCheckBox)
            .addComponent(rememberCodeAccessCheckBox)
            .addComponent(JBLabel("<html><small>允许读取代码后，AI 可以分析代码变更内容以生成更详细的总结</small></html>"))
            .addComponentFillVertically(JPanel(), 0)
            .panel

        // 提醒设置面板
        val reminderPanel = FormBuilder.createFormBuilder()
            .addComponent(reminderEnabledCheckBox)
            .addLabeledComponent(JBLabel("提醒时间 (HH:mm):"), reminderTimeField, 1, false)
            .addComponent(closeReminderCheckBox)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        // 导出和存储设置面板
        val storagePanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("默认导出格式:"), exportFormatComboBox, 1, false)
            .addLabeledComponent(JBLabel("存储路径:"), storageLocationField, 1, false)
            .addComponent(JBLabel("<html><small>相对于项目根目录的路径</small></html>"))
            .addComponentFillVertically(JPanel(), 0)
            .panel

        // 提示词模板面板
        val systemPromptScrollPane = JScrollPane(systemPromptArea)
        val userPromptScrollPane = JScrollPane(userPromptTemplateArea)
        val promptPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("系统提示词:"), systemPromptScrollPane, 1, true)
            .addLabeledComponent(JBLabel("用户提示词模板:"), userPromptScrollPane, 1, true)
            .addComponent(JBLabel("<html><small>可用变量: {{commits}}, {{code_diff}}, {{#if hasCodeAccess}}...{{/if}}</small></html>"))
            .addComponentFillVertically(JPanel(), 0)
            .panel

        // 日志输出模板面板
        val outputTemplateScrollPane = JScrollPane(workLogOutputTemplateArea)
        val examplesScrollPane = JScrollPane(templateExamplesArea)
        val templatePanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("工作日志输出模板:"), outputTemplateScrollPane, 1, true)
            .addComponent(JBLabel("<html><small>可用变量: {{date}}, {{ai_summary}}, {{git_commits}}, {{code_changes}}<br>条件语法: {{#if hasCodeAccess}}...{{/if}}</small></html>"))
            .addLabeledComponent(JBLabel("模板示例（仅供参考）:"), examplesScrollPane, 1, true)
            .addComponent(JBLabel("<html><small>提示：可以从示例中复制模板格式到上方的输出模板中</small></html>"))
            .addComponentFillVertically(JPanel(), 0)
            .panel

        // 文件过滤设置面板
        excludedFileExtensionsArea.lineWrap = true
        excludedFileExtensionsArea.wrapStyleWord = true
        excludedDirectoriesArea.lineWrap = true
        excludedDirectoriesArea.wrapStyleWord = true

        val extensionsScrollPane = JScrollPane(excludedFileExtensionsArea)
        val directoriesScrollPane = JScrollPane(excludedDirectoriesArea)

        val filterPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("排除的文件扩展名:"), extensionsScrollPane, 1, true)
            .addComponent(JBLabel("<html><small>用逗号分隔，例如: ckpt,pth,bin,pb<br>这些类型的文件不会被包含在 Git diff 中，避免发送大文件到 AI</small></html>"))
            .addLabeledComponent(JBLabel("排除的目录:"), directoriesScrollPane, 1, true)
            .addComponent(JBLabel("<html><small>用逗号分隔，例如: /node_modules/,/dist/,/build/</small></html>"))
            .addLabeledComponent(JBLabel("文件大小限制 (KB):"), maxFileSizeField, 1, false)
            .addComponent(JBLabel("<html><small>超过此大小的文件不会获取 diff（默认 1024KB = 1MB）</small></html>"))
            .addComponentFillVertically(JPanel(), 0)
            .panel

        // 创建选项卡面板
        val tabbedPane = JTabbedPane()
        tabbedPane.addTab("AI API 配置", aiApiPanel)
        tabbedPane.addTab("代码访问权限", codeAccessPanel)
        tabbedPane.addTab("提醒设置", reminderPanel)
        tabbedPane.addTab("存储和导出", storagePanel)
        tabbedPane.addTab("文件过滤", filterPanel)
        tabbedPane.addTab("提示词模板", promptPanel)
        tabbedPane.addTab("输出模板", templatePanel)

        settingsPanel = JPanel(BorderLayout())
        settingsPanel?.add(tabbedPane, BorderLayout.CENTER)

        return settingsPanel!!
    }

    private fun loadSettings(settings: AppSettingsState) {
        // 加载 API 配置列表
        apiConfigTableModel.configs.clear()
        apiConfigTableModel.configs.addAll(settings.apiConfigs.map { it.copy() })
        apiConfigTableModel.fireTableDataChanged()

        allowCodeAccessCheckBox.isSelected = settings.allowCodeAccess
        rememberCodeAccessCheckBox.isSelected = settings.rememberCodeAccessChoice

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
            config.copy(
                id = config.id,
                name = config.name,
                apiUrl = config.apiUrl,
                apiKey = config.apiKey,
                modelName = config.modelName,
                apiFormat = config.apiFormat,
                customRequestTemplate = config.customRequestTemplate,
                customResponseJsonPath = config.customResponseJsonPath,
                isEnabled = config.isEnabled  // 保持原始的启用状态！
            )
        })

        // 调试日志
        println("=== WorkLog Settings Apply ===")
        println("Saving ${settings.apiConfigs.size} API configs")
        settings.apiConfigs.forEachIndexed { index, config ->
            println("Config $index: ${config.name}, enabled=${config.isEnabled}, hasKey=${config.apiKey.isNotBlank()}")
        }
        val activeConfig = settings.getActiveApiConfig()
        println("Active config after save: ${activeConfig?.name}, key=${activeConfig?.apiKey?.take(4)}...")
        println("==============================")

        settings.allowCodeAccess = allowCodeAccessCheckBox.isSelected
        settings.rememberCodeAccessChoice = rememberCodeAccessCheckBox.isSelected

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
    }

    override fun reset() {
        loadSettings(AppSettingsState.getInstance())
    }

    /**
     * 创建 API 配置面板（表格形式）
     */
    private fun createApiConfigPanel(): JPanel {
        val panel = JPanel(BorderLayout(5, 5))

        // 配置表格
        apiConfigTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        val scrollPane = JScrollPane(apiConfigTable)
        scrollPane.preferredSize = Dimension(600, 200)

        // 按钮面板
        val buttonPanel = JPanel()
        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.Y_AXIS)

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
        buttonPanel.add(Box.createVerticalStrut(5))
        buttonPanel.add(editButton)
        buttonPanel.add(Box.createVerticalStrut(5))
        buttonPanel.add(deleteButton)
        buttonPanel.add(Box.createVerticalStrut(5))
        buttonPanel.add(copyButton)
        buttonPanel.add(Box.createVerticalStrut(5))
        buttonPanel.add(enableButton)
        buttonPanel.add(Box.createVerticalStrut(10))
        buttonPanel.add(testButton)

        panel.add(scrollPane, BorderLayout.CENTER)
        panel.add(buttonPanel, BorderLayout.EAST)

        val helpLabel = JBLabel("<html><small>双击表格行可以编辑配置。每次只能启用一个 API 配置。</small></html>")
        panel.add(helpLabel, BorderLayout.SOUTH)

        // 双击编辑
        apiConfigTable.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    editApiConfig()
                }
            }
        })

        return panel
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
        val originalConfigs = tempSettings.apiConfigs.toList()

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
                    // 测试失败，恢复原始配置
                    tempSettings.apiConfigs.clear()
                    tempSettings.apiConfigs.addAll(originalConfigs)

                    Messages.showErrorDialog(
                        "AI 接口测试失败：\n\n${e.message}\n\n请检查配置是否正确",
                        "测试失败"
                    )
                }
            }
        } catch (e: Exception) {
            // 发生异常，恢复原始配置
            tempSettings.apiConfigs.clear()
            tempSettings.apiConfigs.addAll(originalConfigs)
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
