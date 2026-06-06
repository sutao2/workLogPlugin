package com.worklog.services.git

import kotlin.test.Test
import kotlin.test.assertContains

class GitDiffAnnotatorTest {
    @Test
    fun `adds markers for added and context lines`() {
        val diff = """
            diff --git a/src/App.kt b/src/App.kt
            +++ b/src/App.kt
            @@ -10,2 +10,3 @@
             val old = true
            +val added = true
            -val removed = true
        """.trimIndent()

        val annotated = GitDiffAnnotator.annotateWithLineMarkers(diff)

        assertContains(annotated, ">> src/App.kt:10\n val old = true")
        assertContains(annotated, ">> src/App.kt:11\n+val added = true")
    }
}
