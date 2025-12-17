package com.worklog.listeners

/**
 * 关闭提醒状态管理器
 * 用于防止ProjectCloseListener和AppCloseListener在同一次关闭流程中重复显示对话框
 */
object CloseReminderState {
    @Volatile
    private var isShowingDialog = false

    @Volatile
    private var lastDialogTimestamp = 0L

    /**
     * 尝试获取显示对话框的权限
     * @return true 如果可以显示对话框，false 如果对话框正在显示或刚刚显示过
     */
    fun tryAcquireDialogLock(): Boolean {
        synchronized(this) {
            val now = System.currentTimeMillis()

            // 如果对话框正在显示，返回false
            if (isShowingDialog) {
                return false
            }

            // 如果5秒内显示过对话框，认为是同一次关闭流程，返回false
            // 这样可以防止：关闭多个项目时每个项目都提示，或者项目关闭后IDE关闭又提示
            if (now - lastDialogTimestamp < 5000) {
                return false
            }

            isShowingDialog = true
            lastDialogTimestamp = now
            return true
        }
    }

    /**
     * 释放对话框显示权限
     */
    fun releaseDialogLock() {
        synchronized(this) {
            isShowingDialog = false
        }
    }

    /**
     * 检查对话框是否正在显示
     */
    fun isDialogShowing(): Boolean {
        return isShowingDialog
    }
}
