# JavaFX GUI vs Compose-App 对比分析报告

**日期**: 2026-05-12  
**分析范围**: JavaFX (`java-fx-app/`) vs Compose for Desktop (`compose-app/`)

---

## 概述

两个 GUI 实现均依赖于相同的 `:backend` 核心模块（`BackendBridge`、`AgentLoop`、配置管理等）。JavaFX 版已成熟稳定，Compose-App 正在并行开发中。以下是逐项对比。

---

## 一、Compose-App 缺失的功能（JavaFX 有，Compose 无）

### 1. 项目绑定管理（Project System）

| 组件 | JavaFX | Compose-App |
|------|--------|-------------|
| `ProjectPopover` | ✅ 项目绑定/解绑弹窗，支持主项目标记 | ❌ 完全缺失 |
| `ProjectStatusBadge` | ✅ 状态栏项目徽标，支持项目名+N缩略显示 | ❌ 完全缺失 |

**影响**: 使用 `:workspace` 模式和项目绑定功能的用户无法在 Compose-App 中管理项目上下文。

### 2. 任务追踪（Todo/Task System）

| 组件 | JavaFX | Compose-App |
|------|--------|-------------|
| `TodoFloatBadge` | ✅ 浮动任务计数徽标，带脉冲动画和展开面板 | ❌ 完全缺失 |
| `TodoResultView` | ✅ `TodoWrite` 工具结果渲染（状态图标、进度条） | ❌ 完全缺失 |

**影响**: 代理工具 `TodoWrite` 的输出结果无法在 Compose-App 中结构化展示，只能回退为纯文本。

### 3. AskUserQuestion 结果渲染

| 组件 | JavaFX | Compose-App |
|------|--------|-------------|
| `AskQuestionResultView` | ✅ `AskUserQuestion` 结果选项列表渲染（绿色高亮选中项） | ❌ 完全缺失 |

**影响**: AI 提问工具的结果在 Compose-App 中无法结构化展示。

### 4. 暗色主题

| 特性 | JavaFX | Compose-App |
|------|--------|-------------|
| 暗色主题 | ✅ 完整 `dark.css`，覆盖所有 UI 组件样式 | ❌ 仅有一组浅色配色，无暗色支持 |
| 主题切换 | ✅ CSS 样式表动态切换 | ❌ 无 |

**影响**: Compose-App 用户无法使用暗色模式。

### 5. 自定义窗口装饰

| 特性 | JavaFX | Compose-App |
|------|--------|-------------|
| 无边框窗口 | ✅ `StageStyle.TRANSPARENT` + 自定义标题栏 | ❌ 使用原生窗口装饰 |
| 窗口圆角/裁剪 | ✅ 20px 圆角裁剪 + SVGPath 自定义按钮 | ❌ 原生矩形窗口 |
| 边缘拖拽缩放 | ✅ 6px 热区，8方向 resize 检测 | ❌ 原生窗口缩放 |
| 窗口图标 | ✅ 多分辨率图标（16px-512px + ico/icns/svg） | ❌ 默认 Java 图标 |

**影响**: Compose-App 外观与操作系统原生窗口一致，缺少自定义视觉身份。

### 6. 应用图标资源

| 类型 | JavaFX | Compose-App |
|------|--------|-------------|
| 图标文件 | ✅ 10 个图标文件（.icns, .ico, .svg, .png） | ❌ 无图标资源 |

### 7. 历史会话自动恢复

| 特性 | JavaFX | Compose-App |
|------|--------|-------------|
| 自动恢复今天会话 | ✅ 切换到 Chat 页面时自动恢复最近会话 | ❌ 需要手动从历史页选择 |
| 智能会话判断 | ✅ 判断会话是否为今天创建，仅恢复当天会话 | ❌ 无此逻辑 |

---

## 二、Compose-App 增强的功能（JavaFX 无，Compose 有）

### 1. 多标签页对话（Tabs）

