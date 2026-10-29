package io.github.jieaccount.taskwidget.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/** v1 为原型快照，v2 将入口选择与业务数据一起持久化。 */
object SnapshotCodec {
    fun decode(raw: String): TaskSnapshot {
        val root = JSONObject(raw)
        val version = root.getInt("version")
        require(version in 1..2) { "任务数据版本不兼容，请使用对应版本的应用。" }
        val tables = root.getJSONArray("tables").objects().map { TaskTable(it.getLong("id"), it.getString("name")) }
        val tasks = root.getJSONArray("tasks").objects().map { item ->
            TaskItem(item.getLong("id"), item.getLong("tableId"), item.getString("title"), item.getString("description"),
                item.optString("date").takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
                item.optString("time").takeIf { it.isNotEmpty() }?.let(LocalTime::parse),
                item.getBoolean("done"), item.getLong("created"),
                if (item.has("completed") && !item.isNull("completed")) item.getLong("completed") else null, item.getLong("manual"))
        }
        val selections = if (version == 1) emptyMap() else root.getJSONArray("selections").objects().associate { item ->
            item.getInt("entrance") to WidgetSelection(item.getLong("table"), TaskView.valueOf(item.getString("view")), item.getInt("page"))
        }
        val state = TaskSnapshot(tables, tasks, root.getLong("sequence"), selections)
        validate(state)
        return state
    }

    fun encode(state: TaskSnapshot): String {
        validate(state)
        return JSONObject().put("version", 2).put("sequence", state.sequence)
            .put("tables", JSONArray().apply { state.tables.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) } })
            .put("tasks", JSONArray().apply { state.tasks.forEach { task ->
                put(JSONObject().put("id", task.id).put("tableId", task.tableId).put("title", task.title)
                    .put("description", task.description).put("date", task.date?.toString() ?: "").put("time", task.time?.toString() ?: "")
                    .put("done", task.done).put("created", task.createdOrder).put("completed", task.completedOrder).put("manual", task.manualOrder))
            } })
            .put("selections", JSONArray().apply { state.selections.forEach { (id, value) ->
                put(JSONObject().put("entrance", id).put("table", value.tableId).put("view", value.view.name).put("page", value.page))
            } }).toString()
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)

    private fun validate(state: TaskSnapshot) {
        require(state.tables.isNotEmpty() && state.tables.map { it.id }.toSet().size == state.tables.size) { "表数据无效。" }
        require(state.tables.all { it.id > 0 && it.name.isNotBlank() }) { "表数据无效。" }
        require(state.tasks.map { it.id }.toSet().size == state.tasks.size) { "任务标识重复。" }
        require(state.tasks.all { task -> task.id > 0 && task.title.isNotBlank() && state.tables.any { it.id == task.tableId }
            && (task.date == null || task.date.year in 1..9999) && (task.time == null || (task.time.second == 0 && task.time.nano == 0))
            && (!task.done || task.completedOrder != null) }) { "任务数据无效。" }
        val maximum = (state.tables.map { it.id } + state.tasks.flatMap { listOf(it.id, it.createdOrder, it.completedOrder ?: 0) }).maxOrNull() ?: 0
        require(state.sequence >= maximum) { "任务序号无效。" }
    }
}
