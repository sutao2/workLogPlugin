package com.worklog.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.worklog.services.StatisticsService
import java.time.LocalDate
import javax.swing.JFileChooser

/**
 * 生成年报的 Action
 */
class GenerateYearlyReportAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val statisticsService = project.getService(StatisticsService::class.java)

        // 获取当前年份
        val currentYear = LocalDate.now().year

        // 询问用户选择年份
        val yearInput = Messages.showInputDialog(
            project,
            "请输入要生成年报的年份：",
            "生成年报",
            Messages.getQuestionIcon(),
            currentYear.toString(),
            null
        ) ?: return

        val year = try {
            yearInput.toInt()
        } catch (e: NumberFormatException) {
            Messages.showErrorDialog(project, "无效的年份", "错误")
            return
        }

        // 生成年报
        val yearlyReport = statisticsService.generateYearlyReport(year)

        if (yearlyReport.workLogs.isEmpty()) {
            Messages.showInfoMessage(
                project,
                "${year}年没有工作日志记录",
                "提示"
            )
            return
        }

        // 显示预览对话框
        val result = Messages.showYesNoDialog(
            project,
            "年报已生成，是否导出？\n\n" +
            "工作天数: ${yearlyReport.totalWorkDays}\n" +
            "提交次数: ${yearlyReport.totalCommits}\n" +
            "平均每日: %.1f 次".format(yearlyReport.averageCommitsPerDay),
            "年报生成成功",
            "导出",
            "取消",
            Messages.getInformationIcon()
        )

        if (result == Messages.YES) {
            // 选择保存位置
            val fileChooser = JFileChooser()
            fileChooser.dialogTitle = "选择年报保存位置"
            fileChooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY

            if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                try {
                    val outputDir = fileChooser.selectedFile.toPath()
                    val fileName = "yearly_report_${year}.md"
                    val outputFile = outputDir.resolve(fileName).toFile()
                    outputFile.writeText(yearlyReport.summary)

                    Messages.showInfoMessage(
                        project,
                        "年报已导出至: ${outputFile.absolutePath}",
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
