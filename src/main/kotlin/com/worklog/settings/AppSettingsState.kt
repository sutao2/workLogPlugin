package com.worklog.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.worklog.models.ApiConfig
import com.worklog.models.ApiFormat
import com.worklog.models.ExportFormat
import java.time.LocalTime
import java.util.*

/**
 * 插件设置状态管理
 * 使用 PersistentStateComponent 实现持久化存储
 */
@State(
    name = "com.worklog.settings.AppSettingsState",
    storages = [Storage("WorkLogPlugin.xml")]
)
class AppSettingsState : PersistentStateComponent<AppSettingsState> {
    // AI API 配置列表
    var apiConfigs: MutableList<ApiConfig> = mutableListOf()

    // 代码访问权限
    var allowCodeAccess: Boolean = false
    var rememberCodeAccessChoice: Boolean = false

    // 提醒设置
    var reminderEnabled: Boolean = true
    var reminderTime: String = "17:30"  // 使用字符串存储，格式 HH:mm
    var closeReminderEnabled: Boolean = true  // IDE 关闭时提醒

    // 导出设置
    var defaultExportFormat: ExportFormat = ExportFormat.MARKDOWN

    // 存储路径设置
    var storageLocation: String = ".worklogs"  // 相对于项目根目录

    // AI 提示词模板
    var systemPrompt: String = """
        你是一个专业的工作日志助手。根据用户的 Git 提交记录和代码变更，帮助总结今日的工作内容。

        重要规则：
        1. 直接输出工作总结内容，不要输出任何思考过程、推理步骤或 XML 标签
        2. 不要使用 <think>、<reasoning> 等任何标签
        3. 用简洁、专业的语言描述工作内容，突出重点和成果
        4. 严格按照用户指定的输出格式
    """.trimIndent()

    var userPromptTemplate: String = """
        请根据以下 Git 提交记录总结今日工作内容：

        {{commits}}

        {{#if hasCodeAccess}}
        以下是相关代码变更：
        {{code_diff}}
        {{/if}}

        请以 Markdown 格式输出工作总结，不要包含 Git 提交记录部分。
        只输出以下内容：
        1. 主要工作内容（2-3句话概述）
        2. 完成的功能或修复的问题（列表形式，每项简短说明）
        3. 涉及的技术点（列表形式）

        注意：不要输出思考过程，不要重复提交记录，直接输出总结内容。
    """.trimIndent()

    // 工作日志输出模板
    var workLogOutputTemplate: String = """
        # 工作日志 - {{date}}

        ## 工作总结

        {{ai_summary}}

        ## Git 提交记录

        {{git_commits}}

        {{#if hasCodeAccess}}
        ## 代码变更摘要

        {{code_changes}}
        {{/if}}
    """.trimIndent()

    // 模板示例（用于用户参考）
    var templateExamples: String = """
        # 示例1：简洁风格
        # 工作日志 - {{date}}
        {{ai_summary}}
        ---
        {{git_commits}}

        # 示例2：详细风格
        # 工作日志 - {{date}}

        ## 今日总结
        {{ai_summary}}

        ## 提交记录
        {{git_commits}}

        ## 代码变更
        {{code_changes}}

        ## 备注
        <!-- 在这里添加手动备注 -->

        # 示例3：极简风格
        ## {{date}} 工作日志
        {{ai_summary}}
    """.trimIndent()

    override fun getState(): AppSettingsState {
        return this
    }

    override fun loadState(state: AppSettingsState) {
        XmlSerializerUtil.copyBean(state, this)

        // 确保至少有一个默认配置
        if (apiConfigs.isEmpty()) {
            apiConfigs.add(ApiConfig(
                id = UUID.randomUUID().toString(),
                name = "默认配置",
                apiUrl = "https://api.openai.com/v1/chat/completions",
                apiKey = "",
                modelName = "gpt-4",
                apiFormat = ApiFormat.OPENAI,
                isEnabled = true
            ))
        }

        // 确保只有一个配置是激活的
        ensureSingleActiveConfig()
    }

