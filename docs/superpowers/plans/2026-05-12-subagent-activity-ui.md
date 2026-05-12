# Sub-Agent Activity UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Display real-time sub-agent activity in Compose Desktop UI: nested tool call cards in chat, status bar indicator with popover, and tool call restoration from session history.

**Architecture:** Bus-based progress events for chat display (event-driven), polling-based AppState query for status bar (1s interval), and session message parsing for history restoration.

**Tech Stack:** Java 17 (backend), Kotlin/Compose Desktop (UI), Gradle, Jackson, SLF4J

---

### Task 0: Pre-flight — Read key files

- [ ] **Step 1: Read ToolUseContext.java** to understand how to add a currentToolCallId field

Run: `cat -n backend/src/main/java/agent/tool/ToolUseContext.java | head -300`

- [ ] **Step 2: Read AgentLoop.java around line 2350-2380** to see where tools.execute() is called

Run: `cat -n backend/src/main/java/agent/AgentLoop.java | sed -n '2350,2380p'`

---

### Task 1: Backend — Add currentToolCallId to ToolUseContext

**Files:**
- Modify: `backend/src/main/java/agent/tool/ToolUseContext.java`

- [ ] **Step 1: Add field, getter, and setter**

```java
// Add field alongside other fields:
private String currentToolCallId;

// Add getter:
public String getCurrentToolCallId() { return currentToolCallId; }

// Add setter:
public void setCurrentToolCallId(String id) { this.currentToolCallId = id; }
```

- [ ] **Step 2: Add to Builder**

```java
// In the Builder class:
private String currentToolCallId;

public Builder currentToolCallId(String id) {
    this.currentToolCallId = id;
    return this;
}

// In build():
instance.currentToolCallId = currentToolCallId;
```

---

### Task 2: Backend — Set currentToolCallId before tool execution in AgentLoop

**Files:**
- Modify: `backend/src/main/java/agent/AgentLoop.java`

- [ ] **Step 1: Add setter call before tools.execute()**

Around line 2371, before `return tools.execute(...)`:

```java
// Before executing tool, set the current tool call ID on context
toolContext.setCurrentToolCallId(tc.getId());
```

---

### Task 3: Backend — Add getActiveTasks() to AppState

**Files:**
- Modify: `backend/src/main/java/agent/subagent/task/AppState.java`

- [ ] **Step 1: Add instance method getActiveTasks()**

```java
public java.util.List<TaskState> getActiveTasks() {
    return tasks.values().stream()
            .filter(t -> !t.isTerminal())
            .collect(java.util.stream.Collectors.toList());
}
```

---

### Task 4: Backend — Add progress callback to RunAgent.executeQueryLoopAsync

**Files:**
- Modify: `backend/src/main/java/agent/subagent/execution/RunAgent.java`

- [ ] **Step 1: Add progressConsumer parameter and publish events**

```java
// Update method signature (around line 168):
public static String executeQueryLoopAsync(String systemPrompt, String userPrompt,
                                           String agentType, String model,
                                           LLMProvider provider, ToolUseContext toolUseContext,
                                           java.util.function.Consumer<Map<String, Object>> progressConsumer)

// Inside the tool loop (around line 286-303), add before/after executeTool:
for (var toolCall : response.getToolCalls()) {
    String toolName = toolCall.getName();
    Map<String, Object> toolArgs = toolCall.getArguments();
    String toolCallId = toolCall.getId();

    // --- ADD: publish pre-tool event ---
    if (progressConsumer != null) {
        progressConsumer.accept(java.util.Map.of(
            "_subagent_status", "tool_call",
            "_subagent_tool_name", toolName,
            "_subagent_tool_params", safeTruncate(GsonFactory.toJson(toolArgs), 500),
            "_subagent_tool_call_id", toolCallId,
            "_subagent_iteration", iterations.get()
        ));
    }

    String toolResult = executeTool(toolName, toolArgs, toolUseContext);

    // --- ADD: publish post-tool event ---
    if (progressConsumer != null) {
        String truncated = toolResult != null && toolResult.length() > 10240
            ? toolResult.substring(0, 10240) + "... [truncated]"
            : toolResult;
        progressConsumer.accept(java.util.Map.of(
            "_subagent_status", "tool_result",
            "_subagent_tool_name", toolName,
            "_subagent_tool_result", truncated,
            "_subagent_tool_call_id", toolCallId,
            "_subagent_iteration", iterations.get()
        ));
    }

    // rest of existing code...
}
```

- [ ] **Step 2: Add safeTruncate helper if not present**

```java
private static String safeTruncate(String s, int maxLen) {
    if (s == null) return "";
    return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
}
```

---

### Task 5: Backend — Publish progress from BackgroundAgentExecutor

**Files:**
- Modify: `backend/src/main/java/agent/subagent/execution/BackgroundAgentExecutor.java`

- [ ] **Step 1: Add parentToolCallId to TaskMetadata**

