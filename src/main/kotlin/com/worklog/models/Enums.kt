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