    companion object {
        fun getInstance(): AppSettingsState {
            return ApplicationManager.getApplication().getService(AppSettingsState::class.java)
        }
    }

    /**
     * 获取激活的 API 配置
     */
    fun getActiveApiConfig(): ApiConfig? {
        return apiConfigs.firstOrNull { it.isEnabled }
    }

    /**
     * 设置激活的 API 配置（同时禁用其他配置）
     */
    fun setActiveApiConfig(configId: String) {
        apiConfigs.forEach { config ->
            config.isEnabled = (config.id == configId)
        }
    }

    /**
     * 添加新的 API 配置
     */
    fun addApiConfig(config: ApiConfig) {
        // 如果这是第一个配置，自动激活
        if (apiConfigs.isEmpty()) {
            config.isEnabled = true
        }
        apiConfigs.add(config)
    }

    /**
     * 删除 API 配置
     */
    fun removeApiConfig(configId: String): Boolean {
        val config = apiConfigs.find { it.id == configId } ?: return false
        val wasActive = config.isEnabled

        apiConfigs.removeIf { it.id == configId }

        // 如果删除的是激活的配置，激活第一个配置
        if (wasActive && apiConfigs.isNotEmpty()) {
            apiConfigs[0].isEnabled = true
        }

        return true
    }

    /**
     * 更新 API 配置
     */
    fun updateApiConfig(configId: String, updatedConfig: ApiConfig): Boolean {
        val index = apiConfigs.indexOfFirst { it.id == configId }
        if (index == -1) return false

        // 保持原来的激活状态
        updatedConfig.id = configId
        updatedConfig.isEnabled = apiConfigs[index].isEnabled

        apiConfigs[index] = updatedConfig
        return true
    }

    /**
     * 确保只有一个配置是激活的
     */
    private fun ensureSingleActiveConfig() {
        val activeConfigs = apiConfigs.filter { it.isEnabled }

        when {
            activeConfigs.isEmpty() && apiConfigs.isNotEmpty() -> {
                // 如果没有激活的配置，激活第一个
                apiConfigs[0].isEnabled = true
            }
            activeConfigs.size > 1 -> {
                // 如果有多个激活的配置，只保留第一个
                apiConfigs.forEach { it.isEnabled = false }
                activeConfigs.first().isEnabled = true
            }
        }
    }

    /**
     * 向后兼容的属性：apiUrl
     */
    val apiUrlCompat: String
        get() = getActiveApiConfig()?.apiUrl ?: ""

    /**
     * 向后兼容的属性：apiKey
     */
    val apiKeyCompat: String
        get() = getActiveApiConfig()?.apiKey ?: ""

    /**
     * 向后兼容的属性：modelName
     */
    val modelNameCompat: String
        get() = getActiveApiConfig()?.modelName ?: ""

    /**
     * 向后兼容的属性：apiFormat
     */
    val apiFormatCompat: ApiFormat
        get() = getActiveApiConfig()?.apiFormat ?: ApiFormat.OPENAI

    /**
     * 向后兼容的属性：customRequestTemplate
     */
    val customRequestTemplate: String
        get() = getActiveApiConfig()?.customRequestTemplate ?: ""

    /**
     * 向后兼容的属性：customResponseJsonPath
     */
    val customResponseJsonPath: String
        get() = getActiveApiConfig()?.customResponseJsonPath ?: ""

    /**
     * 获取提醒时间（转换为 LocalTime）
     */
    fun getReminderLocalTime(): LocalTime {
        return try {
            LocalTime.parse(reminderTime)
        } catch (e: Exception) {
            LocalTime.of(17, 30)  // 默认 17:30
        }
    }

    /**
     * 设置提醒时间
     */
    fun setReminderLocalTime(time: LocalTime) {
        reminderTime = time.toString()
    }
}
