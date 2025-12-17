package com.worklog.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.worklog.models.ApiFormat
import com.worklog.models.ExportFormat
import java.time.LocalTime

/**
 * 插件设置状态管理
 * 使用 PersistentStateComponent 实现持久化存储
 */
@State(
    name = "com.worklog.settings.AppSettingsState",
    storages = [Storage("WorkLogPlugin.xml")]
)
class AppSettingsState : PersistentStateComponent<AppSettingsState> {
    // AI API 设置
    var apiUrl: String = "https://api.openai.com/v1/chat/completions"
    var apiKey: String = ""
    var modelName: String = "gpt-4"
    var apiFormat: ApiFormat = ApiFormat.OPENAI

    // 自定义 API 格式模板（仅在 apiFormat = CUSTOM 时使用）
    var customRequestTemplate: String = ""
    var customResponseJsonPath: String = ""  // JSON 路径表达式，用于提取响应内容

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
        请用简洁、专业的语言描述工作内容，突出重点和成果。
    """.trimIndent()

    var userPromptTemplate: String = """
        请根据以下 Git 提交记录总结今日工作内容：

        {{commits}}

        {{#if hasCodeAccess}}
        以下是相关代码变更：
        {{code_diff}}
        {{/if}}

        请以 Markdown 格式输出工作总结，包括：
        1. 主要工作内容
        2. 完成的功能或修复的问题
        3. 涉及的技术点
    """.trimIndent()

    override fun getState(): AppSettingsState {
        return this
    }

    override fun loadState(state: AppSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): AppSettingsState {
            return ApplicationManager.getApplication().getService(AppSettingsState::class.java)
        }
    }

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
