# Changelog

All notable changes to JavaClawBot will be documented in this file.

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

