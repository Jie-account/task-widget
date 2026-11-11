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
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jieaccount.taskwidget.data.*
import io.github.jieaccount.taskwidget.widget.TaskWidgetProvider
import java.time.LocalDate
import java.time.LocalTime

/** 透明窗口保留原入口背景；完整页面自行铺黑底，确认操作只绘制弹窗。 */
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
        // 数据已经提交；组件更新失败单独记录，不把已成功保存显示为失败。
        runCatching { TaskWidgetProvider.refreshAll(this) }.onFailure { android.util.Log.e("TaskWidget", "保存后刷新失败", it) }
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        exitFlow()
    }

    @Composable
    private fun Flow() {
        var revision by remember { mutableIntStateOf(0) }
        val loaded = remember(revision) { runCatching { TaskStore.load(this) }.onFailure { android.util.Log.e("TaskWidget", "读取任务失败", it) } }
        val state = loaded.getOrNull()
        if (state == null) { DataReadError(::exitFlow, { revision++ }); return }
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
            "new_table" -> TableNameEditor("", true, ::exitFlow) { name ->
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                    .takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID }
                runCatching { TaskStore.saveTable(this, 0L, name, selectForEntrance = widgetId) }
                    .fold({ saved(); null }, { it.message ?: "保存失败，请重试。" })
            }
            "add", "edit" -> {
                val initial = existing ?: TaskItem(0, tableId, "", date = if (view == TaskView.DATED) LocalDate.now() else null, createdOrder = 0)
                TaskEditor(initial, tableName, ::exitFlow,
                    onSave = { edited -> runCatching { TaskStore.saveTask(this, edited) }.fold({ saved(); null }, { it.message ?: "保存失败，请重试。" }) })
            }
            "menu" -> {
                BackHandler(onBack = ::exitFlow)
                WidgetSubtableMenu(intent.sourceBounds, view, ::exitFlow) { action = "sort" }
            }
            "sort" -> SortEditor(tableId, tableName, TaskRules.visible(state.tasks, tableId, TaskView.UNDATED), ::exitFlow) { ids ->
                if (runCatching { TaskStore.reorder(this, tableId, ids) }.getOrDefault(false)) { saved(); true } else false
            }
            "complete", "restore", "cancel_date", "delete" -> {
                val task = existing!!
                var error by remember { mutableStateOf("") }
                val wording = when (action) {
                    "complete" -> "完成这项任务？" to "确认后进入已完成最上方。"
                    "restore" -> "恢复为未完成？" to if (task.date != null) "保留原日期，回到有日期并按日期排序。" else "回到无日期最上方。"
                    "cancel_date" -> "取消这项任务的日期？" to "确认后进入无日期最上方。"
                    else -> "删除这项任务？" to "删除后无法恢复。"
                }
                OperationDialog(wording.first, "“${task.title}”\n${wording.second}${if (error.isNotEmpty()) "\n$error" else ""}", {
                    runCatching { TaskStore.act(this, task.id, action) }.onSuccess { saved() }.onFailure { error = it.message ?: "操作失败，请重试。" }
                }, ::exitFlow)
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
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
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
private fun TaskEditor(initial: TaskItem, tableName: String, onExit: () -> Unit, onSave: (TaskItem) -> String?) {
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var description by rememberSaveable { mutableStateOf(initial.description) }
    var date by rememberSaveable { mutableStateOf(initial.date?.toString().orEmpty()) }
    var time by rememberSaveable { mutableStateOf(initial.time?.toString().orEmpty()) }
    var error by rememberSaveable { mutableStateOf("") }
    var discard by rememberSaveable { mutableStateOf(false) }
    var showDate by rememberSaveable { mutableStateOf(false) }
    val titleFocus = remember { FocusRequester() }
    val dirty = title != initial.title || description != initial.description || date != initial.date?.toString().orEmpty() || time != initial.time?.toString().orEmpty()
    // 显式捕获当前草稿状态，确保系统返回回调随编辑状态更新。
    val leave = { if (dirty) discard = true else onExit() }
    fun save() {
        if (title.isBlank()) { error = "请填写任务标题。"; titleFocus.requestFocus(); return }
        error = onSave(initial.copy(title = title.trim(), description = description,
            date = date.takeIf { it.isNotBlank() }?.let(LocalDate::parse), time = time.takeIf { it.isNotBlank() }?.let(LocalTime::parse))).orEmpty()
    }
    if (showDate) {
        DateEditor(date, time, { showDate = false }) { choice ->
            date = choice?.date?.toString().orEmpty(); time = choice?.time?.toString().orEmpty(); showDate = false
        }
        return
    }
    BackHandler(onBack = leave)
    FlowPage(if (initial.id == 0L) "添加任务" else "任务详情", leave, footer = {
        Column(Modifier.fillMaxWidth()) {
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text("保存") }
        }
    }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("所属表：$tableName", Modifier.padding(top = 12.dp))
            OutlinedTextField(title, { title = it }, label = { Text("标题 · 必填") }, modifier = Modifier.fillMaxWidth().focusRequester(titleFocus), minLines = 2)
            OutlinedTextField(description, { description = it }, label = { Text("描述 · 选填") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            Text(if (date.isBlank()) "未设置日期" else if (time.isBlank()) date else "$date $time")
            OutlinedButton(onClick = { showDate = true }) { Text(if (date.isBlank()) "设置日期" else "修改日期") }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (discard) OperationDialog("放弃未保存修改？", "本次修改尚未保存，确认后放弃修改。", onExit, { discard = false })
}

@Composable
private fun SortEditor(tableId: Long, tableName: String, initial: List<TaskItem>, onExit: () -> Unit, onSave: (List<Long>) -> Boolean) {
    var ids by rememberSaveable(tableId) { mutableStateOf(initial.map { it.id }) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }
    val dirty = ids != initial.map { it.id }
    val leave = { if (dirty) discard = true else onExit() }
    BackHandler(onBack = leave)
    FlowPage("修改排序", leave, footer = {
        Button(onClick = { if (!onSave(ids)) error = "任务已变化，请返回后重新进入排序。" }, enabled = ids.size > 1, modifier = Modifier.fillMaxWidth()) { Text("保存排序") }
    }) {
        Text("$tableName / 无日期", Modifier.padding(vertical = 16.dp))
        if (ids.isEmpty()) Text("暂无无日期任务")
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        DragReorderList(ids, { ids = it }, {}, Modifier.weight(1f)) { id ->
            Column {
                Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
                    DragHandle()
                    Text(initial.first { it.id == id }.title, Modifier.weight(1f).padding(vertical = 16.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                HorizontalDivider()
            }
        }
    }
    if (discard) OperationDialog("放弃未保存修改？", "本次排序尚未保存，确认后放弃修改。", onExit, { discard = false })
}
