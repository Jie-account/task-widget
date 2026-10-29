package io.github.jieaccount.taskwidget.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class TaskRulesTest {
    private val day = LocalDate.of(2026, 10, 29)
    private fun task(id: Long, date: LocalDate? = null, time: LocalTime? = null, done: Boolean = false, manual: Long = 0) =
        TaskItem(id, 1, "任务$id", date = date, time = time, done = done, createdOrder = id, completedOrder = if (done) id else null, manualOrder = manual)

    @Test fun completedStatusWinsOverDate() {
        assertEquals(TaskView.DONE, TaskRules.viewOf(task(1, day, done = true)))
        assertEquals(TaskView.DONE, TaskRules.viewOf(task(2, done = true)))
    }
    @Test fun unfinishedTasksSplitByDate() {
        assertEquals(TaskView.DATED, TaskRules.viewOf(task(1, day)))
        assertEquals(TaskView.UNDATED, TaskRules.viewOf(task(2)))
    }
    @Test fun sameDayTimedTasksPrecedeDateOnlyAndNewerTiesWin() {
        val tasks = listOf(task(1, day), task(2, day, LocalTime.of(12, 0)), task(3, day, LocalTime.of(9, 0)), task(4, day, LocalTime.of(9, 0)), task(5, day.minusDays(1)))
        assertEquals(listOf(5L, 4L, 3L, 2L, 1L), TaskRules.visible(tasks, 1, TaskView.DATED).map { it.id })
    }
    @Test fun midnightAndDateOnlyAreDifferent() {
        val now = day.atTime(0, 1)
        assertTrue(TaskRules.overdue(task(1, day, LocalTime.MIDNIGHT), now))
        assertFalse(TaskRules.overdue(task(2, day), now))
    }
    @Test fun dateOnlyExpiresAtNextDayAcrossYear() {
        val date = LocalDate.of(2026, 12, 31)
        assertFalse(TaskRules.overdue(task(1, date), date.atTime(23, 59)))
        assertTrue(TaskRules.overdue(task(1, date), LocalDateTime.of(2027, 1, 1, 0, 0)))
    }
    @Test fun timedTaskExpiresStrictlyAfterItsMinute() {
        val item = task(1, day, LocalTime.of(9, 30))
        assertFalse(TaskRules.overdue(item, day.atTime(9, 30)))
        assertTrue(TaskRules.overdue(item, day.atTime(9, 30, 1)))
    }
    @Test fun completedTasksNeverUseOverdueColor() {
        assertFalse(TaskRules.overdue(task(1, day.minusDays(1), done = true), day.atStartOfDay()))
    }
    @Test fun cancellingDateInsertsAtTopWithoutChangingOtherOrder() {
        val original = listOf(task(1, manual = 4), task(2, manual = -3), task(3, day, LocalTime.NOON))
        val changed = TaskRules.cancelDate(original[2], original)
        assertNull(changed.date); assertNull(changed.time)
        assertEquals(listOf(3L, 2L, 1L), TaskRules.visible(original.take(2) + changed, 1, TaskView.UNDATED).map { it.id })
    }
    @Test fun restoringRetainsDateAndPrecision() {
        val original = task(1, day, LocalTime.MIDNIGHT, done = true)
        val changed = TaskRules.restore(original, listOf(original))
        assertEquals(day, changed.date); assertEquals(LocalTime.MIDNIGHT, changed.time)
        assertEquals(TaskView.DATED, TaskRules.viewOf(changed)); assertNull(changed.completedOrder)
    }
    @Test fun restoringUndatedTaskInsertsAtTop() {
        val tasks = listOf(task(1, manual = -20), task(2, done = true))
        val restored = TaskRules.restore(tasks[1], tasks)
        assertEquals(listOf(2L, 1L), TaskRules.visible(listOf(tasks[0], restored), 1, TaskView.UNDATED).map { it.id })
    }
    @Test fun completedListUsesCompletionOrderAndFiltersTable() {
        val tasks = listOf(task(1, done = true).copy(completedOrder = 30), task(2, done = true).copy(completedOrder = 20), task(3, done = true).copy(tableId = 2))
        assertEquals(listOf(1L, 2L), TaskRules.visible(tasks, 1, TaskView.DONE).map { it.id })
    }
    @Test fun lastLabelPageAllowsPartialPage() {
        assertEquals(1, TaskRules.pageCount(1)); assertEquals(1, TaskRules.pageCount(3))
        assertEquals(2, TaskRules.pageCount(4)); assertEquals(3, TaskRules.pageCount(7))
    }
    @Test(expected = IllegalArgumentException::class) fun timeRequiresDate() {
        task(1, time = LocalTime.NOON)
    }
}
