package com.worklog.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.worklog.models.ApiFormat
import com.worklog.models.GitCommit
import com.worklog.settings.AppSettingsState
import com.worklog.utils.MarkdownUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * AI 服务
 * 负责调用大模型 API 生成工作总结
 */
@Service(Service.Level.PROJECT)
class AIService(private val project: Project) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    /**
     * 总结工作内容
     */
    suspend fun summarizeWork(commits: List<GitCommit>, includeCode: Boolean): String {
        val settings = AppSettingsState.getInstance()

        // 检查配置
        if (settings.apiUrl.isBlank() || settings.apiKey.isBlank()) {
            throw IllegalStateException("请先在设置中配置 AI API")
        }

        // 构建提示词
        val prompt = buildPrompt(commits, includeCode, settings)

        // 根据 API 格式调用不同的方法
        return when (settings.apiFormat) {
            ApiFormat.OPENAI -> callOpenAIFormat(prompt, settings)
            ApiFormat.CUSTOM -> callCustomFormat(prompt, settings)
        }
    }

    /**
     * 构建提示词
     */
    private fun buildPrompt(commits: List<GitCommit>, includeCode: Boolean, settings: AppSettingsState): String {
        val commitsInfo = MarkdownUtil.extractCommitsForAI(commits, includeCode)

        var prompt = settings.userPromptTemplate
        prompt = prompt.replace("{{commits}}", commitsInfo)

        // 处理条件模板
        if (includeCode && commits.any { it.diff != null }) {
            val codeDiff = commits.mapNotNull { it.diff }.joinToString("\n\n")
            prompt = prompt.replace(Regex("\\{\\{#if hasCodeAccess}}([\\s\\S]*?)\\{\\{/if}}")) { matchResult ->
                val content = matchResult.groupValues[1]
                content.replace("{{code_diff}}", codeDiff)
            }
        } else {
            // 移除条件块
            prompt = prompt.replace(Regex("\\{\\{#if hasCodeAccess}}[\\s\\S]*?\\{\\{/if}}"), "")
        }

        return prompt
    }

    /**
     * 调用 OpenAI 格式的 API
     */
    private suspend fun callOpenAIFormat(prompt: String, settings: AppSettingsState): String {
        return withContext(Dispatchers.IO) {
            val requestBody = buildJsonObject {
                put("model", settings.modelName)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "system")
                        put("content", settings.systemPrompt)
                    }
                    addJsonObject {
                        put("role", "user")
                        put("content", prompt)
                    }
                }
                put("temperature", 0.7)
            }

            val request = Request.Builder()
                .url(settings.apiUrl)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer ${settings.apiKey}")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw RuntimeException("API 调用失败: ${response.code} ${response.message}")
                }

                val responseBody = response.body?.string() ?: throw RuntimeException("响应体为空")
                val jsonResponse = json.parseToJsonElement(responseBody).jsonObject

                // 解析 OpenAI 格式响应
                val content = jsonResponse["choices"]
                    ?.jsonArray?.get(0)
                    ?.jsonObject?.get("message")
                    ?.jsonObject?.get("content")
                    ?.jsonPrimitive?.content

                content ?: throw RuntimeException("无法从响应中提取内容")
            }
        }
    }

    /**
     * 调用自定义格式的 API
     */
    private suspend fun callCustomFormat(prompt: String, settings: AppSettingsState): String {
        return withContext(Dispatchers.IO) {
            // 解析自定义请求模板
            val requestTemplate = settings.customRequestTemplate.ifBlank {
                throw IllegalStateException("自定义 API 格式需要配置请求模板")
            }

            // 替换模板变量
            val requestBodyStr = requestTemplate
                .replace("{{prompt}}", prompt)
                .replace("{{system_prompt}}", settings.systemPrompt)
                .replace("{{model}}", settings.modelName)

            val request = Request.Builder()
                .url(settings.apiUrl)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer ${settings.apiKey}")
                .post(requestBodyStr.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw RuntimeException("API 调用失败: ${response.code} ${response.message}")
                }

                val responseBody = response.body?.string() ?: throw RuntimeException("响应体为空")

                // 使用 JSON Path 提取响应内容
                extractContentFromResponse(responseBody, settings.customResponseJsonPath)
            }
        }
    }

    /**
     * 从响应中提取内容
     * 支持简单的 JSON Path，例如: "data.content" 或 "choices[0].message.content"
     */
    private fun extractContentFromResponse(responseBody: String, jsonPath: String): String {
        if (jsonPath.isBlank()) {
            // 如果没有指定路径，直接返回响应体
            return responseBody
        }

        val jsonElement = json.parseToJsonElement(responseBody)
        var current: JsonElement = jsonElement

        // 解析简单的 JSON Path
        val parts = jsonPath.split(".")
        for (part in parts) {
            current = when {
                part.contains("[") -> {
                    // 处理数组索引，例如 "choices[0]"
                    val arrayName = part.substringBefore("[")
                    val index = part.substringAfter("[").substringBefore("]").toInt()
                    current.jsonObject[arrayName]?.jsonArray?.get(index)
                        ?: throw RuntimeException("无法找到路径: $part")
                }
                current is JsonObject -> {
                    current.jsonObject[part] ?: throw RuntimeException("无法找到路径: $part")
                }
                else -> throw RuntimeException("无效的 JSON 路径: $jsonPath")
            }
        }

        return when (current) {
            is JsonPrimitive -> current.content
            else -> current.toString()
        }
    }

    /**
     * 测试 API 连接
     */
    suspend fun testConnection(): Boolean {
        return try {
            val testCommit = GitCommit(
                hash = "test",
                shortHash = "test",
                message = "测试提交",
                author = "测试",
                authorEmail = "test@test.com",
                timestamp = java.time.Instant.now(),
                files = listOf("test.txt")
            )
            summarizeWork(listOf(testCommit), false)
            true
        } catch (e: Exception) {
            false
        }
    }
}
