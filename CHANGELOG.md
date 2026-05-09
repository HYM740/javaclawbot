# Changelog

All notable changes to JavaClawBot will be documented in this file.

## [2.2.7] - 2026-05-08

### Added
- **聊天页面底部状态栏显示工作空间路径和项目注册信息**：状态栏从居中 Label 改为左右分布 HBox，左侧显示模型状态（黑色文字），右下角新增 `ProjectStatusBadge` 组件
  - 普通用户模式（无绑定项目）：显示 `📂 工作空间路径`，点击弹出菜单支持复制路径或打开文件夹
  - 开发者模式（有绑定项目）：显示 `📁 主项目名 ⭐ +N ⚙`，点击弹出 `ProjectPopover` 面板
- **ProjectPopover 项目管理弹出面板**：非模态 Popup 从右下角浮出，列出所有绑定项目，支持：
  - 路径直接点击编辑，Enter/失焦后实时同步到 ProjectRegistry 并持久化
  - 切换主项目、解绑项目、绑定新项目
  - 路径不存在时显示 ⚠️ 警告图标
  - 主项目行蓝色高亮背景

### Changed
- **状态栏文字颜色**：从 `rgba(0,0,0,0.36)` 改为 `#000000` 纯黑，提高可读性



## [2.2.6] - 2026-05-08

### Fixed
- **`/stop` 命令误触发标题生成和回调清除**：`handleStopCommand`（及其他系统命令如 `/help`、`/new`、`/clear`、`/mcp-reload`）发布的 outbound 消息使用空 `Map.of()` 元数据，被 `BackendBridge.outboundTask` 误判为"最终回复"，导致：(1) 清除原始用户问题的响应回调 (2) 错误触发标题生成。修复：在 `AgentLoop.java` 中给所有系统命令的 outbound 消息添加 `"_system_command": true` 元数据标记；在 `BackendBridge.java` 的 `outboundTask` 中检测该标记，系统命令消息仅转发到进度回调，不触发响应终结和标题生成流程。
- **`/context-press` 压缩后 LLM API 报错 `missing field 'content'`**：压缩后的 session 消息包含两类无 `content` 字段的消息：(1) `compact_boundary` 边界标记 (2) `attachment` 附件消息（`skill_listing`、`plan_file_reference`、`task_status` 等）。这些消息通过 `ContextBuilder.buildMessages()` 直接加入 API 消息列表，导致 API 返回 HTTP 400。修复：在 `LLMProvider.sanitizeEmptyContent()` 中增加对 `content` 为 `null`（key 不存在）的处理 — attachment 消息将元数据序列化为可读文本；其他消息设置 `"(empty)"` 占位符。
- **GUI 聊天界面偶显 `(empty)` 文本**：`LLMProvider.sanitizeEmptyContent()` 对空 content 消息填充的 `"(empty)"` 占位符未在 GUI 渲染层过滤，导致历史会话恢复或流式回调中显示无意义文本。修复：在 `ChatPage.addAssistantMessage()` 和 `addAssistantMessageWithReasoning()` 中添加 `"(empty)"` 字符串拦截，不渲染该占位符到界面。
- **Dev Console 多行日志续行显示为 `unknown` logger**：`AgentLoop` 的 LLM 思考/回复日志包含多行内容，Logback 将换行原样写入日志文件。`LogWatcher.parseLine()` 的正则 `LOG_PATTERN` 不匹配续行，fallback 硬编码 `"unknown"` 为 logger，续行被渲染为带假 `[时间戳] INFO unknown -` 前缀的伪日志行。修复：`parseLine()` 续行 logger 设为空字符串；`DevConsolePage` 的 JS `appendLog` 检测到空 logger 时按原始文本渲染（缩进 16px），不加任何前缀。

## [2.2.5] - 2026-05-08

### Changed
- **TodoFloatBadge 全面重写**：参照 IDEA 插件同款设计，改进如下：
  - 视觉：浅色主题（`#ffffff` 背景），圆形按钮 38×38，蓝色脉冲光环，下拉面板紧凑分组（In Progress / Pending / Completed）+ 头部进度条
  - 定位：改为手动 `layoutX/Y` 精确定位，`setManaged(false)` 避免 StackPane 布局算法干扰，下拉面板始终紧贴按钮上方 8px
  - 交互：移除拖拽功能，保留点击展开/收起 + 外部点击关闭

