package io.github.jieaccount.taskwidget.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.os.BatteryManager
import android.os.PowerManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.jieaccount.taskwidget.data.RefreshRules
import io.github.jieaccount.taskwidget.data.TaskStore
import java.time.ZonedDateTime

/** 每次更新后只保留最近一个边界闹钟，不申请精确闹钟权限。 */
object WidgetRefreshScheduler {
    const val ACTION = "io.github.jieaccount.taskwidget.REFRESH_BOUNDARY"
    private fun diagnostics(context: Context) = context.getSharedPreferences("widget_refresh_diagnostics_v1", Context.MODE_PRIVATE)

    /** 留存最近一次边界与接收条件，断开 USB 的隔夜观察也能回读；不保存任务内容。 */
    fun recordReceive(context: Context) {
        val prefs = diagnostics(context)
        val now = System.currentTimeMillis()
        val power = context.getSystemService(PowerManager::class.java)
        prefs.edit().putLong("receivedBoundary", prefs.getLong("scheduledBoundary", 0))
            .putLong("receivedAt", now)
            .putBoolean("powerSave", power.isPowerSaveMode)
            .putBoolean("deviceIdle", power.isDeviceIdleMode)
            .putBoolean("interactive", power.isInteractive)
            .putBoolean("charging", context.getSystemService(BatteryManager::class.java).isCharging)
            .apply()
        Log.i("TaskWidget", "boundaryReceived=$now")
    }

    fun schedule(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(context, 0, Intent(context, TaskWidgetProvider::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarm.cancel(pending)
        diagnostics(context).edit().putLong("scheduledBoundary", 0).apply()
        val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, TaskWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val next = RefreshRules.nextBoundary(TaskStore.load(context).tasks, ZonedDateTime.now()) ?: return
        // 系统允许唤醒，但省电模式和厂商后台策略仍可能推迟执行。
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toEpochMilli(), pending)
        diagnostics(context).edit().putLong("scheduledBoundary", next.toEpochMilli()).apply()
        Log.i("TaskWidget", "scheduledBoundary=${next.toEpochMilli()} now=${System.currentTimeMillis()} widgets=${ids.size}")
    }
}
