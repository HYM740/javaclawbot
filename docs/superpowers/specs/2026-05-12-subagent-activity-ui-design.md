# Sub-Agent Activity UI Design

## 概述

在 Compose Desktop UI 中显示子Agent（BackgroundAgentExecutor / ForkAgentExecutor）的实时活动状态，包括对话区嵌套卡片、状态栏指示器、状态栏弹出详情、以及历史会话恢复时的工具调用还原。

## 1. 后端 — 子Agent进度发布

### 1.1 Metadata 契约

子Agent执行过程中通过 MessageBus 发布 OutboundMessage，新增 metadata 标签：

| Key | 类型 | 说明 |
|-----|------|------|
| `_progress` | `true` | 标记为进度事件 |
| `_subagent_progress` | `true` | 标记为子Agent进度 |
| `_subagent_task_id` | String | 子Agent任务唯一ID |
| `_subagent_type` | String | 子Agent类型: explore/plan/general-purpose |
| `_subagent_status` | String | 状态: running/tool_call/tool_result/completed/error |
| `_subagent_tool_name` | String? | 当前执行的工具名 |
| `_subagent_tool_params` | String? | 工具参数字符串 |
| `_subagent_tool_result` | String? | 工具结果（>10KB截断） |
| `_subagent_tool_call_id` | String? | 子Agent的工具调用ID |
| `_subagent_iteration` | int | 当前LLM迭代次数 |
| `_parent_tool_call_id` | String | 父级Agent工具调用的ID，用于UI嵌套关联 |

### 1.2 BackgroundAgentExecutor 改动

`BackgroundAgentExecutor` 已有 `messageBus` 引用。在 `executeAsync()` 的 LLM 执行循环中，在每次工具调用前后发布进度事件：

- 子Agent启动时发布 `{status: "running", iteration: 0}`
- 每次工具调用前发布 `{status: "tool_call", toolName, toolParams}`
- 每次工具结果后发布 `{status: "tool_result", toolName, toolResult}`
- 子Agent完成时发布 `{status: "completed"}`

需要将 `parentToolCallId` 作为参数传入 `executeAsync()`，由 `AgentTool.java` 在调用时传递。

### 1.3 ForkAgentExecutor 改动

`ForkAgentExecutor` 当前没有 `MessageBus` 引用。需要新增构造参数注入。在 `executeLoop()` 的每次 LLM 迭代中，在工具执行前后发布进度事件（同 BackgroundAgentExecutor）。

### 1.4 频率控制

- 相同状态连续发布最小间隔 200ms（如 `tool_call` → `tool_call`）
- 工具结果 > 10KB 时截断再发布
- 最终完整结果仍在 session 中保存，UI 通过 `getSessionHistory()` 获取完整内容

## 2. Bridge / BackendBridge — 事件路由

### 2.1 BackendBridge.ProgressEvent 扩展

```java
public record ProgressEvent(
    String content,
    boolean isToolHint,
    boolean isToolResult,
    String toolName,
    String toolCallId,
    boolean isReasoning,
    boolean isToolError,
    boolean isSubagentProgress,
    String subagentTaskId,
    String subagentType,
    String subagentStatus,
    String subagentToolName,
    String subagentToolParams,
    String subagentToolResult,
    String subagentToolCallId,
    int subagentIteration,
    String parentToolCallId
)
```

### 2.2 routeOutboundToSession 扩展

在 `_progress` 处理分支中新增 `_subagent_progress` 判断，走相同的 `ctx.currentProgressCallback` 推送，但携带完整子Agent字段。

### 2.3 Bridge.kt Progress 扩展

对应添加 Kotlin 默认参数，progressAdapter 中逐字段映射。

## 3. UI — 对话区嵌套卡片

### 3.1 ToolCall 模型扩展

```kotlin
data class ToolCall(
    val name: String,
    val status: ToolStatus,
    val params: String? = null,
    val result: String? = null,
    val subCalls: List<SubagentCall> = emptyList()
)

data class SubagentCall(
    val taskId: String,
    val agentType: String,
    val toolName: String,
    val toolParams: String? = null,
    val toolResult: String? = null,
    val status: ToolStatus,
    val toolCallId: String? = null,
    val iteration: Int = 0
)
```

### 3.2 ChatPage onProgress 扩展

收到 `isSubagentProgress=true` 的事件时：

1. 在 `exchangeMsgs` 中查找包含匹配 `parentToolCallId` 的 `ToolCall` 的消息
2. 根据 `subagentStatus` 更新该 ToolCall 的 `subCalls`：
   - `running` → 确保 subCalls 中有对应 taskId 的条目，状态设为 RUNNING
   - `tool_call` → 追加新的 SubagentCall(taskId, toolName, params, RUNNING)
   - `tool_result` → 更新对应 SubagentCall 的 result, COMPLETED/ERROR
   - `completed` → 无工具调用时标记为 COMPLETED
3. 调用 `onMessagesChanged(exchangeMsgs.toList())` 触发重组

### 3.3 MessageBubble 渲染

`ToolCallCard` 组件内部，当 `tc.subCalls.isNotEmpty()` 时，渲染子Agent活动区域：