| 特性 | JavaFX | Compose-App |
|------|--------|-------------|
| 多会话标签 | ❌ 单会话模式 | ✅ `TabBar` 支持多个聊天标签页 |
| 标签内联重命名 | ❌ | ✅ F2 / 双击重命名 |
| 标签右键菜单 | ❌ | ✅ 关闭全部/右侧/左侧 |
| 历史页面独立 | ❌ 历史在侧边栏内 | ✅ 独立 `HistoryPage`，网格布局 |

**影响**: Compose-App 的多标签设计显著提升了多会话管理体验。

### 2. 消息列表视图模式

| 特性 | JavaFX | Compose-App |
|------|--------|-------------|
| 气泡视图 | ✅ | ✅ |
| 列表视图 | ❌ | ✅ `MessageList` 组件，带时间戳的紧凑视图 |
| 视图切换 | ❌ | ✅ 一键切换气泡/列表模式 |

### 3. 布局灵活性

| 特性 | JavaFX | Compose-App |
|------|--------|-------------|
| 顶部导航栏 | ❌ | ✅ `TopNavBar` 水平导航 |
| 结构分离 | ❌ 所有布局耦合在 `MainStage.java` | ✅ 布局拆分为 `AppShell`、`Sidebar`、`StatusBar`、`TopNavBar`、`TabBar` |

### 4. 数据源配置增强

| 特性 | JavaFX | Compose-App |
|------|--------|-------------|
| 数据库类型模板 | ❌ 仅通用 JDBC URL 输入 | ✅ 7 种数据库模板（MySQL、MariaDB、PostgreSQL、Oracle、SQL Server、H2、SQLite） |
| JDBC URL 构建器 | ❌ 手动输入 | ✅ 从表单字段自动构建 JDBC URL |
| SQLite 文件浏览器 | ❌ | ✅ 文件选择器 |
| URL 反解析 | ❌ | ✅ JDBC URL 自动解析回表单字段 |

### 5. Markdown 渲染增强

| 特性 | JavaFX | Compose-App |
|------|--------|-------------|
| 表格扩展窗口 | ❌ WebView 内内联展示 | ✅ 表格可展开到独立窗口 |
| 查看原始消息 | ❌ | ✅ "View raw" 按钮打开原始内容窗口 |
| 代码块复制 | ✅ (JS) | ✅ (Compose) |

### 6. 工具调用结果增强

| 特性 | JavaFX | Compose-App |
|------|--------|-------------|
| "Show all" 展开 | ❌ | ✅ 工具结果可打开独立窗口查看全部内容 |

### 7. CJK 字体处理

| 特性 | JavaFX | Compose-App |
|------|--------|-------------|
| CJK 字体解析 | ❌ 依赖系统默认 | ✅ `CjkFontResolver` 按 locale 选择合适字体（zh-CN/zh-TW/ja/ko） |
| 字体覆盖 | ❌ | ✅ 通过 `javaclawbot.font.override` 系统属性覆盖 |

### 8. 模型管理增强

| 特性 | JavaFX | Compose-App |
|------|--------|-------------|
| 内置提供商列表 | 有限 | ✅ 16 个预配置提供商 |
| 收藏/默认标记 | ❌ | ✅ 星标收藏模型 |
| think/extraBody 编辑 | ✅ 复杂对话框 | ✅ JSON 内联编辑 |

### 9. 状态管理

| 特性 | JavaFX | Compose-App |
|------|--------|-------------|
| 状态面板 | ❌ 仅状态文本 | ✅ `StatusBar` + `StatusPopover` 点击展开详情 |
| 模型/代理/MCP 状态聚合 | ❌ | ✅ 底部状态栏聚合所有运行时状态 |

### 10. 对话框组件

| 组件 | JavaFX | Compose-App |
|------|--------|-------------|
| `ErrorDialog` | ❌ 内联错误显示 | ✅ 独立错误弹窗，可复制错误文本，支持"忽略并保存" |
| `ConfirmDialog` | ❌ 内联确认 | ✅ 独立确认弹窗，红色确认按钮 |

---

## 三、基础设施对比

