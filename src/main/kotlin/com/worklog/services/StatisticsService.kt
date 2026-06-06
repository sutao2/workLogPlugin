package com.worklog.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.worklog.models.MonthlyReport
import com.worklog.models.WeeklyReport
import com.worklog.models.WorkLog
import com.worklog.models.WorkLogStatistics
import com.worklog.models.YearlyReport
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.*

/**
 * 统计服务
 * 提供工作日志的统计分析功能
 */
@Service(Service.Level.PROJECT)
class StatisticsService(private val project: Project) {

    private val workLogService = project.getService(WorkLogService::class.java)

    /**
     * 获取指定日期范围的统计数据
     */
    fun getStatistics(startDate: LocalDate, endDate: LocalDate): WorkLogStatistics {
        val dates = workLogService.getAllWorkLogDates()
            .filter { it in startDate..endDate }

        val workLogs = dates.mapNotNull { workLogService.loadWorkLog(it) }

        val totalCommits = workLogs.sumOf { it.gitCommits.size }
        val totalFiles = workLogs.flatMap { it.gitCommits.flatMap { commit -> commit.files } }.toSet().size

        val commitsByDate = workLogs.associate {
            it.date to it.gitCommits.size
        }

        val mostProductiveEntry = commitsByDate.maxByOrNull { it.value }

        val commitsByWeekday = workLogs.groupBy {
            it.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINESE)
        }.mapValues { entry ->
            entry.value.sumOf { it.gitCommits.size }
        }

