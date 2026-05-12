# Handoff - Subagent Activity UI Implementation

## Current State

Worktree `subagent-activity-ui` (branch: `worktree-subagent-activity-ui`) has 4 commits:

```
630dfec fix: remove empty finally block in BackgroundAgentExecutor
6477af7 fix: add 200ms throttling to subagent progress publishing
a5766e2 feat: add progress callback to RunAgent and publish progress from BackgroundAgentExecutor
11da61a feat: add currentToolCallId to ToolUseContext, set it in AgentLoop, add getActiveTasks to AppState
```

## Completed

- **Tasks 1-3**: ToolUseContext.currentToolCallId, AgentLoop.setCurrentToolCallId, AppState.getActiveTasks()
- **Tasks 4-5**: RunAgent progressConsumer parameter + BackgroundAgentExecutor publishSubagentProgress
  - Spec review ✅ (after fixing 200ms throttle gap)
  - Code quality review ✅ (after removing empty finally block)

## Next: Task 6 - ForkAgentExecutor progress publishing

ForkAgentExecutor is at `src/main/java/agent/subagent/fork/ForkAgentExecutor.java`.

Changes needed:
1. Add `MessageBus messageBus` field + constructor parameter
2. Add `String channel, String chatId` params to `execute()` method (for MessageBus routing)
3. Add `publishSubagentProgress()` helper (same pattern as BackgroundAgentExecutor)
4. In `execute()`: publish "running" before executeLoop, wire progressConsumer
5. In `executeLoop()`: publish "tool_call"/"tool_result" before/after executeTool
6. After completion: publish "completed"/"error"
7. Add 200ms throttling (same pattern)

## Task Queue

After Task 6:
- Tasks 7-8: AgentTool wiring - extract parentToolCallId, wire MessageBus to ForkAgentExecutor
- Tasks 9-10: Bridge layer - BackendBridge ProgressEvent, routeOutboundToSession, Bridge.kt
- Tasks 11+14: UI Models - SubagentCall, ToolCall.subCalls, StatusInfo.agentTasks
- Task 12: ChatPage onProgress sub-agent handling
- Task 13: MessageBubble ToolCallCard sub-agent rendering
- Tasks 15-16: StatusBar + StatusPopover
- Tasks 17-18: App.kt polling + history restoration
- Task 19: Compile verification

## Design Doc

`docs/superpowers/specs/2026-05-12-subagent-activity-ui-design.md`

## Plan File

`docs/superpowers/plans/2026-05-12-subagent-activity-ui.md`
