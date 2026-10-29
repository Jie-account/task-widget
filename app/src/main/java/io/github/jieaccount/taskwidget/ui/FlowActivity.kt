package io.github.jieaccount.taskwidget.ui

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jieaccount.taskwidget.data.*
import io.github.jieaccount.taskwidget.widget.TaskWidgetProvider
import java.time.LocalDate
import java.time.LocalTime

/** 组件操作放入独立任务栈；App 内入口沿用首页任务栈。 */
class FlowActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enablePrototypeEdgeToEdge()
        setContent { PrototypeTheme { Flow() } }
    }

    private fun exitFlow() {
        if (intent.getBooleanExtra("fromWidget", false) && isTaskRoot) finishAndRemoveTask() else finish()
    }
    private fun saved() {
        TaskWidgetProvider.refreshAll(this)
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        exitFlow()
    }

    @Composable
    private fun Flow() {
        val state = remember { PrototypeStore.load(this) }
        val existing = state.tasks.find { it.id == intent.getLongExtra("taskId", 0) }
        val tableId = existing?.tableId ?: intent.getLongExtra("tableId", state.tables.first().id)
        val view = TaskView.entries.firstOrNull { it.name == intent.getStringExtra("view") } ?: TaskView.DATED
        var action by rememberSaveable { mutableStateOf(intent.getStringExtra("flow") ?: "edit") }
        val tableName = state.tables.find { it.id == tableId }?.name.orEmpty()
        if (action in listOf("edit", "complete", "restore", "cancel_date", "delete") && existing == null) {
            LaunchedEffect(Unit) { Toast.makeText(this@FlowActivity, "任务已不存在", Toast.LENGTH_SHORT).show(); exitFlow() }
            return
        }
        when (action) {
            "add", "edit" -> {
                val initial = existing ?: TaskItem(0, tableId, "", date = if (view == TaskView.DATED) LocalDate.now() else null, createdOrder = 0)
                TaskEditor(initial, tableName, ::exitFlow) { PrototypeStore.saveTask(this, it); saved() }
            }
            "menu" -> {
                BackHandler(onBack = ::exitFlow)
                FlowPage("子表菜单", ::exitFlow) {
                    Text("$tableName / ${view.label}", Modifier.padding(vertical = 18.dp))
                    if (view == TaskView.UNDATED) Button(onClick = { action = "sort" }, modifier = Modifier.fillMaxWidth()) { Text("修改排序") }
                    else Text("当前子表暂无可用操作")
                    TextButton(onClick = ::exitFlow) { Text(if (view == TaskView.UNDATED) "关闭" else "知道了") }
                }
            }
            "sort" -> SortEditor(tableId, tableName, TaskRules.visible(state.tasks, tableId, TaskView.UNDATED), ::exitFlow) { ids ->
                if (PrototypeStore.reorder(this, tableId, ids)) { saved(); true } else false
            }
            "complete", "restore", "cancel_date", "delete" -> {
                val task = existing!!
                val wording = when (action) {
                    "complete" -> Triple("完成这项任务？", "确认后进入已完成最上方。", "确认完成")
                    "restore" -> Triple("恢复为未完成？", if (task.date != null) "保留原日期，回到有日期并按日期排序。" else "回到无日期最上方。", "确认恢复")
                    "cancel_date" -> Triple("取消这项任务的日期？", "确认后进入无日期最上方。", "取消日期")
                    else -> Triple("删除这项任务？", "删除后无法恢复。", "删除任务")
                }
                FlowPage("确认操作", ::exitFlow) { Text("$tableName / ${view.label}", Modifier.padding(vertical = 18.dp)) }
                OperationDialog(wording.first, "“${task.title}”\n${wording.second}", wording.third, {
                    PrototypeStore.act(this, task.id, action); saved()
                }, ::exitFlow, destructive = action == "delete")
            }
        }
    }

    companion object {
        fun intent(context: Context, action: String, tableId: Long, view: TaskView, taskId: Long = 0,
                   widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID): Intent {
            val fromWidget = widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
            return Intent(context, FlowActivity::class.java)
                .setData(Uri.parse("taskwidget://flow/$widgetId/$action/$tableId/${view.name}/$taskId"))
                .putExtra("tableId", tableId).putExtra("view", view.name).putExtra("fromWidget", fromWidget)
                .apply {
                    // 集合模板保留这两个字段为空，让行内 fill-in Intent 提供实际操作与任务。
                    if (action != "row") putExtra("flow", action)
                    if (taskId != 0L) putExtra("taskId", taskId)
                    if (fromWidget) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
        }
    }
}