```java
// Add field:
final String parentToolCallId;

// Update constructor:
TaskMetadata(String agentType, String prompt, String sessionKey,
             String channel, String chatId, String parentToolCallId) {
    // existing fields...
    this.parentToolCallId = parentToolCallId;
}
```

- [ ] **Step 2: Add publishSubagentProgress helper method**

```java
private void publishSubagentProgress(String taskId, String status,
                                      String toolName, String toolParams,
                                      String toolResult, String toolCallId,
                                      int iteration) {
    if (messageBus == null) return;
    TaskMetadata meta = taskMetadata.get(taskId);
    if (meta == null) return;

    Map<String, Object> metadata = new java.util.LinkedHashMap<>();
    metadata.put("_progress", true);
    metadata.put("_subagent_progress", true);
    metadata.put("_subagent_task_id", taskId);
    metadata.put("_subagent_type", meta.agentType);
    metadata.put("_subagent_status", status);
    metadata.put("_subagent_iteration", iteration);
    if (meta.parentToolCallId != null) metadata.put("_parent_tool_call_id", meta.parentToolCallId);
    if (toolName != null) metadata.put("_subagent_tool_name", toolName);
    if (toolParams != null) metadata.put("_subagent_tool_params", toolParams);
    if (toolResult != null) metadata.put("_subagent_tool_result", toolResult);
    if (toolCallId != null) metadata.put("_subagent_tool_call_id", toolCallId);

    messageBus.publishOutbound(new OutboundMessage(
        meta.channel, meta.chatId, "", List.of(), metadata
    ));
}
```

- [ ] **Step 3: Accept parentToolCallId parameter in executeAsync**

```java
// Update signature (line 112):
public String executeAsync(String agentType, String prompt, String systemPrompt,
                           ToolUseContext parentContext,
                           String sessionKey, String channel, String chatId,
                           String parentToolCallId)
```

- [ ] **Step 4: Store parentToolCallId when creating TaskMetadata**

```java
// Around line 137 - update metadata creation:
taskMetadata.put(taskId, new TaskMetadata(agentType, prompt, sessionKey, channel, chatId, parentToolCallId));
```

- [ ] **Step 5: Publish "running" event after task activation**

```java
// After AppState.updateTaskState to RUNNING (around line 158):
publishSubagentProgress(taskId, "running", null, null, null, null, 0);
```

- [ ] **Step 6: Pass progressConsumer to RunAgent.executeQueryLoopAsync**

```java
// Replace the line calling RunAgent.executeQueryLoopAsync (around line 161):
String result = RunAgent.executeQueryLoopAsync(
    systemPrompt, prompt, agentType, null, null, isolatedContext,
    progressData -> {
        String st = (String) progressData.get("_subagent_status");
        String tn = (String) progressData.get("_subagent_tool_name");
        String tp = (String) progressData.get("_subagent_tool_params");
        String tr = (String) progressData.get("_subagent_tool_result");
        String tcid = (String) progressData.get("_subagent_tool_call_id");
        int iter = progressData.get("_subagent_iteration") instanceof Number n ? n.intValue() : 0;
        publishSubagentProgress(taskId, st, tn, tp, tr, tcid, iter);
    }
);
```

- [ ] **Step 7: Publish "completed" event after result**

```java
// After resultFuture.complete(result) (around line 181):
publishSubagentProgress(taskId, "completed", null, null, null, null, 0);
```

---

### Task 6: Backend — Publish progress from ForkAgentExecutor

**Files:**
- Modify: `backend/src/main/java/agent/subagent/fork/ForkAgentExecutor.java`

- [ ] **Step 1: Add MessageBus field and constructor parameter**

```java
// Add field:
private final MessageBus messageBus;

// Add to shorthand constructor (line 74):
public ForkAgentExecutor(LLMProvider provider, Path workspace, Path sessionsDir,
                         ForkCompletionCallback completionCallback,
                         AppState appState, AppState.Setter setAppState,
                         ToolView toolView, MessageBus messageBus) {
    this(provider, workspace, sessionsDir, DEFAULT_MAX_ITERATIONS, completionCallback,
         appState, setAppState, toolView, messageBus);
}

// Add to full constructor (line 86):
public ForkAgentExecutor(..., MessageBus messageBus) {
    // ... existing fields ...
    this.messageBus = messageBus;
}
```

- [ ] **Step 2: Add channel/chatId parameters to execute()**

```java
// Update method signatures:
public CompletableFuture<ForkResult> execute(String sessionId, ForkContext forkContext,
                                              SubagentContext subagentContext,
                                              String parentToolCallId,
                                              String channel, String chatId)
```

- [ ] **Step 3: Add publishSubagentProgress helper**

