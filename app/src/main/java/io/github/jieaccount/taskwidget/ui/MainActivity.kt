package io.github.jieaccount.taskwidget.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jieaccount.taskwidget.data.*
import io.github.jieaccount.taskwidget.widget.TaskWidgetProvider
import java.time.LocalDateTime

class MainActivity : ComponentActivity() {
    private var revision by mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PrototypeTheme { Home(revision) } }
    }
    override fun onResume() { super.onResume(); revision++ }

    private fun addWidget() {
        val manager = AppWidgetManager.getInstance(this)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(this, TaskWidgetProvider::class.java), null, null)
        } else Toast.makeText(this, "请从桌面的小组件列表添加“任务组件·原型”", Toast.LENGTH_LONG).show()
    }

    @Composable
    private fun Home(version: Int) {
        val state = remember(version) { PrototypeStore.load(this) }
        var tableId by rememberSaveable { mutableLongStateOf(state.tables.first().id) }
        var viewName by rememberSaveable { mutableStateOf(TaskView.DATED.name) }
        var page by rememberSaveable { mutableIntStateOf(0) }
        var resetDialog by rememberSaveable { mutableStateOf(false) }
        val view = TaskView.valueOf(viewName)
        val visible = TaskRules.visible(state.tasks, tableId, view)
        val tableName = state.tables.find { it.id == tableId }?.name.orEmpty()
        val listState = rememberLazyListState()
        LaunchedEffect(tableId, view) { listState.scrollToItem(0) }
        fun open(action: String, taskId: Long = 0) = startActivity(FlowActivity.intent(this, action, tableId, view, taskId))
        Scaffold(topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("任务组件·原型", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = ::addWidget) { Text("添加组件") }
            }
        }) { insets ->
            Column(Modifier.fillMaxSize().padding(insets)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("当前为演示数据", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { resetDialog = true }) { Text("重置演示数据") }
                }
                Text("当前表：$tableName", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
                Column(Modifier.weight(1f).fillMaxWidth().background(Color.Black).padding(horizontal = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { page-- }, enabled = page > 0, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(32.dp)) { Text("‹", color = Color.White) }
                        state.tables.drop(page * 3).take(3).forEach { table ->
                            TextButton(onClick = { tableId = table.id }, modifier = Modifier.weight(1f)) {
                                Text(table.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (table.id == tableId) Color.White else Color.Gray)
                            }
                        }
                        TextButton(onClick = { page++ }, enabled = page < TaskRules.pageCount(state.tables.size) - 1, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(32.dp)) { Text("›", color = Color.White) }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TaskView.entries.forEach { target ->
                            TextButton(onClick = { viewName = target.name }, contentPadding = PaddingValues(6.dp)) {
                                Text(target.label, color = if (target == view) Color.White else Color.Gray)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        if (view != TaskView.DONE) TextButton(onClick = { open("add") }, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(40.dp)) { Text("＋", color = Color.White) }
                        TextButton(onClick = { open("menu") }, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(40.dp)) { Text("⋯", color = Color.White) }
                    }
                    if (visible.isEmpty()) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("还没有${view.label}任务", color = Color.Gray) }
                    } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState) {
                        items(visible, key = { it.id }) { task ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { open(if (task.done) "restore" else "complete", task.id) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(40.dp)) { Text(if (task.done) "☑" else "☐", color = Color.White) }
                                Text(task.title.replace('\n', ' '), Modifier.weight(1f).clickable { open("edit", task.id) }.padding(vertical = 16.dp),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (TaskRules.overdue(task, LocalDateTime.now())) Color(0xFFFF8585) else Color.White)
                                if (view == TaskView.DATED) Text(task.date.toString(), Modifier.width(100.dp).clickable { open("cancel_date", task.id) }.padding(vertical = 16.dp),
                                    color = Color(0xFFFFB366), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { open("delete", task.id) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(36.dp)) { Text("×", color = Color.Gray) }
                            }
                            HorizontalDivider(color = Color(0xFF232323))
                        }
                    }
                }
            }
        }
        if (resetDialog) OperationDialog("重置演示数据？", "当前原型中的修改会被演示样例替换。", "重置", {
            PrototypeStore.resetDemo(this); TaskWidgetProvider.refreshAll(this); revision++; resetDialog = false
        }, { resetDialog = false }, destructive = true)
    }
}
