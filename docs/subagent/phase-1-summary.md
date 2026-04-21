# Phase 1: Fork 核心 完成总结

## 交付物

### Fork 机制 (`src/main/java/agent/subagent/fork/`)
- `ForkContext.java` - Fork 上下文
- `ForkContextBuilder.java` - Fork 上下文构建器
- `CacheSafeParams.java` - Cache 安全参数
- `ForkAgentDefinition.java` - Fork 代理定义和消息构建
- `ForkSubagentTool.java` - Fork 入口 Tool
- `ForkAgentExecutor.java` - Fork 执行器

### 上下文隔离 (`src/main/java/agent/subagent/context/`)
- `SubagentContext.java` - 子代理上下文隔离

### 代理定义 (`src/main/java/agent/subagent/definition/`)
- `AgentDefinition.java` - 代理定义基类
- `PermissionMode.java` - 权限模式枚举

### 工具 (`src/main/java/agent/tool/`)
- `ToolUseContext.java` - 工具使用上下文

## 关键设计决策

### 1. Fork 消息构建策略
Fork 子代理的消息格式经过优化以最大化 Prompt Cache 命中率：
- 保留父代理完整的 assistant 消息（包含所有 tool_use）
- 为每个 tool_use 生成相同的 placeholder 结果
- 只有最后一条 user 消息（包含指令）不同

### 2. 上下文隔离策略
- 文件状态缓存：克隆而非共享
- AbortController：创建子控制器，可选链接到父控制器
- 权限提示：默认禁止，避免干扰父代理

### 3. Fork 指令格式
包含明确的规则：
- "你是 fork 的工作进程，不是主代理"
- 禁止对话，直接执行
- 最终输出必须以 "Scope:" 开头
- 结构化报告格式

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

## 如何继续

Phase 2（专用代理）依赖此阶段的内容：

1. `AgentDefinition` 基类 - 所有代理定义都继承它
2. `PermissionMode` - 代理的权限模式
3. `ForkAgentExecutor` - 可以扩展支持专用代理

## 下一阶段

**Phase 2: 专用代理**

- `AgentDefinitionLoader` - 代理定义加载器
- `GeneralPurposeAgent` - 通用代理
- `ExploreAgent` - 探索代理（只读搜索）
- `PlanAgent` - 计划代理（只读规划）
- `AgentTool` - 主入口 Tool（统一 Fork 和专用代理）

## 已知限制

1. **工具执行未实现** - ForkAgentExecutor 的工具调用返回 placeholder
2. **消息格式简化** - 使用 Map<String, Object> 而非专门的 Message 类
3. **无 MCP 服务器支持** - 尚未实现代理级别的 MCP 服务器

## Git 提交历史

```
5dcb4fe feat(subagent): add Fork context, CacheSafeParams, and context isolation
c41a32b feat(subagent): add ForkSubagentTool and ForkAgentExecutor
```

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
├── definition/
│   ├── AgentDefinition.java
│   └── PermissionMode.java
└── tool/
    └── ToolUseContext.java
```
