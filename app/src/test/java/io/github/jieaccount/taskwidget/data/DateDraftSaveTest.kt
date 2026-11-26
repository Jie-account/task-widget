package io.github.jieaccount.taskwidget.data

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime

class DateDraftSaveTest {
    private class MemoryStorage(initial: TaskSnapshot) : SnapshotStorage {
        var raw = SnapshotCodec.encode(initial)
        var fail = false
        var writes = 0
        override fun read() = raw
        override fun write(value: String) {
            if (fail) throw IOException("写入失败")
            raw = value; writes++
        }
    }

    private fun snapshot() = TaskSnapshot(listOf(TaskTable(1, "示例表")), listOf(
        TaskItem(3, 1, "原标题", "原描述", LocalDate.of(2026, 11, 29), LocalTime.of(9, 30), createdOrder = 3),
        TaskItem(4, 1, "无日期甲", createdOrder = 4, manualOrder = 0),
        TaskItem(5, 1, "无日期乙", createdOrder = 5, manualOrder = 1)), 5)

    @Test fun savingDateClearCommitsOtherEditsTogetherAndInsertsAtTop() {
        val before = snapshot(); val disk = MemoryStorage(before); val repo = TaskRepository(disk)
        val edited = before.tasks.first().copy(title = "修改标题", description = "修改描述", date = null, time = null)
        assertEquals(before, repo.load())
        val saved = repo.saveTask(edited)
        assertEquals(1, disk.writes)
        assertNull(saved.date); assertNull(saved.time)
        assertEquals("修改标题", saved.title); assertEquals("修改描述", saved.description)
        assertEquals(listOf(3L, 4L, 5L), TaskRules.visible(repo.load().tasks, 1, TaskView.UNDATED).map { it.id })
        assertEquals(before.tasks.drop(1), repo.load().tasks.filter { it.id != 3L })
    }

    @Test fun failedSaveRetainsOriginalDateAndAllOtherFields() {
        val before = snapshot(); val disk = MemoryStorage(before); val repo = TaskRepository(disk)
        disk.fail = true
        assertThrows(IOException::class.java) { repo.saveTask(before.tasks.first().copy(title = "修改标题", date = null, time = null)) }
        assertEquals(before, repo.load())
    }

    @Test fun completedTaskDateClearKeepsCompletionMetadata() {
        val initial = snapshot()
        val task = initial.tasks.first().copy(done = true, completedOrder = 5)
        val before = initial.copy(tasks = listOf(task) + initial.tasks.drop(1))
        val repo = TaskRepository(MemoryStorage(before))
        val saved = repo.saveTask(task.copy(date = null, time = null))
        assertEquals(TaskView.DONE, TaskRules.viewOf(saved))
        assertEquals(task.completedOrder, saved.completedOrder)
        assertEquals(task.createdOrder, saved.createdOrder)
    }

    @Test fun invalidTitlePreventsDateClearFromBeingSavedSeparately() {
        val before = snapshot(); val disk = MemoryStorage(before); val repo = TaskRepository(disk)
        assertThrows(IllegalArgumentException::class.java) { repo.saveTask(before.tasks.first().copy(title = "", date = null, time = null)) }
        assertEquals(before, repo.load())
        assertEquals(0, disk.writes)
    }
}
