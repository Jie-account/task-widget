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

当前构建为 `0.2-local`（versionCode 2），显示名称为“任务组件”。应用标识继续使用 `io.github.jieaccount.taskwidget.prototype`，沿用原签名与组件类名，方便覆盖升级并保留原有组件。T-005 已完成本机测试及 P60 关键运行闭环，完整首版条件继续按 T-006 验收。

新安装创建一张空的“默认”表。首页“表管理”可新建、重命名、上下移动、删除表；删除前确认完整表名与含已完成的任务数，始终保留至少一张。表删除后同时清理所属任务，并修正 App 与组件的选择。

任务详情中点“设置日期/修改日期”，分别填写年、月、日，可通过加减按钮调整；开启时分时需填全时和分。候选先确认回到任务草稿，再保存任务。过去日期可用，无效月日保留输入并提示；仅日期与 `00:00` 分别保存。详情内支持删除，取消删除后保留编辑草稿。

正式数据位于应用私有目录 `files/tasks-v2.json`，通过 Android `AtomicFile` 整份提交，表、任务、顺序和入口选择共同保存。首次升级会读取旧 `capability_prototype_v1` 偏好中的 v1 快照和组件选择，保留原表、任务、日期精度与排序，并留下旧偏好；新文件存在后优先读取新文件。首页移除了演示数据重置入口，已有原型样例可通过正常表/任务操作处理。

App 与组件共用同一仓储、归类和日期规则；单进程内串行提交。读取损坏或未知版本时保留原数据并显示重试，写入失败保留当前草稿；仓储层的失败行为已做 JVM 测试。P60 覆盖升级、文件重载和冷启动恢复已验证，设备写入故障与损坏注入未执行。

从组件打开的流程使用独立任务栈，结束时移除该流程；从 App 首页进入的流程结束时返回首页。当前日期页、表管理、任务流转和返回的实际结果见[T-005 实机记录](verification/t005-p60.md)。

## 4. 连机后怎样验证

ADB 位于 `.tools/android-sdk/platform-tools/adb.exe`。P60 已完成 USB 调试授权并安装原型；后续连接后按下列命令检查和更新。

```powershell
.\.tools\android-sdk\platform-tools\adb.exe devices -l
.\.tools\android-sdk\platform-tools\adb.exe install -r .\app\build\outputs\apk\debug\app-debug.apk
```

打开 App，通过“添加组件”请求或桌面小组件列表添加，默认使用已认可的 4×4 尺寸。长按组件，出现边框后拖动边缘圆点调整大小，点击外部结束；当前 P60 可向下拉高一行并缩回，横向最小范围由桌面网格和组件最小宽度共同决定。

用预置长列表检查顶部固定、滚动、行内各按钮、表翻页、菜单、保存返回和更新；组件选区参数在 `TaskWidget` 标签日志中记录。宿主 options 为系统提供的尺寸范围，与实际像素 bounds 分开记录，见[P60 实测](verification/t004-p60.md)。

组件保留平台 30 分钟周期更新，用户操作后主动刷新；另为最近的未完成任务日期边界安排一个非精确闹钟。仅日期任务取次日零点，定时任务取指定时刻后 1 毫秒，刷新后重新安排最近边界；无组件或没有未来边界时取消闹钟。日期、时间、时区变化、开机及覆盖安装后会重新计算；App 前台也重新计算颜色。P60 亮屏桌面前台/USB 供电样本测得一次约 40.5 秒的边界延迟，详见[记录](verification/t005-p60.md#4-实际后台刷新)，长时间省电条件由 T-006 继续测量。
