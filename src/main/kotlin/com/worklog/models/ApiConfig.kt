package com.worklog.models

import java.util.*

/**
 * AI API 配置
 * 注意：所有字段必须有默认值，以支持 XML 序列化
 */
data class ApiConfig(
    var id: String = "",
    var name: String = "",
    var apiUrl: String = "",
    var apiKey: String = "",
    var modelName: String = "",
    var apiFormat: ApiFormat = ApiFormat.OPENAI,
    var customRequestTemplate: String = "",
    var customResponseJsonPath: String = "",
    var isEnabled: Boolean = false
) {
    /**
     * 初始化块：如果 id 为空，生成新的 UUID
     */
    init {
        if (id.isBlank()) {
            id = UUID.randomUUID().toString()
        }
    }
    /**
     * 获取显示名称（带状态标识）
     */
    fun getDisplayName(): String {
        return if (isEnabled) "✓ $name" else name
    }

    /**
     * 获取简短描述
     */
    fun getDescription(): String {
        val modelInfo = if (modelName.isNotBlank()) modelName else "未设置模型"
        val formatInfo = when (apiFormat) {
            ApiFormat.OPENAI -> "OpenAI 格式"
            ApiFormat.CUSTOM -> "自定义格式"
        }
        return "$modelInfo - $formatInfo"
    }

    /**
     * 创建配置的副本（用于"复制"功能）
     */
    fun duplicate(): ApiConfig {
        return ApiConfig(
            id = UUID.randomUUID().toString(),
            name = "$name (副本)",
            apiUrl = apiUrl,
            apiKey = apiKey,
            modelName = modelName,
            apiFormat = apiFormat,
            customRequestTemplate = customRequestTemplate,
            customResponseJsonPath = customResponseJsonPath,
            isEnabled = false  // 副本默认不启用
        )
    }

    /**
     * 验证配置是否完整
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()

        if (name.isBlank()) {
            errors.add("配置名称不能为空")
        }

        if (apiUrl.isBlank()) {
            errors.add("API 地址不能为空")
        }

        if (apiKey.isBlank()) {
            errors.add("API Key 不能为空")
        }

        if (modelName.isBlank()) {
            errors.add("模型名称不能为空")
        }

        if (apiFormat == ApiFormat.CUSTOM) {
            if (customRequestTemplate.isBlank()) {
                errors.add("自定义请求模板不能为空")
            }
            if (customResponseJsonPath.isBlank()) {
                errors.add("响应路径不能为空")
            }
        }

        return errors
    }

    /**
     * 获取掩码后的 API Key（用于显示）
     */
    fun getMaskedApiKey(): String {
        if (apiKey.isBlank()) return ""
        if (apiKey.length <= 8) return "****"

        val start = apiKey.substring(0, 4)
        val end = apiKey.substring(apiKey.length - 4)
        return "$start....$end"
    }
}
