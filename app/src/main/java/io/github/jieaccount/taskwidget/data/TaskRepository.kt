package io.github.jieaccount.taskwidget.data

/** 持久层仅负责完整快照的原子读写；业务规则可在普通 JVM 上验证。 */
interface SnapshotStorage {
    fun read(): String?
    fun write(value: String)
    fun legacy(): LegacyData? = null
}

data class LegacyData(val snapshot: String, val selections: Map<Int, WidgetSelection>)

/** 单进程内串行提交，表、任务、顺序和入口选择在同一份快照内保存。 */
class TaskRepository(private val storage: SnapshotStorage) {
    @Synchronized
    fun load(): TaskSnapshot {
        storage.read()?.let { return SnapshotCodec.decode(it) }
        val legacy = storage.legacy()
        val initial = if (legacy == null) TaskSnapshot(listOf(TaskTable(1, "默认")), emptyList(), 1)
        else SnapshotCodec.decode(legacy.snapshot).let { it.copy(selections = legacy.selections) }
        // 迁移成功后才写新文件，旧数据继续保留，读取错误不会初始化覆盖。
        return persist(normalize(initial))
    }

    private fun persist(state: TaskSnapshot): TaskSnapshot {
        storage.write(SnapshotCodec.encode(state))
        return state
    }

    @Synchronized
    fun saveTask(edited: TaskItem): TaskItem {
        val state = load()
        require(edited.title.isNotBlank()) { "请填写任务标题。" }
        require(edited.date == null || edited.date.year in 1..9999) { "年份须在 1–9999 之间。" }
        require(edited.time == null || (edited.time.second == 0 && edited.time.nano == 0)) { "日期精度须为时分。" }
        val next = state.sequence + 1
        val saved = if (edited.id == 0L) {
            require(state.tables.any { it.id == edited.tableId }) { "所属表已删除，请返回后重新添加。" }
            edited.copy(id = next, title = edited.title.trim(), done = false, completedOrder = null,
                createdOrder = next, manualOrder = TaskRules.nextTopOrder(state.tasks, edited.tableId))
        } else {
            val original = state.tasks.find { it.id == edited.id } ?: error("任务已删除，请返回后重新打开。")
            require(original.tableId == edited.tableId) { "任务所属表保持固定。" }
            original.copy(title = edited.title.trim(), description = edited.description, date = edited.date, time = edited.time,
                manualOrder = if (original.date != null && edited.date == null && !original.done)
                    TaskRules.nextTopOrder(state.tasks, original.tableId) else original.manualOrder)
        }
        persist(state.copy(tasks = state.tasks.filterNot { it.id == saved.id } + saved, sequence = next))
        return saved
    }

    @Synchronized
    fun act(taskId: Long, action: String) {
        val state = load()
        val task = state.tasks.find { it.id == taskId } ?: error("任务已删除，请返回后重新打开。")
        val next = state.sequence + 1
        val changed = when (action) {
            "complete" -> if (task.done) return else task.copy(done = true, completedOrder = next)
            "restore" -> if (!task.done) return else TaskRules.restore(task, state.tasks)
            "cancel_date" -> if (task.date == null) return else TaskRules.cancelDate(task, state.tasks)
            "delete" -> null
            else -> error("未知任务操作：$action")
        }
        persist(state.copy(tasks = state.tasks.filterNot { it.id == taskId } + listOfNotNull(changed), sequence = next))
    }

    @Synchronized
    fun reorder(tableId: Long, ids: List<Long>): Boolean {
        val state = load()
        val current = TaskRules.visible(state.tasks, tableId, TaskView.UNDATED).map { it.id }
        if (state.tables.none { it.id == tableId } || ids.size != current.size || ids.toSet() != current.toSet()) return false
        val positions = ids.withIndex().associate { it.value to it.index.toLong() }
        persist(state.copy(tasks = state.tasks.map { task -> positions[task.id]?.let { task.copy(manualOrder = it) } ?: task }))
        return true
    }

