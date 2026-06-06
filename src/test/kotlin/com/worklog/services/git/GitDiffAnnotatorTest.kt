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

    @Test
    fun `does not shift new file line numbers after deleted lines`() {
        val diff = """
            diff --git a/src/App.kt b/src/App.kt
            --- a/src/App.kt
            +++ b/src/App.kt
            @@ -10,4 +10,4 @@
             val first = true
            -val removed = true
             val second = true
            +val added = true
             val third = true
        """.trimIndent()

        val annotated = GitDiffAnnotator.annotateWithLineMarkers(diff)

        assertContains(annotated, ">> src/App.kt:10\n val first = true")
        assertContains(annotated, ">> src/App.kt:11\n val second = true")
        assertContains(annotated, ">> src/App.kt:12\n+val added = true")
        assertContains(annotated, ">> src/App.kt:13\n val third = true")
    }
}
