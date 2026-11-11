# task-widget

以桌面小组件为主要入口的安卓任务记录软件，默认采用 4×4 尺寸，支持拖动边框调整大小。

**首版完成标志：** 在用户的华为 P60 上完成[首版需求第 9 节](docs/requirements.md)的验收，交付可日常使用的安装包与简明使用说明。

首版验收已完成，用电与自动过期已有人工验收。当前版本 **v0.3.1** 新增组件末页新建表，正式APK已安装P60并通过新功能及必要回归，旧版数据已导入并核对一致，见[验证摘要](docs/verification/t011-p60.md)。安装包、使用说明和校验文件见 [GitHub Release](https://github.com/Jie-account/task-widget/releases/tag/v0.3.1)，交付范围见[发布说明](docs/releases/v0.3.1.md)。

支持三个任务视图、表管理、拖拽排序及JSON整体导入／导出，操作见[使用说明](docs/user-guide.md)。正式包沿用原型签名，可覆盖升级。

组件表标签最后一页的“＋”可直接新建表，保存后返回桌面并自动选中新表。当前交付状态见[任务清单](docs/progress.md)。

## 从这里接续


1. 阅读根目录 [AGENTS.md](AGENTS.md)，定位本项目规则与阅读路径。
2. 查看[当前状态与唯一任务清单](docs/progress.md)，确定本次任务。
3. 阅读近期[会话行动记录](docs/session-log.md)，登记身份后展开相关正文。

可复制的新会话接续说明见[当前状态文档第 5 节](docs/progress.md)。

## 版本管理

GitHub 公开仓库：[Jie-account/task-widget](https://github.com/Jie-account/task-widget)，主分支为 `main`。

```bash
git clone git@github.com:Jie-account/task-widget.git
```

## 按问题查找

| 要了解什么 | 正文入口 |
|---|---|
| 用户已确认哪些行为、怎样验收 | [首版需求](docs/requirements.md) |
| 桌面组件与 App 怎样布局、怎样操作 | [当前交互说明](docs/wireframes.md)；[交互线框 v0.4 历史快照](docs/design/wireframes.html) |
| 原生安卓、桌面组件、跨品牌兼容与设备条件 | [技术方向](docs/technical-direction.md) |
| 怎样构建、定位代码与连机使用 | [工程构建与使用](docs/development.md)；[T-005 实机验证](docs/verification/t005-p60.md) |
| 怎样安装和日常操作、当前验收结果 | [简明使用说明](docs/user-guide.md)；[T-006 验收](docs/verification/t006-p60.md) |
| 拖拽和JSON备份怎样使用、验证范围 | [T-008实测](docs/verification/t008-p60.md)；[JSON格式与恢复](docs/json-backup.md) |
| 当前做到哪里、下一项任务与未定条件 | [当前状态与唯一任务清单](docs/progress.md) |
| 谁做了什么、结果在哪里 | [会话行动记录](docs/session-log.md) |
| 暂缓与候选功能 | [后续扩展方向](docs/future-directions.md) |
| 公开内容与发布维护 | [公开维护说明](docs/publication.md) |

