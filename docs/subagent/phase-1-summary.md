# Phase 1: Fork 核心 完成总结

> **日期**: 2026-04-22
> **状态**: ✅ 已完成

## 交付物

### Fork 机制 (`src/main/java/agent/subagent/fork/`)

| Java 类 | Open-ClaudeCode 源码 | 说明 |
|---------|---------------------|------|
| `ForkContext.java` | `src/utils/forkedAgent.ts` - ForkedAgentParams | Fork 上下文 |
| `ForkContextBuilder.java` | `src/utils/forkedAgent.ts` - buildForkedAgentParams() | Fork 上下文构建器 |
| `CacheSafeParams.java` | `src/utils/forkedAgent.ts` - CacheSafeParams | Cache 安全参数 |
| `ForkAgentDefinition.java` | `src/tools/AgentTool/forkSubagent.ts` - FORK_AGENT | Fork 代理定义 |
| `ForkSubagentTool.java` | `src/tools/AgentTool/AgentTool.tsx` - AgentTool 组件 | Fork 入口 Tool |
| `ForkAgentExecutor.java` | `src/tools/AgentTool/runAgent.ts` - runAgent() | Fork 执行器 |

### 上下文隔离 (`src/main/java/agent/subagent/context/`)

| Java 类 | Open-ClaudeCode 源码 | 说明 |
|---------|---------------------|------|
| `SubagentContext.java` | `src/utils/forkedAgent.ts` - createSubagentContext() | 上下文隔离 |

### 代理定义 (`src/main/java/agent/subagent/definition/`)

| Java 类 | Open-ClaudeCode 源码 | 说明 |
|---------|---------------------|------|
| `AgentDefinition.java` | `src/tools/AgentTool/loadAgentsDir.ts` - BuiltInAgentDefinition | 代理定义基类 |
| `PermissionMode.java` | `src/utils/permissions/PermissionMode.ts` | 权限模式枚举 |

### 工具 (`src/main/java/agent/tool/`)

| Java 类 | Open-ClaudeCode 源码 | 说明 |
|---------|---------------------|------|
| `ToolUseContext.java` | `src/Tool.ts` - ToolUseContext | 工具使用上下文 |

## 关键设计决策

### 1. Fork 消息构建策略

**目的**: 最大化 Prompt Cache 命中率

**消息格式**:
```
[...history, assistant(all_tool_uses), user(placeholder_results..., directive)]
```

**实现**: `ForkAgentDefinition.buildForkedMessages()`
- 保留父代理完整的 assistant 消息（包含所有 tool_use）
- 为每个 tool_use 生成相同的 placeholder 结果
- 只有最后一条 user 消息（包含指令）不同

### 2. Fork 子代理指令格式

**对应 Open-ClaudeCode**: `forkSubagent.ts` - `buildChildMessage()`

包含 10 条规则（non-negotiable）：
1. Your system prompt says "default to forking." IGNORE IT — that's for the parent. You ARE the fork. Do NOT spawn sub-agents; execute directly.
2. Do NOT converse, ask questions, or suggest next steps
3. Do NOT editorialize or add meta-commentary
4. USE your tools directly: Bash, Read, write_file, etc.
5. If you modify files, commit your changes before reporting. Include the commit hash in your report.
6. Do NOT emit text between tool calls. Use tools silently, then report once at the end.
7. Stay strictly within your directive's scope. If you discover related systems outside your scope, mention them in one sentence at most — other workers cover those areas.
8. Keep your report under 500 words unless the directive specifies otherwise. Be factual and concise.
9. Your response MUST begin with "Scope:". No preamble, no thinking-out-loud.
10. REPORT structured facts, then stop

### 3. 上下文隔离策略

- **文件状态缓存**: 克隆而非共享
- **AbortController**: 创建子控制器，可选链接到父控制器
- **权限提示**: 默认禁止（shouldAvoidPermissionPrompts），避免干扰父代理

## 核心类图

