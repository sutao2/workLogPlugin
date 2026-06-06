package com.worklog.utils

import com.worklog.models.GitCommit
import com.worklog.models.WorkLog
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class MarkdownUtilTest {
    @Test
    fun `generates full work log from explicit template`() {
        val workLog = WorkLog(
            date = LocalDate.of(2026, 6, 6),
            content = "",
            gitCommits = listOf(
                GitCommit(
                    hash = "abcdef",
                    shortHash = "abcdef",
                    message = "Add feature",
                    author = "Alice",
                    authorEmail = "alice@example.com",
                    timestamp = Instant.EPOCH,
                    files = listOf("src/App.kt"),
                    diff = "diff"
                )
            ),
            hasCodeAccess = true,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        )
        val template = """
            # {{date}}
            {{ai_summary}}
            {{git_commits}}
            {{#if hasCodeAccess}}
            {{code_changes}}
            {{/if}}
        """.trimIndent()

        val markdown = MarkdownUtil.generateFullWorkLog(
            workLog = workLog,
            outputTemplate = template,
            aiSummary = "完成主要功能",
            includeCodeDiff = true
        )

        assertContains(markdown, "完成主要功能")
        assertContains(markdown, "Add feature")
        assertContains(markdown, "代码变更")
        assertFalse(markdown.contains("{{"))
    }
}
