package io.github.jieaccount.taskwidget.data

import java.time.Instant
import java.time.ZonedDateTime

/** 计算下一次颜色可能变化的时刻；平台调度延迟由实际宿主验证。 */
object RefreshRules {
    fun nextBoundary(tasks: List<TaskItem>, now: ZonedDateTime): Instant? = tasks.asSequence()
        .filter { !it.done && it.date != null }
        .map { task ->
            if (task.time == null) task.date!!.plusDays(1).atStartOfDay(now.zone).toInstant()
            // 定时任务在严格超过指定时刻时过期；毫秒边界适配 AlarmManager。
            else task.date!!.atTime(task.time).atZone(now.zone).toInstant().plusMillis(1)
        }.filter { it.isAfter(now.toInstant()) }.minOrNull()
}
