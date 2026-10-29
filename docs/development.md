# 工程构建与原型使用

本页负责复现构建、定位代码和使用 T-004 能力原型；当前阶段与待办以[进度](progress.md)为准，产品规则以[需求](requirements.md)为准。

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
| 原型样例与演示数据存储 | [PrototypeStore.kt](../app/src/main/java/io/github/jieaccount/taskwidget/data/PrototypeStore.kt) |
| App 首页与组件添加请求 | [MainActivity.kt](../app/src/main/java/io/github/jieaccount/taskwidget/ui/MainActivity.kt) |
| 编辑、菜单、排序、确认与返回 | [FlowActivity.kt](../app/src/main/java/io/github/jieaccount/taskwidget/ui/FlowActivity.kt) |
| 组件固定顶部、翻页与点击模板 | [TaskWidgetProvider.kt](../app/src/main/java/io/github/jieaccount/taskwidget/widget/TaskWidgetProvider.kt) |
| 滚动集合与行内独立点击 | [TaskListService.kt](../app/src/main/java/io/github/jieaccount/taskwidget/widget/TaskListService.kt)、[组件布局](../app/src/main/res/layout/task_widget.xml)、[行布局](../app/src/main/res/layout/task_row.xml) |
| 规则边界测试 | [TaskRulesTest.kt](../app/src/test/java/io/github/jieaccount/taskwidget/data/TaskRulesTest.kt) |

## 3. 能力原型的范围

原型应用标识为 `io.github.jieaccount.taskwidget.prototype`，名称为“任务组件·原型”。首次载入相对本机今天生成的样例数据，首页提供明确的演示数据标识和重置入口，供组件能力验证使用。

本轮代码覆盖表标签翻页、三个子表、固定顶部和滚动集合；任务行分别进入详情、完成/恢复确认、取消日期确认和删除确认；编辑保存、未保存返回提示和无日期排序形成最小更新闭环。日期暂用 `YYYY-MM-DD` 与可选 `HH:mm` 文本输入，保留仅日期与 `00:00` 的区别。

演示数据通过应用私有 SharedPreferences 中的版本化 JSON 跨组件、Activity 和进程重建读取；正式数据层、Room 取舍及迁移、完整表管理、线框日期控件和后台时间边界调度由 T-005 推进。此原型不代表首版业务功能已完成。

从组件打开的流程使用独立任务栈，结束时移除该流程；从 App 首页进入的流程结束时返回首页。P60 上的实际点击、键盘、返回及进程重建结果见[实机记录](verification/t004-p60.md)。

## 4. 连机后怎样验证

ADB 位于 `.tools/android-sdk/platform-tools/adb.exe`。P60 已完成 USB 调试授权并安装原型；后续连接后按下列命令检查和更新。

```powershell
.\.tools\android-sdk\platform-tools\adb.exe devices -l
.\.tools\android-sdk\platform-tools\adb.exe install -r .\app\build\outputs\apk\debug\app-debug.apk
```

打开 App，通过“添加组件”请求或桌面小组件列表添加，默认使用已认可的 4×4 尺寸。长按组件，出现边框后拖动边缘圆点调整大小，点击外部结束；当前 P60 可向下拉高一行并缩回，横向最小范围由桌面网格和组件最小宽度共同决定。

用预置长列表检查顶部固定、滚动、行内各按钮、表翻页、菜单、保存返回和更新；组件选区参数在 `TaskWidget` 标签日志中记录。宿主 options 为系统提供的尺寸范围，与实际像素 bounds 分开记录，见[P60 实测](verification/t004-p60.md)。

原型的组件周期更新设为平台允许的 30 分钟，并监听系统日期、时间、时区变化；用户保存或确认状态操作时主动请求刷新。分钟边界自动调度及省电延迟尚待后续实现和实测。
