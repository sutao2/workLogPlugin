package com.worklog.listeners

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 关闭提醒状态管理器
 * 用于防止ProjectCloseListener和AppCloseListener在同一次关闭流程中重复显示对话框
 */
object CloseReminderState {
    private val isShowingDialog = AtomicBoolean(false)
    private val lastDialogTimestamp = AtomicLong(0L)

    // 增加时间窗口到 60 秒，避免用户等待时重复弹窗
    private const val DIALOG_COOLDOWN_MS = 60_000L

    /**
     * 尝试获取显示对话框的权限
     * @return true 如果可以显示对话框，false 如果对话框正在显示或刚刚显示过
     */
    fun tryAcquireDialogLock(): Boolean {
        val now = System.currentTimeMillis()

        // 检查是否在冷却期内（60秒内显示过对话框）
        val lastTime = lastDialogTimestamp.get()
        if (now - lastTime < DIALOG_COOLDOWN_MS) {
            println("WorkLog: 对话框在冷却期内，跳过显示 (距上次: ${(now - lastTime) / 1000}秒)")
            return false
        }

        // 使用 CAS 操作确保原子性
        if (!isShowingDialog.compareAndSet(false, true)) {
            println("WorkLog: 对话框正在显示中，跳过")
            return false
        }

        // 成功获取锁，更新时间戳
        lastDialogTimestamp.set(now)
        println("WorkLog: 已获取对话框显示权限")
        return true
    }

    /**
     * 释放对话框显示权限
     */
    fun releaseDialogLock() {
        val released = isShowingDialog.compareAndSet(true, false)
        if (released) {
            println("WorkLog: 已释放对话框显示权限")
        }
    }

    /**
     * 检查对话框是否正在显示
     */
    fun isDialogShowing(): Boolean {
        return isShowingDialog.get()
    }

    /**
     * 强制重置状态（仅用于测试或异常恢复）
     */
    fun forceReset() {
        isShowingDialog.set(false)
        lastDialogTimestamp.set(0L)
        println("WorkLog: 状态已强制重置")
    }
}
