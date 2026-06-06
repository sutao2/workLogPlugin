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
import java.awt.FlowLayout
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
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class CodeReviewResultDialog(
    private val project: Project,
    private val result: ReviewResult,
    private val onReReview: (() -> Unit)? = null,
    private val allowContinueCommit: Boolean = false,
    private val onContinueCommit: (() -> Unit)? = null
) : DialogWrapper(project) {

    private val detailSearchText = buildDetailContent()
    private val reviewIssues = CodeReviewIssueGrouper.buildReviewIssues(result, detailSearchText)
    private val fileGroups = CodeReviewIssueGrouper.buildFileGroups(result, detailSearchText)
    private var reviewTree: Tree? = null
    private var openFileButton: JButton? = null
    private var issueTitleLabel: JBLabel? = null
    private var issueMetaLabel: JBLabel? = null
    private var issueMessageArea: JTextArea? = null
    private var selectedIssue: ReviewIssue? = null
    private var selectedFileGroup: ReviewFileGroup? = null
    private var initialTreePath: TreePath? = null
    private val activeHighlighters = mutableListOf<RangeHighlighter>()
    private val cachedExportHtmlContent by lazy { renderHtml(buildHtmlBodyContent()) }

    init {
        title = result.title
        isModal = allowContinueCommit
        setSize(920, 560)
        setOKButtonText(if (allowContinueCommit) "取消" else "关闭")
        init()
        ApplicationManager.getApplication().invokeLater {
            selectInitialReviewItem()
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(980, 560)
        panel.border = JBUI.Borders.empty(10, 14, 8, 14)

        panel.add(createSummaryPanel(), BorderLayout.NORTH)

        val workspace = JPanel(BorderLayout(12, 0))
        workspace.border = JBUI.Borders.empty(10, 0, 0, 0)
        workspace.add(createNavigationPanel(), BorderLayout.WEST)
        workspace.add(createReportPanel(), BorderLayout.CENTER)
        panel.add(workspace, BorderLayout.CENTER)

        return panel
    }

    private fun createSummaryPanel(): JComponent {
        val panel = JPanel(BorderLayout(14, 0))
        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border()),
            JBUI.Borders.empty(0, 2, 8, 2)
        )

        val titlePanel = JPanel(BorderLayout(0, 4))
        titlePanel.add(JBLabel(summaryText()).apply {
            foreground = JBColor.GRAY
            if (result.truncated) {
                icon = AllIcons.General.Warning
            }
        }, BorderLayout.NORTH)

        val actionPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0))
        actionPanel.add(JBLabel(statusLabel()).apply {
            icon = if (result.hasFindings) AllIcons.General.Warning else AllIcons.General.InspectionsOK
            foreground = if (result.hasFindings) severityColor("HIGH") else JBColor.GRAY
        })
        panel.add(titlePanel, BorderLayout.CENTER)
        panel.add(actionPanel, BorderLayout.EAST)
        return panel
    }

    private fun createNavigationPanel(): JComponent {
        val tree = Tree(buildReviewTreeModel())
        reviewTree = tree
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.rowHeight = JBUI.scale(24)
        tree.cellRenderer = ReviewTreeRenderer()
        tree.border = JBUI.Borders.empty()
        tree.emptyText.text = "没有评审结果"
        TreeSpeedSearch.installOn(tree)
        tree.addTreeSelectionListener {
            val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
            handleReviewTreeSelection(node?.userObject as? ReviewTreeItem)
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

        val treePanel = JPanel(BorderLayout(0, 6))
        treePanel.add(compactHeader("代码评审", "${displayIssueCount()} 个问题"), BorderLayout.NORTH)
        treePanel.add(flatScrollPane(tree), BorderLayout.CENTER)

        val panel = JPanel(BorderLayout(0, 0))
        panel.preferredSize = Dimension(JBUI.scale(306), JBUI.scale(500))
        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 0, 1, JBColor.border()),
            JBUI.Borders.empty(0, 0, 0, 12)
        )
        panel.add(treePanel, BorderLayout.CENTER)
        return panel
    }

    private fun createReportPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 8))
        panel.border = JBUI.Borders.empty()

        val toolbar = JPanel(BorderLayout(10, 0))
        toolbar.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border()),
            JBUI.Borders.empty(0, 0, 8, 0)
        )
        openFileButton = WorkLogUi.button("打开源码", icon = AllIcons.Actions.Find) {
            openSelectedFile()
        }.apply {
            isEnabled = false
        }

        toolbar.add(
            compactHeader("问题详情", "Enter 或双击打开源码"),
            BorderLayout.CENTER
        )
        toolbar.add(openFileButton, BorderLayout.EAST)
        if (result.truncated) {
            toolbar.add(JBLabel("Diff 已截断").apply {
                foreground = JBColor.GRAY
                icon = AllIcons.General.Warning
            }, BorderLayout.WEST)
        }
        panel.add(toolbar, BorderLayout.NORTH)

        panel.add(createIssueDetailPanel(), BorderLayout.CENTER)
        return panel
    }

    private fun createIssueDetailPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 10))
        panel.border = JBUI.Borders.empty(8, 0, 0, 0)

        issueTitleLabel = JBLabel(if (result.hasFindings) "选择一个问题查看详情" else "未发现明显问题").apply {
            font = font.deriveFont(Font.BOLD, 15f)
        }
        issueMetaLabel = JBLabel("${result.reviewedFiles.size} 个文件 · ${result.issues.size} 个结构化问题").apply {
            foreground = JBColor.GRAY
        }

        val titlePanel = JPanel(BorderLayout(0, 5))
        titlePanel.border = JBUI.Borders.empty(0, 0, 4, 0)
        titlePanel.add(issueTitleLabel, BorderLayout.NORTH)
        titlePanel.add(issueMetaLabel, BorderLayout.CENTER)
        panel.add(titlePanel, BorderLayout.NORTH)

        issueMessageArea = createReadOnlyTextArea("问题说明会显示在这里。")
        panel.add(flatScrollPane(issueMessageArea!!), BorderLayout.CENTER)

        return panel
    }

    private fun compactHeader(title: String, meta: String): JComponent {
        return JPanel(BorderLayout(8, 0)).apply {
            add(JBLabel(title).apply {
                font = font.deriveFont(Font.BOLD, 12f)
            }, BorderLayout.WEST)
            add(JBLabel(meta).apply {
                foreground = JBColor.GRAY
                horizontalAlignment = JLabel.RIGHT
            }, BorderLayout.EAST)
        }
    }

    private fun createReadOnlyTextArea(text: String): JTextArea {
        return WorkLogUi.editorArea(readOnly = true).apply {
            this.text = text
            margin = JBUI.insets(6, 0)
            border = JBUI.Borders.empty()
            font = JBLabel().font.deriveFont(13f)
        }
    }

    private fun flatScrollPane(component: JComponent): JBScrollPane {
        return JBScrollPane(component).apply {
            border = JBUI.Borders.empty()
            viewportBorder = JBUI.Borders.empty()
        }
    }

    private fun openSelectedFile() {
        val issue = selectedIssue
        if (issue != null && issue.filePath.isNotBlank()) {
            openFile(issue.filePath, issue.line, highlight = issue.line != null)
            return
        }

        val group = selectedFileGroup ?: return
        if (group.canOpenFile) {
            openFile(group.path, null, highlight = false)
        }
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
            val group = selectedFileGroup
            issueTitleLabel?.text = when {
                group == null && fileGroups.isEmpty() -> "没有可展示的评审结果"
                group != null && group.issues.isEmpty() -> "未发现明显问题"
                result.hasFindings -> "选择一个问题查看详情"
                else -> "未发现明显问题"
            }
            issueTitleLabel?.foreground = JBColor.foreground()
            issueMetaLabel?.text = group?.let {
                "${it.displayPath} · ${it.issues.size} 个问题"
            } ?: "${fileGroups.size} 个文件 · ${displayIssueCount()} 个问题"
            issueMessageArea?.text = if (fileGroups.isEmpty()) {
                detailSearchText.ifBlank { "没有可展示的评审结果。" }
            } else if (group != null && group.issues.isEmpty()) {
                "该文件参与了评审，未发现可定位问题。"
            } else if (result.issues.isEmpty() && result.hasFindings) {
                "当前 AI 响应没有提供完整的结构化问题。已根据文本内容生成可预览的问题分组；如需更准确定位，请重新评审。"
            } else {
                "从左侧选择具体问题后会直接跳转到源码位置，并在编辑器中高亮对应行。"
            }
            issueMessageArea?.caretPosition = 0
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

    private fun handleReviewTreeSelection(item: ReviewTreeItem?) {
        when (item) {
            is ReviewTreeItem.Finding -> {
                selectedIssue = item.issue
                selectedFileGroup = fileGroupForIssue(item.issue)
                updateIssueDetails(item.issue)
                updateOpenFileButton()
                if (item.issue.filePath.isNotBlank()) {
                    navigateToIssue(item.issue)
                }
            }
            is ReviewTreeItem.File -> {
                selectedFileGroup = item.group
                selectedIssue = null
                updateFileSummary(item.group)
                updateOpenFileButton()
            }
            is ReviewTreeItem.Severity -> {
                selectedIssue = null
                selectedFileGroup = null
                updateSeveritySummary(item)
                updateOpenFileButton()
            }
            is ReviewTreeItem.Group -> {
                selectedIssue = null
                selectedFileGroup = null
                updateGroupSummary(item)
                updateOpenFileButton()
            }
            else -> {
                selectedIssue = null
                selectedFileGroup = null
                updateIssueDetails(null)
                updateOpenFileButton()
            }
        }
    }

    private fun selectInitialReviewItem() {
        val tree = reviewTree
        val path = initialTreePath
        if (tree != null && path != null) {
            tree.expandPath(path.parentPath)
            tree.selectionPath = path
            tree.scrollPathToVisible(path)
            return
        }

        if (fileGroups.isEmpty() && reviewIssues.isEmpty()) {
            updateIssueDetails(null)
            updateOpenFileButton()
        }
    }

    private fun buildReviewTreeModel(): DefaultTreeModel {
        initialTreePath = null
        val root = DefaultMutableTreeNode(ReviewTreeItem.Root)

        if (reviewIssues.isNotEmpty()) {
            root.add(buildFindingsTree())
        } else if (fileGroups.isNotEmpty()) {
            val filesNode = DefaultMutableTreeNode(ReviewTreeItem.Group("评审文件", fileGroups.size))
            fileGroups.forEach { group ->
                val fileNode = DefaultMutableTreeNode(ReviewTreeItem.File(group))
                filesNode.add(fileNode)
            }
            root.add(filesNode)
        } else {
            root.add(DefaultMutableTreeNode(ReviewTreeItem.Group("没有可展示的评审结果", 0)))
        }

        findInitialTreeNode(root)?.let { initialTreePath = TreePath(it.path) }
        return DefaultTreeModel(root)
    }

    private fun buildFindingsTree(): DefaultMutableTreeNode {
        val findingsNode = DefaultMutableTreeNode(ReviewTreeItem.Group("问题", reviewIssues.size))
        reviewIssues
            .groupBy { it.severity }
            .toList()
            .sortedBy { (severity, _) -> CodeReviewIssueGrouper.severityRank(severity) }
            .forEach { (severity, severityIssues) ->
                val severityNode = DefaultMutableTreeNode(ReviewTreeItem.Severity(severity, severityIssues.size))
                severityIssues
                    .groupBy { it.filePath.ifBlank { CodeReviewIssueGrouper.UNLOCATED_GROUP_PATH } }
                    .toList()
                    .sortedBy { (path, _) -> displayPathForIssuePath(path).lowercase() }
                    .forEach { (path, fileIssues) ->
                        val group = fileGroupForPath(path)
                        if (group == null) {
                            fileIssues.forEach { severityNode.add(findingNode(it)) }
                        } else {
                            val fileNode = DefaultMutableTreeNode(ReviewTreeItem.File(group))
                            fileIssues.forEach { fileNode.add(findingNode(it)) }
                            severityNode.add(fileNode)
                        }
                    }
                findingsNode.add(severityNode)
            }
        return findingsNode
    }

    private fun findingNode(issue: ReviewIssue): DefaultMutableTreeNode {
        return DefaultMutableTreeNode(ReviewTreeItem.Finding(issue))
    }

    private fun findInitialTreeNode(node: DefaultMutableTreeNode): DefaultMutableTreeNode? {
        return findFirstTreeNode(node) { it is ReviewTreeItem.Finding }
            ?: findFirstTreeNode(node) { it is ReviewTreeItem.File }
    }

    private fun findFirstTreeNode(
        node: DefaultMutableTreeNode,
        predicate: (ReviewTreeItem) -> Boolean
    ): DefaultMutableTreeNode? {
        val item = node.userObject as? ReviewTreeItem
        if (item != null && predicate(item)) {
            return node
        }
        for (index in 0 until node.childCount) {
            val child = node.getChildAt(index) as? DefaultMutableTreeNode ?: continue
            val match = findFirstTreeNode(child, predicate)
            if (match != null) {
                return match
            }
        }
        return null
    }

    private fun updateFileSummary(group: ReviewFileGroup) {
        issueTitleLabel?.text = group.displayPath.substringAfterLast('/').ifBlank { group.displayPath }
        issueTitleLabel?.foreground = JBColor.foreground()
        issueMetaLabel?.text = if (group.issues.isEmpty()) {
            "${group.displayPath} · 无问题"
        } else {
            "${group.displayPath} · ${group.issues.size} 个问题 · 最高 ${severityLabel(group.highestSeverity.orEmpty())}"
        }
        issueMessageArea?.text = if (group.issues.isEmpty()) {
            "该文件参与了评审，未发现可定位问题。"
        } else {
            group.issues.joinToString("\n") { issue ->
                "- ${severityLabel(issue.severity)}${issue.line?.let { " :$it" } ?: ""} ${issue.title}"
            }
        }
        issueMessageArea?.caretPosition = 0
    }

    private fun updateSeveritySummary(item: ReviewTreeItem.Severity) {
        issueTitleLabel?.text = "${severityLabel(item.severity)}风险问题"
        issueTitleLabel?.foreground = severityColor(item.severity)
        issueMetaLabel?.text = "${item.count} 个问题 · ${fileGroups.size} 个文件"
        issueMessageArea?.text = reviewIssues
            .filter { it.severity == item.severity }
            .joinToString("\n") { issue ->
                "- ${issue.filePath.ifBlank { "未定位问题" }}${issue.line?.let { ":$it" } ?: ""} ${issue.title}"
            }
            .ifBlank { "该级别下没有可展示的问题。" }
        issueMessageArea?.caretPosition = 0
    }

    private fun updateGroupSummary(item: ReviewTreeItem.Group) {
        issueTitleLabel?.text = item.title
        issueTitleLabel?.foreground = JBColor.foreground()
        issueMetaLabel?.text = "${fileGroups.size} 个文件 · ${displayIssueCount()} 个问题"
        issueMessageArea?.text = when {
            displayIssueCount() > 0 -> severitySummaryText()
            fileGroups.isNotEmpty() -> "已完成 ${fileGroups.size} 个文件的评审，未发现明显问题。"
            else -> detailSearchText.ifBlank { "没有可展示的评审结果。" }
        }
        issueMessageArea?.caretPosition = 0
    }

    private fun severitySummaryText(): String {
        return reviewIssues
            .groupBy { severityLabel(it.severity) }
            .entries
            .sortedBy { (severity, _) -> CodeReviewIssueGrouper.severityRank(severity) }
            .joinToString("\n") { (severity, issues) ->
                "- $severity：${issues.size} 个问题"
            }
    }

    private fun fileGroupForIssue(issue: ReviewIssue): ReviewFileGroup? {
        return fileGroupForPath(issue.filePath.ifBlank { CodeReviewIssueGrouper.UNLOCATED_GROUP_PATH })
    }

    private fun fileGroupForPath(path: String): ReviewFileGroup? {
        val normalizedPath = if (path.isBlank()) CodeReviewIssueGrouper.UNLOCATED_GROUP_PATH else path
        return fileGroups.firstOrNull { group ->
            if (group.isUnlocated) {
                normalizedPath == CodeReviewIssueGrouper.UNLOCATED_GROUP_PATH
            } else {
                group.path == normalizedPath
            }
        }
    }

    private fun displayPathForIssuePath(path: String): String {
        return if (path == CodeReviewIssueGrouper.UNLOCATED_GROUP_PATH) {
            "未定位问题"
        } else {
            path
        }
    }

    private fun updateOpenFileButton() {
        openFileButton?.isEnabled =
            selectedIssue?.filePath?.isNotBlank() == true || selectedFileGroup?.canOpenFile == true
    }

    private fun displayIssueCount(): Int {
        return fileGroups.sumOf { it.issues.size }
    }

    override fun dispose() {
        clearEditorHighlights()
        reviewTree = null
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
        if (allowContinueCommit && onContinueCommit != null) {
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

    private fun summaryText(): String {
        val diffState = if (result.truncated) "Diff 已截断" else "Diff 完整"
        return "${reviewScopeLabel()} · ${displayIssueCount()} 个问题 · ${fileGroups.size} 个文件 · $diffState"
    }

    private fun statusLabel(): String {
        return if (result.hasFindings) "需要关注" else "未发现明显问题"
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

    private sealed class ReviewTreeItem {
        data object Root : ReviewTreeItem()
        data class Group(val title: String, val count: Int) : ReviewTreeItem()
        data class Severity(val severity: String, val count: Int) : ReviewTreeItem()
        data class File(val group: ReviewFileGroup) : ReviewTreeItem()
        data class Finding(val issue: ReviewIssue) : ReviewTreeItem()
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
            val item = (value as? DefaultMutableTreeNode)?.userObject as? ReviewTreeItem
            when (item) {
                is ReviewTreeItem.Group -> {
                    icon = AllIcons.Nodes.Folder
                    append(item.title, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    append("  ${item.count}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                is ReviewTreeItem.Severity -> {
                    icon = AllIcons.General.Warning
                    append(severityLabel(item.severity), severityTextAttributes(item.severity))
                    append("  ${item.count}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                is ReviewTreeItem.File -> {
                    val group = item.group
                    icon = if (group.isUnlocated) AllIcons.General.Warning else AllIcons.FileTypes.Any_type
                    append(fileName(group), SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    if (group.issues.isNotEmpty()) {
                        append("  ${group.issues.size}", fileMetaAttributes(group))
                    }
                    toolTipText = group.displayPath
                }
                is ReviewTreeItem.Finding -> {
                    icon = AllIcons.General.Warning
                    append(item.issue.title, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    item.issue.line?.let {
                        append("  :$it", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    }
                    toolTipText = item.issue.message.ifBlank { item.issue.title }
                }
                ReviewTreeItem.Root -> {
                    append("代码评审", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                }
                null -> append(value?.toString().orEmpty(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }
        }

        private fun fileName(group: ReviewFileGroup): String {
            return group.displayPath.substringAfterLast('/').ifBlank { group.displayPath }
        }

        private fun fileMetaAttributes(group: ReviewFileGroup): SimpleTextAttributes {
            return group.highestSeverity?.let { severityTextAttributes(it) } ?: SimpleTextAttributes.GRAYED_ATTRIBUTES
        }
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
        "MEDIUM", "中" -> SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
        else -> SimpleTextAttributes.GRAYED_ATTRIBUTES
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