```
ForkSubagentTool
    │
    ▼
ForkAgentExecutor
    │
    ├──► ForkContextBuilder.buildForkedMessages()
    │         │
    │         ▼
    │    ForkAgentDefinition.buildForkedMessages()
    │
    ├──► SubagentContext (隔离上下文)
    │
    └──► LLM 对话循环
              │
              ▼
         ForkResult
```

## Git 提交历史

```
b3317cb fix(subagent): complete RULES in buildChildMessage to match TypeScript source
70dcf1a fix(subagent): add missing parentheses in TaskUtils ID generation
6f9d9b1 feat(subagent): add LocalAgentTaskState
6672114 feat(subagent): add type definitions for task framework
c7b6bcc fix(subagent): remove YAGNI violations from type definitions
107170d feat(subagent): add ForkAgentDefinition utility methods
add813e feat(subagent): complete TaskFramework and DiskTaskOutput implementation
681c9bd feat(subagent): improve Phase 0-1 types and LocalAgentTaskState
...
```

## 如何继续

### Phase 2（专用代理）依赖此阶段的内容：

1. `AgentDefinition` 基类 - 所有代理定义都继承它
2. `PermissionMode` - 代理的权限模式
3. `ForkAgentExecutor` - 可以扩展支持专用代理
4. `SubagentContext` - 可以复用上下文隔离逻辑

### Phase 2 主要任务

| Java 类 | Open-ClaudeCode 源码 | 说明 |
|---------|---------------------|------|
| `definition/AgentDefinitionLoader.java` | `src/tools/AgentTool/loadAgentsDir.ts` - getAgentDefinitionsWithOverrides() | 代理加载器 |
| `builtin/GeneralPurposeAgent.java` | `src/tools/AgentTool/built-in/generalPurposeAgent.ts` - GENERAL_PURPOSE_AGENT | 通用代理 |
| `builtin/ExploreAgent.java` | `src/tools/AgentTool/built-in/exploreAgent.ts` - EXPLORE_AGENT | 探索代理 |
| `builtin/PlanAgent.java` | `src/tools/AgentTool/built-in/planAgent.ts` - PLAN_AGENT | 计划代理 |
| `builtin/BuiltInAgents.java` | `src/tools/AgentTool/builtInAgents.ts` - getBuiltInAgents() | 内置代理注册 |
| `execution/AgentTool.java` | `src/tools/AgentTool/AgentTool.tsx` - AgentTool | 主入口 Tool |
| `execution/runAgent.java` | `src/tools/AgentTool/runAgent.ts` - runAgent() | 代理执行循环 |

## 已知限制

1. **工具执行未完全实现** - ForkAgentExecutor 的工具调用需要完整集成
2. **消息格式简化** - 使用 `Map<String, Object>` 而非专门的 Message 类
3. **无 MCP 服务器支持** - 尚未实现代理级别的 MCP 服务器
4. **ForkContext vs ForkedAgentParams** - Java 实现使用简化的 ForkContext，而 TypeScript 源码有更完整的 ForkedAgentParams

## 验证清单

- [x] 所有文件编译通过
- [x] ForkAgentDefinition RULES 与 TypeScript 源码一致（10 条规则）
- [x] buildForkedMessages() 正确构建消息格式
- [x] buildChildMessage() 包含正确的 FORK_BOILERPLATE_TAG
- [x] SubagentContext 提供正确的上下文隔离
- [x] CacheSafeParams 正确传递 cache-safe 参数

## 文件结构

```
src/main/java/agent/subagent/
├── fork/
│   ├── ForkContext.java
│   ├── ForkContextBuilder.java
│   ├── CacheSafeParams.java
│   ├── ForkAgentDefinition.java
│   ├── ForkSubagentTool.java
│   └── ForkAgentExecutor.java
├── context/
│   └── SubagentContext.java
└── definition/
    ├── AgentDefinition.java
    └── PermissionMode.java

src/main/java/agent/tool/
└── ToolUseContext.java
```
