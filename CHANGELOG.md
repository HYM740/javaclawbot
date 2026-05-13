# Changelog

All notable changes to NexusAI will be documented in this file.

## [2.3.5] - 2026-05-13

### Added
- **右下角浮标折叠/展开交互**：`FileDiffBadge` 从始终展开 210px 重构为默认折叠 48px 竖条（显示 📝文件数 / 📋任务进度 / ◀），点击向左滑出完整 210px 面板。展开态增加"▶ 收起"按钮，支持点击外部区域自动折叠。250ms clip 动画 + 快速连点中断保护。

### Fixed
- **菜单栏中文化**：侧栏导航项（Chat/Models/Agents/Channels/Skills/MCP/Databases/Cron Tasks→对话/模型/代理/通道/技能/MCP/数据库/定时任务）及底部菜单（Settings/Dev Console/Help→设置/开发者控制台/帮助）全面中文化，新增 `pageKey` 字段保持路由不变
- **历史对话取消横向滚动条**：侧栏历史区域 ScrollPane 添加 `setHbarPolicy(NEVER)` 彻底禁用横向滚动条
- **Settings 自动更新报错 `this.input is null`**：`UpdateService.checkForUpdates()` 增加服务端返回的 `version` 和 `url` 字段空值校验，缺失时抛出明确 IOException 而非 NPE；`SettingsPage.startDownload()` 增加 `pendingUpdate.getUrl()` 空值前置检查
- **`/memory` 命令 LLM 回复未发送到 GUI**："⏳ 正在整理记忆..." 消息缺少 `_progress` 元数据，被 BackendBridge 误当作"最终回复"消费了 `currentResponseCallback`，导致后续真正的 LLM 回复无回调可用。修复：添加 `Map.of("_progress", true)` 元数据，确保走进度回调通道
- **备份文件被「新对话」自动清除**：`FileDiffBadge.clearFiles()` 内部调用 `backupManager.clearAll()` 删除所有持久化备份，而 `clearFiles()` 被 `ChatPage.clearMessages()` 在「新对话」时调用 → 每次开新对话自动清空上一轮备份。修复：拆分 `clearFiles()`（仅清 UI）与 `clearFilesAndBackups()`（清 UI + 删除备份），后者仅由用户手动点击弹窗「🗑 清除全部」按钮触发
- **设置页更新检查报"下载地址无效"**：服务端 JSON 将 JAR 下载 URL 嵌套在 `jar.url` 中，但 `UpdateInfo` 类只映射顶层 `url` 字段 → Jackson 反序列化后 `getUrl()` 返回 null → 校验失败。修复：新增 `UpdateInfo.JarInfo` 嵌套类映射 `jar` 对象，`getUrl()`/`getSize()` 优先读取 `jar.*`，回退顶层字段保持向下兼容
- **开发者模式右下角显示非开发者界面**：`ProjectStatusBadge` 按注册表中项目数量判断显示模式，但 `newSession()` 会清空注册表 → badge 退化显示工作空间路径而非项目绑定界面。修复：新增 `developerMode` 字段，开发者模式下始终显示项目绑定入口（无项目时显示"📁 绑定项目"提示 + 齿轮图标），且禁止弹出"打开文件夹"菜单

## [2.3.4] - 2026-05-12

### Fixed
- **修复恢复历史对话时 JavaFX 文本布局崩溃**：
  - `MessageBubble` USER 消息气泡从 `Label`（`wrapText=true`）切换为 `WebView` 渲染，避免 JavaFX 内部 `TextRun.getWrapIndex` 数组越界导致的 `ArrayIndexOutOfBoundsException`
  - 根本原因：会话文件中的特定 Unicode 字符序列（如双编码乱码字符）触发 JavaFX `PrismTextLayout` 内部 bug（JDK-8087498 类问题）
  - `ChatPage.loadMessages()` 新增逐消息 try-catch 防御层，单条消息恢复失败不阻断整个历史加载流程
  - USER 气泡视觉保持一致：蓝底（`#0a84ff`）白字，支持 Markdown 渲染
- **修复 `String.format()` 误解析 CSS `%` 导致 `UnknownFormatConversionException`**：
  - `ToolCallCard.java`、`DiffViewerPopup.java`、`FileDiffBadge.java` 的 HTML 模板中 CSS 含 `height:100%` 等百分比值
  - `String.format(TEMPLATE, arg)` 将 CSS 中的 `%` 解析为格式说明符（如 `%;` → 非法转换字符），触发 `UnknownFormatConversionException: Conversion = ';'`
  - 修复：统一替换为 `TEMPLATE.replace("%s", arg)` 字面量替换，不触发表单格式解析器
