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
import io.github.jieaccount.taskwidget.data.DateDisplay
import io.github.jieaccount.taskwidget.data.DateDisplayMode
import io.github.jieaccount.taskwidget.data.DateDisplayPreferences
import java.time.LocalDateTime

@Suppress("DEPRECATION")
class TaskListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val selection = TaskStore.selection(applicationContext, widgetId)
        // 每个集合固定自己的显示上下文；旧宿主缓存入口沿用当前选择。
        return TaskFactory(applicationContext, intent.getLongExtra("tableId", selection.tableId),
            TaskView.valueOf(intent.getStringExtra("view") ?: selection.view.name),
            intent.getStringExtra("dateMode")?.let(DateDisplayMode::fromPreference) ?: DateDisplayPreferences.read(applicationContext))
    }
}

private class TaskFactory(private val context: Context, private val tableId: Long, private val view: TaskView,
                          private val dateMode: DateDisplayMode) : RemoteViewsService.RemoteViewsFactory {
    private var items: List<TaskItem> = emptyList()
    override fun onCreate() = onDataSetChanged()
    override fun onDataSetChanged() {
        items = TaskRules.visible(TaskStore.load(context).tasks, tableId, view)
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
        val now = LocalDateTime.now()
        views.setTextColor(R.id.task_title, if (TaskRules.overdue(task, now)) Color.rgb(255, 133, 133) else Color.WHITE)
        views.setTextViewText(R.id.task_date, task.date?.let { DateDisplay.label(it, now.toLocalDate(), dateMode) } ?: "")
        views.setViewVisibility(R.id.task_date, if (view == TaskView.DATED) View.VISIBLE else View.GONE)
        val targets = listOf(
            Triple(R.id.task_check, if (task.done) "restore" else "complete", if (task.done) "恢复未完成" else "完成任务"),
            Triple(R.id.task_title, "edit", "查看详情"),
            Triple(R.id.task_date, "toggle_date_display", dateMode.toggleDescription),
            Triple(R.id.task_delete, "delete", "删除任务"),
        )
        targets.forEach { (viewId, action, label) ->
            views.setContentDescription(viewId, "$label：${task.title}")
            views.setOnClickFillInIntent(viewId, Intent().putExtra("flow", action).putExtra("taskId", task.id))
        }
        return views
    }
}