### Fixed
- **Dev Console 不展示已有日志**：`LogWatcher.run()` 仅在 WatchService 检测到文件变更事件时才调用 `readNewLines()`，导致历史日志永远不会被读取展示。修复：在 `registerWatcher()` 之后、`mainLoop()` 之前调用 `initialRead()` 读取已有内容
- **日志文件无限膨胀导致 Dev Console 卡死**：`logback.xml` 使用 `FileAppender` + `immediateFlush=true`，日志文件持续增长至 2.7MB（4 万行），`LogWatcher` 初始读取全部推入 WebView → 4 万次 DOM 操作卡死 UI。修复：
  - `logback.xml`：参照生产认证方案，`FileAppender` → `RollingFileAppender` + `SizeAndTimeBasedRollingPolicy`，单文件上限 1MB，保留最近 5 个滚动历史文件
  - `LogWatcher.initialRead()`：用 `ArrayDeque` 环形缓冲区只保留最后 5000 行推入 buffer，与 WebView `MAX_BUFFER_SIZE` 对齐
  - `LogWatcher.mainLoop()`：检测 `ENTRY_CREATE` 事件（文件滚动后重新创建）时，关闭旧 reader 并打开新 reader，确保滚动后不丢日志

## [2.2.4] - 2026-05-07

### Fixed
- **TodoFloatBadge 下拉面板过小导致任务文本截断为省略号**：原因：(1) 面板宽度仅 280px；(2) contentLabel 虽设置 wrapText 但未约束 maxWidth，HBox 中 spacer 挤占空间导致不换行。修复：面板宽度增至 380px，移除 spacer，改用 `HBox.setHgrow(contentLabel, ALWAYS)` + `contentLabel.setMaxWidth(Double.MAX_VALUE)` 确保内容区占满剩余宽度并正确换行
- **TodoFloatBadge 下拉面板展开位置始终在右上角**：根因是 translateY 绑定中 `dropdown.heightProperty()` 在 hidden（managed=false）时为 0，展开瞬间绑定计算位置错误。修复：移除 translateY 静态绑定，在 `showDropdown()` 中先设置 visible+managed 让 JavaFX 计算实际高度，再根据按钮在屏幕上的位置动态决定向上/向下展开（按钮中心在上半屏→向下展开，在下半屏→向上展开）
- **窗口默认宽度 640px 过窄**：`DEFAULT_WIDTH` 被意外改为 640，导致内容区仅 384px（侧栏占 256px），消息气泡有效宽度仅 308px。恢复为 1100px，内容区 844px，消息可达 700px 最大宽度
- **标题生成 AI 不可用时永远无法生成标题**：两个协同 bug：(1) v2.2.0 CHANGELOG 声称的 `resetTitleFlags` 修复从未实现，`titleGenerationPending`/`titleRegenerationPending` 标志位在 `triggerTitleGeneration` 完成后不重置，导致首次失败后所有后续消息的标题生成被跳过；(2) `force=false` 失败时只打日志等待 `force=true` 重试，但 `force=true` 需 3 条消息才触发，短对话永远无标题。修复：(A) 新增 `resetTitleFlags(force)` 方法，在 `finally` 块中重置标志位；(B) `force=false` 失败时立即回退到截断首条用户消息，不再等待 `force=true`
- **TitleGenerator 静默返回 null 无诊断日志**：5 个 return-null 路径使用 `LOG.fine` 或完全无日志，导致调试困难。修复：全部升级为 `LOG.info` 级别并增加 `[标题诊断]` 前缀，输出关键状态（provider/model/contextMessages/LLM响应等）

## [2.2.3] - 2026-05-07

