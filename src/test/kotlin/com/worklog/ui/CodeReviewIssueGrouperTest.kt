package com.worklog.ui

import com.worklog.models.ReviewResult
import kotlin.test.Test
import kotlin.test.assertEquals

class CodeReviewIssueGrouperTest {
    @Test
    fun `builds fallback issue groups from review text`() {
        val result = ReviewResult(
            title = "代码评审结果",
            content = "发现问题",
            hasFindings = true,
            reviewedFiles = listOf("src/main/App.kt"),
            issues = emptyList()
        )
        val groups = CodeReviewIssueGrouper.buildFileGroups(
            result,
            "- 高 src/main/App.kt:12 这里可能空指针"
        )

        assertEquals(1, groups.size)
        assertEquals("src/main/App.kt", groups.first().path)
        assertEquals(1, groups.first().issues.size)
        assertEquals(12, groups.first().issues.first().line)
    }
}