```java
private void publishSubagentProgress(String runId, String status, String toolName,
                                      String toolParams, String toolResult,
                                      String toolCallId, int iteration,
                                      String parentToolCallId, String channel, String chatId) {
    if (messageBus == null) return;
    Map<String, Object> metadata = new java.util.LinkedHashMap<>();
    metadata.put("_progress", true);
    metadata.put("_subagent_progress", true);
    metadata.put("_subagent_task_id", runId);
    metadata.put("_subagent_type", "fork");
    metadata.put("_subagent_status", status);
    metadata.put("_subagent_iteration", iteration);
    if (parentToolCallId != null) metadata.put("_parent_tool_call_id", parentToolCallId);
    if (toolName != null) metadata.put("_subagent_tool_name", toolName);
    if (toolParams != null) metadata.put("_subagent_tool_params", toolParams);
    if (toolResult != null) metadata.put("_subagent_tool_result", toolResult);
    if (toolCallId != null) metadata.put("_subagent_tool_call_id", toolCallId);
    messageBus.publishOutbound(new OutboundMessage(channel, chatId, "", List.of(), metadata));
}
```

- [ ] **Step 4: Publish events in executeLoop**

In `executeLoop`, inside the tool loop (around line 328):

```java
// Before executeTool:
publishSubagentProgress(runId, "tool_call", tc.getName(),
    safeTruncate(toJson(tc.getArguments()), 500), null, tc.getId(),
    i, parentToolCallId, forkContext != null ? forkContext.getChannel() : null,
    forkContext != null ? forkContext.getChatId() : null);

String result = executeTool(tc.getName(), tc.getArguments(), canUseTool);

// After executeTool:
publishSubagentProgress(runId, "tool_result", tc.getName(), null,
    result != null && result.length() > 10240 ? result.substring(0, 10240) + "... [truncated]" : result,
    tc.getId(), i, parentToolCallId,
    forkContext != null ? forkContext.getChannel() : null,
    forkContext != null ? forkContext.getChatId() : null);
```

Wait — ForkContext doesn't have channel/chatId. Need to pass them differently. The simplest: pass channel, chatId as method params to executeLoop (they were passed to execute). But executeLoop is private. Just store channel/chatId as instance fields set per execution, or pass as parameters.

Actually the cleanest approach is to just store channel/chatId as fields on ForkAgentExecutor, set before each execute() call. But since execute() is async, this could race.

Better: pass them directly to the execute methods, and pass to executeLoop.

Let me simplify: the channel/chatId are available from the caller (AgentTool). ForkAgentExecutor receives them as execute() params and passes them through.

Actually, let me just add channel/chatId as constructor params since they never change at runtime... wait, they can change per session.

OK, let me just keep simple: pass channel/chatId to execute() and store them in local variables that the inner lambda captures.

The execute() method is async and runs in a separate thread. The parameters need to be final/effectively final. So let me just add them as method parameters.

Let me keep this simpler in the plan. The ForkAgentExecutor already has a `runId` that identifies the task. The publishSubagentProgress just needs channel/chatId parameters.

- [ ] **Step 4 (revised): Publish from within execute() method**

Before `executeLoop()` call (around line 209), publish "running":

```java
publishSubagentProgress(runId, "running", null, null, null, null, 0,
    parentToolCallId, channel, chatId);
```

Inside `executeLoop()`, the tool loop needs channel/chatId too. Pass them as parameters to executeLoop.

After `executeLoop()` returns, publish "completed" (around line 230):

```java
publishSubagentProgress(runId, "completed", null, null, null, null, 0,
    parentToolCallId, channel, chatId);
```

---

### Task 7: Backend — Pass parentToolCallId and channel/chatId from AgentTool

**Files:**
- Modify: `backend/src/main/java/agent/subagent/execution/AgentTool.java`

- [ ] **Step 1: Extract parentToolCallId from parentContext**

```java
// In runNamedAgent (around line 221) and runForkAgent (around line 281):
String parentToolCallId = parentContext != null ? parentContext.getCurrentToolCallId() : null;
```

- [ ] **Step 2: Pass to backgroundExecutor.executeAsync**

```java
// In runNamedAgent (around line 234):
return backgroundExecutor.executeAsync(
    subagentType, prompt, systemPrompt, parentContext,
    sessionKey, channel, chatId,
    parentToolCallId);
```

- [ ] **Step 3: Pass to forkExecutor.execute**

```java
// In runForkAgent, async path (around line 321):
CompletableFuture<ForkAgentExecutor.ForkResult> future =
    forkExecutor.execute(sessionId, forkContext, subagentContext,
                         parentToolCallId, channel, chatId);

// In runForkAgent, sync path (around line 333):
ForkAgentExecutor.ForkResult result = forkExecutor.execute(
    sessionId, forkContext, subagentContext,
    parentToolCallId, channel, chatId
).toCompletableFuture().join();
```

---

### Task 8: Backend — Wire up MessageBus in AgentLoop

**Files:**
- Modify: `backend/src/main/java/agent/AgentLoop.java`

