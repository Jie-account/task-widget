# 工程构建与使用

本页负责复现构建、定位代码和使用当前本地功能版本；当前阶段与待办以[进度](progress.md)为准，产品规则以[需求](requirements.md)为准。T-004 的历史宿主验证见[P60 记录](verification/t004-p60.md)，当前本地功能验证见[T-005 离线](verification/t005-offline.md)与[P60 运行闭环](verification/t005-p60.md)。

## 1. 本机构建入口

在项目根目录使用 PowerShell；本机可用 JDK 位于 `JAVA_HOME`。

```powershell
.\tools\prepare-android.ps1 -JavaHome $env:JAVA_HOME
.\tools\build.ps1 -JavaHome $env:JAVA_HOME
```

准备脚本下载并校验固定版本的 Android 命令行工具与 Gradle，安装所需 SDK 包，生成本机 `local.properties`。脚本复用本机已经接受的 SDK 许可；若其他机器提示新许可，可按 sdkmanager 的说明查看和接受后继续。

当前环境使用本机代理下载依赖时，可在准备命令中增加 `-ProxyUrl $env:TASK_WIDGET_HTTPS_PROXY`。该设置只进入忽略的 `.tools/gradle-user-home/gradle.properties`，下载器通过进程环境使用代理。

默认构建依次执行 `testDebugUnitTest`、`assembleDebug`、`lintDebug`。JDK、SDK、依赖缓存和构建输出的本机路径不进入 Git；`.tools` 承载本项目下载的依赖，现有全局 Java/Android 安装保持各自状态。

已配置 JDK 与 SDK 的其他环境可使用标准入口：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

SDK 路径由本机 `local.properties` 或 `ANDROID_HOME` 提供。需要复用本项目隔离缓存时，将进程级 `GRADLE_USER_HOME` 指向 `.tools/gradle-user-home`。

## 2. 从哪里看代码

| 职责 | 入口 |
|---|---|
| 固定构建版本、最低 API、应用标识 | [根插件配置](../build.gradle.kts)、[App 配置](../app/build.gradle.kts)、[Wrapper](../gradle/wrapper/gradle-wrapper.properties) |
| 归类、排序、日期精度和过期 | [TaskModel.kt](../app/src/main/java/io/github/jieaccount/taskwidget/data/TaskModel.kt) |
| 任务/表事务、顺序和入口选择 | [TaskRepository.kt](../app/src/main/java/io/github/jieaccount/taskwidget/data/TaskRepository.kt) |
| 私有原子文件与旧数据迁移 | [TaskStore.kt](../app/src/main/java/io/github/jieaccount/taskwidget/data/TaskStore.kt)、[SnapshotCodec.kt](../app/src/main/java/io/github/jieaccount/taskwidget/data/SnapshotCodec.kt) |
| App 首页与组件添加请求 | [MainActivity.kt](../app/src/main/java/io/github/jieaccount/taskwidget/ui/MainActivity.kt) |
| 编辑、菜单、排序、确认与返回 | [FlowActivity.kt](../app/src/main/java/io/github/jieaccount/taskwidget/ui/FlowActivity.kt) |
| 表管理与日期候选 | [TableManagementActivity.kt](../app/src/main/java/io/github/jieaccount/taskwidget/ui/TableManagementActivity.kt)、[DateEditor.kt](../app/src/main/java/io/github/jieaccount/taskwidget/ui/DateEditor.kt)、[DateDraft.kt](../app/src/main/java/io/github/jieaccount/taskwidget/data/DateDraft.kt) |
| 组件固定顶部、翻页与点击模板 | [TaskWidgetProvider.kt](../app/src/main/java/io/github/jieaccount/taskwidget/widget/TaskWidgetProvider.kt) |
| 滚动集合与行内独立点击 | [TaskListService.kt](../app/src/main/java/io/github/jieaccount/taskwidget/widget/TaskListService.kt)、[组件布局](../app/src/main/res/layout/task_widget.xml)、[行布局](../app/src/main/res/layout/task_row.xml) |
| 规则边界测试 | [TaskRulesTest.kt](../app/src/test/java/io/github/jieaccount/taskwidget/data/TaskRulesTest.kt) |
| 迁移/写入失败/表任务闭环及日期调度测试 | [TaskRepositoryTest.kt](../app/src/test/java/io/github/jieaccount/taskwidget/data/TaskRepositoryTest.kt)、[DateAndRefreshTest.kt](../app/src/test/java/io/github/jieaccount/taskwidget/data/DateAndRefreshTest.kt) |
| 日期边界刷新 | [RefreshRules.kt](../app/src/main/java/io/github/jieaccount/taskwidget/data/RefreshRules.kt)、[WidgetRefreshScheduler.kt](../app/src/main/java/io/github/jieaccount/taskwidget/widget/WidgetRefreshScheduler.kt) |

## 3. 当前版本与数据

