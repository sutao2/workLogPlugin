package com.worklog.services.git

internal object GitDiffAnnotator {
    fun annotateWithLineMarkers(diff: String): String {
        var currentFile: String? = null
        var newLineNumber: Int? = null

        return buildString(diff.length + diff.lines().size * 18) {
            diff.lineSequence().forEach { line ->
                when {
                    line.startsWith("+++ b/") -> {
                        currentFile = line.removePrefix("+++ b/")
                        newLineNumber = null
                        appendLine(line)
                    }
                    line.startsWith("@@") -> {
                        newLineNumber = Regex("""\+(\d+)""").find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
                        appendLine(line)
                    }
                    currentFile != null && newLineNumber != null && line.startsWith("+") && !line.startsWith("+++") -> {
                        appendLine(">> ${currentFile}:${newLineNumber}")
                        appendLine(line)
                        newLineNumber = newLineNumber?.plus(1)
                    }
                    currentFile != null && newLineNumber != null && line.startsWith(" ") -> {
                        appendLine(">> ${currentFile}:${newLineNumber}")
                        appendLine(line)
                        newLineNumber = newLineNumber?.plus(1)
                    }
                    line.startsWith("-") && !line.startsWith("---") -> {
                        appendLine(line)
                    }
                    else -> appendLine(line)
                }
            }
        }.trimEnd()
    }
}
