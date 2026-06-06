package com.worklog.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import com.worklog.models.ExportFormat
import com.worklog.services.AIService
import com.worklog.services.StatisticsService
import com.worklog.services.WorkLogService
import com.worklog.settings.AppSettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.*
import javax.swing.*

/**
 * 统计信息对话框
 */
class StatisticsDialog(private val project: Project) : DialogWrapper(project) {

    private val statisticsService = project.getService(StatisticsService::class.java)
    private val workLogService = project.getService(WorkLogService::class.java)
    private val aiService = project.getService(AIService::class.java)
    private val tabbedPane = JBTabbedPane()
    private val scope = CoroutineScope(Dispatchers.Main)

    companion object {
        private val THINK_TAG_REGEX = Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL)
        private val THINKING_TAG_REGEX = Regex("<thinking>.*?</thinking>", RegexOption.DOT_MATCHES_ALL)
        private val REASONING_TAG_REGEX = Regex("<reasoning>.*?</reasoning>", RegexOption.DOT_MATCHES_ALL)
    }

    private fun cleanAiSummary(rawSummary: String): String {
        var cleanedSummary = rawSummary.trim()
        cleanedSummary = cleanedSummary.replace(THINK_TAG_REGEX, "")
        cleanedSummary = cleanedSummary.replace(THINKING_TAG_REGEX, "")
        cleanedSummary = cleanedSummary.replace(REASONING_TAG_REGEX, "")
        return cleanedSummary.trim()
    }

    init {
        title = "工作统计"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(800, 600)
        panel.border = JBUI.Borders.empty(8)

        // 创建标签页
        tabbedPane.addTab("本周统计", createWeeklyPanel())
        tabbedPane.addTab("本月统计", createMonthlyPanel())
        tabbedPane.addTab("年度统计", createYearlyPanel())
        tabbedPane.addTab("自定义范围", createCustomRangePanel())

        panel.add(tabbedPane, BorderLayout.CENTER)
        return panel
    }

    private fun createWeeklyPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 8))

        // 顶部控制面板
        val topPanel = createActionBar()
        val generateButton = JButton("生成本周AI总结")
        val exportButton = JButton("导出周报")
        topPanel.add(generateButton)
        topPanel.add(exportButton)

        // 结果显示区域
        val resultArea = createReportTextArea()

        // 获取本周日期范围
        val now = LocalDate.now()
        val weekFields = WeekFields.of(Locale.getDefault())
        val startOfWeek = now.with(weekFields.dayOfWeek(), 1)
        val endOfWeek = now.with(weekFields.dayOfWeek(), 7)

        // 默认显示统计数据
        val stats = statisticsService.getStatistics(startOfWeek, endOfWeek)
        resultArea.text = formatWeeklyReport(startOfWeek, endOfWeek, stats, null)

        // 生成AI总结按钮
        generateButton.addActionListener {
            generateButton.isEnabled = false
            generateButton.text = "生成中..."

            scope.launch {
                try {
                    val aiSummary = withContext(Dispatchers.IO) {
                        generateWeeklySummary(startOfWeek, endOfWeek)
                    }
                    resultArea.text = formatWeeklyReport(startOfWeek, endOfWeek, stats, aiSummary)
                    generateButton.text = "重新生成AI总结"

                    // 提示用户已保存
                    val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
                    val fileName = "${startOfWeek.format(formatter)}-${endOfWeek.format(formatter)}工作周报.md"
                    JOptionPane.showMessageDialog(
                        panel,
                        "AI总结已生成并自动保存到：\n.worklogs/reports/$fileName",
                        "生成成功",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                } catch (e: Exception) {
                    JOptionPane.showMessageDialog(
                        panel,
                        "AI总结生成失败: ${e.message}",
                        "错误",
                        JOptionPane.ERROR_MESSAGE
                    )
                    generateButton.text = "生成本周AI总结"
                } finally {
                    generateButton.isEnabled = true
                }
            }
        }

        // 导出按钮
        exportButton.addActionListener {
            scope.launch {
                try {
                    val aiSummary = if (resultArea.text.contains("## AI工作总结")) {
                        // 已经生成过AI总结
                        resultArea.text.substringAfter("## AI工作总结").substringBefore("## 统计数据").trim()
                    } else {
                        null
                    }
                    exportWeeklyReport(startOfWeek, endOfWeek, aiSummary)
                } catch (e: Exception) {
                    JOptionPane.showMessageDialog(
                        panel,
                        "导出失败: ${e.message}",
                        "错误",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }

        panel.add(topPanel, BorderLayout.NORTH)
        panel.add(createReportScrollPane(resultArea), BorderLayout.CENTER)

        return wrapStatsPage(panel)
    }

    private fun createMonthlyPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 8))

        // 顶部控制面板 - 和周报、年报保持一致的 FlowLayout
        val topPanel = createActionBar()
        topPanel.add(JBLabel("选择月份:"))

        val yearField = JTextField(6)
        yearField.text = LocalDate.now().year.toString()
        topPanel.add(yearField)

        topPanel.add(JBLabel("年"))

        val monthField = JTextField(3)
        monthField.text = String.format("%02d", LocalDate.now().monthValue)
        topPanel.add(monthField)

        topPanel.add(JBLabel("月"))

        val queryButton = JButton("查询")
        val generateButton = JButton("生成月度AI总结")
        val exportButton = JButton("导出月报")

        topPanel.add(queryButton)
        topPanel.add(generateButton)
        topPanel.add(exportButton)

        // 结果显示区域
        val resultArea = createReportTextArea()

        var currentYear = LocalDate.now().year
        var currentMonth = LocalDate.now().monthValue

        // 默认加载本月数据
        val startDate = LocalDate.of(currentYear, currentMonth, 1)
        val endDate = startDate.with(TemporalAdjusters.lastDayOfMonth())
        val stats = statisticsService.getStatistics(startDate, endDate)
        resultArea.text = formatMonthlyReport(currentYear, currentMonth, stats, null)

        // 查询按钮
        queryButton.addActionListener {
            try {
                currentYear = yearField.text.toInt()
                currentMonth = monthField.text.toInt()
                val start = LocalDate.of(currentYear, currentMonth, 1)
                val end = start.with(TemporalAdjusters.lastDayOfMonth())
                val monthStats = statisticsService.getStatistics(start, end)
                resultArea.text = formatMonthlyReport(currentYear, currentMonth, monthStats, null)
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(
                    panel,
                    "日期格式错误: ${e.message}",
                    "错误",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }

        // 生成AI总结按钮
        generateButton.addActionListener {
            generateButton.isEnabled = false
            generateButton.text = "生成中..."

            scope.launch {
                try {
                    val aiSummary = withContext(Dispatchers.IO) {
                        generateMonthlySummary(currentYear, currentMonth)
                    }
                    val start = LocalDate.of(currentYear, currentMonth, 1)
                    val end = start.with(TemporalAdjusters.lastDayOfMonth())
                    val monthStats = statisticsService.getStatistics(start, end)
                    resultArea.text = formatMonthlyReport(currentYear, currentMonth, monthStats, aiSummary)
                    generateButton.text = "重新生成AI总结"

                    // 提示用户已保存
                    val fileName = "${currentYear}年${currentMonth}月工作月报.md"
                    JOptionPane.showMessageDialog(
                        panel,
                        "AI总结已生成并自动保存到：\n.worklogs/reports/$fileName",
                        "生成成功",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                } catch (e: Exception) {
                    JOptionPane.showMessageDialog(
                        panel,
                        "AI总结生成失败: ${e.message}",
                        "错误",
                        JOptionPane.ERROR_MESSAGE
                    )
                    generateButton.text = "生成月度AI总结"
                } finally {
                    generateButton.isEnabled = true
                }
            }
        }

        // 导出按钮
        exportButton.addActionListener {
            scope.launch {
                try {
                    val aiSummary = if (resultArea.text.contains("## AI工作总结")) {
                        resultArea.text.substringAfter("## AI工作总结").substringBefore("## 统计数据").trim()
                    } else {
                        null
                    }
                    exportMonthlyReport(currentYear, currentMonth, aiSummary)
                } catch (e: Exception) {
                    JOptionPane.showMessageDialog(
                        panel,
                        "导出失败: ${e.message}",
                        "错误",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }

        panel.add(topPanel, BorderLayout.NORTH)
        panel.add(createReportScrollPane(resultArea), BorderLayout.CENTER)

        return wrapStatsPage(panel)
    }

    private fun createYearlyPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 8))

        // 顶部控制面板
        val topPanel = createActionBar()
        topPanel.add(JBLabel("选择年份:"))

        val yearField = JTextField(6)
        yearField.text = LocalDate.now().year.toString()
        topPanel.add(yearField)

        val queryButton = JButton("查询")
        val generateButton = JButton("生成年度AI总结")
        val exportButton = JButton("导出年报")

        topPanel.add(queryButton)
        topPanel.add(generateButton)
        topPanel.add(exportButton)

        // 结果显示区域
        val resultArea = createReportTextArea()

        var currentYear = LocalDate.now().year

        // 默认加载当前年度数据
        val stats = statisticsService.getYearlyStatistics(currentYear)
        resultArea.text = formatYearlyReport(currentYear, stats, null)

        // 查询按钮
        queryButton.addActionListener {
            try {
                currentYear = yearField.text.toInt()
                val yearStats = statisticsService.getYearlyStatistics(currentYear)
                resultArea.text = formatYearlyReport(currentYear, yearStats, null)
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(
                    panel,
                    "年份格式错误: ${e.message}",
                    "错误",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }

        // 生成AI总结按钮
        generateButton.addActionListener {
            generateButton.isEnabled = false
            generateButton.text = "生成中..."

            scope.launch {
                try {
                    val aiSummary = withContext(Dispatchers.IO) {
                        generateYearlySummary(currentYear)
                    }
                    val yearStats = statisticsService.getYearlyStatistics(currentYear)
                    resultArea.text = formatYearlyReport(currentYear, yearStats, aiSummary)
                    generateButton.text = "重新生成AI总结"

                    // 提示用户已保存
                    val fileName = "${currentYear}年度工作总结.md"
                    JOptionPane.showMessageDialog(
                        panel,
                        "AI总结已生成并自动保存到：\n.worklogs/reports/$fileName",
                        "生成成功",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                } catch (e: Exception) {
                    JOptionPane.showMessageDialog(
                        panel,
                        "AI总结生成失败: ${e.message}",
                        "错误",
                        JOptionPane.ERROR_MESSAGE
                    )
                    generateButton.text = "生成年度AI总结"
                } finally {
                    generateButton.isEnabled = true
                }
            }
        }

        // 导出按钮
        exportButton.addActionListener {
            scope.launch {
                try {
                    val aiSummary = if (resultArea.text.contains("## AI工作总结")) {
                        resultArea.text.substringAfter("## AI工作总结").substringBefore("## 统计数据").trim()
                    } else {
                        null
                    }
                    exportYearlyReport(currentYear, aiSummary)
                } catch (e: Exception) {
                    JOptionPane.showMessageDialog(
                        panel,
                        "导出失败: ${e.message}",
                        "错误",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }

        panel.add(topPanel, BorderLayout.NORTH)
        panel.add(createReportScrollPane(resultArea), BorderLayout.CENTER)

        return wrapStatsPage(panel)
    }

    private fun createCustomRangePanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 8))

        val topPanel = createActionBar()
        topPanel.add(JBLabel("开始日期: "))
        val startDateField = JTextField(10)
        startDateField.text = LocalDate.now().minusDays(30).toString()
        topPanel.add(startDateField)

        topPanel.add(JBLabel("  结束日期: "))
        val endDateField = JTextField(10)
        endDateField.text = LocalDate.now().toString()
        topPanel.add(endDateField)

        val queryButton = JButton("查询")
        val resultArea = createReportTextArea()

        queryButton.addActionListener {
            try {
                val startDate = LocalDate.parse(startDateField.text)
                val endDate = LocalDate.parse(endDateField.text)
                val stats = statisticsService.getStatistics(startDate, endDate)
                resultArea.text = formatStatistics(stats)
            } catch (e: Exception) {
                resultArea.text = "日期格式错误，请使用 yyyy-MM-dd 格式"
            }
        }
        topPanel.add(queryButton)

        panel.add(topPanel, BorderLayout.NORTH)
        panel.add(createReportScrollPane(resultArea), BorderLayout.CENTER)

        return wrapStatsPage(panel)
    }

    private fun wrapStatsPage(content: JComponent): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(8)
        panel.add(content, BorderLayout.CENTER)
        return panel
    }

    private fun createActionBar(): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
    }

    private fun createReportTextArea(): JTextArea {
        return JTextArea().apply {
            isEditable = false
            font = Font("Monospaced", Font.PLAIN, 12)
            lineWrap = true
            wrapStyleWord = true
            margin = JBUI.insets(10)
        }
    }

    private fun createReportScrollPane(textArea: JTextArea): JBScrollPane {
        return JBScrollPane(textArea).apply {
            border = BorderFactory.createLineBorder(JBColor.border())
        }
    }

    private fun formatStatistics(stats: com.worklog.models.WorkLogStatistics): String {
        return buildString {
            appendLine("工作统计报告")
            appendLine("=".repeat(50))
            appendLine()
            appendLine("总览：")
            appendLine("  工作天数: ${stats.totalDays} 天")
            appendLine("  总提交数: ${stats.totalCommits} 次")
            appendLine("  涉及文件: ${stats.totalFiles} 个")
            appendLine("  平均每日提交: %.2f 次".format(stats.averageCommitsPerDay))
            appendLine()
            if (stats.mostProductiveDay != null) {
                appendLine("最高产的一天: ${stats.mostProductiveDay} (${stats.mostProductiveDayCommits} 次提交)")
                appendLine()
            }
            appendLine("按星期统计：")
            stats.commitsByWeekday.entries.sortedByDescending { it.value }.forEach { (day, count) ->
                appendLine("  $day: $count 次")
            }
        }
    }

    private fun formatMonthlyReport(year: Int, month: Int, stats: com.worklog.models.WorkLogStatistics, aiSummary: String?): String {
        return buildString {
            appendLine("$year 年 $month 月工作月报")
            appendLine("=".repeat(50))
            appendLine()

            // AI总结部分
            if (aiSummary != null) {
                appendLine("## AI工作总结")
                appendLine()
                appendLine(aiSummary)
                appendLine()
                appendLine("=".repeat(50))
                appendLine()
            }

            // 统计数据
            appendLine("## 统计数据")
            appendLine()
            appendLine(formatStatistics(stats))
        }
    }

    private fun formatYearlyReport(year: Int, stats: com.worklog.models.WorkLogStatistics, aiSummary: String?): String {
        return buildString {
            appendLine("$year 年度工作总结")
            appendLine("=".repeat(50))
            appendLine()

            // AI总结部分
            if (aiSummary != null) {
                appendLine("## AI工作总结")
                appendLine()
                appendLine(aiSummary)
                appendLine()
                appendLine("=".repeat(50))
                appendLine()
            }

            // 统计数据
            appendLine("## 统计数据")
            appendLine()
            appendLine(formatStatistics(stats))
            appendLine()
            appendLine("## 月度汇总")
            appendLine("-".repeat(50))

            // 按月统计
            for (month in 1..12) {
                val startDate = LocalDate.of(year, month, 1)
                val endDate = startDate.with(TemporalAdjusters.lastDayOfMonth())
                val monthStats = statisticsService.getStatistics(startDate, endDate)

                if (monthStats.totalDays > 0) {
                    appendLine()
                    appendLine("### ${month}月")
                    appendLine("  工作天数: ${monthStats.totalDays} 天")
                    appendLine("  总提交数: ${monthStats.totalCommits} 次")
                    appendLine("  平均每日提交: %.2f 次".format(monthStats.averageCommitsPerDay))
                }
            }
        }
    }

    private fun formatWeeklyReport(startDate: LocalDate, endDate: LocalDate, stats: com.worklog.models.WorkLogStatistics, aiSummary: String?): String {
        return buildString {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            appendLine("${startDate.format(formatter)} 至 ${endDate.format(formatter)} 工作周报")
            appendLine("=".repeat(50))
            appendLine()

            // AI总结部分
            if (aiSummary != null) {
                appendLine("## AI工作总结")
                appendLine()
                appendLine(aiSummary)
                appendLine()
                appendLine("=".repeat(50))
                appendLine()
            }

            // 统计数据
            appendLine("## 统计数据")
            appendLine()
            appendLine(formatStatistics(stats))
        }
    }

    // AI总结生成方法
    private suspend fun generateWeeklySummary(startDate: LocalDate, endDate: LocalDate): String {
        val logs = collectWorkLogs(startDate, endDate)
        if (logs.isEmpty()) {
            throw Exception("该周没有工作日志，无法生成AI总结")
        }

        val systemPrompt = """
            你是一个专业的工作总结助手。请根据用户提供的工作日志生成工作周报。

            重要规则：
            1. 直接输出工作周报内容，不要输出任何思考过程、推理步骤或XML标签
            2. 不要使用 <think>、<reasoning>、<thinking> 等任何标签
            3. 用简洁、专业的语言描述工作内容
            4. 严格按照用户要求的格式输出
        """.trimIndent()

        val prompt = buildString {
            appendLine("请根据以下本周的工作日志，生成一份简洁的工作周报总结。")
            appendLine()
            appendLine("要求：")
            appendLine("1. 总结本周主要完成的工作（3-5条）")
            appendLine("2. 突出重点成果和技术亮点")
            appendLine("3. 简洁明了，每条工作1-2句话")
            appendLine("4. 使用Markdown格式")
            appendLine()
            appendLine("本周工作日志：")
            appendLine("=".repeat(50))
            logs.forEach { (date, content) ->
                appendLine()
                appendLine("## $date")
                appendLine(content)
            }
        }

        val rawSummary = aiService.callAI(prompt, systemPrompt)
        val cleanedSummary = cleanAiSummary(rawSummary)

        // 自动保存到目录
        autoSaveWeeklyReport(startDate, endDate, cleanedSummary)

        return cleanedSummary
    }

    private suspend fun generateMonthlySummary(year: Int, month: Int): String {
        val startDate = LocalDate.of(year, month, 1)
        val endDate = startDate.with(TemporalAdjusters.lastDayOfMonth())
        val logs = collectWorkLogs(startDate, endDate)

        if (logs.isEmpty()) {
            throw Exception("该月没有工作日志，无法生成AI总结")
        }

        val systemPrompt = """
            你是一个专业的工作总结助手。请根据用户提供的工作日志生成月度工作总结。

            重要规则：
            1. 直接输出月度工作总结内容，不要输出任何思考过程、推理步骤或XML标签
            2. 不要使用 <think>、<reasoning>、<thinking> 等任何标签
            3. 用简洁、专业的语言描述工作内容
            4. 严格按照用户要求的格式输出
        """.trimIndent()

        val prompt = buildString {
            appendLine("请根据以下本月的工作日志，生成一份详细的月度工作总结。")
            appendLine()
            appendLine("要求：")
            appendLine("1. 概述本月整体工作情况（2-3段）")
            appendLine("2. 列出主要完成的功能和项目（分类列举）")
            appendLine("3. 总结技术难点和解决方案")
            appendLine("4. 提炼本月的学习和成长")
            appendLine("5. 使用Markdown格式，结构清晰")
            appendLine()
            appendLine("本月工作日志：")
            appendLine("=".repeat(50))
            logs.forEach { (date, content) ->
                appendLine()
                appendLine("## $date")
                appendLine(content)
            }
        }

        val rawSummary = aiService.callAI(prompt, systemPrompt)
        val cleanedSummary = cleanAiSummary(rawSummary)

        // 自动保存到目录
        autoSaveMonthlyReport(year, month, cleanedSummary)

        return cleanedSummary
    }

    private suspend fun generateYearlySummary(year: Int): String {
        val startDate = LocalDate.of(year, 1, 1)
        val endDate = LocalDate.of(year, 12, 31)
        val logs = collectWorkLogs(startDate, endDate)

        if (logs.isEmpty()) {
            throw Exception("该年没有工作日志，无法生成AI总结")
        }

        val systemPrompt = """
            你是一个专业的工作总结助手。请根据用户提供的工作日志生成年度工作总结报告。

            重要规则：
            1. 直接输出年度工作总结内容，不要输出任何思考过程、推理步骤或XML标签
            2. 不要使用 <think>、<reasoning>、<thinking> 等任何标签
            3. 用简洁、专业的语言描述工作内容
            4. 严格按照用户要求的格式输出
        """.trimIndent()

        val prompt = buildString {
            appendLine("请根据以下全年的工作日志，生成一份年度工作总结报告。")
            appendLine()
            appendLine("要求：")
            appendLine("1. 概述全年工作的整体情况和主要成就")
            appendLine("2. 按季度或按项目梳理重要工作")
            appendLine("3. 总结技术成长和能力提升")
            appendLine("4. 提炼全年的亮点工作")
            appendLine("5. 简要展望下一年计划")
            appendLine("6. 使用Markdown格式，结构完整清晰")
            appendLine()
            appendLine("全年工作日志：")
            appendLine("=".repeat(50))

            // 由于日志可能很多，按月汇总（避免每月重复遍历全部日志）
            val logsByMonth = logs.entries.groupBy { LocalDate.parse(it.key).monthValue }
            for (month in 1..12) {
                val monthLogs = logsByMonth[month].orEmpty()
                if (monthLogs.isNotEmpty()) {
                    appendLine()
                    appendLine("### ${month}月工作")
                    monthLogs.forEach { (date, content) ->
                        val summary = content.lines().take(5).joinToString("\n")
                        appendLine("- $date: $summary")
                    }
                }
            }
        }

        val rawSummary = aiService.callAI(prompt, systemPrompt)
        val cleanedSummary = cleanAiSummary(rawSummary)

        // 自动保存到目录
        autoSaveYearlyReport(year, cleanedSummary)

        return cleanedSummary
    }

    private fun collectWorkLogs(startDate: LocalDate, endDate: LocalDate): Map<String, String> {
        val logs = mutableMapOf<String, String>()
        var currentDate = startDate

        while (!currentDate.isAfter(endDate)) {
            val workLog = workLogService.loadWorkLog(currentDate)
            if (workLog != null && workLog.content.isNotBlank()) {
                logs[currentDate.toString()] = workLog.content
            }
            currentDate = currentDate.plusDays(1)
        }

        return logs
    }

    private fun exportWeeklyReport(startDate: LocalDate, endDate: LocalDate, aiSummary: String?) {
        val stats = statisticsService.getStatistics(startDate, endDate)
        val reportContent = formatWeeklyReport(startDate, endDate, stats, aiSummary)

        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        val fileName = "${startDate.format(formatter)}-${endDate.format(formatter)}工作周报.md"
        val fileChooser = JFileChooser()
        fileChooser.dialogTitle = "保存周报"
        fileChooser.selectedFile = File(fileName)

        if (fileChooser.showSaveDialog(tabbedPane) == JFileChooser.APPROVE_OPTION) {
            val file = fileChooser.selectedFile
            file.writeText(reportContent)

            val result = JOptionPane.showConfirmDialog(
                tabbedPane,
                "周报已导出到: ${file.absolutePath}\n\n是否打开文件?",
                "导出成功",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.INFORMATION_MESSAGE
            )

            if (result == JOptionPane.YES_OPTION && Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file)
            }
        }
    }

    private fun exportMonthlyReport(year: Int, month: Int, aiSummary: String?) {
        try {
            val startDate = LocalDate.of(year, month, 1)
            val endDate = startDate.with(TemporalAdjusters.lastDayOfMonth())
            val stats = statisticsService.getStatistics(startDate, endDate)
            val reportContent = formatMonthlyReport(year, month, stats, aiSummary)

            val fileName = "${year}年${month}月工作月报.md"
            val fileChooser = JFileChooser()
            fileChooser.dialogTitle = "保存月报"
            fileChooser.selectedFile = File(fileName)

            if (fileChooser.showSaveDialog(tabbedPane) == JFileChooser.APPROVE_OPTION) {
                val file = fileChooser.selectedFile
                file.writeText(reportContent)

                val result = JOptionPane.showConfirmDialog(
                    tabbedPane,
                    "月报已导出到: ${file.absolutePath}\n\n是否打开文件?",
                    "导出成功",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.INFORMATION_MESSAGE
                )

                if (result == JOptionPane.YES_OPTION && Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(file)
                }
            }
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                tabbedPane,
                "导出失败: ${e.message}",
                "错误",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    private fun exportYearlyReport(year: Int, aiSummary: String?) {
        try {
            val stats = statisticsService.getYearlyStatistics(year)
            val reportContent = formatYearlyReport(year, stats, aiSummary)

            val fileName = "${year}年度工作总结.md"
            val fileChooser = JFileChooser()
            fileChooser.dialogTitle = "保存年报"
            fileChooser.selectedFile = File(fileName)

            if (fileChooser.showSaveDialog(tabbedPane) == JFileChooser.APPROVE_OPTION) {
                val file = fileChooser.selectedFile
                file.writeText(reportContent)

                val result = JOptionPane.showConfirmDialog(
                    tabbedPane,
                    "年报已导出到: ${file.absolutePath}\n\n是否打开文件?",
                    "导出成功",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.INFORMATION_MESSAGE
                )

                if (result == JOptionPane.YES_OPTION && Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(file)
                }
            }
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                tabbedPane,
                "导出失败: ${e.message}",
                "错误",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    // 自动保存报告的方法
    private suspend fun autoSaveWeeklyReport(startDate: LocalDate, endDate: LocalDate, aiSummary: String) {
        withContext(Dispatchers.IO) {
            try {
                val settings = AppSettingsState.getInstance()
                val storageLocation = settings.storageLocation
                val projectBasePath = project.basePath ?: return@withContext

                // 创建reports目录
                val reportsDir = File(projectBasePath, "$storageLocation/reports")
                if (!reportsDir.exists()) {
                    reportsDir.mkdirs()
                }

                // 生成文件名
                val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
                val fileName = "${startDate.format(formatter)}-${endDate.format(formatter)}工作周报.md"
                val file = File(reportsDir, fileName)

                // 生成报告内容
                val stats = statisticsService.getStatistics(startDate, endDate)
                val content = formatWeeklyReport(startDate, endDate, stats, aiSummary)

                // 写入文件
                file.writeText(content)
            } catch (e: Exception) {
                // 静默失败，不影响UI显示
                e.printStackTrace()
            }
        }
    }

    private suspend fun autoSaveMonthlyReport(year: Int, month: Int, aiSummary: String) {
        withContext(Dispatchers.IO) {
            try {
                val settings = AppSettingsState.getInstance()
                val storageLocation = settings.storageLocation
                val projectBasePath = project.basePath ?: return@withContext

                // 创建reports目录
                val reportsDir = File(projectBasePath, "$storageLocation/reports")
                if (!reportsDir.exists()) {
                    reportsDir.mkdirs()
                }

                // 生成文件名
                val fileName = "${year}年${month}月工作月报.md"
                val file = File(reportsDir, fileName)

                // 生成报告内容
                val startDate = LocalDate.of(year, month, 1)
                val endDate = startDate.with(TemporalAdjusters.lastDayOfMonth())
                val stats = statisticsService.getStatistics(startDate, endDate)
                val content = formatMonthlyReport(year, month, stats, aiSummary)

                // 写入文件
                file.writeText(content)
            } catch (e: Exception) {
                // 静默失败，不影响UI显示
                e.printStackTrace()
            }
        }
    }

    private suspend fun autoSaveYearlyReport(year: Int, aiSummary: String) {
        withContext(Dispatchers.IO) {
            try {
                val settings = AppSettingsState.getInstance()
                val storageLocation = settings.storageLocation
                val projectBasePath = project.basePath ?: return@withContext

                // 创建reports目录
                val reportsDir = File(projectBasePath, "$storageLocation/reports")
                if (!reportsDir.exists()) {
                    reportsDir.mkdirs()
                }

                // 生成文件名
                val fileName = "${year}年度工作总结.md"
                val file = File(reportsDir, fileName)

                // 生成报告内容
                val stats = statisticsService.getYearlyStatistics(year)
                val content = formatYearlyReport(year, stats, aiSummary)

                // 写入文件
                file.writeText(content)
            } catch (e: Exception) {
                // 静默失败，不影响UI显示
                e.printStackTrace()
            }
        }
    }

    override fun createActions(): Array<Action> {
        return arrayOf(okAction)
    }
}
