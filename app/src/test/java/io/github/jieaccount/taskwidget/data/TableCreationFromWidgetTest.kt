package io.github.jieaccount.taskwidget.data

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class TableCreationFromWidgetTest {
    /** 记录实际提交次数，验证新表与入口选择共同写入。 */
    private class MemoryStorage(initial: TaskSnapshot) : SnapshotStorage {
        var raw = SnapshotCodec.encode(initial)
        var writes = 0
        var fail = false
        override fun read() = raw
        override fun write(value: String) {
            writes++
            if (fail) throw IOException("写入失败")
            raw = value
        }
    }

    private fun initial() = TaskSnapshot(
        listOf(TaskTable(1, "示例甲"), TaskTable(2, "示例乙"), TaskTable(3, "示例丙")),
        listOf(TaskItem(4, 1, "示例任务", description = "保留正文", createdOrder = 4)), 4,
        mapOf(42 to WidgetSelection(3, TaskView.DONE), -1 to WidgetSelection(2, TaskView.UNDATED)),
    )

    @Test fun createAppendsAndSelectsNewPageInOneCommit() {
        val before = initial(); val disk = MemoryStorage(before); val repo = TaskRepository(disk)
        val table = repo.saveTable(0, "  新表示例  ", selectForEntrance = 42)
        val after = repo.load()
        assertEquals(before.tables + table, after.tables)
        assertEquals("新表示例", table.name)
        assertEquals(WidgetSelection(table.id, TaskView.DONE, 1), after.selections[42])
        assertEquals(before.selections[-1], after.selections[-1])
        assertEquals(before.tasks, after.tasks)
        assertEquals(1, disk.writes)
    }

    @Test fun invalidOrDuplicateNamePreservesTablesAndSelection() {
        val disk = MemoryStorage(initial()); val repo = TaskRepository(disk); val raw = disk.raw
        for (name in listOf("  ", "示例甲")) {
            assertThrows(IllegalArgumentException::class.java) { repo.saveTable(0, name, selectForEntrance = 42) }
            assertEquals(raw, disk.raw)
        }
        assertEquals(0, disk.writes)
    }

    @Test fun failedCommitPreservesBothTableAndSelectedPage() {
        val disk = MemoryStorage(initial()); val repo = TaskRepository(disk); val raw = disk.raw
        disk.fail = true
        assertThrows(IOException::class.java) { repo.saveTable(0, "新表示例", selectForEntrance = 42) }
        assertEquals(raw, disk.raw)
    }

    @Test fun existingTableManagementCreateAndRenameKeepSelections() {
        val before = initial(); val disk = MemoryStorage(before); val repo = TaskRepository(disk)
        val table = repo.saveTable(0, "管理页示例")
        repo.saveTable(table.id, "管理页改名")
        assertEquals(before.selections, repo.load().selections)
        assertEquals(before.tasks, repo.load().tasks)
        assertEquals("管理页改名", repo.load().tables.last().name)
    }
}
