package com.worklog.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.worklog.services.StatisticsService
import java.time.LocalDate
import javax.swing.JFileChooser

/**
 * 生成月报的 Action
 */
class GenerateMonthlyReportAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val statisticsService = project.getService(StatisticsService::class.java)

        // 获取本月
        val today = LocalDate.now()
        val year = today.year
        val month = today.monthValue

        // 生成月报
        val monthlyReport = statisticsService.generateMonthlyReport(year, month)

        // 显示预览对话框
        val result = Messages.showYesNoDialog(
            project,
            "月报已生成，是否导出？\n\n工作天数: ${monthlyReport.workLogs.size}\n提交次数: ${monthlyReport.totalCommits}",
            "月报生成成功",
            "导出",
            "取消",
            Messages.getInformationIcon()
        )

        if (result == Messages.YES) {
            // 选择保存位置
            val fileChooser = JFileChooser()
            fileChooser.dialogTitle = "选择月报保存位置"
            fileChooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY

            if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                try {
                    val outputDir = fileChooser.selectedFile.toPath()
                    val fileName = "monthly_report_${year}_${month.toString().padStart(2, '0')}.md"
                    val outputFile = outputDir.resolve(fileName).toFile()
                    outputFile.writeText(monthlyReport.summary)

                    Messages.showInfoMessage(
                        project,
                        "月报已导出至: ${outputFile.absolutePath}",
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
