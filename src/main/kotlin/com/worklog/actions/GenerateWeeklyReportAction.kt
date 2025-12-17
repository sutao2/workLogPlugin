package com.worklog.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.worklog.services.StatisticsService
import com.worklog.utils.ExportUtil
import com.worklog.utils.MarkdownUtil
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.swing.JFileChooser

/**
 * 生成周报的 Action
 */
class GenerateWeeklyReportAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val statisticsService = project.getService(StatisticsService::class.java)

        // 获取本周的开始日期（周一）
        val today = LocalDate.now()
        val startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        // 生成周报
        val weeklyReport = statisticsService.generateWeeklyReport(startOfWeek)

        // 显示预览对话框
        val result = Messages.showYesNoDialog(
            project,
            "周报已生成，是否导出？\n\n工作天数: ${weeklyReport.workLogs.size}\n提交次数: ${weeklyReport.totalCommits}",
            "周报生成成功",
            "导出",
            "取消",
            Messages.getInformationIcon()
        )

        if (result == Messages.YES) {
            // 选择保存位置
            val fileChooser = JFileChooser()
            fileChooser.dialogTitle = "选择周报保存位置"
            fileChooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY

            if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                try {
                    val outputDir = fileChooser.selectedFile.toPath()
                    val fileName = "weekly_report_${startOfWeek}.md"
                    val outputFile = outputDir.resolve(fileName).toFile()
                    outputFile.writeText(weeklyReport.summary)

                    Messages.showInfoMessage(
                        project,
                        "周报已导出至: ${outputFile.absolutePath}",
                        "导出成功"
                    )
                } catch (ex: Exception) {
                    Messages.showErrorDialog(
                        project,
                        "导出失败: ${ex.message}",
                        "错误"
                    )
                }
            }
        }
    }
}
