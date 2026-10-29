package io.github.jieaccount.taskwidget.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import io.github.jieaccount.taskwidget.data.RefreshRules
import io.github.jieaccount.taskwidget.data.TaskStore
import java.time.ZonedDateTime

/** 每次更新后只保留最近一个边界闹钟，不申请精确闹钟权限。 */
object WidgetRefreshScheduler {
    const val ACTION = "io.github.jieaccount.taskwidget.REFRESH_BOUNDARY"
    fun schedule(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(context, 0, Intent(context, TaskWidgetProvider::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarm.cancel(pending)
        val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, TaskWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val next = RefreshRules.nextBoundary(TaskStore.load(context).tasks, ZonedDateTime.now()) ?: return
        // 系统允许唤醒，但省电模式和厂商后台策略仍可能推迟执行。
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toEpochMilli(), pending)
    }
}