### Fixed
- **新建会话复用昨日标题**：`SessionManager.createNew()` 新建空会话后未写入磁盘文件（`save()` 检测到 0 条消息直接返回），导致 `listSessions()` 只看到旧会话文件，sidebar 显示旧标题。修复：`createNew()` 立即写入仅含 metadata 的会话文件
- **TodoFloatBadge 下拉面板定位错误**：VBox + TOP_RIGHT 组合导致下拉面板偏移出可视区。修复：改用 StackPane 绝对定位 + translateX/translateY 绑定，下拉面板右对齐按钮并出现在按钮上方
- **切换历史对话后 `/projects` 显示错误的项目绑定**：两个协同 bug：(1) `BackendBridge.resumeSession()` 未恢复会话对应的 ProjectRegistry，仍保留旧会话的绑定数据；(2) `CliAgentCommandHandler.handleCommand()` 未设置 `currentSessionKey`（ThreadLocal），导致 `getProjectRegistry()` 始终回退到全局默认 registry。修复：`resumeSession()` 加载并注册会话专属 ProjectRegistry；`handleCommand()` 在处理命令前设置 `currentSessionKey`、finally 中清除

## [2.2.2] - 2026-05-06

### Added
- **悬浮 Todo 进度浮标**：参照 javaclawbot-idea-plugin 设计，新增 `TodoFloatBadge` 组件。圆形按钮显示 completed/total 计数，in_progress 脉冲动画，点击展开下拉任务列表，支持拖拽。全部完成后保留显示（含「关闭」按钮），新 TodoWrite 到达时自动重新显示

### Fixed
- **子代理日志缺编号**：所有 `[子代理 {}]` 日志补充短 ID（agentId 后 8 位），并行子代理可区分

## [2.2.1] - 2026-05-06

### Fixed
- **子代理 "Tool not found: Bash"**：三个上下文构建路径均未设置 `.toolView()`，导致 `getTool()` 永远返回 null。修复：三个构建路径均补充 `.toolView()` 设置
- **标题首次 AI 生成失败后立即回退截断，导致永远无法使用 AI 生成**：`force=false` 失败时直接设置 fallback 标题，导致后续无法重试 AI。修复：`force=false` 失败时不再设 fallback，`force=true` 失败时才回退；新增 `force=false` 已存在标题的预检查
- **恢复历史会话后标题被重新触发生成**：`resumeSession` 将 `userMessageCount`/flags 全部重置为 0，sidebar 点击历史时甚至未调用 `resumeSession`。修复：`resumeSession` 根据会话实际用户消息数初始化计数器（>=3 则禁止标题生成）；sidebar 恢复监听器补充调用 `resumeSession`
- **AskUserQuestion 工具卡片不显示"done"**：`ToolCallCard` 缺少 `setStatus()` 方法，工具执行完成后状态始终为 "running"。修复：添加 `statusIcon` 字段和 `setStatus()` 方法，`handleToolResult` 中标记为 completed
- **历史记录加载顺序错乱**：`loadMessages` 中 `hasToolCalls` 分支将文本放在推理之前显示，与实时聊天顺序（推理→文本→工具卡片）不一致。修复：调整顺序，并让工具卡片初始 status="running"、工具结果到达后更新为 completed

## [2.2.0] - 2026-05-06

### Fixed
- **macOS IDEA 运行图标不显示**：`.idea/misc.xml` 中 `project-jdk-name="17"` 在 macOS 上 JDK 命名不匹配导致模块加载失败
- **macOS JavaFX 原生库缺失**：`pom.xml` 仅声明 `win`/`linux` classifier，缺少 `mac`/`mac-aarch64`，导致 macOS 上依赖解析异常和运行失败
- **工具调用场景下「已深度思考」点击展开显示空白**：`addReasoningBlock` 通过 `sceneProperty` 监听器计算 WebView 宽度（`newScene.getWidth() - 332`），在批量添加消息时可能得到负数宽度，导致内容无法渲染。修复：改为 `Platform.runLater` 直接加载内容，宽度计算增加 `w > 0` 守卫
- **展开逻辑静默失败**：高度测量未就绪时 `forceMeasureHeight` 失败返回 0 → 展开条件不满足 → WebView 保持 `maxHeight=0`。修复：测量失败时使用 200px 兜底高度展开，后台测量完成后同步更新 `maxHeight`
- **标题生成始终回退到截断用户消息，AI 未被使用**：
  - `triggerTitleGeneration` 中 `titleGenerationPending`/`titleRegenerationPending` AtomicBoolean 从未在生成完成后重置，导致首次之后的标题生成/更新都被跳过。修复：在 `finally` 块中调用 `resetTitleFlags(force)` 重置标志位
  - `TitleGenerator.generateTitle` 不检查 LLM 响应的 `finishReason`，"error" 响应会被当作有效标题处理。修复：检测到 `finish_reason="error"` 时直接返回 null，触发 fallback
  - 增强诊断日志：区分 AI 成功生成、回退截断、跳过更新三种情况，记录 LLM 原始响应 finish_reason 和内容

