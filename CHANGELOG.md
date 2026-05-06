# Changelog

All notable changes to JavaClawBot will be documented in this file.

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
