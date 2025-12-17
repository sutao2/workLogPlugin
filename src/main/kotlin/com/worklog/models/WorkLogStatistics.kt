package com.worklog.models

import java.time.LocalDate

/**
 * 工作日志统计数据
 */
data class WorkLogStatistics(
    val totalDays: Int,
    val totalCommits: Int,
    val totalFiles: Int,
    val averageCommitsPerDay: Double,
    val mostProductiveDay: LocalDate?,
    val mostProductiveDayCommits: Int,
    val commitsByDate: Map<LocalDate, Int>,
    val commitsByWeekday: Map<String, Int>
)

/**
 * 周报数据
 */
data class WeeklyReport(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val workLogs: List<WorkLog>,
    val totalCommits: Int,
    val totalFiles: Int,
    val summary: String
)

/**
 * 月报数据
 */
data class MonthlyReport(
    val year: Int,
    val month: Int,
    val workLogs: List<WorkLog>,
    val totalCommits: Int,
    val totalFiles: Int,
    val summary: String
)

/**
 * 年报数据
 */
data class YearlyReport(
    val year: Int,
    val workLogs: List<WorkLog>,
    val totalCommits: Int,
    val totalFiles: Int,
    val totalWorkDays: Int,
    val averageCommitsPerDay: Double,
    val mostProductiveMonth: Int?,
    val mostProductiveMonthCommits: Int,
    val commitsByMonth: Map<Int, Int>,
    val summary: String
)