当前构建为 `0.2.3-local`（versionCode 5），已安装 P60，显示名称为“任务组件”。应用标识继续使用 `io.github.jieaccount.taskwidget.prototype`，沿用原签名与组件类名，覆盖安装完整快照保持。T-005 已完成关键运行闭环；T-006 已补长内容、新安装、重启及界面反馈，今晚用新版继续隔夜观察，见[验收记录](verification/t006-p60.md)。新版候选包留在 `dist/task-widget-0.2.3-local/`，日常操作见[使用说明](user-guide.md)。

独立 `acceptance` 构建变体使用 `.acceptance` 应用标识后缀和“任务组件·验收”显示名，用于在独立私有目录验证首次启动，避免清空现用主应用。可在构建脚本的 `-Tasks` 列表增加 `assembleAcceptance`；该临时包已完成新安装验收并卸载，日常安装使用主包。

新安装创建一张空的“默认”表。首页“表管理”可新建、重命名、上下移动、删除表；删除前确认完整表名与含已完成的任务数，始终保留至少一张。表删除后同时清理所属任务，并修正 App 与组件的选择。

任务详情中点“设置日期/修改日期”，分别填写年、月、日，可通过加减按钮调整；开启时分时需填全时和分。候选先确认回到任务草稿，再保存任务。过去日期可用，无效月日保留输入并提示；仅日期与 `00:00` 分别保存。详情内支持删除，取消删除后保留编辑草稿。

正式数据位于应用私有目录 `files/tasks-v2.json`，通过 Android `AtomicFile` 整份提交，表、任务、顺序和入口选择共同保存。首次升级会读取旧 `capability_prototype_v1` 偏好中的 v1 快照和组件选择，保留原表、任务、日期精度与排序，并留下旧偏好；新文件存在后优先读取新文件。首页移除了演示数据重置入口，已有原型样例可通过正常表/任务操作处理。

App 与组件共用同一仓储、归类和日期规则；单进程内串行提交。读取损坏或未知版本时保留原数据并显示重试，写入失败保留当前草稿；仓储层的失败行为已做 JVM 测试。P60 覆盖升级、文件重载和冷启动恢复已验证，设备写入故障与损坏注入未执行。

从组件打开的流程使用独立任务栈，结束时移除该流程；从 App 首页进入的流程结束时返回首页。当前日期页、表管理、任务流转和返回的实际结果见[T-005 实机记录](verification/t005-p60.md)。

`0.2.2-local` 起 Compose 与原生窗口主题统一黑底，系统栏使用浅色图标，确认采用深灰表面与浅色／红色操作按钮。`FlowActivity` 使用透明窗口：编辑、日期、菜单、排序由 Scaffold 铺满黑底，行内确认只绘制 Dialog；表删除、详情删除与草稿退出继续叠原页。`0.2.3-local` 已在 P60 检查组件／App 的四类行内确认与取消／系统返回，实际保留原入口。

`0.2.3-local` 详情所属表仅显示表名，日期按已有精度显示年月日或年月日时分，日期候选预览同步；无日期仍显示“未设置日期”。组件与 App 的有日期／无日期空态为“点击右上方+添加任务”，已完成空态为“当前没有已完成任务”；六个空态及详情精度已在 P60 验证。

## 4. 连机后怎样验证

ADB 位于 `.tools/android-sdk/platform-tools/adb.exe`。P60 已完成 USB 调试授权并安装原型；后续连接后按下列命令检查和更新。

```powershell
.\.tools\android-sdk\platform-tools\adb.exe devices -l
.\.tools\android-sdk\platform-tools\adb.exe install -r .\app\build\outputs\apk\debug\app-debug.apk
```

打开 App，通过“添加组件”请求或桌面小组件列表添加，默认使用已认可的 4×4 尺寸。长按组件，出现边框后拖动边缘圆点调整大小，点击外部结束；当前 P60 可向下拉高一行并缩回，横向最小范围由桌面网格和组件最小宽度共同决定。

用已有列表或专用测试表检查顶部固定、滚动、行内各按钮、表翻页、菜单、保存返回和更新；组件选区参数在 `TaskWidget` 标签日志中记录。宿主 options 为系统提供的尺寸范围，与实际像素 bounds 分开记录，见[P60 实测](verification/t004-p60.md)。

组件保留平台 30 分钟周期更新，用户操作后主动刷新；另为最近的未完成任务日期边界安排一个非精确闹钟。仅日期任务取次日零点，定时任务取指定时刻后 1 毫秒，刷新后重新安排最近边界；无组件或没有未来边界时取消闹钟。日期、时间、时区变化、开机及覆盖安装后会重新计算；App 前台也重新计算颜色。P60 亮屏桌面前台/USB 供电样本测得一次约 40.5 秒的边界延迟，详见[记录](verification/t005-p60.md#4-实际后台刷新)，长时间省电条件由 T-006 继续测量。

`0.2.1-local` 增加应用私有 `widget_refresh_diagnostics_v1` 偏好，保存安排的边界、最近边界回调时间及接收时的省电／空闲／交互／充电状态，不含任务内容。隔夜回读先保留这些记录及组件截图，再打开 App；具体步骤见[T-006 第 4 节](verification/t006-p60.md#4-今晚的自然零点与省电观察)。系统日期／时区变化专项验收按 E17 暂缓。
