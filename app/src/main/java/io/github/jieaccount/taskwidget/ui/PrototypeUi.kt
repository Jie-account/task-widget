package io.github.jieaccount.taskwidget.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** App 页面统一黑底，系统栏使用浅色图标。 */
fun ComponentActivity.enablePrototypeEdgeToEdge() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
    )
}

@Composable
fun PrototypeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color.White, onPrimary = Color.Black,
        secondary = Color(0xFFD0D0D0), onSecondary = Color.Black,
        background = Color.Black, onBackground = Color.White,
        surface = Color.Black, onSurface = Color.White,
        surfaceVariant = Color(0xFF202020), onSurfaceVariant = Color(0xFFC7C7C7),
        outline = Color(0xFF808080), error = Color(0xFFFF8585), onError = Color.Black,
        surfaceTint = Color.Transparent,
    ), content = content)
}

@Composable
fun DataReadError(onBack: () -> Unit, onRetry: () -> Unit) {
    FlowPage("读取任务", onBack) {
        Text("暂时无法读取任务，原数据已保留。", Modifier.padding(vertical = 24.dp))
        Button(onClick = onRetry) { Text("重试") }
    }
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
fun OperationDialog(title: String, message: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Color(0xFF1C1C1C),
        tonalElevation = 0.dp,
        title = { Text(title) },
        text = { Text(message, Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("确认") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("取消") } },
    )
}
