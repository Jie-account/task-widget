# T-013 组件加载占位：离线结果

- 执行：S-002，2026-11-27；候选：0.3.3-local／code12，release变体。
- 本轮按用户安排完成修改、必要离线检查与源码推送；手机安装和实际视觉效果由用户后续安排。

## 修改与代价

`RemoteViewsFactory.getLoadingView()`返回null会采用系统默认加载行。现改为显式返回`task_loading.xml`，使用RemoteViews支持的FrameLayout：52dp高、黑底、无文字或操作，与任务行等高。

加载过程继续异步执行，实际行就绪后替换占位；过渡时可能短暂留空。集合绑定、缓存键、行点击、数据保存和JSON格式沿用原实现。

## 必要检查

`assembleRelease`、`lintRelease`通过，lint为0错误／29警告。核对APK中的编译布局包含黑色空白FrameLayout，任务行和占位均为52dp；包版本、非debuggable属性及签名核对通过。检查集中在资源、RemoteViews回调及构建兼容性，本次未运行无关业务测试、连接手机或迁移数据；实际切换显示尚待人工确认。

新增的一项Overdraw提示基于App主题推断；该布局实际由桌面承载，显式黑底用于保持加载过渡颜色。其余28项沿用既有警告。

```powershell
.\tools\build.ps1 -JavaHome $env:JAVA_HOME -Tasks @('assembleRelease', 'lintRelease', '--offline')
```

## 候选包与后续安装

| 项目 | 值 |
|---|---|
| 留存 | `dist/task-widget-0.3.3-local/task-widget-0.3.3-local.apk`，附SHA256SUMS.txt |
| 大小 | 6,945,390字节 |
| APK SHA256 | `3f7754f822641864b373b8c6faf76a42837761b7e3b020053d21ce165ed4c82e` |
| 签名证书SHA256 | `41fa9c0bb358a98cd2a73915000607865532cbe641a57b930a6aee0fba54341d` |

本轮仅推送源码，正式Release仍为v0.3.2。用户安排安装时，先从手机导出当时最新JSON并留存组件选择，再同签名覆盖升级和核对；如需恢复或迁移，以当轮最新备份为基线。私人备份继续保存在忽略目录。
