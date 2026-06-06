package com.worklog.services

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.worklog.settings.AppSettingsState
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 提醒服务
 * 负责定时提醒用户填写工作日志
 */
@Service(Service.Level.PROJECT)
class ReminderService(private val project: Project) : Disposable {

    private var scheduler: ScheduledExecutorService? = null
    private val isRunning = AtomicBoolean(false)

    private val workLogService: WorkLogService
        get() = project.getService(WorkLogService::class.java)

    /**
     * 启动定时提醒
     */
    @Synchronized
    fun start() {
        stop()  // 先停止之前的任务

        val settings = AppSettingsState.getInstance()
        if (!settings.reminderEnabled) {
            return
        }

        isRunning.set(true)
        scheduler = Executors.newScheduledThreadPool(1)
        scheduleNextReminder()
    }

    /**
     * 停止定时提醒
     */
    @Synchronized
    fun stop() {
        isRunning.set(false)
        scheduler?.shutdown()
        try {
            // 等待任务完成，最多等待 2 秒
            if (scheduler?.awaitTermination(2, TimeUnit.SECONDS) == false) {
                scheduler?.shutdownNow()
            }
        } catch (e: InterruptedException) {
            scheduler?.shutdownNow()
            Thread.currentThread().interrupt()
        }
        scheduler = null
    }

    /**
     * 调度下一次提醒
     */
    private fun scheduleNextReminder() {
        // 检查服务是否仍在运行
        if (!isRunning.get()) {
            return
        }

        val currentScheduler = scheduler
        if (currentScheduler == null || currentScheduler.isShutdown) {
            return
        }

        val settings = AppSettingsState.getInstance()
        val reminderTime = settings.getReminderLocalTime()

        val now = LocalDateTime.now()
        var nextReminder = LocalDateTime.of(LocalDate.now(), reminderTime)

        // 如果今天的提醒时间已过，调度到明天
        if (nextReminder.isBefore(now)) {
            nextReminder = nextReminder.plusDays(1)
        }

        val delay = java.time.Duration.between(now, nextReminder).toMillis()

        try {
            currentScheduler.schedule({
                if (isRunning.get()) {
                    showReminder()
                    // 调度下一次提醒（24小时后）
                    scheduleNextReminder()
                }
            }, delay, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            // scheduler 可能已被关闭，忽略异常
        }
    }

    /**
     * 显示提醒通知
     */
    fun showReminder() {
        // 检查今日是否已有日志
        if (workLogService.hasTodayWorkLog()) {
            return  // 已有日志，不再提醒
        }

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("WorkLog Notifications")
            .createNotification(
                "工作日志提醒",
                "今日工作日志尚未填写，点击此处快速生成。",
                NotificationType.INFORMATION
            )

        notification.addAction(object : com.intellij.openapi.actionSystem.AnAction("立即生成") {
            override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                // 触发生成日志的操作
                notification.expire()
                com.intellij.openapi.actionSystem.ActionManager.getInstance()
                    .getAction("com.worklog.actions.GenerateWorkLogAction")
                    ?.actionPerformed(e)
            }
        })

        notification.addAction(object : com.intellij.openapi.actionSystem.AnAction("稍后提醒") {
            override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                notification.expire()
            }
        })

        Notifications.Bus.notify(notification, project)
    }

    /**
     * 重新启动提醒（当设置更改时调用）
     */
    fun restart() {
        start()
    }

    override fun dispose() {
        stop()
    }
}