### Changed
- `addReasoningBlock` 内容加载时机从 `sceneProperty` 监听器改为 `Platform.runLater`，与 `addAssistantMessageWithReasoning` 对齐

## [2.1.0] - 2026-04-30

### Added
- GUI 首次启动自动初始化：检测 workspace/skills 为空时自动安装内置技能和 zjkycode.js 插件
- 应用图标（SVG/PNG/ICO/ICNS），在所有平台的任务栏/Dock/Alt+Tab 中显示
- Windows EXE 打包支持 `--icon` 参数（build-exe.bat）
- macOS DMG 打包脚本（build-dmg.sh）

### Fixed
- **WebView 尾部留白根因修复**：`html{height:100%}` 导致 `documentElement.scrollHeight` 始终等于 WebView 视口高度而非内容高度。测量时临时设置 `html.style.height='auto'` 获取真实内容高度后再恢复样式
- WebView 思考块渲染：宽度在 loadContent 前绑定到场景实际宽度，避免窄宽度导致 scrollHeight 虚高
- build-exe.bat 主类路径修正 `gui.JavaClawBotGUI` → `gui.ui.Launcher`
- build-exe.bat 版本号修正 `1.0.0` → `2.1.0`

### Changed
- `BuiltinSkillsInstaller.findAssociatedPlugin()` 可见性改为 public

## [Unreleased]

### Fixed
- 深度思考块点击无法展开：`measureWebViewHeightWithRetry` 在高度返回 0 且重试耗尽时未设置 `ready[0]=true`，导致点击处理器永远无法展开。修复：耗尽重试后标记 ready，并在点击处理器中增加强制测量回退
- LLM 错误响应（如 HTTP 402 余额不足）在 message 工具已发送后会被静默丢弃，导致 GUI 无法显示错误。修复：`isSentInTurn()` 跳过逻辑增加判断——若 finalContent 以 "Error:" 开头则仍然发送到 GUI
- MCP 管理页状态不准确：原来用 `sc.isEnable()` 代替连接状态，导致无法区分"已连接"/"已禁用"/"连接失败"。修复：通过 `McpManager.isServerConnected()` 获取实时连接状态，`McpServerCard` 显示三种状态
- MCP 工具列表滞后：原来卡片工具列表硬编码为空。修复：`McpManager` 新增 `getServerToolNames()` 从 handles 获取实时工具名
- MCP "重新加载" 后状态异常：`reconnectServer()` 在第 489 行 `handles.remove()` 后重连成功但未 `handles.put()` 回去，导致 `isServerConnected()` 始终返回 false。修复：重连成功后重新 `handles.put`
- MCP 重连异步时序问题：`refreshTools()` 中 `reconnectServer()` 返回 `CompletionStage<String>` 但未被 await，导致 `supplyAsync` 在重连实际完成前就返回，GUI 刷新时 handles 尚未更新。修复：收集所有 reconnect futures 用 `CompletableFuture.allOf().get(30s)` 等待全部完成后才返回

### Changed
- MCP 管理支持编辑：`AddMcpServerDialog` 支持新增/编辑双模式，`McpServerCard` 新增编辑按钮，`BackendBridge` 新增 `updateMcpServer`/`updateMcpServerRaw`/`deleteMcpServer` 方法
- Models 页面支持编辑模型：模型列表每行新增编辑按钮，点击后展开预填表单，可修改模型名称、别名、类型、参数并保存

### Added
