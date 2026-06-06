package com.worklog.services

import com.intellij.openapi.Disposable
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
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * AI 服务
 * 负责调用大模型 API 生成工作总结
 */
@Service(Service.Level.PROJECT)
class AIService(private val project: Project) : Disposable {

    companion object {
        private val CONDITIONAL_BLOCK_REGEX = Regex("\\{\\{#if hasCodeAccess}}([\\s\\S]*?)\\{\\{/if}}")
        private val CONDITIONAL_BLOCK_STRIP_REGEX = Regex("\\{\\{#if hasCodeAccess}}[\\s\\S]*?\\{\\{/if}}")
    }

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
        if (settings.apiUrlCompat.isBlank() || settings.apiKeyCompat.isBlank()) {
            throw IllegalStateException("请先在设置中配置 AI API")
        }
        validateApiUrl(settings.apiUrlCompat)

        // 构建提示词
        val prompt = buildPrompt(commits, includeCode, settings)

        // 根据 API 格式调用不同的方法
        return when (settings.apiFormatCompat) {
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

            val maxCodeDiffSize = 30000
            val truncatedDiff = if (codeDiff.length > maxCodeDiffSize) {
                codeDiff.take(maxCodeDiffSize) + "\n\n... (代码变更过长，已截断)"
            } else {
                codeDiff
            }

            prompt = prompt.replace(CONDITIONAL_BLOCK_REGEX) { matchResult ->
                val content = matchResult.groupValues[1]
                content.replace("{{code_diff}}", truncatedDiff)
            }
        } else {
            prompt = prompt.replace(CONDITIONAL_BLOCK_STRIP_REGEX, "")
        }

