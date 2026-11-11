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
import io.github.jieaccount.taskwidget.data.TaskStore
import io.github.jieaccount.taskwidget.data.TaskRules
import io.github.jieaccount.taskwidget.data.TaskView
import io.github.jieaccount.taskwidget.ui.FlowActivity

/** 固定导航由 Provider 更新，滚动任务集合交给 RemoteViewsService。 */
class TaskWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { refresh(context, it) }
        WidgetRefreshScheduler.schedule(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        val dimensions = listOf(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
            AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            .joinToString { "$it=${options.getInt(it)}" }
        Log.i("TaskWidget", "widget=$id $dimensions density=${context.resources.displayMetrics.density}")
        refresh(context, id)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { TaskStore.forgetWidget(context, it) }
        WidgetRefreshScheduler.schedule(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WidgetRefreshScheduler.ACTION) WidgetRefreshScheduler.recordReceive(context)
        super.onReceive(context, intent)
        if (intent.action == NAVIGATE) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
            val state = TaskStore.load(context)
            val old = TaskStore.selection(context, id)
            val next = when (intent.getStringExtra("kind")) {
                "table" -> old.copy(tableId = intent.getLongExtra("table", old.tableId))
                "view" -> old.copy(view = TaskView.valueOf(intent.getStringExtra("view")!!))
                "page" -> old.copy(page = (old.page + intent.getIntExtra("delta", 0)).coerceIn(0, TaskRules.pageCount(state.tables.size) - 1))
                else -> old
            }
            TaskStore.select(context, id, next)
            refresh(context, id, resetScroll = intent.getStringExtra("kind") != "page")
        } else if (intent.action in listOf(Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, WidgetRefreshScheduler.ACTION)) {
            refreshAll(context)
        }
    }

    companion object {
        private const val NAVIGATE = "io.github.jieaccount.taskwidget.NAVIGATE"
        fun refreshAll(context: Context) {
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, TaskWidgetProvider::class.java))
                .forEach { refresh(context, it) }
            WidgetRefreshScheduler.schedule(context)
        }

        @Suppress("DEPRECATION") // 保留 API 26 起的集合组件路径，API 35 仍支持。
        fun refresh(context: Context, id: Int, resetScroll: Boolean = false) {
            val state = TaskStore.load(context)
            val selection = TaskStore.selection(context, id)
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
            val lastPage = TaskRules.pageCount(state.tables.size) - 1
            val nextEnabled = selection.page < lastPage
            // 新建表入口只在最后一页显示，独立于下方的添加任务按钮。
            views.setViewVisibility(R.id.add_table, if (selection.page == lastPage) View.VISIBLE else View.GONE)
            views.setOnClickPendingIntent(R.id.add_table, flow(context, id, "new_table"))
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
            // 集合行需要 fill-in Intent；菜单另需接收系统提供的点击矩形。
            val rowIntent = FlowActivity.intent(context, "row", selection.tableId, selection.view, widgetId = id)
            views.setPendingIntentTemplate(R.id.task_list, PendingIntent.getActivity(context, 0, rowIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))
            val message = if (selection.view == TaskView.DONE) "当前没有已完成任务" else "点击右上方+添加任务"
            views.setTextViewText(R.id.empty_list, message)
            views.setEmptyView(R.id.task_list, R.id.empty_list)
            if (resetScroll) views.setScrollPosition(R.id.task_list, 0)
            manager.updateAppWidget(id, views)
            manager.notifyAppWidgetViewDataChanged(id, R.id.task_list)
        }

        private fun flow(context: Context, id: Int, action: String): PendingIntent {
            val selection = TaskStore.selection(context, id)
            // RemoteViews 通过 sourceBounds 传入菜单按钮位置，可变入口才能接收该矩形。
            val mutability = if (action == "menu") PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getActivity(context, 0, FlowActivity.intent(context, action, selection.tableId, selection.view, widgetId = id),
                PendingIntent.FLAG_UPDATE_CURRENT or mutability)
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
