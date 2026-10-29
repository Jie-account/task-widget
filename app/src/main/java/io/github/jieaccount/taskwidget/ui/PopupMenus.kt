package io.github.jieaccount.taskwidget.ui

import android.graphics.Rect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.jieaccount.taskwidget.data.TaskView
import kotlin.math.roundToInt

/** App 与桌面组件沿用相同的子表菜单项。 */
@Composable
fun SubtableMenuItems(view: TaskView, onSort: () -> Unit) {
    if (view == TaskView.UNDATED) {
        DropdownMenuItem(text = { Text("修改排序") }, onClick = onSort)
    } else {
        DropdownMenuItem(text = { Text("当前子表暂无可用操作") }, onClick = {}, enabled = false)
    }
}

/** RemoteViews 的点击矩形为屏幕坐标，透明窗口据此在原按钮下方展开菜单。 */
@Composable
fun WidgetSubtableMenu(anchor: Rect?, view: TaskView, onDismiss: () -> Unit, onSort: () -> Unit) {
    val host = LocalView.current
    val density = LocalDensity.current
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var origin by remember { mutableStateOf(IntOffset.Zero) }
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    val margin = with(density) { 8.dp.roundToPx() }
    val gap = with(density) { 4.dp.roundToPx() }
    val preferredWidth = with(density) { 200.dp.roundToPx() }
    val menuInteraction = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
        val screen = IntArray(2)
        host.getLocationOnScreen(screen)
        val local = coordinates.positionInRoot()
        origin = IntOffset(screen[0] + local.x.roundToInt(), screen[1] + local.y.roundToInt())
        viewport = coordinates.size
    }.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)) {
        if (viewport.width > 0) {
            val width = preferredWidth.coerceAtMost((viewport.width - margin * 2).coerceAtLeast(1))
            val right = anchor?.right?.minus(origin.x) ?: (viewport.width - margin)
            val below = anchor?.bottom?.minus(origin.y) ?: margin
            val left = (right - width).coerceIn(margin, (viewport.width - width - margin).coerceAtLeast(margin))
            // 可用空间不足时收敛到屏幕内；正常位置优先贴在按钮下方。
            val top = (below + gap).coerceIn(margin, (viewport.height - menuSize.height - margin).coerceAtLeast(margin))
            Surface(
                modifier = Modifier.offset { IntOffset(left, top) }.width(with(density) { width.toDp() })
                    .onSizeChanged { menuSize = it }
                    .clickable(interactionSource = menuInteraction, indication = null) {},
                shape = RoundedCornerShape(8.dp), color = Color(0xFF1C1C1C), shadowElevation = 8.dp,
            ) {
                Column(Modifier.padding(vertical = 8.dp)) { SubtableMenuItems(view, onSort) }
            }
        }
    }
}
