package io.github.jieaccount.taskwidget.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class TaskView(val label: String) { DATED("有日期"), UNDATED("无日期"), DONE("已完成") }

data class TaskTable(val id: Long, val name: String)

/** 日期与时分分开保存，保留“仅日期”和“主动设置 00:00”的区别。 */
data class TaskItem(
    val id: Long,
    val tableId: Long,
    val title: String,
    val description: String = "",
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val done: Boolean = false,
    val createdOrder: Long,
    val completedOrder: Long? = null,
    val manualOrder: Long = 0,
) {
    init { require(date != null || time == null) }
}

data class TaskSnapshot(val tables: List<TaskTable>, val tasks: List<TaskItem>, val sequence: Long)
data class WidgetSelection(val tableId: Long, val view: TaskView = TaskView.DATED, val page: Int = 0)

/** App 与组件共用归类、排序和过期规则，避免两个入口各自解释日期。 */
object TaskRules {
    fun viewOf(task: TaskItem): TaskView = when {
        task.done -> TaskView.DONE
        task.date != null -> TaskView.DATED
        else -> TaskView.UNDATED
    }

    fun visible(tasks: List<TaskItem>, tableId: Long, view: TaskView): List<TaskItem> {
        val matching = tasks.filter { it.tableId == tableId && viewOf(it) == view }
        return when (view) {
            TaskView.DATED -> matching.sortedWith(
                compareBy<TaskItem> { it.date }
                    .thenBy { it.time == null }
                    .thenBy { it.time }
                    .thenByDescending { it.createdOrder }
                    .thenBy { it.id }
            )
            TaskView.UNDATED -> matching.sortedWith(compareBy<TaskItem> { it.manualOrder }.thenBy { it.id })
            TaskView.DONE -> matching.sortedWith(compareByDescending<TaskItem> { it.completedOrder }.thenBy { it.id })
        }
    }

    fun overdue(task: TaskItem, now: LocalDateTime): Boolean {
        val date = task.date ?: return false
        if (task.done) return false
        return task.time?.let { now.isAfter(date.atTime(it)) } ?: now.toLocalDate().isAfter(date)
    }

    fun nextTopOrder(tasks: List<TaskItem>, tableId: Long): Long =
        (visible(tasks, tableId, TaskView.UNDATED).minOfOrNull { it.manualOrder } ?: 0L) - 1L

    fun cancelDate(task: TaskItem, tasks: List<TaskItem>): TaskItem = task.copy(
        date = null, time = null,
        manualOrder = if (task.done) task.manualOrder else nextTopOrder(tasks, task.tableId),
    )

    fun restore(task: TaskItem, tasks: List<TaskItem>): TaskItem = task.copy(
        done = false, completedOrder = null,
        manualOrder = if (task.date == null) nextTopOrder(tasks, task.tableId) else task.manualOrder,
    )

    /** 表标签翻页只改变可见标签；选择表另由点击动作提交。 */
    fun pageCount(tableCount: Int): Int = ((tableCount + 2) / 3).coerceAtLeast(1)
}
