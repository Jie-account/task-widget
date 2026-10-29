package io.github.jieaccount.taskwidget.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jieaccount.taskwidget.data.TaskStore
import io.github.jieaccount.taskwidget.widget.TaskWidgetProvider

/** 表排序立即提交；新建和重命名使用独立草稿，删除同时显示全部任务数。 */
class TableManagementActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enablePrototypeEdgeToEdge()
        setContent { PrototypeTheme { Tables() } }
    }

    @Composable
    private fun Tables() {
        var revision by remember { mutableIntStateOf(0) }
        var editing by rememberSaveable { mutableStateOf<Long?>(null) }
        var deleting by rememberSaveable { mutableStateOf<Long?>(null) }
        var error by rememberSaveable { mutableStateOf("") }
        val loaded = remember(revision) { runCatching { TaskStore.load(this) }.onFailure { android.util.Log.e("TaskWidget", "读取表失败", it) } }
        val state = loaded.getOrNull()
        if (state == null) { DataReadError({ finish() }, { revision++ }); return }

        fun applyChange(action: () -> Unit): Boolean {
            return runCatching { action() }.fold({
                error = ""; revision++
                runCatching { TaskWidgetProvider.refreshAll(this) }.onFailure { android.util.Log.e("TaskWidget", "更新组件失败", it) }
                true
            }, { error = it.message ?: "保存失败，请重试。"; false })
        }

        val editId = editing
        if (editId != null) {
            val table = state.tables.find { it.id == editId }
            if (editId != 0L && table == null) { editing = null; return }
            TableNameEditor(table?.name.orEmpty(), editId == 0L, { editing = null }) { name ->
                if (applyChange { TaskStore.saveTable(this, editId, name) }) { editing = null; null } else error
            }
            return
        }

        FlowPage("管理表", { finish() }, footer = {
            Button(onClick = { editing = 0L }, modifier = Modifier.fillMaxWidth()) { Text("新建表") }
        }) {
            Text("共 ${state.tables.size} 张表 · 排序即时保存", Modifier.padding(vertical = 16.dp))
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(state.tables, key = { _, table -> table.id }) { index, table ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Text(table.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text("${state.tasks.count { it.tableId == table.id }} 项任务（含已完成）", style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { editing = table.id }) { Text("改名") }
                            TextButton(onClick = { applyChange { TaskStore.moveTable(this@TableManagementActivity, table.id, -1) } }, enabled = index > 0) { Text("↑") }
                            TextButton(onClick = { applyChange { TaskStore.moveTable(this@TableManagementActivity, table.id, 1) } }, enabled = index < state.tables.lastIndex) { Text("↓") }
                            TextButton(onClick = { deleting = table.id }, enabled = state.tables.size > 1) { Text("删除") }
                        }
                        if (state.tables.size == 1) Text("至少保留一张表", style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
        }
        state.tables.find { it.id == deleting }?.let { table ->
            val count = state.tasks.count { it.tableId == table.id }
            OperationDialog("删除这张表？", "“${table.name}”\n共 $count 项任务（含已完成）。表和所属任务将一并删除，无法恢复。", "删除表和任务", {
                applyChange { TaskStore.deleteTable(this, table.id, count) }
                deleting = null; revision++
            }, { deleting = null }, destructive = true)
        }
    }
}

@Composable
private fun TableNameEditor(initial: String, isNew: Boolean, onBack: () -> Unit, onSave: (String) -> String?) {
    var name by rememberSaveable { mutableStateOf(initial) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }
    val leave = { if (name != initial) discard = true else onBack() }
    BackHandler(onBack = leave)
    FlowPage(if (isNew) "新建表" else "重命名表", leave, footer = {
        Button(onClick = { error = onSave(name).orEmpty() }, modifier = Modifier.fillMaxWidth()) { Text("保存") }
    }) {
        OutlinedTextField(name, { name = it }, label = { Text("表名 · 必填") }, modifier = Modifier.fillMaxWidth().padding(top = 20.dp), minLines = 3)
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
    }
    if (discard) OperationDialog("放弃未保存修改？", "表名尚未保存。", "放弃修改", onBack, { discard = false }, "继续编辑")
}