- [ ] **Step 1: Pass messageBus to ForkAgentExecutor constructor**

Find where ForkAgentExecutor is created in AgentLoop and pass `messageBus`:

```java
// Existing ForkAgentExecutor creation:
new ForkAgentExecutor(provider, workspace, sessionsDir, maxIterations,
    completionCallback, appState, appState.setter(), toolView,
    messageBus)  // add this
```

---

### Task 9: Bridge — Extend ProgressEvent and routing in BackendBridge

**Files:**
- Modify: `java-fx-app/src/main/java/gui/ui/BackendBridge.java`

- [ ] **Step 1: Add sub-agent fields to ProgressEvent record**

```java
public record ProgressEvent(String content, boolean isToolHint,
                            boolean isToolResult, String toolName, String toolCallId,
                            boolean isReasoning, boolean isToolError,
                            boolean isSubagentProgress,
                            String subagentTaskId, String subagentType,
                            String subagentStatus, String subagentToolName,
                            String subagentToolParams, String subagentToolResult,
                            String subagentToolCallId, int subagentIteration,
                            String parentToolCallId) {
    // Existing convenience constructor
    public ProgressEvent(String content, boolean isToolHint) {
        this(content, isToolHint, false, null, null, false, false,
             false, null, null, null, null, null, null, null, 0, null);
    }
}
```

- [ ] **Step 2: In routeOutboundToSession, read _subagent_progress metadata**

Around line 265, before the `uiDispatcher.accept` call, read sub-agent fields:

```java
boolean isSubagentProgress = Boolean.TRUE.equals(meta.get("_subagent_progress"));
String subagentTaskId = meta.get("_subagent_task_id") instanceof String s ? s : null;
String subagentType = meta.get("_subagent_type") instanceof String s ? s : null;
String subagentStatus = meta.get("_subagent_status") instanceof String s ? s : null;
String subagentToolName = meta.get("_subagent_tool_name") instanceof String s ? s : null;
String subagentToolParams = meta.get("_subagent_tool_params") instanceof String s ? s : null;
String subagentToolResult = meta.get("_subagent_tool_result") instanceof String s ? s : null;
String subagentToolCallId = meta.get("_subagent_tool_call_id") instanceof String s ? s : null;
int subagentIteration = meta.get("_subagent_iteration") instanceof Number n ? n.intValue() : 0;
String parentToolCallId = meta.get("_parent_tool_call_id") instanceof String s ? s : null;
```

- [ ] **Step 3: Pass sub-agent fields to ProgressEvent constructor in the progress callback**

```java
uiDispatcher.accept(() -> cb.accept(
    new ProgressEvent(content, isToolHint, isToolResult, toolName, toolCallId,
                      isReasoning, isToolError,
                      isSubagentProgress, subagentTaskId, subagentType,
                      subagentStatus, subagentToolName, subagentToolParams,
                      subagentToolResult, subagentToolCallId, subagentIteration,
                      parentToolCallId)));
```

---

### Task 10: Bridge — Extend Bridge.kt Progress and add getActiveAgentTasks

**Files:**
- Modify: `compose-app/src/main/kotlin/gui/ui/Bridge.kt`

- [ ] **Step 1: Add sub-agent fields to Progress data class**

```kotlin
data class Progress(
    val content: String,
    val isToolHint: Boolean = false,
    val isToolResult: Boolean = false,
    val toolName: String? = null,
    val toolCallId: String? = null,
    val isReasoning: Boolean = false,
    val isToolError: Boolean = false,
    val isSubagentProgress: Boolean = false,
    val subagentTaskId: String? = null,
    val subagentType: String? = null,
    val subagentStatus: String? = null,
    val subagentToolName: String? = null,
    val subagentToolParams: String? = null,
    val subagentToolResult: String? = null,
    val subagentToolCallId: String? = null,
    val subagentIteration: Int = 0,
    val parentToolCallId: String? = null
)
```

- [ ] **Step 2: Map fields in progressAdapter**

Add to the progressAdapter lambda:

```kotlin
isSubagentProgress = event.isSubagentProgress(),
subagentTaskId = event.subagentTaskId(),
subagentType = event.subagentType(),
subagentStatus = event.subagentStatus(),
subagentToolName = event.subagentToolName(),
subagentToolParams = event.subagentToolParams(),
subagentToolResult = event.subagentToolResult(),
subagentToolCallId = event.subagentToolCallId(),
subagentIteration = event.subagentIteration(),
parentToolCallId = event.parentToolCallId()
```

- [ ] **Step 3: Add getActiveAgentTasks method**

```kotlin
fun getActiveAgentTasks(): List<AgentTaskInfo> {
    val appState = bridge.agentLoop?.appState ?: return emptyList()
    val activeTasks = appState.getActiveTasks()
    return activeTasks.map { task ->
        val agentType = task.type?.name() ?: ""
        val status = task.status?.name() ?: ""
        AgentTaskInfo(
            taskId = task.id,
            agentType = agentType,
            status = status,
            currentTool = null,  // not available from AppState
            iteration = 0,
            chatId = null
        )
    }
}
```

