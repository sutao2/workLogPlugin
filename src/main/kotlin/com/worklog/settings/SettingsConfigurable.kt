package com.worklog.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.worklog.models.ApiFormat
import com.worklog.models.ExportFormat
import java.awt.BorderLayout
import javax.swing.*

/**
 * 插件设置配置页面
 */
class SettingsConfigurable : Configurable {

    private var settingsPanel: JPanel? = null

    // AI API 设置
    private val apiUrlField = JBTextField()
    private val apiKeyField = JBPasswordField()
    private val modelNameField = JBTextField()
    private val apiFormatComboBox = JComboBox(ApiFormat.values())

    // 自定义 API 格式设置
    private val customRequestTemplateArea = JTextArea(10, 50)
    private val customResponseJsonPathField = JBTextField()

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

    override fun getDisplayName(): String {
        return "WorkLog"
    }

    override fun createComponent(): JComponent {
        val settings = AppSettingsState.getInstance()

        // 加载当前设置
        loadSettings(settings)

        // API 格式切换监听
        apiFormatComboBox.addActionListener {
            updateCustomFieldsVisibility()
        }

        // AI API 设置面板
        val aiApiPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("API URL:"), apiUrlField, 1, false)
            .addLabeledComponent(JBLabel("API Key:"), apiKeyField, 1, false)
            .addLabeledComponent(JBLabel("模型名称:"), modelNameField, 1, false)
            .addLabeledComponent(JBLabel("API 格式:"), apiFormatComboBox, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        // 自定义 API 格式面板
        val customScrollPane = JScrollPane(customRequestTemplateArea)
        val customApiPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("请求模板 (JSON):"), customScrollPane, 1, true)
            .addLabeledComponent(JBLabel("响应 JSON 路径:"), customResponseJsonPathField, 1, false)
            .addComponent(JBLabel("<html><small>示例: data.content 或 choices[0].message.content</small></html>"))
            .addComponentFillVertically(JPanel(), 0)
            .panel

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

        // 创建选项卡面板
        val tabbedPane = JTabbedPane()
        tabbedPane.addTab("AI API 设置", aiApiPanel)
        tabbedPane.addTab("自定义 API", customApiPanel)
        tabbedPane.addTab("代码访问权限", codeAccessPanel)
        tabbedPane.addTab("提醒设置", reminderPanel)
        tabbedPane.addTab("存储和导出", storagePanel)
        tabbedPane.addTab("提示词模板", promptPanel)

        settingsPanel = JPanel(BorderLayout())
        settingsPanel?.add(tabbedPane, BorderLayout.CENTER)

        updateCustomFieldsVisibility()

        return settingsPanel!!
    }

    private fun loadSettings(settings: AppSettingsState) {
        apiUrlField.text = settings.apiUrl
        apiKeyField.text = settings.apiKey
        modelNameField.text = settings.modelName
        apiFormatComboBox.selectedItem = settings.apiFormat

        customRequestTemplateArea.text = settings.customRequestTemplate
        customResponseJsonPathField.text = settings.customResponseJsonPath

        allowCodeAccessCheckBox.isSelected = settings.allowCodeAccess
        rememberCodeAccessCheckBox.isSelected = settings.rememberCodeAccessChoice

        reminderEnabledCheckBox.isSelected = settings.reminderEnabled
        reminderTimeField.text = settings.reminderTime
        closeReminderCheckBox.isSelected = settings.closeReminderEnabled

        exportFormatComboBox.selectedItem = settings.defaultExportFormat
        storageLocationField.text = settings.storageLocation

        systemPromptArea.text = settings.systemPrompt
        userPromptTemplateArea.text = settings.userPromptTemplate
    }

    private fun updateCustomFieldsVisibility() {
        val isCustom = apiFormatComboBox.selectedItem == ApiFormat.CUSTOM
        customRequestTemplateArea.isEnabled = isCustom
        customResponseJsonPathField.isEnabled = isCustom
    }

    override fun isModified(): Boolean {
        val settings = AppSettingsState.getInstance()
        return apiUrlField.text != settings.apiUrl ||
                String(apiKeyField.password) != settings.apiKey ||
                modelNameField.text != settings.modelName ||
                apiFormatComboBox.selectedItem != settings.apiFormat ||
                customRequestTemplateArea.text != settings.customRequestTemplate ||
                customResponseJsonPathField.text != settings.customResponseJsonPath ||
                allowCodeAccessCheckBox.isSelected != settings.allowCodeAccess ||
                rememberCodeAccessCheckBox.isSelected != settings.rememberCodeAccessChoice ||
                reminderEnabledCheckBox.isSelected != settings.reminderEnabled ||
                reminderTimeField.text != settings.reminderTime ||
                closeReminderCheckBox.isSelected != settings.closeReminderEnabled ||
                exportFormatComboBox.selectedItem != settings.defaultExportFormat ||
                storageLocationField.text != settings.storageLocation ||
                systemPromptArea.text != settings.systemPrompt ||
                userPromptTemplateArea.text != settings.userPromptTemplate
    }

    override fun apply() {
        val settings = AppSettingsState.getInstance()
        settings.apiUrl = apiUrlField.text
        settings.apiKey = String(apiKeyField.password)
        settings.modelName = modelNameField.text
        settings.apiFormat = apiFormatComboBox.selectedItem as ApiFormat

        settings.customRequestTemplate = customRequestTemplateArea.text
        settings.customResponseJsonPath = customResponseJsonPathField.text

        settings.allowCodeAccess = allowCodeAccessCheckBox.isSelected
        settings.rememberCodeAccessChoice = rememberCodeAccessCheckBox.isSelected

        settings.reminderEnabled = reminderEnabledCheckBox.isSelected
        settings.reminderTime = reminderTimeField.text
        settings.closeReminderEnabled = closeReminderCheckBox.isSelected

        settings.defaultExportFormat = exportFormatComboBox.selectedItem as ExportFormat
        settings.storageLocation = storageLocationField.text

        settings.systemPrompt = systemPromptArea.text
        settings.userPromptTemplate = userPromptTemplateArea.text
    }

    override fun reset() {
        loadSettings(AppSettingsState.getInstance())
    }
}
