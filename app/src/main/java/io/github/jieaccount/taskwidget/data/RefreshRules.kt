package io.github.jieaccount.taskwidget.data

import java.time.Instant
import java.time.ZonedDateTime

/** 计算下一次颜色或相对日期可能变化的时刻；平台调度延迟由实际宿主验证。 */
object RefreshRules {
    fun nextBoundary(tasks: List<TaskItem>, now: ZonedDateTime, relativeDates: Boolean = false): Instant? {
        val taskBoundary = tasks.asSequence()
            .filter { !it.done && it.date != null }
            .map { task ->
                if (task.time == null) task.date!!.plusDays(1).atStartOfDay(now.zone).toInstant()
                // 定时任务在严格超过指定时刻时过期；毫秒边界适配 AlarmManager。
                else task.date!!.atTime(task.time).atZone(now.zone).toInstant().plusMillis(1)
            }.filter { it.isAfter(now.toInstant()) }.minOrNull()
        // 即使所有任务均已过期，相对日期仍需在下一个自然日更新。
        val midnight = if (relativeDates && tasks.any { !it.done && it.date != null })
            now.toLocalDate().plusDays(1).atStartOfDay(now.zone).toInstant() else null
        return listOfNotNull(taskBoundary, midnight).minOrNull()
    }
}
