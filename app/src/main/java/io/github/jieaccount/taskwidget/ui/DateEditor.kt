package io.github.jieaccount.taskwidget.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.jieaccount.taskwidget.data.DateDraft
import io.github.jieaccount.taskwidget.data.DateChoice
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun DateEditor(date: String, time: String, onBack: () -> Unit, onConfirm: (DateChoice?) -> Unit) {
    // 今天在一次候选编辑过程中保持不变，旋转与跨午夜不改变比较基线。
    val initialDay = rememberSaveable { date.ifBlank { LocalDate.now().toString() } }
    val initial = remember(initialDay, time) { DateDraft.from(LocalDate.parse(initialDay), time.takeIf { it.isNotBlank() }?.let(LocalTime::parse)) }
    var year by rememberSaveable { mutableStateOf(initial.year) }
    var month by rememberSaveable { mutableStateOf(initial.month) }
    var day by rememberSaveable { mutableStateOf(initial.day) }
    var hasTime by rememberSaveable { mutableStateOf(initial.hasTime) }
    var hour by rememberSaveable { mutableStateOf(initial.hour) }
    var minute by rememberSaveable { mutableStateOf(initial.minute) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var clear by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }
    val draft = DateDraft(year, month, day, hasTime, hour, minute)
    val leave = { if (draft != initial) discard = true else onBack() }
    val preview = runCatching { draft.parse() }.getOrNull()
    BackHandler(onBack = leave)
    FlowPage("设置日期", leave, footer = {
        Column(Modifier.fillMaxWidth()) {
            // 校验提示与提交按钮一起避让键盘，长表单也能看到失败原因。
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            Button(onClick = {
                runCatching { draft.parse() }.onSuccess { onConfirm(it) }.onFailure { error = it.message.orEmpty() }
            }, modifier = Modifier.fillMaxWidth()) { Text("确认日期") }
        }
    }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("${preview?.let { "${it.date} · ${it.time ?: "无时分"}" } ?: "请填写有效日期"}\n确认后返回任务草稿，保存任务后生效。", Modifier.padding(top = 16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateNumber("年", year, { year = it }, { delta -> year = draft.step("year", delta).year }, Modifier.weight(1.3f))
                DateNumber("月", month, { month = it }, { delta -> month = draft.step("month", delta).month }, Modifier.weight(1f))
                DateNumber("日", day, { day = it }, { delta -> day = draft.step("day", delta).day }, Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("设置时分（24 小时制）", Modifier.weight(1f))
                Switch(hasTime, { hasTime = it })
            }
            if (hasTime) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(hour, { hour = it }, label = { Text("时") }, modifier = Modifier.weight(1f), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(minute, { minute = it }, label = { Text("分") }, modifier = Modifier.weight(1f), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            TextButton(onClick = { if (date.isNotBlank()) clear = true else onConfirm(null) }) { Text("不设置日期") }
        }
    }
    if (discard) OperationDialog("放弃未保存修改？", "日期候选尚未应用到任务。", "放弃修改", onBack, { discard = false }, "继续编辑")
    if (clear) OperationDialog("取消这项任务的日期？", "取消后返回任务草稿，保存任务后生效。", "取消日期", { onConfirm(null) }, { clear = false })
}

@Composable
private fun DateNumber(label: String, value: String, onChange: (String) -> Unit, onStep: (Int) -> Unit, modifier: Modifier) {
    Column(modifier) {
        OutlinedTextField(value, onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = { onStep(-1) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("−") }
            TextButton(onClick = { onStep(1) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text("＋") }
        }
    }
}