| 项目 | JavaFX | Compose-App |
|------|--------|-------------|
| 构建系统 | Gradle + Maven (pom.xml) | 仅 Gradle |
| 语言 | Java | Kotlin |
| UI 框架 | JavaFX 17.0.2 | Compose for Desktop 1.7.1 |
| 状态管理 | 命令式（setter + Platform.runLater） | 声明式（mutableStateOf + LaunchedEffect） |
| 代码行数 | ~35 个 Java 文件 | ~39 个 Kotlin 文件 |
| 样式体系 | CSS 文件（main.css + dark.css） | Kotlin 代码内（AppColors, AppTypography） |
| 测试 | 无 | 无 |

---

## 四、功能矩阵总览

| 功能区域 | JavaFX | Compose-App | 备注 |
|----------|--------|-------------|------|
| Chat（聊天对话） | ✅ | ✅ | Compose 多标签更强 |
| Models（模型管理） | ✅ | ✅ | Compose 提供商列表更全 |
| Agents（代理配置） | ✅ | ✅ | 基本一致 |
| Channels（通道配置） | ✅ | ✅ | 基本一致 |
| Skills（技能管理） | ✅ | ✅ | 基本一致 |
| MCP Servers | ✅ | ✅ | 基本一致 |
| Databases（数据源） | ✅ | ✅ | Compose 数据库模板更强 |
| Cron Tasks | ✅ | ✅ | 基本一致 |
| Settings（设置） | ✅ | ✅ | 基本一致 |
| Dev Console（开发者控制台） | ✅ | ✅ | 均通过 LogWatcher 实现 |
| 暗色主题 | ✅ | ❌ | **Compose 缺失** |
| 项目绑定管理 | ✅ | ❌ | **Compose 缺失** |
| Todo/Task 追踪 | ✅ | ❌ | **Compose 缺失** |
| AskUserQuestion 渲染 | ✅ | ❌ | **Compose 缺失** |
| 自定义窗口装饰 | ✅ | ❌ | **Compose 缺失** |
| 多标签对话 | ❌ | ✅ | **JavaFX 缺失** |
| 消息列表视图 | ❌ | ✅ | **JavaFX 缺失** |
| CJK 字体管理 | ❌ | ✅ | **JavaFX 缺失** |
| StatePopover 详情 | ❌ | ✅ | **JavaFX 缺失** |
| 数据库类型模板 | ❌ | ✅ | **JavaFX 缺失** |
| 错误/确认对话框 | ❌ | ✅ | **JavaFX 缺失** |
| 布局模块化 | ❌ | ✅ | **JavaFX 缺失** |

---

## 五、结论

### 核心差异总结

**Compose-App 缺失的 7 个功能**（按重要性排序）:
1. **项目绑定管理** — ProjectPopover/ProjectStatusBadge，影响工作区/项目模式切换
2. **任务追踪系统** — TodoFloatBadge/TodoResultView，影响代理工具输出展示
3. **AskUserQuestion 结果渲染** — 影响 AI 交互式提问的输出展示
4. **暗色主题** — 影响用户视觉偏好
5. **自定义窗口装饰** — 影响品牌视觉身份
6. **应用图标** — 影响用户体验
7. **历史自动恢复** — 影响使用流畅度

**Compose-App 新增的 10 个增强**:
1. 多标签会话管理（最主要增强）
2. 消息列表视图模式
3. 模块化布局（AppShell/TopNavBar/TabBar）
4. CJK 字体自动适配
5. 数据源配置增强（7种数据库模板）
6. 独立错误/确认对话框
7. 状态栏详情弹窗（StatusPopover）
8. Markdown 表格/原始内容独立窗口
9. 工具结果 "Show all" 展开
10. 模型星标收藏

### 建议

Compose-App 在**多任务处理**和**用户体验细节**方面领先，但在**与后端深度集成**的功能（项目绑定、任务追踪）方面落后。短期优先补全项目绑定和任务追踪功能，以确保代理工具输出的完整展示。
