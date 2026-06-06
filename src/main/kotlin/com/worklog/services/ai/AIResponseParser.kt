package com.worklog.services.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class AIResponseParser(
    private val json: Json
) {
    fun extractOpenAIContent(responseBody: String): String {
        val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
        return jsonResponse["choices"]
            ?.jsonArray?.get(0)
            ?.jsonObject?.get("message")
            ?.jsonObject?.get("content")
            ?.jsonPrimitive?.contentOrNull
            ?: throw RuntimeException("无法从响应中提取内容")
    }

    fun extractCustomContent(responseBody: String, jsonPath: String): String {
        if (jsonPath.isBlank()) {
            return responseBody
        }

        val jsonElement = json.parseToJsonElement(responseBody)
        var current: JsonElement = jsonElement

        for (part in jsonPath.split(".")) {
            current = when {
                part.contains("[") -> {
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

        val resolved = current
        return when (resolved) {
            is JsonPrimitive -> resolved.contentOrNull ?: resolved.toString()
            else -> current.toString()
        }
    }
}
