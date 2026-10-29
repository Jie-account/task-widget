package io.github.jieaccount.taskwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import io.github.jieaccount.taskwidget.R
import io.github.jieaccount.taskwidget.data.TaskStore
import io.github.jieaccount.taskwidget.data.TaskItem
import io.github.jieaccount.taskwidget.data.TaskRules
import io.github.jieaccount.taskwidget.data.TaskView
import java.time.LocalDateTime

@Suppress("DEPRECATION")
class TaskListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = TaskFactory(applicationContext,
        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))
}

private class TaskFactory(private val context: Context, private val widgetId: Int) : RemoteViewsService.RemoteViewsFactory {
    private var items: List<TaskItem> = emptyList()
    private var view = TaskView.DATED
    override fun onCreate() = onDataSetChanged()
    override fun onDataSetChanged() {
        val selection = TaskStore.selection(context, widgetId)
        view = selection.view
        items = TaskRules.visible(TaskStore.load(context).tasks, selection.tableId, view)
    }
    override fun onDestroy() { items = emptyList() }
    override fun getCount() = items.size
    override fun getViewTypeCount() = 1
    override fun hasStableIds() = true
    override fun getItemId(position: Int) = items.getOrNull(position)?.id ?: -1L
    override fun getLoadingView(): RemoteViews? = null

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.task_row)
        val task = items.getOrNull(position) ?: return views
        views.setTextViewText(R.id.task_check, if (task.done) "☑" else "☐")
        views.setTextViewText(R.id.task_title, task.title.replace('\n', ' '))
        views.setTextColor(R.id.task_title, if (TaskRules.overdue(task, LocalDateTime.now())) Color.rgb(255, 133, 133) else Color.WHITE)
        views.setTextViewText(R.id.task_date, task.date?.toString() ?: "")
        views.setViewVisibility(R.id.task_date, if (view == TaskView.DATED) View.VISIBLE else View.GONE)
        val targets = listOf(
            Triple(R.id.task_check, if (task.done) "restore" else "complete", if (task.done) "恢复未完成" else "完成任务"),
            Triple(R.id.task_title, "edit", "查看详情"),
            Triple(R.id.task_date, "cancel_date", "取消日期"),
            Triple(R.id.task_delete, "delete", "删除任务"),
        )
        targets.forEach { (viewId, action, label) ->
            views.setContentDescription(viewId, "$label：${task.title}")
            views.setOnClickFillInIntent(viewId, Intent().putExtra("flow", action).putExtra("taskId", task.id))
        }
        return views
    }
}
