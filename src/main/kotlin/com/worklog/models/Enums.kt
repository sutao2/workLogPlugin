package com.worklog.models

/**
 * API 格式类型
 */
enum class ApiFormat {
    /**
     * OpenAI 格式（兼容 OpenAI、Azure OpenAI 和大多数国内大模型）
     */
    OPENAI,

    /**
     * 自定义格式（允许用户自定义请求和响应格式）
     */
    CUSTOM
}

/**
 * API 服务商预设
 */
enum class ApiProvider(
    val displayName: String,
    val baseUrl: String,
    val defaultModel: String,
    val format: ApiFormat
) {
    OPENAI("OpenAI", "https://api.openai.com/v1/chat/completions", "gpt-4o", ApiFormat.OPENAI),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1/chat/completions", "deepseek-chat", ApiFormat.OPENAI),
    DASHSCOPE("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", "qwen-plus", ApiFormat.OPENAI),
    ZHIPU("智谱 GLM", "https://open.bigmodel.cn/api/paas/v4/chat/completions", "glm-4-flash", ApiFormat.OPENAI),
    MOONSHOT("Moonshot (Kimi)", "https://api.moonshot.cn/v1/chat/completions", "moonshot-v1-8k", ApiFormat.OPENAI),
    SILICONFLOW("SiliconFlow", "https://api.siliconflow.cn/v1/chat/completions", "Qwen/Qwen2.5-7B-Instruct", ApiFormat.OPENAI),
    OLLAMA("Ollama (本地)", "http://localhost:11434/v1/chat/completions", "llama3", ApiFormat.OPENAI),
    CUSTOM("自定义", "", "", ApiFormat.OPENAI);

    companion object {
        /**
         * 根据 API URL 反向匹配服务商
         */
        fun matchByUrl(url: String): ApiProvider {
            if (url.isBlank()) return CUSTOM
            return entries.firstOrNull { it != CUSTOM && url.startsWith(it.baseUrl.substringBefore("/v1")) }
                ?: CUSTOM
        }
    }
}

/**
 * 导出格式类型
 */
enum class ExportFormat {
    /**
     * Markdown 原生格式
     */
    MARKDOWN,

    /**
     * HTML 格式
     */
    HTML,

    /**
     * PDF 格式
     */
    PDF
}