        return prompt
    }

    /**
     * 调用 OpenAI 格式的 API
     */
    private suspend fun callOpenAIFormat(prompt: String, settings: AppSettingsState): String {
        return withContext(Dispatchers.IO) {
            val requestBody = buildJsonObject {
                put("model", settings.modelNameCompat)
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

            val requestBodyStr = requestBody.toString()

            val request = Request.Builder()
                .url(settings.apiUrlCompat)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer ${settings.apiKeyCompat}")
                .post(requestBodyStr.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()

                if (!response.isSuccessful) {
                    val errorMsg = if (responseBody != null) {
                        try {
                            val errorJson = json.parseToJsonElement(responseBody).jsonObject
                            val errorMessage = errorJson["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                                ?: errorJson["message"]?.jsonPrimitive?.content
                                ?: responseBody
                            "API 调用失败 (${response.code}): $errorMessage"
                        } catch (e: Exception) {
                            "API 调用失败 (${response.code}): ${response.message}\n$responseBody"
                        }
                    } else {
                        "API 调用失败 (${response.code}): ${response.message}"
                    }

                    throw RuntimeException(errorMsg)
                }

                val jsonResponse = json.parseToJsonElement(responseBody ?: "").jsonObject

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

            // 替换模板变量（避免多次创建大字符串）
            val requestBodyStr = buildString(requestTemplate.length + prompt.length + settings.systemPrompt.length + settings.modelNameCompat.length) {
                append(requestTemplate)
            }
                .replace("{{prompt}}", prompt)
                .replace("{{system_prompt}}", settings.systemPrompt)
                .replace("{{model}}", settings.modelNameCompat)

            val request = Request.Builder()
                .url(settings.apiUrlCompat)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer ${settings.apiKeyCompat}")
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
     * @throws Exception 如果连接失败，抛出详细的异常信息
     */
    suspend fun testConnection() {
        val settings = AppSettingsState.getInstance()

        // 检查配置
        if (settings.apiUrlCompat.isBlank()) {
            throw IllegalStateException("API URL 未配置")
        }
        if (settings.apiKeyCompat.isBlank()) {
            throw IllegalStateException("API Key 未配置")
        }
        if (settings.modelNameCompat.isBlank()) {
            throw IllegalStateException("模型名称未配置")
        }

        // 创建测试数据
        val testCommit = GitCommit(
            hash = "test123",
            shortHash = "test",
            message = "测试连接：添加测试功能",
            author = "测试用户",
            authorEmail = "test@example.com",
            timestamp = java.time.Instant.now(),
            files = listOf("test.kt")
        )

        // 调用AI服务测试
        try {
            val result = summarizeWork(listOf(testCommit), false)
            if (result.isBlank()) {
                throw RuntimeException("AI 返回内容为空")
            }
        } catch (e: IllegalStateException) {
            // 配置错误，直接抛出
            throw e
        } catch (e: Exception) {
            // 包装其他异常，提供更详细的信息
            throw RuntimeException("API 调用失败：${e.message}", e)
        }
    }

    /**
     * 通用AI调用方法
     * @param userPrompt 用户提示词
     * @param systemPrompt 系统提示词（可选，默认使用设置中的系统提示词）
     */
    suspend fun callAI(userPrompt: String, systemPrompt: String? = null): String {
        val settings = AppSettingsState.getInstance()

        if (settings.apiUrlCompat.isBlank() || settings.apiKeyCompat.isBlank()) {
            throw IllegalStateException("请先在设置中配置 AI API")
        }
        validateApiUrl(settings.apiUrlCompat)

        val actualSystemPrompt = systemPrompt ?: settings.systemPrompt

        // 根据 API 格式调用不同的方法
        return when (settings.apiFormatCompat) {
            ApiFormat.OPENAI -> callOpenAIFormatWithPrompt(userPrompt, actualSystemPrompt, settings)
            ApiFormat.CUSTOM -> callCustomFormatWithPrompt(userPrompt, actualSystemPrompt, settings)
        }
    }

    private suspend fun callOpenAIFormatWithPrompt(userPrompt: String, systemPrompt: String, settings: AppSettingsState): String {
        return withContext(Dispatchers.IO) {
            val requestBody = buildJsonObject {
                put("model", settings.modelNameCompat)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "system")
                        put("content", systemPrompt)
                    }
                    addJsonObject {
                        put("role", "user")
                        put("content", userPrompt)
                    }
                }
                put("temperature", 0.7)
            }

            val request = Request.Builder()
                .url(settings.apiUrlCompat)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer ${settings.apiKeyCompat}")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw RuntimeException("API 调用失败: ${response.code} ${response.message}\n${response.body?.string()}")
                }

                val responseBody = response.body?.string() ?: throw RuntimeException("响应体为空")
                val jsonResponse = json.parseToJsonElement(responseBody).jsonObject

                jsonResponse["choices"]?.jsonArray?.get(0)
                    ?.jsonObject?.get("message")
                    ?.jsonObject?.get("content")
                    ?.jsonPrimitive?.content
                    ?: throw RuntimeException("无法从响应中提取内容")
            }
        }
    }

    private suspend fun callCustomFormatWithPrompt(userPrompt: String, systemPrompt: String, settings: AppSettingsState): String {
        return withContext(Dispatchers.IO) {
            // 处理自定义请求模板
            var requestTemplate = settings.customRequestTemplate
            requestTemplate = requestTemplate.replace("{{system}}", systemPrompt)
            requestTemplate = requestTemplate.replace("{{system_prompt}}", systemPrompt)
            requestTemplate = requestTemplate.replace("{{user}}", userPrompt)
            requestTemplate = requestTemplate.replace("{{prompt}}", userPrompt)
            requestTemplate = requestTemplate.replace("{{model}}", settings.modelNameCompat)

            val request = Request.Builder()
                .url(settings.apiUrlCompat)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer ${settings.apiKeyCompat}")
                .post(requestTemplate.toRequestBody("application/json".toMediaType()))
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

    private fun validateApiUrl(url: String) {
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            throw IllegalStateException("API URL 格式不正确: $url")
        }

        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        val isLocalHttp = scheme == "http" && host in setOf("localhost", "127.0.0.1", "::1")

        if (scheme != "https" && !isLocalHttp) {
            throw IllegalStateException("API URL 必须使用 HTTPS（本地地址除外），当前: $url")
        }
    }

    override fun dispose() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
