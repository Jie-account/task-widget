package io.github.jieaccount.taskwidget.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/** T-004 的独立演示存储；正式业务持久化与迁移在 T-005 处理。 */
object PrototypeStore {
    private const val PREFS = "capability_prototype_v1"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun load(context: Context): TaskSnapshot {
        val raw = prefs(context).getString("snapshot", null) ?: return resetDemo(context)
        val root = JSONObject(raw)
        check(root.getInt("version") == 1) { "演示数据版本不兼容" }
        val tables = root.getJSONArray("tables").let { array ->
            (0 until array.length()).map { i -> array.getJSONObject(i).let { TaskTable(it.getLong("id"), it.getString("name")) } }
        }
        val tasks = root.getJSONArray("tasks").let { array ->
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                TaskItem(
                    id = item.getLong("id"), tableId = item.getLong("tableId"),
                    title = item.getString("title"), description = item.getString("description"),
                    date = item.optString("date").takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
                    time = item.optString("time").takeIf { it.isNotEmpty() }?.let(LocalTime::parse),
                    done = item.getBoolean("done"), createdOrder = item.getLong("created"),
                    completedOrder = if (item.has("completed")) item.getLong("completed") else null,
                    manualOrder = item.getLong("manual"),
                )
            }
        }
        return TaskSnapshot(tables, tasks, root.getLong("sequence"))
    }

    private fun write(context: Context, snapshot: TaskSnapshot) {
        val root = JSONObject().put("version", 1).put("sequence", snapshot.sequence)
        root.put("tables", JSONArray().apply {
            snapshot.tables.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) }
        })
        root.put("tasks", JSONArray().apply {
            snapshot.tasks.forEach { task ->
                put(JSONObject().put("id", task.id).put("tableId", task.tableId).put("title", task.title)
                    .put("description", task.description).put("date", task.date?.toString() ?: "")
                    .put("time", task.time?.toString() ?: "").put("done", task.done)
                    .put("created", task.createdOrder).put("completed", task.completedOrder)
                    .put("manual", task.manualOrder))
            }
        })
        check(prefs(context).edit().putString("snapshot", root.toString()).commit()) { "演示数据保存失败" }
    }

    @Synchronized
    fun saveTask(context: Context, edited: TaskItem): TaskItem {
        val state = load(context)
        val original = state.tasks.find { it.id == edited.id }
        val next = state.sequence + 1
        val saved = if (original == null) edited.copy(
            id = next, createdOrder = next,
            manualOrder = TaskRules.nextTopOrder(state.tasks, edited.tableId),
        ) else edited.copy(manualOrder = if (original.date != null && edited.date == null && !edited.done)
            TaskRules.nextTopOrder(state.tasks, edited.tableId) else original.manualOrder)
        write(context, state.copy(tasks = state.tasks.filterNot { it.id == saved.id } + saved, sequence = next))
        return saved
    }

    @Synchronized
    fun act(context: Context, taskId: Long, action: String) {
        val state = load(context)
        val task = state.tasks.find { it.id == taskId } ?: return
        val next = state.sequence + 1
        val changed = when (action) {
            "complete" -> if (task.done) task else task.copy(done = true, completedOrder = next)
            "restore" -> if (!task.done) task else TaskRules.restore(task, state.tasks)
            "cancel_date" -> TaskRules.cancelDate(task, state.tasks)
            "delete" -> null
            else -> error("未知任务操作：$action")
        }
        write(context, state.copy(tasks = state.tasks.filterNot { it.id == taskId } + listOfNotNull(changed), sequence = next))
    }

    @Synchronized
    fun reorder(context: Context, tableId: Long, ids: List<Long>): Boolean {
        val state = load(context)
        val current = TaskRules.visible(state.tasks, tableId, TaskView.UNDATED).map { it.id }
        if (ids.size != current.size || ids.toSet() != current.toSet()) return false
        val positions = ids.withIndex().associate { it.value to it.index.toLong() }
        write(context, state.copy(tasks = state.tasks.map { task ->
            positions[task.id]?.let { task.copy(manualOrder = it) } ?: task
        }))
        return true
    }

    fun selection(context: Context, widgetId: Int): WidgetSelection {
        val state = load(context)
        val selected = prefs(context).getLong("widget.$widgetId.table", state.tables.first().id)
        return WidgetSelection(
            tableId = selected.takeIf { id -> state.tables.any { it.id == id } } ?: state.tables.first().id,
            view = TaskView.entries.firstOrNull { it.name == prefs(context).getString("widget.$widgetId.view", "") } ?: TaskView.DATED,
            page = prefs(context).getInt("widget.$widgetId.page", 0).coerceIn(0, TaskRules.pageCount(state.tables.size) - 1),
        )
    }

    fun select(context: Context, widgetId: Int, value: WidgetSelection) {
        check(prefs(context).edit().putLong("widget.$widgetId.table", value.tableId)
            .putString("widget.$widgetId.view", value.view.name).putInt("widget.$widgetId.page", value.page).commit())
    }

    fun forgetWidget(context: Context, widgetId: Int) {
        prefs(context).edit().remove("widget.$widgetId.table").remove("widget.$widgetId.view").remove("widget.$widgetId.page").apply()
    }

    @Synchronized
    fun resetDemo(context: Context): TaskSnapshot {
        val today = LocalDate.now()
        val tables = listOf("默认", "工作", "生活", "阅读计划与长期资料整理", "灵感", "学习", "家务")
            .mapIndexed { index, name -> TaskTable(index + 1L, name) }
        val tasks = mutableListOf(
            TaskItem(1, 1, "提交月度复盘与下一阶段行动计划", "用于检查长标题与过期颜色。", today.minusDays(1), createdOrder = 1),
            TaskItem(2, 1, "确认设计评审", "点标题查看完整时分，点日期取消日期。", today, LocalTime.of(9, 30), createdOrder = 2),
            TaskItem(3, 1, "发送会议纪要", date = today, time = LocalTime.of(14, 0), createdOrder = 3),
            TaskItem(4, 1, "整理本周资料", date = today, createdOrder = 4),
            TaskItem(5, 1, "收集参考资料", createdOrder = 5, manualOrder = -1),
            TaskItem(6, 1, "梳理任务拆分", createdOrder = 6, manualOrder = -2),
            TaskItem(7, 1, "确认首版需求", date = today.minusDays(2), done = true, createdOrder = 7, completedOrder = 9),
            TaskItem(8, 1, "建立项目文档", done = true, createdOrder = 8, completedOrder = 8),
        )
        repeat(20) { i -> tasks += TaskItem(20L + i, 1, "滚动验证 ${i + 1}：跟进原型使用反馈", date = today.plusDays(i + 1L), createdOrder = 20L + i) }
        return TaskSnapshot(tables, tasks, 100).also { write(context, it) }
    }
}