```
┌─ Agent ────────────────────────────┐
│ ⏳ 运行中...                        │
│ ────────────────────────────────── │
│ ▼ 子Agent活动 (3)                  │
│  ├─ ⏳ read("file.txt")  迭代 2    │
│  ├─ ✅ search("query")  迭代 3     │
│  └─ ❌ write("output")  迭代 4     │
│     └─ [查看结果]                   │
└────────────────────────────────────┘
```

- 子项宽度比父卡片缩进 8dp
- 工具结果截断 5 行 / 每行 80 字符（复用 `trimToolResult`）
- 点击子项可展开查看完整参数/结果（复用现有 Window 弹窗模式）
- 无工具调用时显示 "子Agent(explore) 思考中..."

## 4. UI — 状态栏 Agent 活动显示

### 4.1 StatusInfo 扩展

```kotlin
data class StatusInfo(
    // ... 现有字段不变
    val activeAgentCount: Int = 0,
    val agentTasks: List<AgentTaskInfo> = emptyList()
)

data class AgentTaskInfo(
    val taskId: String,
    val agentType: String,
    val status: String,
    val currentTool: String? = null,
    val iteration: Int = 0,
    val chatId: String? = null
)
```

### 4.2 StatusBar 改动

- Agent 段文字：`activeAgentCount > 0` 时显示 `▓ ● Agent default (2)`，使用 `AppColors.Accent` 高亮
- `segment` 函数新增 `highlight: Boolean` 参数控制颜色

### 4.3 StatusPopover 重构

Agent 面板改为渲染活跃/近期的子Agent任务列表：

- 有活跃任务时：按任务类型分组，显示每个任务的当前状态、工具、迭代次数、所属会话
- 无活跃任务时：显示 "无活跃子Agent" + agentName

### 4.4 数据更新机制

`App.kt` 中新增 `LaunchedEffect` 每 1 秒轮询：

```kotlin
LaunchedEffect(bridge) {
    while (true) {
        delay(1000L)
        val tasks = bridge?.getActiveAgentTasks() ?: emptyList()
        statusInfo = statusInfo.copy(
            activeAgentCount = tasks.size,
            agentTasks = tasks
        )
    }
}
```

`Bridge.kt` 新增 `getActiveAgentTasks()` 方法，直接查询 `AgentLoop` 的 `AppState`。

## 5. 历史会话 — 工具调用还原

### 5.1 还原算法

在 `App.kt` 的 `onResume` 回调中，解析 session 消息列表：

1. 遍历所有消息，识别三种类型：
   - `role: "assistant"` 且包含 `tool_calls` → 构建 `ToolCall` 列表，状态设为 RUNNING
   - `role: "tool"` → 匹配 `tool_call_id`，更新对应 `ToolCall.status = COMPLETED`
   - 其他消息 → 现有逻辑不变
2. 一个 assistant 消息可能包含多个 tool_calls，全部构建为同一消息的 toolCalls
3. tool 消息不单独生成 ChatMessage，只用于更新 toolCalls 的状态

### 5.2 参数格式化

`function.arguments` 是 JSON 字符串，使用 Jackson 的 `ObjectMapper` 做 pretty-print 后显示。

### 5.3 渲染效果

恢复后的工具调用与实时对话使用相同的 `ToolCallCard` 组件渲染，差异点：
- 所有状态为 `COMPLETED`（不会出现 RUNNING）
- 工具结果保持完整（不截断 10KB 限制）

## 涉及文件清单

| 文件 | 改动类型 |
|------|----------|
| `backend/.../BackgroundAgentExecutor.java` | 修改：添加进度发布 |
| `backend/.../ForkAgentExecutor.java` | 修改：添加 MessageBus 引用 + 进度发布 |
| `backend/.../AgentTool.java` | 修改：传递 parentToolCallId |
| `java-fx-app/.../BackendBridge.java` | 修改：ProgressEvent 扩展 + 路由逻辑 |
| `compose-app/.../Bridge.kt` | 修改：Progress 扩展 + 新增方法 |
| `compose-app/.../model/ChatMessage.kt` | 修改：ToolCall 扩展 + SubagentCall |
| `compose-app/.../model/StatusInfo.kt` | 修改：新增 AgentTaskInfo |
| `compose-app/.../layout/StatusBar.kt` | 修改：高亮 Agent 段 |
| `compose-app/.../components/StatusPopover.kt` | 修改：Agent 面板重构 |
| `compose-app/.../pages/ChatPage.kt` | 修改：onProgress 新增子Agent分支 |
| `compose-app/.../components/MessageBubble.kt` | 修改：ToolCallCard 嵌套子Agent |
| `compose-app/.../App.kt` | 修改：轮询活跃任务 + 历史工具调用还原 |

## 验证标准

1. 调用 `Agent(prompt="...")` 时，对话中显示 Agent 工具卡片，内部嵌套子Agent的工具调用
2. 状态栏 Agent 段在有活跃子Agent时高亮并显示计数
3. 点击状态栏 Agent 段显示当前活跃子Agent的详情
4. 恢复历史会话后，工具调用正确显示（状态均为已完成）
5. 无活跃子Agent时状态栏恢复正常显示
