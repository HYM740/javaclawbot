# 计划：Compose-App 多标签页独立会话支持

**状态**: 已设计，待实施
**日期**: 2026-05-12
**关联分支**: `refactor/ui`

---

## Context

当前 Compose-App 的 TabBar 支持多个聊天标签页，但所有标签共享同一个后端会话（`sessionKey = "cli:direct"`）。用户切换标签、在多标签中同时发送消息时，消息会互相串扰。核心问题：**UI 有多标签，后端会话管理器（SessionManager）和 AgentLoop 已支持多 sessionKey 路由，但桥接层 BackendBridge 被固定为单会话模式**。

---

## 架构洞察

- **SessionManager** (`backend/src/main/java/session/SessionManager.java`): 已支持 `Map<sessionKey, Session>`，`getOrCreate(key)` / `createNew(key)` / `resumeSession(key, sessionId)` 都是按 key 隔离的
- **AgentLoop** (`backend/src/main/java/agent/AgentLoop.java`): `dispatch(msg)` 已按 `msg.getSessionKey()` 排队到 `AgentLoopQueue` 的 per-session lane，per-session 状态（`usageTrackers`、`stopFlags`、`activeTasks`、`autoCompactTracking`）均用 `ConcurrentHashMap<String, ...>` 索引。共享资源（MCP Manager、DataSourceManager、MemoryStore 等）保持全局
- **`OutboundMessage`** (`backend/src/main/java/bus/OutboundMessage.java`): 有 `channel` + `chatId` 字段。可通过 `chatId` 区分不同标签的出站消息，无需新增字段
- **瓶颈**: `BackendBridge` (`java-fx-app/src/main/java/gui/ui/BackendBridge.java`) — 单个 `sessionKey = "cli:direct"`、单个 `currentProgressCallback`、单个 `waitingForResponse`
- **`InboundMessage.getSessionKey()`** = 优先 `sessionKeyOverride`，否则 `channel + ":" + chatId`。因此给每个标签分配唯一 `chatId` 自动获得独立会话

---

## 实施步骤

### 第1步：BackendBridge — 多会话上下文支持

**文件**: `java-fx-app/src/main/java/gui/ui/BackendBridge.java`

**改动**:

1.1 新增内部类 `SessionContext`，封装 per-session 状态（回调、等待标志、消息计数、标题生成状态）

1.2 替换 `currentProgressCallback` / `currentResponseCallback` / `waitingForResponse` 等单字段为 `ConcurrentHashMap<String, SessionContext>`，键为 `chatId`

1.3 `sendMessage()` 添加 `String chatId` 参数重载，默认 `"direct"` 保持向后兼容

1.4 `isTargetCliOutbound()` → `routeOutboundToSession(OutboundMessage out)`:
- 按 `out.getChatId()` 查找对应 `SessionContext`
- 路由到正确的回调

1.5 `stopMessage()` 添加 `String chatId` 重载

1.6 新增 `removeSession(String chatId)` 用于标签关闭时清理

1.7 迁移 `userMessageCount` / `titleGenerationPending` / `titleRegenerationPending` 到 `SessionContext`

1.8 `getContextUsageRatio()` / `renameSession()` / `resumeSession()` / `deleteSession()` 接受 `chatId` 参数

### 第2步：Bridge.kt — Compose-Aware 多会话桥接

**文件**: `compose-app/src/main/kotlin/gui/ui/Bridge.kt`

**改动**:

2.1 `sendMessage()` 添加 `chatId` 参数（默认 `"direct"`）

2.2 新增 `createSession(chatId)` / `removeSession(chatId)` / `stopMessage(chatId)` 方法

2.3 `newSession(chatId)` / `resumeSession(sessionId, chatId)` 转发

### 第3步：App.kt — 标签-会话映射

**文件**: `compose-app/src/main/kotlin/gui/ui/App.kt`

**改动**:

3.1 添加 `tabChatIds: Map<String, String>` 状态（tabId → chatId）

3.2 创建标签时生成唯一 `chatId = "gui:tab_${nanoTime}"`

3.3 关闭标签时调用 `bridge.removeSession(chatId)` 并清理映射

3.4 发送/停止消息时从映射中查询 `chatId` 并传入

3.5 从历史页恢复会话时创建新 `chatId` 并与 sessionId 关联

### 第4步：ChatPage.kt — 接收 session 参数

**文件**: `compose-app/src/main/kotlin/gui/ui/pages/ChatPage.kt`

**改动**:

4.1 添加 `chatId: String` 参数

4.2 发送和停止时使用传入的 `chatId`

### 第5步：AgentLoop 检查

**文件**: `backend/src/main/java/agent/AgentLoop.java`

确认 `dispatch()` 和 `processMessage()` 中所有 `publishOutbound()` 调用使用来自 `InboundMessage` 的正确 `channel` 和 `chatId`。现有代码已是如此，无需修改。

### 第6步：JavaFX 向后兼容

JavaFX 所有 `sendMessage()`/`stopMessage()` 调用不传 `chatId`，使用默认值 `"direct"`，不受影响。

---

## 文件变更清单

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `java-fx-app/.../BackendBridge.java` | 主要修改 | +SessionContext, 多会话路由, per-session 状态迁移 |
| `compose-app/.../Bridge.kt` | 修改 | +chatId 参数, +createSession/removeSession |
| `compose-app/.../App.kt` | 修改 | +tabChatIds 映射, 标签生命周期与 session 关联 |
| `compose-app/.../pages/ChatPage.kt` | 修改 | +chatId 参数 |
| `compose-app/.../layout/AppShell.kt` | 可能修改 | 传递 chatId 到 ChatPage |
| `compose-app/.../layout/TabBar.kt` | 可能修改 | tab 关闭时通知上层清理 session |
| `backend/.../agent/AgentLoop.java` | 检查（无需修改） | 确认 dispatch 路由正确 |

---

## 验证方法

1. **编译检查**:
   - `./gradlew :compose-app:compileKotlin` — Kotlin 编译无错误
   - `./gradlew :java-fx-app:compileJava` — Java 编译无错误

2. **运行时验证** (通过 `./gradlew :compose-app:run`):
   - 打开 Compose-App，默认有一个标签页 "新对话"
   - 新建第2个标签页，在标签A发送 "Hello"，标签B发送 "World"
   - 验证：两个标签的对话历史互不干扰
   - 关闭标签B，再发送消息，验证标签A不受影响
   - 从历史页恢复一个旧会话到新标签，验证正常加载

3. **JavaFX 回归** (通过 `./gradlew :java-fx-app:run`):
   - 验证 JavaFX GUI 所有功能正常（发送消息、新对话、切换页面）