@Composable
private fun TaskEditor(initial: TaskItem, tableName: String, onExit: () -> Unit, onSave: (TaskItem) -> Unit) {
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var description by rememberSaveable { mutableStateOf(initial.description) }
    var date by rememberSaveable { mutableStateOf(initial.date?.toString().orEmpty()) }
    var time by rememberSaveable { mutableStateOf(initial.time?.toString().orEmpty()) }
    var error by rememberSaveable { mutableStateOf("") }
    var discard by rememberSaveable { mutableStateOf(false) }
    var clearDate by rememberSaveable { mutableStateOf(false) }
    val dirty = title != initial.title || description != initial.description || date != initial.date?.toString().orEmpty() || time != initial.time?.toString().orEmpty()
    // 显式捕获当前草稿状态，确保系统返回回调随编辑状态更新。
    val leave = { if (dirty) discard = true else onExit() }
    fun save(clearApproved: Boolean = false) {
        if (title.isBlank()) { error = "请填写任务标题。"; return }
        val parsedDate = if (date.isBlank()) null else runCatching { LocalDate.parse(date.trim()) }.getOrNull()
        if (date.isNotBlank() && (parsedDate == null || !date.trim().matches(Regex("\\d{4}-\\d{2}-\\d{2}")))) { error = "请填写有效日期，格式为 YYYY-MM-DD。"; return }
        val parsedTime = if (time.isBlank()) null else runCatching { LocalTime.parse(time.trim()) }.getOrNull()
        if (time.isNotBlank() && (parsedDate == null || parsedTime == null || !time.trim().matches(Regex("([01]\\d|2[0-3]):[0-5]\\d")))) { error = "设置时分时请先填写日期，并完整填写 HH:mm（24 小时制）。"; return }
        if (initial.date != null && parsedDate == null && !clearApproved) { clearDate = true; return }
        onSave(initial.copy(title = title.trim(), description = description, date = parsedDate, time = parsedTime))
    }
    BackHandler(onBack = leave)
    FlowPage(if (initial.id == 0L) "添加任务" else "任务详情", leave, footer = {
        Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text("保存") }
    }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("所属表：$tableName · ${if (initial.done) "已完成" else "未完成"}", Modifier.padding(top = 12.dp))
            OutlinedTextField(title, { title = it }, label = { Text("标题 · 必填") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            OutlinedTextField(description, { description = it }, label = { Text("描述 · 选填") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            OutlinedTextField(date, { date = it }, label = { Text("日期 · 可留空") }, placeholder = { Text("YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(time, { time = it }, label = { Text("时分 · 可留空") }, placeholder = { Text("HH:mm") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
        }
    }
    if (discard) OperationDialog("放弃未保存修改？", "本次修改尚未保存。", "放弃修改", onExit, { discard = false }, "继续编辑")
    if (clearDate) OperationDialog("取消这项任务的日期？", "“${initial.title}”\n${if (initial.done) "保存后仍在已完成。" else "保存后进入无日期最上方。"}", "取消日期", { clearDate = false; save(true) }, { clearDate = false })
}

@Composable
private fun SortEditor(tableId: Long, tableName: String, initial: List<TaskItem>, onExit: () -> Unit, onSave: (List<Long>) -> Boolean) {
    var ids by rememberSaveable(tableId) { mutableStateOf(initial.map { it.id }) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }
    val dirty = ids != initial.map { it.id }
    val leave = { if (dirty) discard = true else onExit() }
    fun move(index: Int, delta: Int) {
        ids = ids.toMutableList().also { list -> val value = list.removeAt(index); list.add(index + delta, value) }
    }
    BackHandler(onBack = leave)
    FlowPage("修改排序", leave, footer = {
        Button(onClick = { if (!onSave(ids)) error = "任务已变化，请返回后重新进入排序。" }, enabled = ids.size > 1, modifier = Modifier.fillMaxWidth()) { Text("保存排序") }
    }) {
        Text("$tableName / 无日期", Modifier.padding(vertical = 16.dp))
        if (ids.isEmpty()) Text("暂无无日期任务")
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(ids, key = { _, id -> id }) { index, id ->
                Row(Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
                    Text("${index + 1}. ${initial.first { it.id == id }.title}", Modifier.weight(1f).padding(vertical = 16.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { move(index, -1) }, enabled = index > 0) { Text("↑") }
                    TextButton(onClick = { move(index, 1) }, enabled = index < ids.lastIndex) { Text("↓") }
                }
                HorizontalDivider()
            }
        }
    }
    if (discard) OperationDialog("放弃未保存修改？", "本次排序尚未保存。", "放弃修改", onExit, { discard = false }, "继续编辑")
}