- **修复 write_file/edit_file 工具卡片展开后大量空白**：
  - `ToolCallCard.setFileEditResult()` 自适应高度测量使用 `Math.max(document.body.scrollHeight, document.documentElement.scrollHeight)`
  - 当 HTML 模板使用 `html{height:100%}` 保证背景色时，`documentElement.scrollHeight` 恒等于 WebView 视口高度（400px），而非内容实际高度
  - 修复：改用仅 `document.body.scrollHeight` 测量，body 为 `height:auto`，其 scrollHeight 精确等于内容高度
- **TodoFloatBadge + FileDiffBadge 合并为连体常驻浮标**：
  - 移除独立 `TodoFloatBadge`，合并到 `FileDiffBadge` 中，双行连体布局（文件变更 + 任务进度）
  - 始终可见（无数据时显示 0 / 0/0），不再依赖事件激活
  - 固定 210×82px，右下角定位，`setPickOnBounds(false)` 防止拦截背景点击
  - 点击弹出中间 Stage 弹窗，带关闭按钮 (ESC)，重复点击 `toFront()` 不重复创建
- **修复 Stage 弹窗定位漂移（首次打开在左上角）**：
  - `centerOnOwner()` 在 `show()` 前调用时 `stage.getWidth()/getHeight()` 为 NaN
  - 修复：`sizeToScene()` 后用 `scene.getWidth()/scene.getHeight()` 替代，新增重载 `centerOnOwner(Stage, knownW, knownH)`
- **edit_file/write_file 工具卡片改用纯 JavaFX HBox 渲染**：
  - 移除 WebView + HTML 模板 + JS 回调 + `document.body.scrollHeight` 异步高度测量
  - 文件行使用 JavaFX HBox（📄 图标 + 文件名 + +/-统计 + [查看对比] 按钮 + [回滚] 按钮）
  - 按钮通过 `setOnAction` 直接绑定 Java 方法，不再经过 JS `window.status` 桥接
  - 彻底消除卡片展开后的 300px+ 空白区域
- **TodoWrite 工具卡片展开时隐藏 WebView**：
  - `addStructuredContent()` 调用时设置 `contentScrollPane.setVisible(false) + setManaged(false)`
  - 消除 TodoWrite 卡片中 WebView 占位导致的空白区域
- **bash/read_file/Grep/Glob 等文本工具改用 TextArea 替代 WebView**：
  - 移除 WebView、HTML 模板、`escapeHtml()`、`toDataUri()`、`readBodyText()`（JS 桥接）、`loadContent()`（异步监听器链）
  - 改用 JavaFX `TextArea`（只读、等宽字体、自动换行），支持原生 Ctrl+C 复制
  - 高度计算从 JS 异步 `scrollHeight` 改为确定性 `行数 × 行高` 计算（`recalculateTextAreaHeight()`）
  - `toggle()` 移除嵌套 `Platform.runLater` + JS 执行，改为同步 `recalculateTextAreaHeight()` 调用
  - bash 命令完整显示（不再截断），净减少 ~65 行代码
- **修复历史对话恢复时 [查看对比]/[回滚] 按钮无反应**：
  - 根因：`MainStage` 中 `chatPage.loadMessages(history)` 在 `fileDiffBadge.setBackupManager(fbm)` 之前执行，导致历史工具卡片拿到 null 或错误会话的 `FileBackupManager`
  - 修复：将 `setBackupManager` + `loadFromBackupManager` 移到 `loadMessages` 之前（`addResumeListener` 和 Chat 菜单恢复两条路径）
  - `ToolCallCard.handleDiffAction()/handleRollbackAction()` 新增 stderr 诊断日志，替代静默返回
- **backup-index.json 增加 toolCallId 字段**：
  - `FileBackupManager.BackupEntry` 新增 `toolCallId` 字段，记录触发备份的工具调用 ID
  - 新增 `backup(Path, String, String)` 重载方法和 `getBackupByToolCallId()` 查询方法
  - `saveIndex()/loadIndex()` 同步读写 `toolCallId`，兼容旧索引（缺失字段回退为 null）
- **新增 `ToolCallContext` ThreadLocal**：
  - `AgentLoop.executeToolCallsSequential()` 在工具执行前设置 `ToolCallContext.setToolCallId(tc.getId())`，执行后清除
  - `EditTool/WriteTool` 通过 `ToolCallContext.getToolCallId()` 获取当前 toolCallId 并传递给 `backup()`

