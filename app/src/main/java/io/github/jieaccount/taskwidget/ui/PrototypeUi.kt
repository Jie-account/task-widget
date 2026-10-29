package io.github.jieaccount.taskwidget.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PrototypeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Color(0xFF333333), background = Color(0xFFFAF9F6), surface = Color(0xFFFAF9F6),
    ), content = content)
}

/** 输入页统一避让系统栏与键盘，底部保存按钮保持可达。 */
@Composable
fun FlowPage(title: String, onBack: () -> Unit, footer: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp)) {
                TextButton(onClick = onBack) { Text("‹ 返回") }
                Text(title, Modifier.weight(1f).padding(14.dp), style = MaterialTheme.typography.titleMedium)
            }
        },
        bottomBar = {
            if (footer != null) Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) { footer() }
        },
    ) { insets -> Column(Modifier.fillMaxSize().padding(insets).padding(horizontal = 18.dp), content = content) }
}

@Composable
fun OperationDialog(title: String, message: String, accept: String, onConfirm: () -> Unit, onCancel: () -> Unit, cancel: String = "取消", destructive: Boolean = false) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = { Text(message, Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = if (destructive) Color(0xFFAA3930) else MaterialTheme.colorScheme.primary)) { Text(accept) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(cancel) } },
    )
}
