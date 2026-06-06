package com.worklog.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.worklog.models.ReviewIssue
import com.worklog.models.ReviewResult
import com.worklog.utils.StorageUtil
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class CodeReviewResultDialog(
    private val project: Project,
    private val result: ReviewResult,
    private val onReReview: (() -> Unit)? = null,
    private val onContinueCommit: (() -> Unit)? = null
) : DialogWrapper(project) {

    private var resultTree: Tree? = null
    private var openFileButton: JButton? = null
    private var issueTitleLabel: JBLabel? = null
    private var issueMetaLabel: JBLabel? = null
    private var issueMessageArea: JTextArea? = null
    private var selectedNavigableNode: ReviewNode.Navigable? = null
    private val activeHighlighters = mutableListOf<RangeHighlighter>()
    private val detailSearchText = buildDetailContent()
    private val cachedExportHtmlContent by lazy { renderHtml(buildHtmlBodyContent()) }

    init {
        title = result.title
        isModal = onContinueCommit != null
        setSize(860, 520)
        setOKButtonText(if (onContinueCommit != null) "取消" else "关闭")
        init()
        ApplicationManager.getApplication().invokeLater {
            navigateToFirstIssue()
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(860, 520)
        panel.border = JBUI.Borders.empty(8)

        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT)
        splitPane.border = BorderFactory.createEmptyBorder()
        splitPane.dividerSize = JBUI.scale(7)
        splitPane.leftComponent = createResultTreePanel()
        splitPane.rightComponent = createReportPanel()
        splitPane.dividerLocation = JBUI.scale(300)
        splitPane.resizeWeight = 0.0
        panel.add(splitPane, BorderLayout.CENTER)

        return panel
    }

    private fun createResultTreePanel(): JComponent {
        val tree = Tree(buildTreeModel())
        resultTree = tree
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.cellRenderer = ReviewTreeRenderer()
        tree.emptyText.text = "没有评审结果"
        TreeSpeedSearch(tree)
        tree.addTreeSelectionListener {
            val node = tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode
            handleTreeSelection(node?.userObject as? ReviewNode)
        }
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    openSelectedFile()
                }
            }
        })
        tree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    openSelectedFile()
                }
            }
        })
        expandTree(tree)

        val header = JPanel(BorderLayout(0, 2))
        header.border = JBUI.Borders.empty(0, 2, 8, 2)
        val titleLabel = JBLabel("代码评审")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD)

        val scope = if (result.sourceCommitHashes.isEmpty()) "暂存区" else "${result.sourceCommitHashes.size} 个提交"
        val metaLabel = JBLabel("${result.issues.size} 问题 · ${result.reviewedFiles.size} 文件 · $scope")
        metaLabel.foreground = JBColor.GRAY
        header.add(titleLabel, BorderLayout.NORTH)
        header.add(metaLabel, BorderLayout.CENTER)

        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(2, 0, 0, 8)
        panel.add(header, BorderLayout.NORTH)
        panel.add(JBScrollPane(tree).apply {
            border = JBUI.Borders.empty()
        }, BorderLayout.CENTER)
        return panel
    }

    private fun createReportPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 8))

        val toolbar = JPanel(BorderLayout())
        toolbar.border = JBUI.Borders.empty(0, 0, 8, 0)
        openFileButton = JButton("打开源码").apply {
            icon = AllIcons.Actions.Find
            isEnabled = false
            addActionListener { openSelectedFile() }
        }
        toolbar.add(openFileButton, BorderLayout.WEST)
        toolbar.add(JBLabel("问题会定位并高亮到编辑器源码行").apply {
            foreground = JBColor.GRAY
            horizontalAlignment = JBLabel.RIGHT
        }, BorderLayout.EAST)
        if (result.truncated) {
            toolbar.add(JBLabel("Diff 已截断").apply {
                foreground = JBColor.GRAY
                icon = AllIcons.General.Warning
            }, BorderLayout.CENTER)
        }
        panel.add(toolbar, BorderLayout.NORTH)

        panel.add(createIssueDetailPanel(), BorderLayout.CENTER)
        return panel
    }

    private fun createIssueDetailPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 8))
        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.border()),
            JBUI.Borders.empty(12, 4, 4, 4)
        )

        issueTitleLabel = JBLabel(if (result.hasFindings) "选择一个问题查看详情" else "未发现明显问题").apply {
            font = font.deriveFont(Font.BOLD, 15f)
        }
        issueMetaLabel = JBLabel("${result.reviewedFiles.size} 个文件 · ${result.issues.size} 个结构化问题").apply {
            foreground = JBColor.GRAY
        }

        val titlePanel = JPanel(BorderLayout(0, 5))
        titlePanel.border = JBUI.Borders.empty(0, 0, 6, 0)
        titlePanel.add(issueTitleLabel, BorderLayout.NORTH)
        titlePanel.add(issueMetaLabel, BorderLayout.CENTER)
        panel.add(titlePanel, BorderLayout.NORTH)

        issueMessageArea = createReadOnlyTextArea("问题说明会显示在这里。")
        panel.add(JBScrollPane(issueMessageArea), BorderLayout.CENTER)

        return panel
    }

    private fun createReadOnlyTextArea(text: String): JTextArea {
        return JTextArea(text).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            border = JBUI.Borders.empty(12)
            background = JBColor.namedColor("EditorPane.background", JBColor.PanelBackground)
            foreground = JBColor.foreground()
            font = font.deriveFont(13f)
        }
    }

    private fun buildTreeModel(): DefaultTreeModel {
        val root = DefaultMutableTreeNode(
            ReviewNode.Root(if (result.hasFindings) "发现需要关注的问题" else "未发现明显问题")
        )

        val findings = buildFindingNodes()
        if (findings.isNotEmpty()) {
            val findingsNode = DefaultMutableTreeNode(ReviewNode.GroupNode("问题", findings.size))
            findings
                .groupBy { it.severity }
                .toSortedMap(compareBy { severityRank(it) })
                .forEach { (severity, severityFindings) ->
                    val severityNode = DefaultMutableTreeNode(ReviewNode.SeverityNode(severity, severityFindings.size))
                    severityFindings
                        .groupBy { it.targetPath }
                        .forEach { (file, fileFindings) ->
                            if (file.isNullOrBlank()) {
                                fileFindings.forEach { severityNode.add(DefaultMutableTreeNode(it)) }
                            } else {
                                val fileNode = DefaultMutableTreeNode(
                                    ReviewNode.FileNode(file, fileFindings.firstOrNull()?.targetLine)
                                )
                                fileFindings.forEach { fileNode.add(DefaultMutableTreeNode(it)) }
                                severityNode.add(fileNode)
                            }
                        }
                    findingsNode.add(severityNode)
                }
            root.add(findingsNode)
        }

        val filesNode = DefaultMutableTreeNode(ReviewNode.GroupNode("评审文件", result.reviewedFiles.size))
        result.reviewedFiles.forEach { filePath ->
            filesNode.add(DefaultMutableTreeNode(ReviewNode.FileNode(filePath, firstIssueLine(filePath))))
        }
        root.add(filesNode)

        if (result.reviewedCommitSummaries.isNotEmpty()) {
            val commitsNode = DefaultMutableTreeNode(ReviewNode.GroupNode("评审提交", result.reviewedCommitSummaries.size))
            result.reviewedCommitSummaries.forEach { summary ->
                commitsNode.add(DefaultMutableTreeNode(ReviewNode.CommitNode(summary)))
            }
            root.add(commitsNode)
        } else if (result.sourceCommitHashes.isNotEmpty()) {
            val commitsNode = DefaultMutableTreeNode(ReviewNode.GroupNode("评审提交", result.sourceCommitHashes.size))
            result.sourceCommitHashes.forEach { hash ->
                commitsNode.add(DefaultMutableTreeNode(ReviewNode.CommitNode(hash)))
            }
            root.add(commitsNode)
        }

        if (result.truncated) {
            root.add(DefaultMutableTreeNode(ReviewNode.WarningNode("Diff 内容已截断，评审可能不完整")))
        }

        return DefaultTreeModel(root)
    }

    private fun handleTreeSelection(node: ReviewNode?) {
        selectedNavigableNode = node as? ReviewNode.Navigable
        openFileButton?.isEnabled = selectedNavigableNode?.targetPath != null
        when (node) {
            is ReviewNode.FileNode -> updateIssueDetails(null)
            is ReviewNode.FindingNode -> {
                updateIssueDetails(node.issue)
                navigateToIssue(node.issue)
            }
            is ReviewNode.CommitNode -> updateIssueDetails(null)
            else -> updateIssueDetails(null)
        }
    }

    private fun openSelectedFile() {
        val node = selectedNavigableNode ?: return
        val path = node.targetPath ?: return
        openFile(path, node.targetLine, highlight = true)
    }

    private fun openFile(path: String, line: Int?, highlight: Boolean = false) {
        if (path.isBlank()) {
            return
        }
        val resolvedPath = resolveProjectPath(path)
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(resolvedPath)
        if (virtualFile == null) {
            Messages.showWarningDialog(project, "未找到文件：$path", "无法打开文件")
            return
        }

        val lineIndex = line?.minus(1)?.coerceAtLeast(0) ?: 0
        OpenFileDescriptor(project, virtualFile, lineIndex, 0).navigate(true)
        if (highlight) {
            ApplicationManager.getApplication().invokeLater {
                highlightCurrentEditorLine(lineIndex)
            }
        }
    }

    private fun navigateToFirstIssue() {
        val firstIssue = result.issues.firstOrNull() ?: return
        updateIssueDetails(firstIssue)
        navigateToIssue(firstIssue)
    }

    private fun navigateToIssue(issue: ReviewIssue) {
        openFile(issue.filePath, issue.line, highlight = issue.line != null)
    }

    private fun highlightCurrentEditorLine(lineIndex: Int) {
        clearEditorHighlights()
        val selectedEditor = FileEditorManager.getInstance(project).selectedEditor as? TextEditor ?: return
        val editor = selectedEditor.editor
        val attributes = TextAttributes(
            null,
            JBColor(Color(0xFFF7D6), Color(0x4A3A18)),
            JBColor(Color(0xD99A00), Color(0xC98A00)),
            null,
            Font.PLAIN
        )
        activeHighlighters.add(
            editor.markupModel.addLineHighlighter(
                lineIndex,
                HighlighterLayer.WARNING,
                attributes
            )
        )
    }

    private fun clearEditorHighlights() {
        activeHighlighters.forEach { highlighter ->
            runCatching { highlighter.dispose() }
        }
        activeHighlighters.clear()
    }

    private fun updateIssueDetails(issue: ReviewIssue?) {
        if (issue == null) {
            issueTitleLabel?.text = if (result.hasFindings) "选择一个问题查看详情" else "未发现明显问题"
            issueTitleLabel?.foreground = JBColor.foreground()
            issueMetaLabel?.text = "${result.reviewedFiles.size} 个文件 · ${result.issues.size} 个结构化问题"
            issueMessageArea?.text = if (result.issues.isEmpty() && result.hasFindings) {
                "当前 AI 响应没有提供可定位的结构化问题。请重新评审，新的提示词会要求返回 file/line。"
            } else {
                "从左侧选择问题后会直接跳转到源码位置，并在编辑器中高亮对应行。"
            }
            return
        }

        issueTitleLabel?.text = issue.title
        issueTitleLabel?.foreground = severityColor(issue.severity)
        issueMetaLabel?.text = "${severityLabel(issue.severity)} · ${issue.filePath}${issue.line?.let { ":$it" } ?: ""}"
        issueMessageArea?.text = issue.message.ifBlank { issue.title }
        issueMessageArea?.caretPosition = 0
    }

    private fun resolveProjectPath(path: String): File {
        val file = File(path)
        if (file.isAbsolute) {
            return file
        }
        val basePath = project.basePath ?: return file
        return File(basePath, path)
    }

    private fun firstIssueLine(path: String): Int? {
        return result.issues.firstOrNull { it.filePath == path }?.line
    }

    private fun expandTree(tree: JTree) {
        for (row in 0 until tree.rowCount) {
            tree.expandRow(row)
        }
        tree.selectionPath = TreePath((tree.model.root as DefaultMutableTreeNode).path)
    }

    private fun buildFindingNodes(): List<ReviewNode.FindingNode> {
        if (!result.hasFindings) {
            return emptyList()
        }
        if (result.issues.isNotEmpty()) {
            return result.issues.map { ReviewNode.FindingNode(it) }
        }

        val seen = mutableSetOf<String>()
        return detailSearchText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { line ->
                severityOf(line) != null || result.reviewedFiles.any { file ->
                    line.contains(file) || line.contains(file.substringAfterLast('/'))
                }
            }
            .mapNotNull { line ->
                val title = cleanupFindingTitle(line)
                if (title.length < 4 || !seen.add(title)) {
                    null
                } else {
                    val file = findMentionedFile(line)
                    ReviewNode.FindingNode(
                        ReviewIssue(
                            filePath = file.orEmpty(),
                            line = lineReferenceOf(line),
                            severity = severityOf(line) ?: "MEDIUM",
                            title = title,
                            message = line
                        )
                    )
                }
            }
            .take(20)
            .toList()
    }

    private fun severityOf(line: String): String? {
        return when {
            line.contains("严重") || line.contains("高") || line.contains("P0") || line.contains("P1") -> "高"
            line.contains("中") || line.contains("P2") -> "中"
            line.contains("低") || line.contains("P3") -> "低"
            else -> null
        }
    }

    private fun cleanupFindingTitle(line: String): String {
        return line
            .removePrefix("###")
            .removePrefix("##")
            .removePrefix("#")
            .removePrefix("-")
            .removePrefix("*")
            .trim()
            .trim('|')
            .trim()
            .let { if (it.length > 120) it.take(117) + "..." else it }
    }

    private fun findMentionedFile(line: String): String? {
        return result.reviewedFiles.firstOrNull { file ->
            line.contains(file) || line.contains(file.substringAfterLast('/'))
        }
    }

    private fun lineReferenceOf(line: String): Int? {
        return Regex(""":(\d+)\b""").find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    override fun dispose() {
        clearEditorHighlights()
        resultTree = null
        super.dispose()
    }

    override fun createActions(): Array<Action> {
        val actions = mutableListOf<Action>()
        if (onReReview != null) {
            actions.add(object : AbstractAction("重新评审") {
                override fun actionPerformed(e: ActionEvent?) {
                    close(OK_EXIT_CODE)
                    onReReview.invoke()
                }
            })
        }
        if (onContinueCommit != null) {
            actions.add(object : AbstractAction("继续提交") {
                override fun actionPerformed(e: ActionEvent?) {
                    close(OK_EXIT_CODE)
                    onContinueCommit.invoke()
                }
            })
        }
        actions.add(okAction)
        return actions.toTypedArray()
    }

    override fun createLeftSideActions(): Array<Action> {
        return arrayOf(
            object : AbstractAction("复制") {
                override fun actionPerformed(e: ActionEvent?) {
                    CopyPasteManager.getInstance().setContents(StringSelection(result.content))
                }
            },
            object : AbstractAction("保存 MD") {
                override fun actionPerformed(e: ActionEvent?) {
                    saveResultToFile()
                }
            },
            object : AbstractAction("导出 HTML") {
                override fun actionPerformed(e: ActionEvent?) {
                    exportResultAsHtml()
                }
            }
        )
    }

    private fun saveResultToFile() {
        runFileTask(taskTitle = "导出 Markdown", extension = "md", successTitle = "导出成功") { target ->
            Files.writeString(target, buildExportContent())
            "Markdown 评审结果已导出到：${StorageUtil.getProjectRelativePath(project, target)}"
        }
    }

    private fun exportResultAsHtml() {
        runFileTask(taskTitle = "导出 HTML", extension = "html", successTitle = "导出成功") { target ->
            Files.writeString(target, cachedExportHtmlContent)
            "评审结果已导出到：${StorageUtil.getProjectRelativePath(project, target)}"
        }
    }

    private fun runFileTask(
        taskTitle: String,
        extension: String,
        successTitle: String,
        writer: (Path) -> String
    ) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, taskTitle, true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "$taskTitle..."
                try {
                    val reportsDir = StorageUtil.getReportsDir(project)
                    StorageUtil.ensureDirectoryExists(reportsDir)
                    val target = reportsDir.resolve(buildFileName(extension))
                    val message = writer(target)
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(project, message, successTitle)
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showWarningDialog(project, "${taskTitle}失败: ${e.message}", taskTitle)
                    }
                }
            }
        })
    }

    private fun buildFileName(extension: String): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        return "code-review-$timestamp.$extension"
    }

    private fun buildExportContent(): String {
        val generatedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        val reviewBody = buildMarkdownReviewBody()
        return buildString {
            appendLine("# ${result.title}")
            appendLine()
            appendLine("> WorkLog 代码评审报告")
            appendLine("> 生成时间：$generatedAt")
            appendLine()
            appendLine("## 概览")
            appendLine()
            appendLine("| 项目 | 内容 |")
            appendLine("| --- | --- |")
            appendLine("| 状态 | ${markdownCell(if (result.hasFindings) "发现需要关注的问题" else "未发现明显问题")} |")
            appendLine("| 问题数 | ${result.issues.size} |")
            appendLine("| 文件数 | ${result.reviewedFiles.size} |")
            appendLine("| 提交数 | ${result.sourceCommitHashes.size} |")
            appendLine("| 评审范围 | ${markdownCell(reviewScopeLabel())} |")
            appendLine("| Diff 状态 | ${markdownCell(if (result.truncated) "已截断" else "完整")} |")
            appendLine()
            appendLine("## 问题清单")
            appendLine()
            if (result.issues.isEmpty()) {
                appendLine("未发现可定位问题。")
            } else {
                appendLine("| 级别 | 位置 | 问题 | 说明 |")
                appendLine("| --- | --- | --- | --- |")
                result.issues.forEach { issue ->
                    appendLine(
                        "| ${markdownCell(severityLabel(issue.severity))} " +
                            "| ${markdownCell(issueLocation(issue))} " +
                            "| ${markdownCell(issue.title)} " +
                            "| ${markdownCell(issue.message)} |"
                    )
                }
            }
            appendLine()
            if (result.reviewedCommitSummaries.isNotEmpty()) {
                appendLine("## 评审提交")
                appendLine()
                result.reviewedCommitSummaries.forEach { appendLine("- $it") }
                appendLine()
            }
            if (result.reviewedFiles.isNotEmpty()) {
                appendLine("## 评审文件")
                appendLine()
                result.reviewedFiles.forEach { appendLine("- $it") }
                appendLine()
            }
            appendLine("## 评审说明")
            appendLine()
            appendLine(reviewBody.ifBlank { "无补充说明。" })
        }.trimEnd() + "\n"
    }

    private fun buildDetailContent(): String {
        val visibleContent = stripStructuredReviewBlock(result.content).trim()
        if (result.issues.isEmpty()) {
            return visibleContent
        }

        return buildString {
            appendLine("## 问题摘要")
            result.issues.forEach { issue ->
                val location = buildString {
                    append(issue.filePath)
                    issue.line?.let { append(":").append(it) }
                }
                appendLine("- [${severityLabel(issue.severity)}] `$location` ${issue.title}")
            }
            appendLine()
            append(visibleContent)
        }.trim()
    }

    private fun buildHtmlBodyContent(): String {
        return stripStructuredReviewBlock(result.content).trim()
    }

    private fun buildMarkdownReviewBody(): String {
        return stripStructuredReviewBlock(result.content).trim()
    }

    private fun reviewScopeLabel(): String {
        return if (result.sourceCommitHashes.isEmpty()) {
            "暂存区"
        } else {
            "${result.sourceCommitHashes.size} 个提交"
        }
    }

    private fun issueLocation(issue: ReviewIssue): String {
        return buildString {
            append(issue.filePath.ifBlank { "未知文件" })
            issue.line?.let { append(":").append(it) }
        }
    }

    private fun markdownCell(value: String): String {
        return value
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("<br>")
            .ifBlank { "-" }
            .replace("|", "\\|")
    }

    private fun renderHtml(markdown: String): String {
        val parser = Parser.builder().build()
        val document = parser.parse(markdown)
        val renderer = HtmlRenderer.builder()
            .escapeHtml(true)
            .sanitizeUrls(true)
            .build()
        val htmlBody = renderer.render(document)
        val scope = if (result.sourceCommitHashes.isEmpty()) "暂存区" else "${result.sourceCommitHashes.size} 个提交"
        return """
            <!DOCTYPE html>
            <html xmlns="http://www.w3.org/1999/xhtml" lang="zh-CN">
            <head>
                <meta charset="UTF-8" />
                <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src data:;" />
                <title>${escapeHtml(result.title)}</title>
                <style>
                    :root {
                        color-scheme: light dark;
                        --page: #f5f6f8;
                        --panel: #ffffff;
                        --fg: #1f2328;
                        --muted: #6b7280;
                        --border: #d8dee4;
                        --soft-border: #eaeef2;
                        --code-bg: #f6f8fa;
                        --link: #0969da;
                        --high: #b42318;
                        --medium: #9a6700;
                        --low: #57606a;
                    }
                    @media (prefers-color-scheme: dark) {
                        :root {
                            --page: #1e1f22;
                            --panel: #2b2d30;
                            --fg: #e6e6e6;
                            --muted: #a8adb5;
                            --border: #45484d;
                            --soft-border: #383b40;
                            --code-bg: #24262a;
                            --link: #7ab7ff;
                            --high: #ff8a80;
                            --medium: #f0c36a;
                            --low: #a8adb5;
                        }
                    }
                    html, body {
                        margin: 0;
                        padding: 0;
                        background: var(--page);
                        color: var(--fg);
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Arial, sans-serif;
                        line-height: 1.65;
                    }
                    body {
                        padding: 28px;
                        word-break: break-word;
                    }
                    .report {
                        max-width: 1080px;
                        margin: 0 auto;
                        background: var(--panel);
                        border: 1px solid var(--border);
                        border-radius: 10px;
                        overflow: hidden;
                    }
                    .header {
                        padding: 24px 28px 18px;
                        border-bottom: 1px solid var(--soft-border);
                    }
                    .title {
                        margin: 0;
                        font-size: 24px;
                        line-height: 1.25;
                        font-weight: 650;
                    }
                    .subtitle {
                        margin-top: 8px;
                        color: var(--muted);
                        font-size: 13px;
                    }
                    .metrics {
                        display: grid;
                        grid-template-columns: repeat(4, minmax(0, 1fr));
                        border-bottom: 1px solid var(--soft-border);
                    }
                    .metric {
                        padding: 14px 18px;
                        border-right: 1px solid var(--soft-border);
                    }
                    .metric:last-child {
                        border-right: none;
                    }
                    .metric-value {
                        font-size: 20px;
                        font-weight: 650;
                    }
                    .metric-label {
                        margin-top: 3px;
                        color: var(--muted);
                        font-size: 12px;
                    }
                    .section {
                        padding: 22px 28px;
                        border-bottom: 1px solid var(--soft-border);
                    }
                    .section:last-child {
                        border-bottom: none;
                    }
                    .section-title {
                        margin: 0 0 12px;
                        font-size: 18px;
                        font-weight: 650;
                    }
                    .issues {
                        width: 100%;
                        border-collapse: collapse;
                        border: 1px solid var(--soft-border);
                        border-radius: 8px;
                        overflow: hidden;
                    }
                    .issues th, .issues td {
                        padding: 10px 12px;
                        border-bottom: 1px solid var(--soft-border);
                        text-align: left;
                        vertical-align: top;
                        font-size: 13px;
                    }
                    .issues th {
                        background: var(--code-bg);
                        color: var(--muted);
                        font-weight: 600;
                    }
                    .issues tr:last-child td {
                        border-bottom: none;
                    }
                    .badge {
                        display: inline-block;
                        min-width: 36px;
                        padding: 2px 7px;
                        border-radius: 999px;
                        font-size: 12px;
                        font-weight: 650;
                        text-align: center;
                    }
                    .badge.high {
                        color: var(--high);
                        border: 1px solid var(--high);
                    }
                    .badge.medium {
                        color: var(--medium);
                        border: 1px solid var(--medium);
                    }
                    .badge.low {
                        color: var(--low);
                        border: 1px solid var(--border);
                    }
                    .path {
                        font-family: "JetBrains Mono", Consolas, monospace;
                        font-size: 12px;
                        white-space: nowrap;
                    }
                    .content h1 {
                        display: none;
                    }
                    .content h2 {
                        margin: 22px 0 10px;
                        font-size: 17px;
                        font-weight: 650;
                    }
                    .content h3 {
                        margin: 20px 0 8px;
                        font-size: 15px;
                        font-weight: 650;
                    }
                    .content p, .content ul, .content ol {
                        margin: 10px 0;
                    }
                    .content ul, .content ol {
                        padding-left: 22px;
                    }
                    .content li {
                        margin: 6px 0;
                    }
                    a {
                        color: var(--link);
                    }
                    pre {
                        background: var(--code-bg);
                        color: var(--fg);
                        padding: 12px 14px;
                        border-radius: 6px;
                        overflow-x: auto;
                        border: 1px solid var(--border);
                    }
                    code {
                        font-family: "JetBrains Mono", Consolas, monospace;
                    }
                    p code, li code {
                        background: var(--code-bg);
                        padding: 2px 4px;
                        border-radius: 4px;
                    }
                    blockquote {
                        margin: 12px 0;
                        padding: 4px 0 4px 12px;
                        border-left: 4px solid var(--border);
                        color: var(--muted);
                    }
                    .empty {
                        color: var(--muted);
                        padding: 14px 0;
                    }
                </style>
            </head>
            <body>
                <main class="report">
                    <header class="header">
                        <h1 class="title">${escapeHtml(result.title)}</h1>
                        <div class="subtitle">WorkLog 代码评审报告 · ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}</div>
                    </header>
                    <section class="metrics">
                        <div class="metric"><div class="metric-value">${result.issues.size}</div><div class="metric-label">问题</div></div>
                        <div class="metric"><div class="metric-value">${result.reviewedFiles.size}</div><div class="metric-label">文件</div></div>
                        <div class="metric"><div class="metric-value">${result.sourceCommitHashes.size}</div><div class="metric-label">提交</div></div>
                        <div class="metric"><div class="metric-value">${escapeHtml(scope)}</div><div class="metric-label">范围</div></div>
                    </section>
                    <section class="section">
                        <h2 class="section-title">问题清单</h2>
                        ${buildIssuesHtml()}
                    </section>
                    <section class="section content">
                        <h2 class="section-title">评审说明</h2>
                        $htmlBody
                    </section>
                </main>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildIssuesHtml(): String {
        if (result.issues.isEmpty()) {
            return """<div class="empty">未发现可定位问题。</div>"""
        }
        val rows = result.issues.joinToString("\n") { issue ->
            val severity = severityLabel(issue.severity)
            val severityClass = when (issue.severity.uppercase()) {
                "HIGH" -> "high"
                "LOW" -> "low"
                else -> "medium"
            }
            val location = buildString {
                append(issue.filePath)
                issue.line?.let { append(":").append(it) }
            }
            """
                <tr>
                    <td><span class="badge $severityClass">${escapeHtml(severity)}</span></td>
                    <td class="path">${escapeHtml(location)}</td>
                    <td><strong>${escapeHtml(issue.title)}</strong><br />${escapeHtml(issue.message)}</td>
                </tr>
            """.trimIndent()
        }
        return """
            <table class="issues">
                <thead>
                    <tr><th>级别</th><th>位置</th><th>问题</th></tr>
                </thead>
                <tbody>
                    $rows
                </tbody>
            </table>
        """.trimIndent()
    }

    private fun escapeHtml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private sealed class ReviewNode {
        interface Navigable {
            val targetPath: String?
            val targetLine: Int?
        }

        data class Root(val text: String) : ReviewNode() {
            override fun toString(): String = text
        }

        data class GroupNode(val title: String, val count: Int) : ReviewNode() {
            override fun toString(): String = "$title ($count)"
        }

        data class FileNode(val path: String, val line: Int?) : ReviewNode(), Navigable {
            override val targetPath: String? = path.takeIf { it.isNotBlank() }
            override val targetLine: Int? = line

            override fun toString(): String = path
        }

        data class SeverityNode(val severity: String, val count: Int) : ReviewNode() {
            override fun toString(): String = "${severityLabel(severity)} ($count)"
        }

        data class FindingNode(val issue: ReviewIssue) : ReviewNode(), Navigable {
            val severity: String = issue.severity
            override val targetPath: String? = issue.filePath.takeIf { it.isNotBlank() }
            override val targetLine: Int? = issue.line

            override fun toString(): String = issue.title
        }

        data class CommitNode(val summary: String) : ReviewNode() {
            override fun toString(): String = summary
        }

        data class WarningNode(val message: String) : ReviewNode() {
            override fun toString(): String = message
        }
    }

    private class ReviewTreeRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ) {
            val userObject = (value as? DefaultMutableTreeNode)?.userObject
            when (userObject) {
                is ReviewNode.Root -> {
                    icon = AllIcons.Actions.Find
                    append(userObject.text, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                }
                is ReviewNode.GroupNode -> {
                    icon = AllIcons.Nodes.Folder
                    append(userObject.title, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    append(" (${userObject.count})", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                is ReviewNode.SeverityNode -> {
                    icon = AllIcons.General.Warning
                    append(severityLabel(userObject.severity), severityTextAttributes(userObject.severity))
                    append(" (${userObject.count})", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                is ReviewNode.FindingNode -> {
                    icon = AllIcons.General.Warning
                    append(userObject.issue.title, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    userObject.issue.line?.let {
                        append(" :$it", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    }
                    userObject.targetPath?.let {
                        append(" · ${it.substringAfterLast('/')}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    }
                }
                is ReviewNode.FileNode -> {
                    icon = AllIcons.FileTypes.Any_type
                    append(userObject.path, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    userObject.line?.let {
                        append(":$it", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    }
                }
                is ReviewNode.CommitNode -> {
                    icon = AllIcons.Vcs.History
                    append(userObject.summary, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                }
                is ReviewNode.WarningNode -> {
                    icon = AllIcons.General.Warning
                    append(userObject.message, SimpleTextAttributes.ERROR_ATTRIBUTES)
                }
                else -> append(value?.toString().orEmpty(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }
        }
    }
}

private fun severityRank(severity: String): Int {
    return when (severity.uppercase()) {
        "HIGH", "高" -> 0
        "MEDIUM", "中" -> 1
        "LOW", "低" -> 2
        else -> 3
    }
}

private fun severityLabel(severity: String): String {
    return when (severity.uppercase()) {
        "HIGH" -> "高"
        "MEDIUM" -> "中"
        "LOW" -> "低"
        else -> severity
    }
}

private fun severityTextAttributes(severity: String): SimpleTextAttributes {
    return when (severity.uppercase()) {
        "HIGH", "高" -> SimpleTextAttributes.ERROR_ATTRIBUTES
        "LOW", "低" -> SimpleTextAttributes.GRAYED_ATTRIBUTES
        else -> SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
    }
}

private fun severityColor(severity: String): Color {
    return when (severity.uppercase()) {
        "HIGH", "高" -> JBColor.namedColor("ValidationTooltip.errorForeground", JBColor(0xB00020, 0xFF8A80))
        "LOW", "低" -> JBColor.GRAY
        else -> JBColor.foreground()
    }
}

private fun stripStructuredReviewBlock(content: String): String {
    return content.replace(
        Regex("WORKLOG_REVIEW_JSON_START\\s*[\\s\\S]*?\\s*WORKLOG_REVIEW_JSON_END"),
        ""
    )
}
