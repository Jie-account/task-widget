package io.github.jieaccount.taskwidget.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class TaskRepositoryTest {
    private class MemoryStorage(var raw: String? = null, val old: LegacyData? = null) : SnapshotStorage {
        var fail = false
        override fun read() = raw
        override fun write(value: String) { if (fail) throw IOException("写入失败"); raw = value }
        override fun legacy() = old
    }
    private val day = LocalDate.of(2026, 10, 29)
    private fun item(table: Long, title: String, date: LocalDate? = null) = TaskItem(0, table, title, date = date, createdOrder = 0)

    @Test fun freshInstallHasOneDefaultTableAndNoDemonstrationTasks() {
        val disk = MemoryStorage(); val repo = TaskRepository(disk)
        assertEquals(listOf(TaskTable(1, "默认")), repo.load().tables)
        assertTrue(repo.load().tasks.isEmpty())
        assertEquals(repo.load(), TaskRepository(disk).load())
        assertEquals(2, JSONObject(disk.raw!!).getInt("version"))
    }

    @Test fun migrationPreservesIdsPrecisionCompletionOrderAndWidgetChoice() {
        val original = TaskSnapshot(listOf(TaskTable(1, "默认"), TaskTable(2, "工作")), listOf(
            TaskItem(3, 2, "测试", "多行\n描述", day, LocalTime.MIDNIGHT, true, 3, 4, -7)), 10)
        val v1 = JSONObject(SnapshotCodec.encode(original)).put("version", 1).removeSelections()
        val choice = WidgetSelection(2, TaskView.DONE, 0)
        val disk = MemoryStorage(old = LegacyData(v1, mapOf(16 to choice)))
        val migrated = TaskRepository(disk).load()
        assertEquals(original.tasks, migrated.tasks)
        assertEquals(choice, migrated.selections[16])
        assertEquals(migrated, TaskRepository(disk).load())
    }

    @Test fun migratedStaleWidgetSelectionsFallBackAndClampPage() {
        val original = TaskSnapshot(listOf(TaskTable(1, "默认")), emptyList(), 1)
        val disk = MemoryStorage(old = LegacyData(JSONObject(SnapshotCodec.encode(original)).put("version", 1).removeSelections(),
            mapOf(7 to WidgetSelection(88, TaskView.UNDATED, 5))))
        assertEquals(WidgetSelection(1, TaskView.UNDATED, 0), TaskRepository(disk).selection(7))
    }

    @Test fun corruptOrFutureDataIsNotReplacedByEmptyDefaults() {
        for (raw in listOf("{broken", "{\"version\":99}")) {
            val disk = MemoryStorage(raw)
            assertThrows(Exception::class.java) { TaskRepository(disk).load() }
            assertEquals(raw, disk.raw)
        }
    }

    @Test fun failedMigrationDoesNotCreateNewSnapshot() {
        val disk = MemoryStorage(old = LegacyData("{broken", emptyMap()))
        assertThrows(Exception::class.java) { TaskRepository(disk).load() }
        assertNull(disk.raw)
    }

    @Test fun writeFailureKeepsPreviouslyCommittedTasksAndSelection() {
        val disk = MemoryStorage(); val repo = TaskRepository(disk)
        val first = repo.saveTask(item(1, "保留")); repo.select(16, WidgetSelection(1, TaskView.UNDATED))
        val before = repo.load(); disk.fail = true
        assertThrows(IOException::class.java) { repo.saveTask(first.copy(title = "未提交")) }
        assertThrows(IOException::class.java) { repo.select(16, WidgetSelection(1, TaskView.DONE)) }
        assertEquals(before, TaskRepository(disk).load())
    }

    @Test fun newTaskAndCancelDateInsertAtTopWithoutReorderingOthers() {
        val repo = TaskRepository(MemoryStorage())
        val a = repo.saveTask(item(1, "a")); val b = repo.saveTask(item(1, "b")); val c = repo.saveTask(item(1, "c", day))
        repo.reorder(1, listOf(a.id, b.id))
        repo.saveTask(c.copy(date = null))
        assertEquals(listOf(c.id, a.id, b.id), TaskRules.visible(repo.load().tasks, 1, TaskView.UNDATED).map { it.id })
    }

    @Test fun editCannotChangeStableMetadataOrCompletedStatus() {
        val repo = TaskRepository(MemoryStorage())
        val a = repo.saveTask(item(1, "a")); repo.act(a.id, "complete")
        val completed = repo.load().tasks.single()
        val edited = repo.saveTask(a.copy(title = " 编辑 ", done = false, createdOrder = 999))
        assertEquals("编辑", edited.title); assertTrue(edited.done)
        assertEquals(completed.completedOrder, edited.completedOrder)
        assertEquals(a.createdOrder, edited.createdOrder)
    }

    @Test fun completedDateCanBeRemovedWithoutLeavingCompletedView() {
        val repo = TaskRepository(MemoryStorage())
        val a = repo.saveTask(item(1, "a", day).copy(time = LocalTime.MIDNIGHT)); repo.act(a.id, "complete")
        repo.act(a.id, "cancel_date")
        val completed = repo.load().tasks.single()
        assertTrue(completed.done); assertNull(completed.date); assertNull(completed.time)
        repo.act(a.id, "restore"); assertEquals(TaskView.UNDATED, TaskRules.viewOf(repo.load().tasks.single()))
    }

    @Test fun completionIsIdempotentAndRestoreRetainsOriginalPrecision() {
        val repo = TaskRepository(MemoryStorage())
        val task = repo.saveTask(item(1, "定时", day).copy(time = LocalTime.MIDNIGHT))
        repo.act(task.id, "complete"); val completed = repo.load()
        repo.act(task.id, "complete"); assertEquals(completed, repo.load())
        repo.act(task.id, "restore")
        assertEquals(LocalTime.MIDNIGHT, repo.load().tasks.single().time)
    }

    @Test fun deletingTaskDoesNotReuseIdOrResurrectStaleEditor() {
        val repo = TaskRepository(MemoryStorage())
        val old = repo.saveTask(item(1, "旧任务")); repo.act(old.id, "delete")
        assertThrows(IllegalStateException::class.java) { repo.saveTask(old) }
        assertTrue(repo.saveTask(item(1, "新任务")).id > old.id)
    }

    @Test fun taskCannotMoveBetweenTablesAndStaleNewTaskCannotUseDeletedTable() {
        val repo = TaskRepository(MemoryStorage()); val table = repo.saveTable(0, "其他")
        val a = repo.saveTask(item(1, "a"))
        assertThrows(IllegalArgumentException::class.java) { repo.saveTask(a.copy(tableId = table.id)) }
        repo.deleteTable(table.id, 0)
        assertThrows(IllegalArgumentException::class.java) { repo.saveTask(item(table.id, "a")) }
    }

    @Test fun reorderRejectsMissingDuplicateAndForeignIdsWithoutWriting() {
        val disk = MemoryStorage(); val repo = TaskRepository(disk)
        val a = repo.saveTask(item(1, "a")); val b = repo.saveTask(item(1, "b"))
        val before = disk.raw
        assertFalse(repo.reorder(1, listOf(a.id)))
        assertFalse(repo.reorder(1, listOf(a.id, a.id)))
        assertFalse(repo.reorder(1, listOf(a.id, 999)))
        assertFalse(repo.reorder(999, listOf(a.id, b.id)))
        assertEquals(before, disk.raw)
    }

    @Test fun tableNamesAreTrimmedRequiredAndUnique() {
        val repo = TaskRepository(MemoryStorage())
        val table = repo.saveTable(0, " 工作 "); assertEquals("工作", table.name)
        assertThrows(IllegalArgumentException::class.java) { repo.saveTable(0, "工作") }
        assertThrows(IllegalArgumentException::class.java) { repo.saveTable(0, "  ") }
        assertEquals(table.id, repo.saveTable(table.id, "工作").id)
    }

    @Test fun renameAndTableReorderKeepTaskOwnershipAndSelection() {
        val repo = TaskRepository(MemoryStorage()); val other = repo.saveTable(0, "其他")
        val item = repo.saveTask(item(other.id, "任务")); val choice = WidgetSelection(other.id, TaskView.DONE)
        repo.select(16, choice); repo.saveTable(other.id, "更名"); repo.moveTable(other.id, -1)
        assertEquals(other.id, repo.load().tables.first().id)
        assertEquals(item, repo.load().tasks.single()); assertEquals(choice, repo.selection(16))
    }

    @Test fun deletingTableCascadesAllViewsAndFallsBackNextThenPrevious() {
        val repo = TaskRepository(MemoryStorage()); val middle = repo.saveTable(0, "中间"); val last = repo.saveTable(0, "末尾")
        repo.saveTask(item(middle.id, "无日期")); repo.saveTask(item(middle.id, "有日期", day))
        val done = repo.saveTask(item(middle.id, "完成")); repo.act(done.id, "complete")
        val retained = repo.saveTask(item(1, "保留"))
        repo.select(16, WidgetSelection(middle.id, TaskView.DONE)); repo.select(TaskRepository.APP_ENTRANCE, WidgetSelection(middle.id))
        repo.deleteTable(middle.id, 3)
        assertEquals(listOf(retained), repo.load().tasks)
        assertEquals(last.id, repo.selection(16).tableId); assertEquals(TaskView.DONE, repo.selection(16).view)
        assertEquals(last.id, repo.selection(TaskRepository.APP_ENTRANCE).tableId)
        repo.deleteTable(last.id, 0); assertEquals(1L, repo.selection(16).tableId)
        assertThrows(IllegalArgumentException::class.java) { repo.deleteTable(1, 1) }
    }

    @Test fun deleteRequiresReconfirmationIfTaskCountChanged() {
        val disk = MemoryStorage(); val repo = TaskRepository(disk); val table = repo.saveTable(0, "删除测试")
        repo.saveTask(item(table.id, "新任务")); val before = disk.raw
        assertThrows(IllegalArgumentException::class.java) { repo.deleteTable(table.id, 0) }
        assertEquals(before, disk.raw)
    }

    @Test fun labelPageClampsAfterRemovingLastPage() {
        val repo = TaskRepository(MemoryStorage()); repeat(6) { repo.saveTable(0, "表$it") }
        repo.select(16, WidgetSelection(1, TaskView.UNDATED, 2))
        repo.deleteTable(repo.load().tables.last().id, 0)
        assertEquals(1, repo.selection(16).page); assertEquals(1L, repo.selection(16).tableId)
    }

    @Test fun appAndWidgetSelectionsSurviveReloadIndependently() {
        val disk = MemoryStorage(); val repo = TaskRepository(disk); val table = repo.saveTable(0, "其他")
        repo.select(16, WidgetSelection(table.id, TaskView.UNDATED)); repo.select(TaskRepository.APP_ENTRANCE, WidgetSelection(1, TaskView.DONE))
        val reloaded = TaskRepository(disk)
        assertEquals(table.id, reloaded.selection(16).tableId)
        assertEquals(TaskView.DONE, reloaded.selection(TaskRepository.APP_ENTRANCE).view)
        reloaded.forgetWidget(16); assertEquals(TaskView.DONE, reloaded.selection(TaskRepository.APP_ENTRANCE).view)
    }

    @Test fun parallelAppAndWidgetWritesDoNotLoseTasksOrReuseIds() {
        val repo = TaskRepository(MemoryStorage()); repo.load()
        val pool = Executors.newFixedThreadPool(4)
        try { pool.invokeAll((1..40).map { number -> Callable { repo.saveTask(item(1, "任务$number")) } }).forEach { it.get() } }
        finally { pool.shutdown() }
        assertEquals(40, repo.load().tasks.size)
        assertEquals(40, repo.load().tasks.map { it.id }.toSet().size)
    }

    private fun JSONObject.removeSelections(): String { remove("selections"); return toString() }
}
