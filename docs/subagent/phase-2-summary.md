# Phase 2: 专用代理 完成总结

> **日期**: 2026-04-22
> **状态**: ✅ 已完成

## 交付物

### 代理定义 (`src/main/java/agent/subagent/definition/`)

| Java 类 | Open-ClaudeCode 源码 | 说明 |
|---------|---------------------|------|
| `AgentDefinitionLoader.java` | `src/tools/AgentTool/loadAgentsDir.ts` - getAgentDefinitionsWithOverrides() | 代理定义加载器 |
| `AgentDefinitionRegistry.java` | `src/tools/AgentTool/loadAgentsDir.ts` - agentRegistry | 代理定义注册表 |

### 内置代理 (`src/main/java/agent/subagent/builtin/`)

| Java 类 | Open-ClaudeCode 源码 | 说明 |
|---------|---------------------|------|
| `BuiltInAgents.java` | `src/tools/AgentTool/builtInAgents.ts` - getBuiltInAgents() | 内置代理注册表 |
| `GeneralPurposeAgent.java` | `src/tools/AgentTool/built-in/generalPurposeAgent.ts` - GENERAL_PURPOSE_AGENT | 通用代理 |
| `ExploreAgent.java` | `src/tools/AgentTool/built-in/exploreAgent.ts` - EXPLORE_AGENT | 探索代理（只读） |
| `PlanAgent.java` | `src/tools/AgentTool/built-in/planAgent.ts` - PLAN_AGENT | 计划代理（只读） |

### 执行 (`src/main/java/agent/subagent/execution/`)

| Java 类 | Open-ClaudeCode 源码 | 说明 |
|---------|---------------------|------|
| `AgentTool.java` | `src/tools/AgentTool/AgentTool.tsx` - AgentTool | Agent 工具主入口 |
| `runAgent.java` | `src/tools/AgentTool/runAgent.ts` - runAgent() | 代理执行循环 |
| `AgentToolResult.java` | `src/tools/AgentTool/AgentTool.tsx` - AgentToolResult | 执行结果 |
| `AgentToolUtils.java` | `src/tools/AgentTool/agentToolUtils.ts` | 工具函数 |
| `resumeAgent.java` | `src/tools/AgentTool/resumeAgent.ts` | 代理恢复 |

## 关键设计决策

### 1. 代理定义结构

**AgentDefinition** 包含：
- `agentType` - 代理类型标识
- `whenToUse` - 描述何时使用
- `tools` - 可用工具列表（`*` 表示全部）
- `disallowedTools` - 禁用工具列表
- `model` - 模型选择（null/inherit/特定模型）
- `permissionMode` - 权限模式
- `maxTurns` - 最大轮次
- `source` - 来源（built-in/user/plugin）
- `getSystemPrompt` - 系统提示词构建器

### 2. 内置代理

**GeneralPurposeAgent**：
- 通用任务处理
- 支持全部工具
- 权限模式：ACCEPT_EDITS
- 最大轮次：200

**ExploreAgent**：
- 只读文件搜索和代码分析
- 禁用写操作工具（Edit, Write, NotebookEdit）
- 使用 haiku 模型（快速）
- 权限模式：PLAN
- 最大轮次：50

**PlanAgent**：
- 只读规划代理
- 与 Explore 相同的工具限制
- 继承父代理模型
- 权限模式：PLAN
- 最大轮次：50

### 3. AgentTool 路由逻辑

```
AgentTool.execute()
    │
    ├── team_name + name → spawnTeammate (TODO)
    │
    ├── subagent_type → runNamedAgent()
    │       │
    │       └── 加载代理定义 → runAgent()
    │
    └── 无 subagent_type → runForkAgent() (TODO)
```

### 4. 工具过滤

**AgentToolUtils.filterTools()**：
- 如果 `tools = ["*"]`，返回全部工具
- 否则，只保留 `tools` 列表中的工具
- 移除 `disallowedTools` 中的工具

## 核心类图

```
AgentDefinition
    ↑
    │
BuiltInAgents ───► GeneralPurposeAgent
                    ExploreAgent
                    PlanAgent

AgentTool ───────► AgentDefinitionLoader ───► BuiltInAgents
      │                 │
      │                 └──► loadAgentsFromDir()
      │
      └──► runAgent()
              │
              ├──► AgentToolUtils.filterTools()
              ├──► SubagentContext
              └──► LLM Query Loop (TODO)
```

## Git 提交历史

```
06a35e6 feat(subagent): add Phase 2 dedicated agents implementation
b3317cb fix(subagent): complete RULES in buildChildMessage to match TypeScript source
dead096 docs: update Phase 1 summary with verification checklist
...
```

## 如何继续

### Phase 3（团队协作）依赖此阶段的内容：

1. `AgentDefinitionLoader` - 需要加载团队代理定义
2. `AgentTool` - 需要集成 TeamCoordinator
3. `runAgent` - 需要完整实现 LLM 查询循环

### Phase 3 主要任务

| Java 类 | Open-ClaudeCode 源码 | 说明 |
|---------|---------------------|------|
| `TeamCoordinator` | `src/tools/shared/spawnMultiAgent.ts` | 团队协调器 |
| `TeammateSpawner` | `src/tools/shared/spawnMultiAgent.ts` - spawnTeammate() | Teammate 生成器 |
| `MultiAgentOutputTracker` | `src/tools/shared/multiAgentOutputTracker.ts` | 多代理输出追踪 |

## 已知限制

1. **AgentTool 路由尚未完全实现**：
   - `spawnTeammate` 返回 "not yet implemented"
   - `runForkAgent` 返回 "not yet implemented"

2. **runAgent 查询循环尚未实现**：
   - 只返回 "Query loop not yet implemented"
   - 需要集成 LLM 调用
   - 需要处理工具调用

3. **MCP 服务器支持尚未实现**

## 验证清单

- [x] 所有文件编译通过
- [x] AgentDefinitionLoader 正确加载内置代理
- [x] BuiltInAgents 返回 3 个内置代理
- [x] GeneralPurposeAgent 配置正确
- [x] ExploreAgent 配置正确（只读）
- [x] PlanAgent 配置正确（只读）
- [x] AgentTool 路由逻辑正确
- [x] AgentToolUtils 工具过滤正确

## 文件结构

```
src/main/java/agent/subagent/
├── definition/
│   ├── AgentDefinition.java
│   ├── AgentDefinitionLoader.java
│   ├── AgentDefinitionRegistry.java
│   └── PermissionMode.java
├── builtin/
│   ├── BuiltInAgents.java
│   ├── GeneralPurposeAgent.java
│   ├── ExploreAgent.java
│   └── PlanAgent.java
├── execution/
│   ├── AgentTool.java
│   ├── AgentToolResult.java
│   ├── AgentToolUtils.java
│   ├── runAgent.java
│   └── resumeAgent.java
├── fork/ (Phase 1)
├── context/ (Phase 1)
└── framework/ (Phase 0)
```
