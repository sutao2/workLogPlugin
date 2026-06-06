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
            return false
        }

        // 使用 CAS 操作确保原子性
        if (!isShowingDialog.compareAndSet(false, true)) {
            return false
        }

        return true
    }

    /**
     * 释放对话框显示权限
     */
    fun releaseDialogLock() {
        markDialogShown()
        isShowingDialog.compareAndSet(true, false)
    }

    fun releaseDialogLockWithoutCooldown() {
        isShowingDialog.compareAndSet(true, false)
    }

    fun markDialogShown() {
        lastDialogTimestamp.set(System.currentTimeMillis())
    }

    /**
     * 在 lock 保护下执行操作，确保异常时也释放锁
     */
    inline fun withDialogLock(action: () -> Unit): Boolean {
        if (!tryAcquireDialogLock()) return false
        try {
            action()
        } finally {
            releaseDialogLock()
        }
        return true
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
    }
}