---

### Task 11: UI Model — Extend ToolCall and StatusInfo

**Files:**
- Modify: `compose-app/src/main/kotlin/gui/ui/model/ChatMessage.kt`
- Modify: `compose-app/src/main/kotlin/gui/ui/model/StatusInfo.kt`

- [ ] **Step 1: Add SubagentCall data class and subCalls to ToolCall**

In `ChatMessage.kt`:

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

- [ ] **Step 2: Add AgentTaskInfo and extend StatusInfo**

In `StatusInfo.kt`:

```kotlin
data class StatusInfo(
    val modelName: String = "",
    val agentName: String = "default",
    val shellConnected: Boolean = false,
    val mcpOnline: Int = 0,
    val mcpTotal: Int = 0,
    val contextUsage: Float = 0f,
    val memoryBlocks: Int = 0,
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

---

### Task 12: UI Chat — Handle sub-agent progress in ChatPage

**Files:**
- Modify: `compose-app/src/main/kotlin/gui/ui/pages/ChatPage.kt`

- [ ] **Step 1: Add sub-agent progress handling in onProgress**

In the `onProgress` lambda (after line 116), add a new branch for `progress.isSubagentProgress`:

```kotlin
// In onProgress, after the isToolHint and isToolResult branches:
if (progress.isSubagentProgress) {
    val parentId = progress.parentToolCallId
    if (parentId != null) {
        // Find the message that contains the parent tool call
        val msgIdx = exchangeMsgs.indexOfLast { msg ->
            msg.toolCalls.any { tc -> tc.name == "Agent" /* or match by being the parent */ }
        }
        // Actually match by tool call ID stored in params or by index
        // Better: match the most recent tool call that has subCalls support
        val msgIdx2 = exchangeMsgs.indexOfLast { msg ->
            msg.toolCalls.isNotEmpty()
        }
        if (msgIdx2 >= 0) {
            val msg = exchangeMsgs[msgIdx2]
            val updatedCalls = msg.toolCalls.map { tc ->
                if (tc.subCalls.isNotEmpty() || progress.subagentStatus == "running") {
                    // Update existing subCalls or add first entry
                    val newSubCalls = when (progress.subagentStatus) {
                        "running" -> {
                            // Add running entry if not present
                            if (tc.subCalls.none { it.taskId == progress.subagentTaskId }) {
                                tc.subCalls + SubagentCall(
                                    taskId = progress.subagentTaskId ?: "",
                                    agentType = progress.subagentType ?: "",
                                    toolName = "",
                                    status = ToolStatus.RUNNING,
                                    iteration = progress.subagentIteration
                                )
                            } else tc.subCalls
                        }
                        "tool_call" -> {
                            // Add new tool call entry to subCalls
                            tc.subCalls + SubagentCall(
                                taskId = progress.subagentTaskId ?: "",
                                agentType = progress.subagentType ?: "",
                                toolName = progress.subagentToolName ?: "",
                                toolParams = progress.subagentToolParams,
                                status = ToolStatus.RUNNING,
                                toolCallId = progress.subagentToolCallId,
                                iteration = progress.subagentIteration
                            )
                        }
                        "tool_result" -> {
                            // Update matching SubagentCall to COMPLETED/ERROR
                            tc.subCalls.map { sc ->
                                if (sc.toolCallId == progress.subagentToolCallId) {
                                    val isErr = progress.subagentToolResult?.startsWith("{") == true
                                    sc.copy(
                                        status = if (isErr) ToolStatus.ERROR else ToolStatus.COMPLETED,
                                        toolResult = progress.subagentToolResult
                                    )
                                } else sc
                            }
                        }
                        "completed" -> {
                            // Mark the running entry as completed
                            tc.subCalls.map { sc ->
                                if (sc.taskId == progress.subagentTaskId && sc.status == ToolStatus.RUNNING) {
                                    sc.copy(status = ToolStatus.COMPLETED)
                                } else sc
                            }
                        }
                        else -> tc.subCalls
                    }
                    tc.copy(subCalls = newSubCalls)
                } else tc
            }
            exchangeMsgs[msgIdx2] = msg.copy(toolCalls = updatedCalls)
            onMessagesChanged(exchangeMsgs.toList())
        }
    }
}
```

Note: The above is the core logic. The exact matching by parentToolCallId needs care. Since `_tool_hint` events set a `tool_call_id` that matches the Agent tool call, and subAgent progress events carry `_parent_tool_call_id`, the ChatPage needs to map between them. But the Progress events from the main loop's `_tool_hint` set toolCallId correctly, and subagent events set parentToolCallId. So in ChatPage, we can look for a message whose tool call's id (or index) matches the parentToolCallId.

Actually, the simpler approach: The progress events from sub-agents carry `parentToolCallId` which is the ID of the main Agent tool call. But the main `_tool_hint` events publish with `toolCallId` as the main tool call ID. These should be the same value. So in ChatPage, when we get a sub-agent event with `parentToolCallId`, we search exchangeMsgs for the tool call whose `tool_call_id` (stored as... hmm, the `ToolCall` model doesn't have a `toolCallId` field yet) matches.

Actually, looking at the current code, the ChatPage doesn't store `toolCallId` on `ToolCall` — it only has `name`, `status`, `params`, `result`. But the `_tool_hint` events pass `toolCallId` through `Progress.toolCallId`.

So we need to either:
1. Add `toolCallId` to `ToolCall` model, or
2. Match by tool name + index position

Option 1 is cleaner. Let me add it.

Actually wait — looking at the current code more carefully, the main loop's `_tool_hint` creates a `ChatMessage` with `toolCalls = [ToolCall(name=progress.toolName)]`. The `toolCallId` from the progress is in `progress.toolCallId` but it's not stored on `ToolCall`.

Let me just update the approach: Add `toolCallId` to `ToolCall`.

But that changes the model... Let me think about what's simplest.

Actually the simplest approach: Don't worry about matching by parentToolCallId. Instead, just always update the LAST tool call that has subCalls support. For the sub-agent scenario, the Agent tool call will be the last/only tool call with subCalls.

Wait no, that's fragile. Let me think of a better way.

The `_tool_hint` event from the main loop for the Agent tool has:
- `toolName = "Agent"`
- `toolCallId = "call_xxx"` (the actual LLM tool call ID)

The subagent progress events have:
- `parentToolCallId = "call_xxx"` (matches the Agent's toolCallId)

So in ChatPage, I need to find the ToolCall whose ID matches `parentToolCallId`. Since we don't store toolCallId on ToolCall, I match by: find the last tool call with name "Agent" where we haven't stored the toolCallId.

Simplest: just add `toolCallId` to `ToolCall` model. It's a useful field anyway.

```kotlin
data class ToolCall(
    val name: String,
    val status: ToolStatus,
    val params: String? = null,
    val result: String? = null,
    val id: String? = null,        // ← NEW: tool call ID for correlation
    val subCalls: List<SubagentCall> = emptyList()
)
```

And in ChatPage, when creating the initial tool hint:
```kotlin
toolCalls = listOf(ToolCall(
    name = progress.toolName,
    status = ToolStatus.RUNNING,
    params = progress.content,
    id = progress.toolCallId  // ← store this
))
```

Then when matching sub-agent events:
```kotlin
val msgIdx = exchangeMsgs.indexOfLast { msg ->
    msg.toolCalls.any { it.id == progress.parentToolCallId }
}
```

This is clean. Let me go with this.

---

### Task 13: UI Chat — Render sub-agent calls in MessageBubble

**Files:**
- Modify: `compose-app/src/main/kotlin/gui/ui/components/MessageBubble.kt`

- [ ] **Step 1: Add SubagentCallItem composable**

Add a new private composable after `ToolCallCard`:

```kotlin
@Composable
private fun SubagentCallItem(sc: SubagentCall, modifier: Modifier = Modifier) {
    var expandedResult by remember { mutableStateOf(false) }
    val statusIcon = when (sc.status) {
        ToolStatus.RUNNING -> "⏳"
        ToolStatus.COMPLETED -> "✅"
        ToolStatus.ERROR -> "❌"
    }
    val statusColor = when (sc.status) {
        ToolStatus.RUNNING -> Color(0xFF6B7280)
        ToolStatus.COMPLETED -> Color(0xFF22C55E)
        ToolStatus.ERROR -> Color(0xFFEF4444)
    }

    Column(
        modifier = modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(statusIcon, fontSize = 12.sp)
            Spacer(Modifier.width(4.dp))
            Text(
                sc.toolName.ifEmpty { "子Agent(${sc.agentType})" },
                fontSize = 12.sp,
                color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (sc.iteration > 0) {
                Text("迭代 ${sc.iteration}", fontSize = 10.sp, color = AppColors.TextSecondary)
            }
        }
        // Params (collapsible)
        if (!sc.toolParams.isNullOrBlank()) {
            Text(
                sc.toolParams,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = AppColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp)
            )
        }
        // Result (if completed/error)
        if (!sc.toolResult.isNullOrBlank() && sc.status != ToolStatus.RUNNING) {
            val displayResult = trimToolResult(sc.toolResult)
            val isTruncated = displayResult != sc.toolResult
            Text(
                displayResult,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = statusColor,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp)
            )
            if (isTruncated) {
                Text(
                    "查看完整结果",
                    fontSize = 10.sp,
                    color = AppColors.Accent,
                    modifier = Modifier.padding(start = 16.dp).clickable { expandedResult = true }
                )
            }
        }
        // Full result window
        if (expandedResult && sc.toolResult != null) {
            // Reuse same Window pattern from ToolCallCard
            val rawWindowState = rememberWindowState(
                position = WindowPosition.Aligned(Alignment.Center),
                width = 600.dp, height = 400.dp
            )
            Window(
                onCloseRequest = { expandedResult = false },
                title = "工具结果 - ${sc.toolName}",
                state = rawWindowState, resizable = true
            ) {
                (window as? java.awt.Window)?.minimumSize = java.awt.Dimension(400, 300)
                val rawScrollState = rememberScrollState()
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(AppColors.Surface)
                        .padding(16.dp).verticalScroll(rawScrollState)) {
                        SelectionContainer {
                            Text(sc.toolResult, style = AppTheme.typography.mono, color = AppColors.TextPrimary)
                        }
                    }
                    VerticalScrollbar(Modifier.width(8.dp).padding(vertical = 2.dp),
                        adapter = rememberScrollbarAdapter(scrollState = rawScrollState))
                }
            }
        }
    }
}
```

- [ ] **Step 2: Modify ToolCallCard to render subCalls**

Inside `ToolCallCard`, after the status row and before the closing `}` of the `Column`, add:

```kotlin
// Sub-agent calls (if any)
if (tc.subCalls.isNotEmpty()) {
    Spacer(Modifier.height(6.dp))
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFF1F5F9)).padding(6.dp)
    ) {
        Column {
            Text(
                "子Agent活动 (${tc.subCalls.size})",
                fontSize = 11.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = AppColors.TextSecondary
            )
            tc.subCalls.forEach { sc -> SubagentCallItem(sc) }
        }
    }
}
```

- [ ] **Step 3: Add missing imports**

Add to the import section:
```kotlin
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
```

---

### Task 14: UI Model — Add toolCallId to ToolCall

**Files:**
- Modify: `compose-app/src/main/kotlin/gui/ui/model/ChatMessage.kt`

- [ ] **Step 1: Add id field**

```kotlin
data class ToolCall(
    val name: String,
    val status: ToolStatus,
    val params: String? = null,
    val result: String? = null,
    val id: String? = null,
    val subCalls: List<SubagentCall> = emptyList()
)
```

- [ ] **Step 2: Update ChatPage tool hint creation to pass toolCallId**

In `ChatPage.kt`, when creating tool hint in the onProgress callback (around line 123):

```kotlin
exchangeMsgs.add(ChatMessage(
    id = "tool_${System.currentTimeMillis()}_${progress.toolName}_${progress.content.hashCode().toString().take(8)}",
    role = ChatMessage.Role.ASSISTANT,
    content = "",
    toolCalls = listOf(ToolCall(
        name = progress.toolName ?: "",
        status = ToolStatus.RUNNING,
        params = progress.content,
        id = progress.toolCallId    // ← ADD THIS
    ))
))
```

---

### Task 15: UI StatusBar — Highlight agent segment when active

**Files:**
- Modify: `compose-app/src/main/kotlin/gui/ui/layout/StatusBar.kt`

- [ ] **Step 1: Add highlight parameter to segment function**

```kotlin
@Composable
private fun segment(
    icon: String, label: String, d: StatusDetail, cur: StatusDetail,
    onClick: () -> Unit, highlight: Boolean = false
) {
    Row(
        Modifier.clickable { onClick() }.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, style = AppTheme.typography.caption,
            color = if (highlight) AppColors.Accent else AppColors.TextSecondary)
        Spacer(Modifier.width(4.dp))
        Text(label, style = AppTheme.typography.caption,
            color = if (highlight) AppColors.Accent else AppColors.TextPrimary)
    }
}
```

- [ ] **Step 2: Update Agent segment call to pass highlight**

```kotlin
segment(
    if (status.activeAgentCount > 0) "▓ ●" else "▓",
    if (status.activeAgentCount > 0) "Agent ${status.agentName} (${status.activeAgentCount})"
    else "Agent ${status.agentName}",
    StatusDetail.AGENT, detail,
    { detail = if (detail == StatusDetail.AGENT) StatusDetail.NONE else StatusDetail.AGENT },
    highlight = status.activeAgentCount > 0
)
```

---

### Task 16: UI StatusBar — Extend StatusPopover agent panel

**Files:**
- Modify: `compose-app/src/main/kotlin/gui/ui/components/StatusPopover.kt`

- [ ] **Step 1: Rewrite the AGENT case to show active tasks**

```kotlin
StatusDetail.AGENT -> Column {
    Text("Agent", fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text("名称: ${status.agentName}", style = AppTheme.typography.caption)
    Spacer(Modifier.height(8.dp))
    if (status.activeAgentCount > 0) {
        Text("活跃子Agent:", style = AppTheme.typography.caption,
            fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        status.agentTasks.forEach { task ->
            val statusIcon = when (task.status) {
                "RUNNING" -> "●"
                "COMPLETED" -> "✓"
                "FAILED" -> "✗"
                "KILLED" -> "⊘"
                else -> "?"
            }
            val statusColor = when (task.status) {
                "RUNNING" -> Color(0xFF22C55E)
                "COMPLETED" -> Color(0xFF6B7280)
                "FAILED" -> Color(0xFFEF4444)
                else -> Color(0xFF6B7280)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(statusIcon, color = statusColor, fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(
                        task.agentType.ifEmpty { "子Agent" },
                        fontSize = 12.sp, color = AppColors.TextPrimary
                    )
                    if (task.currentTool != null) {
                        Text("当前工具: ${task.currentTool}", fontSize = 10.sp,
                            color = AppColors.TextSecondary)
                    }
                    if (task.chatId != null) {
                        Text("会话: ${task.chatId}", fontSize = 10.sp,
                            color = AppColors.TextSecondary)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    } else {
        Text("无活跃子Agent", style = AppTheme.typography.caption,
            color = AppColors.TextSecondary)
    }
}
```

---

### Task 17: App — Add polling for active agent tasks

**Files:**
- Modify: `compose-app/src/main/kotlin/gui/ui/App.kt`

- [ ] **Step 1: Add LaunchedEffect for polling**

Add before the main `Window` composition (around line 111):

```kotlin
// Poll active agent tasks for status bar
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

Add the delay import at the top:
```kotlin
import kotlinx.coroutines.delay
```

---

### Task 18: App — Restore tool calls from session history

**Files:**
- Modify: `compose-app/src/main/kotlin/gui/ui/App.kt`

- [ ] **Step 1: Rewrite the onResume message mapping**

Replace lines 253-268 with full tool call aware restoration:

```kotlin
data class PendingTool(val callId: String, val msgIndex: Int, val tcIndex: Int)
val pendingTools = mutableListOf<PendingTool>()
val chatMsgs = mutableListOf<ChatMessage>()

msgs.forEach { m ->
    val roleStr = m["role"]?.toString()
    when (roleStr) {
        "assistant" -> {
            val content = m["content"]?.toString() ?: ""
            val reasoning = m["reasoning_content"]?.toString()
            @Suppress("UNCHECKED_CAST")
            val rawToolCalls = m["tool_calls"] as? List<Map<String, Any>>
            if (rawToolCalls != null && rawToolCalls.isNotEmpty()) {
                val toolCalls = rawToolCalls.mapIndexed { i, tc ->
                    val func = tc["function"] as? Map<*, *>
                    val name = func?.get("name")?.toString() ?: ""
                    val rawArgs = func?.get("arguments")?.toString() ?: ""
                    val callId = tc["id"]?.toString() ?: ""
                    pendingTools.add(PendingTool(callId, chatMsgs.size, i))
                    ToolCall(
                        name = name,
                        status = ToolStatus.COMPLETED,
                        params = try {
                            com.fasterxml.jackson.databind.ObjectMapper()
                                .readTree(rawArgs).toPrettyString()
                        } catch (_: Exception) { rawArgs },
                        id = callId
                    )
                }
                chatMsgs.add(ChatMessage(
                    id = "hist_${System.nanoTime()}_${chatMsgs.size}",
                    role = ChatMessage.Role.ASSISTANT,
                    content = content,
                    reasoning = reasoning,
                    toolCalls = toolCalls
                ))
            } else {
                chatMsgs.add(ChatMessage(
                    id = "hist_${System.nanoTime()}_${chatMsgs.size}",
                    role = ChatMessage.Role.ASSISTANT,
                    content = content,
                    reasoning = reasoning
                ))
            }
        }
        "tool" -> {
            val callId = m["tool_call_id"]?.toString() ?: ""
            val result = m["content"]?.toString() ?: ""
            val pending = pendingTools.find { it.callId == callId }
            if (pending != null) {
                val msg = chatMsgs[pending.msgIndex]
                val updatedCalls = msg.toolCalls.toMutableList()
                updatedCalls[pending.tcIndex] = updatedCalls[pending.tcIndex].copy(
                    result = result
                )
                chatMsgs[pending.msgIndex] = msg.copy(toolCalls = updatedCalls)
            }
            // tool messages don't become separate ChatMessages
        }
        "user" -> {
            chatMsgs.add(ChatMessage(
                id = "hist_${System.nanoTime()}_${chatMsgs.size}",
                role = ChatMessage.Role.USER,
                content = m["content"]?.toString() ?: ""
            ))
        }
    }
}
```

---

### Task 19: Compile verification

- [ ] **Step 1: Run compileKotlin**

Run: `./gradlew :compose-app:compileKotlin --no-daemon 2>&1`

- [ ] **Step 2: Fix any compilation errors**

Read error output and fix issues.

- [ ] **Step 3: Run backend compile**

Run: `./gradlew :backend:compileJava --no-daemon 2>&1`
