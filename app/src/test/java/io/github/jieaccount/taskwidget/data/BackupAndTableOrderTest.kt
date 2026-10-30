package io.github.jieaccount.taskwidget.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime

class BackupAndTableOrderTest {
    private class MemoryStorage(var raw: String? = null) : SnapshotStorage {
        var fail = false
        override fun read() = raw
        override fun write(value: String) { if (fail) throw IOException("写入失败"); raw = value }
    }

    private fun backup() = TaskSnapshot(listOf(TaskTable(8, "备份表")), listOf(
        TaskItem(9, 8, "仅日期", "中文\n描述", LocalDate.of(2026, 10, 29), createdOrder = 9, manualOrder = -3),
        TaskItem(10, 8, "已完成定时", date = LocalDate.of(2026, 10, 30), time = LocalTime.MIDNIGHT,
            done = true, createdOrder = 10, completedOrder = 11),
        TaskItem(12, 8, "无日期", createdOrder = 12, manualOrder = -7)), 20,
        mapOf(-1 to WidgetSelection(8, TaskView.DONE), 99 to WidgetSelection(8)))

    @Test fun portableBackupPreservesAllBusinessFieldsAndAppChoice() {
        val state = backup()
        val result = BackupCodec.decode(BackupCodec.encode(state))
        assertEquals(state.copy(selections = state.selections.filterKeys { it == -1 }), result)
        assertEquals("task-widget", JSONObject(BackupCodec.encode(state)).getString("format"))
    }

    @Test fun malformedAndFutureBackupOrDataVersionsAreRejected() {
        val valid = JSONObject(BackupCodec.encode(backup()))
        for (raw in listOf("{broken", "{}", JSONObject(valid.toString()).put("formatVersion", 99).toString(),
            JSONObject(valid.toString()).put("data", JSONObject(valid.getJSONObject("data").toString()).put("version", 99)).toString())) {
            assertThrows(Exception::class.java) { BackupCodec.decode(raw) }
        }
    }

    @Test fun restoreReplacesBusinessAndRetainsOnlyThisDevicesWidgets() {
        val disk = MemoryStorage(); val repo = TaskRepository(disk)
        repo.load(); repo.select(16, WidgetSelection(1, TaskView.UNDATED, 5))
        repo.restoreBackup(backup())
        val result = repo.load()
        assertEquals(backup().tables, result.tables); assertEquals(backup().tasks, result.tasks)
        assertEquals(backup().sequence, result.sequence)
        assertEquals(setOf(-1, 16), result.selections.keys)
        assertEquals(WidgetSelection(8, TaskView.UNDATED, 0), result.selections[16])
        assertEquals(WidgetSelection(8, TaskView.DONE), result.selections[-1])
    }

    @Test fun failedRestoreWriteKeepsOriginalSnapshot() {
        val disk = MemoryStorage(); val repo = TaskRepository(disk)
        repo.load(); val before = disk.raw; disk.fail = true
        assertThrows(IOException::class.java) { repo.restoreBackup(backup()) }
        assertEquals(before, disk.raw)
    }

    @Test fun invalidRestoreCandidateDoesNotWriteAnything() {
        val disk = MemoryStorage(); val repo = TaskRepository(disk)
        repo.load(); val before = disk.raw
        assertThrows(IllegalArgumentException::class.java) { repo.restoreBackup(backup().copy(sequence = 0)) }
        assertEquals(before, disk.raw)
    }

    @Test fun completeTableReorderKeepsTasksAndRejectsStaleOrDuplicateIds() {
        val disk = MemoryStorage(); val repo = TaskRepository(disk)
        val second = repo.saveTable(0, "第二张"); val third = repo.saveTable(0, "第三张")
        repo.saveTask(TaskItem(0, second.id, "任务", createdOrder = 0))
        val before = repo.load()
        repo.reorderTables(listOf(third.id, second.id, 1))
        assertEquals(listOf(third.id, second.id, 1L), repo.load().tables.map { it.id })
        assertEquals(before.tasks, repo.load().tasks); assertEquals(before.sequence, repo.load().sequence)
        val saved = disk.raw
        assertThrows(IllegalArgumentException::class.java) { repo.reorderTables(listOf(third.id, second.id, second.id)) }
        assertThrows(IllegalArgumentException::class.java) { repo.reorderTables(listOf(second.id)) }
        assertEquals(saved, disk.raw)
    }
}