## [2.3.3] - 2026-05-11

### Fixed
- **修复绑定项目组件修改路径后 UI 不刷新的问题**：
  - `BackendBridge.newSession()` 未清理 `projectRegistry`，导致新对话后徽标/Popover 显示旧会话的项目绑定
  - `MainStage.pageChangeListener` 的自动恢复路径和欢迎页路径缺少 `chatPage.refreshProjectBadge()` 调用
  - `ChatPage.refreshProjectBadge()` 未同步 Popover 内容（Popover 打开时注册表变更不会刷新列表）
  - 新增 `ProjectPopover.refreshList()` 公开方法支持外部触发列表重建

## [2.3.2] - 2026-05-11

### Fixed
- **修复 JavaFX WebView 偶发 NPE 崩溃**：`WCPageBackBufferImpl.validate()` 中 `this.texture` 为 null 导致 `NullPointerException`，是 JavaFX 17.0.2 已知 bug（JDK-8193511）。修复：
  - 升级 JavaFX 17.0.2 → 17.0.14
  - `prism.order` 从平台特定（d3d/es2）改为统一 `sw`（软件渲染），避免 GPU 纹理丢失问题
  - 配套更新 `build-dmg.sh` 脚本中的 `prism.order` 配置

## [2.3.1] - 2026-05-11

### Fixed
- **修复右下角项目 Popover 编辑主项目路径不持久化的问题**：`ProjectRegistry.save()` 在 Windows + JDK 17 下使用 `ATOMIC_MOVE` + `REPLACE_EXISTING` 时，因目标文件已存在抛出 `AtomicMoveNotSupportedException`，导致内存已更新但文件未写入。修复：先尝试 `ATOMIC_MOVE`，失败时降级为普通 `REPLACE_EXISTING`（与 `SessionManager.atomicReplace()` 一致）

## [2.3.0] - 2026-05-10

### Added
- **一键安装包 (Inno Setup)**：Windows 安装向导 `NexusAI-Setup-2.3.0.exe`，自动检测已有 Git/Python/Node.js/JDK 环境，缺失组件从内网自动下载并配置用户级 PATH
- **macOS 安装脚本**：`installer/macos/install.sh` 一键检测+下载+配置 PATH
- **Linux 安装脚本**：`installer/linux/install.sh` 支持 .desktop 快捷方式，一键配置环境

### Changed
- **项目更名**：javaclawbot → NexusAI（pom.xml artifactId/finalName、脚本全部更新、CHANGELOG 标题）

### Fixed
- **修复 `/projects` 等 CLI Agent 命令触发标题生成的问题**：`CliAgentCommandHandler.reply()` 未在命令回复中标记 `_system_command` 元数据，导致 BackendBridge 将命令回复误判为普通最终回复而触发异步标题生成（LLM 调用）。修复：`reply()` 统一附加 `_system_command: true` 元数据，与 AgentLoop 中 `/stop` 命令处理保持一致

## [2.2.8] - 2026-05-10

### Added
- **首次启动自动创建 config.json**：`ConfigIO.loadConfig()` 在配置文件不存在时自动调用 `saveConfig` 生成完整默认配置（含所有 provider + 内置模型列表 + channels + tools + gateway），写入失败不阻塞启动。解决全新安装用户无 config.json 时修改模型保存失败的问题

### Fixed
- **修复点击停止后无法继续对话的 bug**：`BackendBridge.stopMessage()` 发送 `/stop` 后未重置 `waitingForResponse` 标志，导致 `isWaitingForResponse()` 持续返回 true，新消息被静默丢弃。现在在 `/stop` 发送时和系统命令回复到达时均重置等待状态
- **修复 AI 批量读取文件时 GUI 卡死的问题**：LLM 流式输出期间每次进度事件都创建新的 MessageBubble(WebView) 且永不清理，多文件读取时大量 WebView 累积导致 JavaFX 线程卡死。现在流式进度使用替换式更新（而非追加），最终回复后清除追踪
- **修复多工具调用链在第一个工具执行后中止的问题**：commit `4c7b711` 将 `awaiting_response` 检测从仅 `AskUserQuestionTool` 扩展到所有工具（用于支持 DbTool 确认流程），但使用简单的 `String.contains("awaiting_response")` 判断。当 `read_file` 读取包含 "awaiting_response" 字符串的源文件（如 `MainStage.java`）时，文件内容误触发用户问答暂停流程，后续工具调用被永久阻塞。修复：将判断改为 JSON 解析 + `status`/`questions` 字段验证，避免文件内容误匹配

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
