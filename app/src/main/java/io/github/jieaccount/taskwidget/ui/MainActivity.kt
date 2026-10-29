package io.github.jieaccount.taskwidget.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.jieaccount.taskwidget.data.*
import io.github.jieaccount.taskwidget.widget.TaskWidgetProvider
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.Duration
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var revision by mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enablePrototypeEdgeToEdge()
        setContent { PrototypeTheme { Home(revision) } }
    }
    override fun onResume() {
        super.onResume(); revision++
        runCatching { TaskWidgetProvider.refreshAll(this) }.onFailure { android.util.Log.e("TaskWidget", "首页刷新失败", it) }
    }

    private fun addWidget() {
        val manager = AppWidgetManager.getInstance(this)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(this, TaskWidgetProvider::class.java), null, null)
        } else Toast.makeText(this, "请从桌面的小组件列表添加“任务组件”", Toast.LENGTH_LONG).show()
    }

    @Composable
    private fun Home(version: Int) {
        DisposableEffect(Unit) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) { revision++ }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_DATE_CHANGED)
            }
            ContextCompat.registerReceiver(this@MainActivity, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            onDispose { unregisterReceiver(receiver) }
        }
        val loaded = remember(version) { runCatching { TaskStore.load(this) }.onFailure { android.util.Log.e("TaskWidget", "读取任务失败", it) } }
        val state = loaded.getOrNull()
        if (state == null) { DataReadError({ finish() }, { revision++ }); return }
        var selected by remember(version) { mutableStateOf(state.selections[TaskRepository.APP_ENTRANCE] ?: WidgetSelection(state.tables.first().id)) }
        val tableId = selected.tableId
        val view = selected.view
        val page = selected.page
        fun choose(next: WidgetSelection) {
            runCatching { TaskStore.select(this, TaskRepository.APP_ENTRANCE, next) }
                .onSuccess { selected = next }
                .onFailure { Toast.makeText(this, "选择保存失败，请重试", Toast.LENGTH_SHORT).show() }
        }
        var now by remember { mutableStateOf(LocalDateTime.now()) }
        LaunchedEffect(version, state.tasks) {
            while (true) {
                val current = ZonedDateTime.now()
                now = current.toLocalDateTime()
                val boundary = RefreshRules.nextBoundary(state.tasks, current) ?: break
                delay(Duration.between(current.toInstant(), boundary).toMillis().coerceAtLeast(1))
            }
        }
        val visible = TaskRules.visible(state.tasks, tableId, view)
        val tableName = state.tables.find { it.id == tableId }?.name.orEmpty()
        val listState = rememberLazyListState()
        LaunchedEffect(tableId, view) { listState.scrollToItem(0) }
        fun open(action: String, taskId: Long = 0) = startActivity(FlowActivity.intent(this, action, tableId, view, taskId))
        Scaffold(topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("任务 · $tableName", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { startActivity(Intent(this@MainActivity, TableManagementActivity::class.java)) }) { Text("表管理") }
                TextButton(onClick = ::addWidget) { Text("添加组件") }
            }
        }) { insets ->
            Column(Modifier.fillMaxSize().padding(insets)) {
                Column(Modifier.weight(1f).fillMaxWidth().background(Color.Black).padding(horizontal = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { choose(selected.copy(page = page - 1)) }, enabled = page > 0, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(32.dp)) { Text("‹", color = Color.White) }
                        state.tables.drop(page * 3).take(3).forEach { table ->
                            TextButton(onClick = { choose(selected.copy(tableId = table.id)) }, modifier = Modifier.weight(1f)) {
                                Text(table.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (table.id == tableId) Color.White else Color.Gray)
                            }
                        }
                        TextButton(onClick = { choose(selected.copy(page = page + 1)) }, enabled = page < TaskRules.pageCount(state.tables.size) - 1, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(32.dp)) { Text("›", color = Color.White) }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TaskView.entries.forEach { target ->
                            TextButton(onClick = { choose(selected.copy(view = target)) }, contentPadding = PaddingValues(6.dp)) {
                                Text(target.label, color = if (target == view) Color.White else Color.Gray)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        if (view != TaskView.DONE) TextButton(onClick = { open("add") }, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(40.dp)) { Text("＋", color = Color.White) }
                        TextButton(onClick = { open("menu") }, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(40.dp)) { Text("⋯", color = Color.White) }
                    }
                    if (visible.isEmpty()) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text(if (view == TaskView.DONE) "当前没有已完成任务" else "点击右上方+添加任务", color = Color.Gray)
                        }
                    } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState) {
                        items(visible, key = { it.id }) { task ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { open(if (task.done) "restore" else "complete", task.id) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(40.dp)) { Text(if (task.done) "☑" else "☐", color = Color.White) }
                                Text(task.title.replace('\n', ' '), Modifier.weight(1f).clickable { open("edit", task.id) }.padding(vertical = 16.dp),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (TaskRules.overdue(task, now)) Color(0xFFFF8585) else Color.White)
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
    }
}
