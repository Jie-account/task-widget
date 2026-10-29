package io.github.jieaccount.taskwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import io.github.jieaccount.taskwidget.R
import io.github.jieaccount.taskwidget.data.PrototypeStore
import io.github.jieaccount.taskwidget.data.TaskRules
import io.github.jieaccount.taskwidget.data.TaskView
import io.github.jieaccount.taskwidget.ui.FlowActivity

/** 固定导航由 Provider 更新，滚动任务集合交给 RemoteViewsService。 */
class TaskWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { refresh(context, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        Log.i("TaskWidget", "widget=$id options=$options density=${context.resources.displayMetrics.density}")
        refresh(context, id)
    }

    override fun onDeleted(context: Context, ids: IntArray) = ids.forEach { PrototypeStore.forgetWidget(context, it) }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == NAVIGATE) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
            val state = PrototypeStore.load(context)
            val old = PrototypeStore.selection(context, id)
            val next = when (intent.getStringExtra("kind")) {
                "table" -> old.copy(tableId = intent.getLongExtra("table", old.tableId))
                "view" -> old.copy(view = TaskView.valueOf(intent.getStringExtra("view")!!))
                "page" -> old.copy(page = (old.page + intent.getIntExtra("delta", 0)).coerceIn(0, TaskRules.pageCount(state.tables.size) - 1))
                else -> old
            }
            PrototypeStore.select(context, id, next)
            refresh(context, id, resetScroll = intent.getStringExtra("kind") != "page")
        } else if (intent.action in listOf(Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) {
            refreshAll(context)
        }
    }

    companion object {
        private const val NAVIGATE = "io.github.jieaccount.taskwidget.NAVIGATE"
        fun refreshAll(context: Context) {
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, TaskWidgetProvider::class.java))
                .forEach { refresh(context, it) }
        }

        @Suppress("DEPRECATION") // 保留 API 26 起的集合组件路径，API 35 仍支持。
        fun refresh(context: Context, id: Int, resetScroll: Boolean = false) {
            val state = PrototypeStore.load(context)
            val selection = PrototypeStore.selection(context, id)
            val views = RemoteViews(context.packageName, R.layout.task_widget)
            val manager = AppWidgetManager.getInstance(context)
            val tabs = intArrayOf(R.id.table_one, R.id.table_two, R.id.table_three)
            tabs.forEachIndexed { index, viewId ->
                val table = state.tables.getOrNull(selection.page * 3 + index)
                views.setViewVisibility(viewId, if (table == null) View.INVISIBLE else View.VISIBLE)
                if (table != null) {
                    views.setTextViewText(viewId, table.name)
                    views.setContentDescription(viewId, "切换表：${table.name}")
                    views.setInt(viewId, "setBackgroundResource", if (table.id == selection.tableId) R.drawable.selected_tab else android.R.color.transparent)
                    views.setOnClickPendingIntent(viewId, navigation(context, id, "table", table = table.id))
                }
            }
            listOf(R.id.view_dated to TaskView.DATED, R.id.view_undated to TaskView.UNDATED, R.id.view_done to TaskView.DONE).forEach { (viewId, view) ->
                views.setTextColor(viewId, if (view == selection.view) Color.WHITE else Color.LTGRAY)
                views.setInt(viewId, "setBackgroundResource", if (view == selection.view) R.drawable.selected_tab else android.R.color.transparent)
                views.setOnClickPendingIntent(viewId, navigation(context, id, "view", view = view))
            }
            val previousEnabled = selection.page > 0
            val nextEnabled = selection.page < TaskRules.pageCount(state.tables.size) - 1
            listOf(Triple(R.id.previous_page, -1, previousEnabled), Triple(R.id.next_page, 1, nextEnabled)).forEach { (viewId, delta, enabled) ->
                views.setBoolean(viewId, "setEnabled", enabled)
                views.setTextColor(viewId, if (enabled) Color.WHITE else Color.DKGRAY)
                views.setOnClickPendingIntent(viewId, navigation(context, id, "page", delta = delta))
            }
            views.setViewVisibility(R.id.add_task, if (selection.view == TaskView.DONE) View.GONE else View.VISIBLE)
            views.setOnClickPendingIntent(R.id.add_task, flow(context, id, "add"))
            views.setOnClickPendingIntent(R.id.open_menu, flow(context, id, "menu"))
            val adapter = Intent(context, TaskListService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .setData(Uri.parse("taskwidget://collection/$id"))
            views.setRemoteAdapter(R.id.task_list, adapter)
            // 集合行需要 fill-in Intent；仅此模板可变，固定按钮均使用不可变 PendingIntent。
            val rowIntent = FlowActivity.intent(context, "row", selection.tableId, selection.view, widgetId = id)
            views.setPendingIntentTemplate(R.id.task_list, PendingIntent.getActivity(context, 0, rowIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))
            val message = when (selection.view) {
                TaskView.DATED -> "还没有有日期任务\n点击右上方 ＋ 添加任务"
                TaskView.UNDATED -> "还没有无日期任务\n点击右上方 ＋ 添加任务"
                TaskView.DONE -> "还没有已完成任务\n完成的任务会显示在这里"
            }
            views.setTextViewText(R.id.empty_list, message)
            views.setEmptyView(R.id.task_list, R.id.empty_list)
            if (resetScroll) views.setScrollPosition(R.id.task_list, 0)
            manager.updateAppWidget(id, views)
            manager.notifyAppWidgetViewDataChanged(id, R.id.task_list)
        }

        private fun flow(context: Context, id: Int, action: String): PendingIntent {
            val selection = PrototypeStore.selection(context, id)
            return PendingIntent.getActivity(context, 0, FlowActivity.intent(context, action, selection.tableId, selection.view, widgetId = id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun navigation(context: Context, id: Int, kind: String, table: Long = 0, view: TaskView = TaskView.DATED, delta: Int = 0): PendingIntent {
            val intent = Intent(context, TaskWidgetProvider::class.java).setAction(NAVIGATE)
                .setData(Uri.parse("taskwidget://navigation/$id/$kind/$table/${view.name}/$delta"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra("kind", kind)
                .putExtra("table", table).putExtra("view", view.name).putExtra("delta", delta)
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
