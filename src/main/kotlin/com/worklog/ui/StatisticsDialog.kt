package com.worklog.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.worklog.services.StatisticsService
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.swing.*

/**
 * 统计信息对话框
 */
class StatisticsDialog(private val project: Project) : DialogWrapper(project) {

    private val statisticsService = project.getService(StatisticsService::class.java)
    private val tabbedPane = JBTabbedPane()

    init {
        title = "工作统计"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(800, 600)

        // 创建标签页
        tabbedPane.addTab("本周统计", createWeeklyPanel())
        tabbedPane.addTab("本月统计", createMonthlyPanel())
        tabbedPane.addTab("年度统计", createYearlyPanel())
        tabbedPane.addTab("自定义范围", createCustomRangePanel())

        panel.add(tabbedPane, BorderLayout.CENTER)
        return panel
    }

    private fun createWeeklyPanel(): JComponent {
        val stats = statisticsService.getWeeklyStatistics()
        return createStatisticsPanel(stats.toString(), "本周工作统计")
    }

    private fun createMonthlyPanel(): JComponent {
        val stats = statisticsService.getMonthlyStatistics()
        return createStatisticsPanel(formatStatistics(stats), "本月工作统计")
    }

    private fun createYearlyPanel(): JComponent {
        val panel = JPanel(BorderLayout())

        val topPanel = JPanel()
        topPanel.add(JBLabel("选择年份: "))
        val yearField = JTextField(10)
        yearField.text = LocalDate.now().year.toString()
        topPanel.add(yearField)

        val queryButton = JButton("查询")
        val resultArea = JTextArea()
        resultArea.isEditable = false
        resultArea.font = Font("Monospaced", Font.PLAIN, 12)

        queryButton.addActionListener {
            try {
                val year = yearField.text.toInt()
                val stats = statisticsService.getYearlyStatistics(year)
                resultArea.text = formatStatistics(stats)
            } catch (e: Exception) {
                resultArea.text = "年份格式错误或查询失败: ${e.message}"
            }
        }
        topPanel.add(queryButton)

        // 默认加载当前年度数据
        val currentYearStats = statisticsService.getYearlyStatistics(LocalDate.now().year)
        resultArea.text = formatStatistics(currentYearStats)

        panel.add(topPanel, BorderLayout.NORTH)
        panel.add(JBScrollPane(resultArea), BorderLayout.CENTER)

        return panel
    }

    private fun createCustomRangePanel(): JComponent {
        val panel = JPanel(BorderLayout())

        val topPanel = JPanel()
        topPanel.add(JBLabel("开始日期: "))
        val startDateField = JTextField(10)
        startDateField.text = LocalDate.now().minusDays(30).toString()
        topPanel.add(startDateField)

        topPanel.add(JBLabel("  结束日期: "))
        val endDateField = JTextField(10)
        endDateField.text = LocalDate.now().toString()
        topPanel.add(endDateField)

        val queryButton = JButton("查询")
        val resultArea = JTextArea()
        resultArea.isEditable = false
        resultArea.font = Font("Monospaced", Font.PLAIN, 12)

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
        panel.add(JBScrollPane(resultArea), BorderLayout.CENTER)

        return panel
    }

    private fun createStatisticsPanel(content: String, title: String): JComponent {
        val panel = JPanel(BorderLayout())

        val textArea = JTextArea(content)
        textArea.isEditable = false
        textArea.font = Font("Monospaced", Font.PLAIN, 12)

        val scrollPane = JBScrollPane(textArea)
        panel.add(scrollPane, BorderLayout.CENTER)

        return panel
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

    override fun createActions(): Array<Action> {
        return arrayOf(okAction)
    }
}