        return WorkLogStatistics(
            totalDays = dates.size,
            totalCommits = totalCommits,
            totalFiles = totalFiles,
            averageCommitsPerDay = if (dates.isNotEmpty()) totalCommits.toDouble() / dates.size else 0.0,
            mostProductiveDay = mostProductiveEntry?.key,
            mostProductiveDayCommits = mostProductiveEntry?.value ?: 0,
            commitsByDate = commitsByDate,
            commitsByWeekday = commitsByWeekday
        )
    }

    /**
     * 获取本周统计
     */
    fun getWeeklyStatistics(): WorkLogStatistics {
        val today = LocalDate.now()
        val startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val endOfWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        return getStatistics(startOfWeek, endOfWeek)
    }

    /**
     * 获取本月统计
     */
    fun getMonthlyStatistics(): WorkLogStatistics {
        val today = LocalDate.now()
        val startOfMonth = today.withDayOfMonth(1)
        val endOfMonth = today.with(TemporalAdjusters.lastDayOfMonth())
        return getStatistics(startOfMonth, endOfMonth)
    }

    /**
     * 生成周报
     */
    fun generateWeeklyReport(startDate: LocalDate): WeeklyReport {
        val endDate = startDate.plusDays(6)
        val dates = workLogService.getAllWorkLogDates()
            .filter { it in startDate..endDate }

        val workLogs = dates.mapNotNull { workLogService.loadWorkLog(it) }
        val totalCommits = workLogs.sumOf { it.gitCommits.size }
        val totalFiles = workLogs.flatMap { it.gitCommits.flatMap { commit -> commit.files } }.toSet().size

        val summary = buildString {
            appendLine("本周工作概览：")
            appendLine("- 工作天数：${workLogs.size} 天")
            appendLine("- 提交次数：$totalCommits 次")
            appendLine("- 涉及文件：$totalFiles 个")
            appendLine()
            appendLine("每日工作摘要：")
            workLogs.forEach { log ->
                appendLine("## ${log.date}")
                appendLine("提交 ${log.gitCommits.size} 次")
                if (log.gitCommits.isNotEmpty()) {
                    log.gitCommits.take(3).forEach { commit ->
                        appendLine("- ${commit.message}")
                    }
                }
                appendLine()
            }
        }

        return WeeklyReport(
            startDate = startDate,
            endDate = endDate,
            workLogs = workLogs,
            totalCommits = totalCommits,
            totalFiles = totalFiles,
            summary = summary
        )
    }

    /**
     * 生成月报
     */
    fun generateMonthlyReport(year: Int, month: Int): MonthlyReport {
        val startDate = LocalDate.of(year, month, 1)
        val endDate = startDate.with(TemporalAdjusters.lastDayOfMonth())

        val dates = workLogService.getAllWorkLogDates()
            .filter { it in startDate..endDate }

        val workLogs = dates.mapNotNull { workLogService.loadWorkLog(it) }
        val totalCommits = workLogs.sumOf { it.gitCommits.size }
        val totalFiles = workLogs.flatMap { it.gitCommits.flatMap { commit -> commit.files } }.toSet().size

        val summary = buildString {
            appendLine("${year}年${month}月工作总结")
            appendLine()
            appendLine("## 工作概览")
            appendLine("- 工作天数：${workLogs.size} 天")
            appendLine("- 提交次数：$totalCommits 次")
            appendLine("- 涉及文件：$totalFiles 个")
            appendLine("- 平均每日提交：%.1f 次".format(if (workLogs.isNotEmpty()) totalCommits.toDouble() / workLogs.size else 0.0))
            appendLine()
            appendLine("## 本月工作明细")
            workLogs.forEach { log ->
                appendLine("### ${log.date}")
                appendLine("提交 ${log.gitCommits.size} 次")
                if (log.gitCommits.isNotEmpty()) {
                    log.gitCommits.forEach { commit ->
                        appendLine("- ${commit.message}")
                    }
                }
                appendLine()
            }
        }

        return MonthlyReport(
            year = year,
            month = month,
            workLogs = workLogs,
            totalCommits = totalCommits,
            totalFiles = totalFiles,
            summary = summary
        )
    }

    /**
     * 获取年度统计
     */
    fun getYearlyStatistics(year: Int): WorkLogStatistics {
        val startDate = LocalDate.of(year, 1, 1)
        val endDate = LocalDate.of(year, 12, 31)
        return getStatistics(startDate, endDate)
    }

    /**
     * 生成年报
     */
    fun generateYearlyReport(year: Int): YearlyReport {
        val startDate = LocalDate.of(year, 1, 1)
        val endDate = LocalDate.of(year, 12, 31)

        val dates = workLogService.getAllWorkLogDates()
            .filter { it.year == year }

        val workLogs = dates.mapNotNull { workLogService.loadWorkLog(it) }
        val totalCommits = workLogs.sumOf { it.gitCommits.size }
        val totalFiles = workLogs.flatMap { it.gitCommits.flatMap { commit -> commit.files } }.toSet().size

        // 按月统计
        val commitsByMonth = workLogs.groupBy { it.date.monthValue }
            .mapValues { entry -> entry.value.sumOf { it.gitCommits.size } }

        val mostProductiveMonthEntry = commitsByMonth.maxByOrNull { it.value }

        val summary = buildString {
            appendLine("# ${year}年度工作总结")
            appendLine()
            appendLine("## 年度概览")
            appendLine("- 工作天数：${workLogs.size} 天")
            appendLine("- 总提交数：$totalCommits 次")
            appendLine("- 涉及文件：$totalFiles 个")
            appendLine("- 平均每日提交：%.1f 次".format(if (workLogs.isNotEmpty()) totalCommits.toDouble() / workLogs.size else 0.0))
            if (mostProductiveMonthEntry != null) {
                appendLine("- 最高产月份：${mostProductiveMonthEntry.key}月 (${mostProductiveMonthEntry.value} 次提交)")
            }
            appendLine()

            appendLine("## 月度统计")
            (1..12).forEach { month ->
                val monthCommits = commitsByMonth[month] ?: 0
                val monthDays = workLogs.count { it.date.monthValue == month }
                if (monthDays > 0) {
                    appendLine("### ${month}月")
                    appendLine("- 工作天数：$monthDays 天")
                    appendLine("- 提交次数：$monthCommits 次")
                    appendLine("- 平均每日：%.1f 次".format(monthCommits.toDouble() / monthDays))
                    appendLine()
                }
            }

            appendLine("## 年度亮点")
            val topDays = workLogs.sortedByDescending { it.gitCommits.size }.take(5)
            topDays.forEachIndexed { index, log ->
                appendLine("${index + 1}. ${log.date} - ${log.gitCommits.size} 次提交")
            }
            appendLine()

            appendLine("## 工作趋势")
            val quarters = workLogs.groupBy { (it.date.monthValue - 1) / 3 + 1 }
            quarters.forEach { (quarter, logs) ->
                val quarterCommits = logs.sumOf { it.gitCommits.size }
                appendLine("第${quarter}季度：${logs.size} 工作日，$quarterCommits 次提交")
            }
        }

        return YearlyReport(
            year = year,
            workLogs = workLogs,
            totalCommits = totalCommits,
            totalFiles = totalFiles,
            totalWorkDays = workLogs.size,
            averageCommitsPerDay = if (workLogs.isNotEmpty()) totalCommits.toDouble() / workLogs.size else 0.0,
            mostProductiveMonth = mostProductiveMonthEntry?.key,
            mostProductiveMonthCommits = mostProductiveMonthEntry?.value ?: 0,
            commitsByMonth = commitsByMonth,
            summary = summary
        )
    }
}
