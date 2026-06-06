package com.worklog.services.review

internal object ReviewPromptBuilder {
    const val NO_FINDINGS_MARKER = "未发现明显问题"
    const val STRUCTURED_RESULT_START = "WORKLOG_REVIEW_JSON_START"
    const val STRUCTURED_RESULT_END = "WORKLOG_REVIEW_JSON_END"

    private const val PLACEHOLDER_FILES = "{{files}}"
    private const val PLACEHOLDER_DIFF = "{{diff}}"
    private const val PLACEHOLDER_COMMIT_MESSAGE = "{{commit_message}}"
    private const val DEFAULT_COMMIT_MESSAGE = "<未提供提交说明>"

    fun build(
        template: String,
        files: List<String>,
        diff: String,
        commitMessage: String?
    ): String {
        var prompt = template
            .replace(PLACEHOLDER_FILES, files.joinToString("\n") { "- $it" })
            .replace(PLACEHOLDER_DIFF, diff)

        prompt = if (commitMessage.isNullOrBlank()) {
            prompt.replace(PLACEHOLDER_COMMIT_MESSAGE, DEFAULT_COMMIT_MESSAGE)
        } else {
            prompt.replace(PLACEHOLDER_COMMIT_MESSAGE, commitMessage)
        }

        return "$prompt\n\n${buildStructuredOutputInstruction(files)}"
    }

    private fun buildStructuredOutputInstruction(files: List<String>): String {
        return """

            输出要求：
            1. 先用 Markdown 给出简短评审说明。
            2. 如果没有明显问题，正文必须包含“$NO_FINDINGS_MARKER”，并在 JSON 中返回空 issues。
            3. 如果发现问题，必须在最后追加以下机器可解析区块，字段必须准确：

            $STRUCTURED_RESULT_START
            {
              "issues": [
                {
                  "file": "必须是下列变更文件之一",
                  "line": 42,
                  "severity": "HIGH|MEDIUM|LOW",
                  "title": "一句话问题标题",
                  "message": "说明为什么这是问题，以及建议如何修复"
                }
              ]
            }
            $STRUCTURED_RESULT_END

            可用文件路径只能从这里选择：
            ${files.joinToString("\n") { "- $it" }}

            定位规则：
            - diff 中形如 “>> src/main/App.kt:42” 的行是可定位坐标。
            - 当问题出现在紧随该坐标后的代码行或附近上下文时，JSON 的 file/line 必须使用这个坐标。
            - 不要返回不存在于变更文件列表中的 file。
            - 不要编造行号；无法确定具体位置时 line 返回 null。
            - 上方 JSON 示例里的 42 只是示例，实际输出必须替换成真实行号或 null。
        """.trimIndent()
    }
}
