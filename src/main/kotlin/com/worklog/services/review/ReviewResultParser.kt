package com.worklog.services.review

import com.worklog.models.ReviewIssue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class ReviewResultParser(
    private val json: Json
) {
    fun parse(content: String, reviewedFiles: List<String>): List<ReviewIssue> {
        val structuredIssues = parseStructuredReviewIssues(content, reviewedFiles)
        if (structuredIssues.isNotEmpty()) {
            return structuredIssues
        }
        return parseLegacyReviewIssues(content, reviewedFiles)
    }

    private fun parseStructuredReviewIssues(content: String, reviewedFiles: List<String>): List<ReviewIssue> {
        val block = Regex(
            "${ReviewPromptBuilder.STRUCTURED_RESULT_START}\\s*([\\s\\S]*?)\\s*${ReviewPromptBuilder.STRUCTURED_RESULT_END}"
        ).find(content)?.groupValues?.getOrNull(1)?.trim() ?: return emptyList()

        return try {
            val root = json.parseToJsonElement(block).jsonObject
            root["issues"]?.jsonArray.orEmpty().mapNotNull { element ->
                val issue = element.jsonObject
                val file = issue.stringValue("file")?.let { normalizeReviewedFile(it, reviewedFiles) }
                    ?: return@mapNotNull null
                ReviewIssue(
                    filePath = file,
                    line = issue["line"]?.jsonPrimitive?.intOrNull,
                    severity = normalizeSeverity(issue.stringValue("severity")),
                    title = issue.stringValue("title").orEmpty().ifBlank { "代码评审问题" },
                    message = issue.stringValue("message").orEmpty()
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseLegacyReviewIssues(content: String, reviewedFiles: List<String>): List<ReviewIssue> {
        if (content.contains(ReviewPromptBuilder.NO_FINDINGS_MARKER)) {
            return emptyList()
        }

        val seen = mutableSetOf<String>()
        return content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val file = reviewedFiles.firstOrNull { filePath ->
                    line.contains(filePath) || line.contains(filePath.substringAfterLast('/'))
                } ?: return@mapNotNull null
                val title = line
                    .removePrefix("###")
                    .removePrefix("##")
                    .removePrefix("#")
                    .removePrefix("-")
                    .removePrefix("*")
                    .trim()
                    .trim('|')
                    .trim()
                val key = "$file:$title"
                if (title.length < 4 || !seen.add(key)) {
                    null
                } else {
                    ReviewIssue(
                        filePath = file,
                        line = Regex(""":(\d+)\b""").find(line)?.groupValues?.getOrNull(1)?.toIntOrNull(),
                        severity = normalizeSeverity(line),
                        title = if (title.length > 120) title.take(117) + "..." else title,
                        message = line
                    )
                }
            }
            .take(20)
            .toList()
    }

    private fun normalizeReviewedFile(file: String, reviewedFiles: List<String>): String? {
        return reviewedFiles.firstOrNull { it == file }
            ?: reviewedFiles.firstOrNull { it.substringAfterLast('/') == file.substringAfterLast('/') }
    }

    private fun normalizeSeverity(value: String?): String {
        val text = value.orEmpty().uppercase()
        return when {
            text.contains("HIGH") || text.contains("严重") || text.contains("高") || text.contains("P0") || text.contains("P1") -> "HIGH"
            text.contains("LOW") || text.contains("低") || text.contains("P3") -> "LOW"
            else -> "MEDIUM"
        }
    }

    private fun JsonObject.stringValue(key: String): String? {
        val value = this[key] ?: return null
        return (value as? JsonPrimitive)?.contentOrNull
    }
}
