package io.github.jieaccount.taskwidget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity

/** 长按一行拖动，越过其他行时调整草稿顺序；停在列表边缘可继续滚动。 */
@Composable
fun DragReorderList(ids: List<Long>, onMove: (List<Long>) -> Unit, onDrop: () -> Unit,
                    modifier: Modifier = Modifier, content: @Composable (Long) -> Unit) {
    val list = rememberLazyListState()
    val latestIds by rememberUpdatedState(ids)
    val move by rememberUpdatedState(onMove)
    val drop by rememberUpdatedState(onDrop)
    var dragging by remember { mutableStateOf<Long?>(null) }
    var start by remember { mutableFloatStateOf(0f) }
    var distance by remember { mutableFloatStateOf(0f) }
    var itemHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val edge = with(density) { 56.dp.toPx() }
    val step = with(density) { 14.dp.toPx() }

    fun reorder() {
        val id = dragging ?: return
        val current = list.layoutInfo.visibleItemsInfo.find { it.key == id } ?: return
        val from = latestIds.indexOf(id)
        // 等待上一笔顺序反映到布局，避免连续指针事件将同一行来回交换。
        if (current.index != from) return
        val center = start + distance + itemHeight / 2f
        val visible = list.layoutInfo.visibleItemsInfo
        val target = (visible.find { center >= it.offset && center < it.offset + it.size }
            ?: if (center < (visible.firstOrNull()?.offset ?: 0)) visible.firstOrNull() else visible.lastOrNull())
            ?.takeIf { it.key != id } ?: return
        val to = latestIds.indexOf(target.key)
        if (from >= 0 && to >= 0 && from != to) move(latestIds.toMutableList().also { it.add(to, it.removeAt(from)) })
    }

    fun finish() { if (dragging != null) { dragging = null; distance = 0f; drop() } }

    LaunchedEffect(dragging) {
        while (dragging != null) {
            withFrameNanos { }
            val top = start + distance
            val bottom = top + itemHeight
            val info = list.layoutInfo
            val scroll = when {
                top < info.viewportStartOffset + edge -> -step
                bottom > info.viewportEndOffset - edge -> step
                else -> 0f
            }
            if (scroll != 0f) { list.scrollBy(scroll); reorder() }
        }
    }

    LazyColumn(state = list, modifier = modifier) {
        items(ids, key = { it }) { id ->
            val active = dragging == id
            val offset = list.layoutInfo.visibleItemsInfo.find { it.key == id }?.offset ?: 0
            Box(Modifier.fillMaxWidth().zIndex(if (active) 1f else 0f).graphicsLayer {
                translationY = if (active) start + distance - offset else 0f
            }.background(if (active) Color(0xFF242424) else Color.Black).pointerInput(id, list) {
                // 行先于父列表消费长按后的位移，避免滚动手势抢走拖拽。
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        list.layoutInfo.visibleItemsInfo.find { it.key == id }?.let {
                            dragging = id; start = it.offset.toFloat(); itemHeight = it.size; distance = 0f
                        }
                    },
                    onDrag = { change, amount -> if (dragging != null) { change.consume(); distance += amount.y; reorder() } },
                    onDragEnd = ::finish,
                    onDragCancel = ::finish,
                )
            }) { content(id) }
        }
    }
}

@Composable
fun DragHandle() {
    Box(Modifier.width(40.dp).height(48.dp).semantics { contentDescription = "长按拖动排序" }, contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text("≡", fontSize = 22.sp, color = Color.LightGray)
    }
}
