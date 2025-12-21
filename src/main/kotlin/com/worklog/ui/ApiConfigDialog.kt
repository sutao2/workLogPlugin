package com.worklog.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.worklog.models.ApiConfig
import com.worklog.models.ApiFormat
import java.awt.*
import javax.swing.*

/**
 * API 配置编辑对话框
 */
class ApiConfigDialog(
    project: Project?,
    private val existingConfig: ApiConfig? = null
) : DialogWrapper(project) {

    private val nameField = JBTextField(20)
    private val apiUrlField = JBTextField(40)
    private val apiKeyField = JBPasswordField()
    private val modelNameField = JBTextField(20)
    private val formatComboBox = JComboBox(ApiFormat.values())
    private val customRequestTemplateArea = JTextArea(5, 40)
    private val customResponseJsonPathField = JBTextField(40)
    private val showApiKeyButton = JButton("显示")
    private var apiKeyVisible = false

    init {
        title = if (existingConfig == null) "添加 API 配置" else "编辑 API 配置"

        // 如果是编辑，填充现有数据
        existingConfig?.let { config ->
            nameField.text = config.name
            apiUrlField.text = config.apiUrl
            apiKeyField.text = config.apiKey
            modelNameField.text = config.modelName
            formatComboBox.selectedItem = config.apiFormat
            customRequestTemplateArea.text = config.customRequestTemplate
            customResponseJsonPathField.text = config.customResponseJsonPath
        }

        // 设置文本区域样式
        customRequestTemplateArea.lineWrap = true
        customRequestTemplateArea.wrapStyleWord = true
        customRequestTemplateArea.font = Font("Monospaced", Font.PLAIN, 12)

        // API Key 显示/隐藏切换
        showApiKeyButton.addActionListener {
            apiKeyVisible = !apiKeyVisible
            if (apiKeyVisible) {
                apiKeyField.echoChar = 0.toChar()
                showApiKeyButton.text = "隐藏"
            } else {
                apiKeyField.echoChar = '•'
                showApiKeyButton.text = "显示"
            }
        }

        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.insets = Insets(5, 5, 5, 5)
        gbc.anchor = GridBagConstraints.WEST
        gbc.fill = GridBagConstraints.HORIZONTAL

        var row = 0

        // 配置名称
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        panel.add(JLabel("配置名称:"), gbc)

        gbc.gridx = 1
        gbc.gridwidth = 2
        gbc.weightx = 1.0
        panel.add(nameField, gbc)

        row++

        // API URL
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 1
        gbc.weightx = 0.0
        panel.add(JLabel("API 地址:"), gbc)

        gbc.gridx = 1
        gbc.gridwidth = 2
        gbc.weightx = 1.0
        panel.add(apiUrlField, gbc)

        row++

        // API Key
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 1
        gbc.weightx = 0.0
        panel.add(JLabel("API Key:"), gbc)

        gbc.gridx = 1
        gbc.gridwidth = 1
        gbc.weightx = 1.0
        panel.add(apiKeyField, gbc)

        gbc.gridx = 2
        gbc.gridwidth = 1
        gbc.weightx = 0.0
        panel.add(showApiKeyButton, gbc)

        row++

        // 模型名称
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        panel.add(JLabel("模型名称:"), gbc)

        gbc.gridx = 1
        gbc.gridwidth = 2
        gbc.weightx = 1.0
        panel.add(modelNameField, gbc)

        row++

        // API 格式
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 1
        gbc.weightx = 0.0
        panel.add(JLabel("API 格式:"), gbc)

        gbc.gridx = 1
        gbc.gridwidth = 2
        gbc.weightx = 1.0
        panel.add(formatComboBox, gbc)

        row++

        // 自定义请求模板（仅当格式为 CUSTOM 时显示）
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 1
        gbc.weightx = 0.0
        gbc.anchor = GridBagConstraints.NORTHWEST
        panel.add(JLabel("请求模板:"), gbc)

        gbc.gridx = 1
        gbc.gridwidth = 2
        gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.BOTH
        gbc.weighty = 1.0
        val scrollPane = JScrollPane(customRequestTemplateArea)
        scrollPane.preferredSize = Dimension(400, 100)
        panel.add(scrollPane, gbc)

        row++

        // 响应 JSON 路径
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 1
        gbc.weightx = 0.0
        gbc.weighty = 0.0
        gbc.anchor = GridBagConstraints.WEST
        gbc.fill = GridBagConstraints.HORIZONTAL
        panel.add(JLabel("响应路径:"), gbc)

        gbc.gridx = 1
        gbc.gridwidth = 2
        gbc.weightx = 1.0
        panel.add(customResponseJsonPathField, gbc)

        return panel
    }

    /**
     * 获取配置的 API 配置对象
     */
    fun getApiConfig(): ApiConfig {
        return ApiConfig(
            id = existingConfig?.id ?: java.util.UUID.randomUUID().toString(),
            name = nameField.text.trim(),
            apiUrl = apiUrlField.text.trim(),
            apiKey = String(apiKeyField.password),
            modelName = modelNameField.text.trim(),
            apiFormat = formatComboBox.selectedItem as ApiFormat,
            customRequestTemplate = customRequestTemplateArea.text.trim(),
            customResponseJsonPath = customResponseJsonPathField.text.trim(),
            isEnabled = existingConfig?.isEnabled ?: false
        )
    }

    override fun doValidate(): ValidationInfo? {
        if (nameField.text.isBlank()) {
            return ValidationInfo("配置名称不能为空", nameField)
        }

        if (apiUrlField.text.isBlank()) {
            return ValidationInfo("API 地址不能为空", apiUrlField)
        }

        if (apiKeyField.password.isEmpty()) {
            return ValidationInfo("API Key 不能为空", apiKeyField)
        }

        if (modelNameField.text.isBlank()) {
            return ValidationInfo("模型名称不能为空", modelNameField)
        }

        return null
    }
}