    /** 从组件创建时，同时保存新表和发起入口的选中状态。 */
    @Synchronized
    fun saveTable(id: Long, name: String, selectForEntrance: Int? = null): TaskTable {
        val state = load()
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "请填写表名。" }
        require(state.tables.none { it.id != id && it.name == trimmed }) { "表名已存在，请换一个名称。" }
        val table = if (id == 0L) TaskTable(state.sequence + 1, trimmed) else {
            require(state.tables.any { it.id == id }) { "这张表已删除。" }
            TaskTable(id, trimmed)
        }
        val tables = if (id == 0L) state.tables + table else state.tables.map { if (it.id == id) table else it }
        val selections = if (id == 0L && selectForEntrance != null) {
            val selected = state.selections[selectForEntrance] ?: WidgetSelection(table.id)
            state.selections + (selectForEntrance to selected.copy(tableId = table.id, page = tables.lastIndex / 3))
        } else state.selections
        persist(state.copy(tables = tables, sequence = if (id == 0L) table.id else state.sequence, selections = selections))
        return table
    }

    @Synchronized
    fun reorderTables(ids: List<Long>) {
        val state = load()
        require(ids.size == state.tables.size && ids.toSet() == state.tables.map { it.id }.toSet()) { "表已变化，请重新排序。" }
        val tables = state.tables.associateBy { it.id }
        persist(normalize(state.copy(tables = ids.map { tables.getValue(it) })))
    }

    /** 整体恢复业务快照；本机组件实例沿用并修正选择，文件中的其他设备实例不导入。 */
    @Synchronized
    fun restoreBackup(imported: TaskSnapshot) {
        SnapshotCodec.encode(imported)
        val current = load()
        val selections = current.selections.filterKeys { it != APP_ENTRANCE } +
            (APP_ENTRANCE to (imported.selections[APP_ENTRANCE] ?: WidgetSelection(imported.tables.first().id)))
        persist(normalize(imported.copy(selections = selections)))
    }

    @Synchronized
    fun moveTable(id: Long, delta: Int) {
        require(delta == -1 || delta == 1)
        val state = load()
        val from = state.tables.indexOfFirst { it.id == id }
        require(from >= 0) { "这张表已删除。" }
        val to = from + delta
        if (to !in state.tables.indices) return
        val tables = state.tables.toMutableList().also { it.add(to, it.removeAt(from)) }
        persist(normalize(state.copy(tables = tables)))
    }

    @Synchronized
    fun deleteTable(id: Long, expectedTaskCount: Int) {
        val state = load()
        require(state.tables.size > 1) { "至少保留一张表。" }
        val index = state.tables.indexOfFirst { it.id == id }
        require(index >= 0) { "这张表已删除。" }
        require(state.tasks.count { it.tableId == id } == expectedTaskCount) { "任务数量已变化，请重新确认删除。" }
        val remaining = state.tables.filterNot { it.id == id }
        val fallback = remaining[index.coerceAtMost(remaining.lastIndex)].id
        val selections = state.selections.mapValues { (_, selected) ->
            if (selected.tableId == id) selected.copy(tableId = fallback, page = remaining.indexOfFirst { it.id == fallback } / 3)
            else selected
        }
        persist(normalize(state.copy(tables = remaining, tasks = state.tasks.filterNot { it.tableId == id }, selections = selections)))
    }

    @Synchronized
    fun selection(entranceId: Int): WidgetSelection {
        val state = load()
        return state.selections[entranceId] ?: WidgetSelection(state.tables.first().id)
    }

    @Synchronized
    fun select(entranceId: Int, value: WidgetSelection) {
        val state = load()
        persist(normalize(state.copy(selections = state.selections + (entranceId to value))))
    }

    @Synchronized
    fun forgetWidget(id: Int) {
        val state = load()
        persist(state.copy(selections = state.selections - id))
    }

    private fun normalize(state: TaskSnapshot): TaskSnapshot = state.copy(selections = state.selections.mapValues { (_, value) ->
        value.copy(tableId = value.tableId.takeIf { id -> state.tables.any { it.id == id } } ?: state.tables.first().id,
            page = value.page.coerceIn(0, TaskRules.pageCount(state.tables.size) - 1))
    })

    companion object { const val APP_ENTRANCE = -1 }
}
