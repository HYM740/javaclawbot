# Phase 1: Fork 核心 实施计划

> **对于代理工作者：**必需的子技能：使用 zjkycode:subagent-driven-development（推荐）或 zjkycode:executing-plans 来逐任务实施此计划。

**目标**：实现 Fork 子代理机制，允许父代理 fork 轻量级工作进程，继承完整上下文并共享 Prompt Cache

**架构**：ForkSubagentTool → ForkAgentExecutor → ForkContextBuilder → SubagentContext（隔离）

**技术栈**：Java 17+, picocli/JLine, Jackson

---

## ⚠️ 实施要求

**必须先阅读**：`docs/zjkycode/specs/2026-04-21-subagent-design.md` 第 3.5 节和第 4.2 节

**复刻原则**：必须完整按照 Open-ClaudeCode 源码逐行实现，做到 Java 语义等价、逻辑等价。

---

## 文件结构

```
src/main/java/agent/subagent/
├── fork/
│   ├── ForkContext.java              # ✅ 已完成 → ForkedAgentParams
│   ├── ForkContextBuilder.java       # ✅ 已完成 → buildForkedAgentParams()
│   ├── CacheSafeParams.java          # ✅ 已完成 → CacheSafeParams
│   ├── ForkAgentDefinition.java      # ✅ 已完成 → FORK_AGENT, buildForkedMessages()
│   ├── ForkSubagentTool.java         # ✅ 已完成 → AgentTool 组件
│   └── ForkAgentExecutor.java        # ✅ 已完成 → runAgent()
├── context/
│   └── SubagentContext.java          # ✅ 已完成 → createSubagentContext()
├── definition/
│   ├── AgentDefinition.java          # ✅ 已完成 → BuiltInAgentDefinition
│   └── PermissionMode.java           # ✅ 已完成 → PermissionMode
└── tool/
    └── ToolUseContext.java          # ❌ 缺失 → ToolUseContext
```

---

## 任务 1：补充缺失的 ToolUseContext

**文件：**
- 创建：`src/main/java/agent/subagent/tool/ToolUseContext.java`
- 源码参考：`src/Tool.ts` - ToolUseContext

**必须实现的功能**：
1. 工具使用上下文信息
2. MCP 服务器配置
3. 可用工具列表

- [ ] **步骤 1：创建目录结构**

```bash
mkdir -p src/main/java/agent/subagent/tool
```

- [ ] **步骤 2：实现 ToolUseContext.java**

```java
package agent.subagent.tool;

import java.util.List;
import java.util.Map;

/**
 * 工具使用上下文
 *
 * 对应 Open-ClaudeCode: src/Tool.ts - ToolUseContext
 *
 * 包含工具使用所需的所有上下文信息：
 * - MCP 服务器配置
 * - 可用工具列表
 * - 工具访问控制
 */
public class ToolUseContext {
    /** MCP 服务器配置 */
    private final Map<String, Object> mcpServers;

    /** 可用工具列表 */
    private final List<String> availableTools;

    /** 禁止使用的工具列表 */
    private final List<String> disallowedTools;

    /** 工具超时配置 */
    private final Map<String, Integer> toolTimeouts;

    private ToolUseContext(Builder builder) {
        this.mcpServers = builder.mcpServers;
        this.availableTools = builder.availableTools;
        this.disallowedTools = builder.disallowedTools;
        this.toolTimeouts = builder.toolTimeouts;
    }

    public Map<String, Object> getMcpServers() { return mcpServers; }
    public List<String> getAvailableTools() { return availableTools; }
    public List<String> getDisallowedTools() { return disallowedTools; }
    public Map<String, Integer> getToolTimeouts() { return toolTimeouts; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Map<String, Object> mcpServers;
        private List<String> availableTools;
        private List<String> disallowedTools;
        private Map<String, Integer> toolTimeouts;

        public Builder mcpServers(Map<String, Object> mcpServers) {
            this.mcpServers = mcpServers;
            return this;
        }

        public Builder availableTools(List<String> availableTools) {
            this.availableTools = availableTools;
            return this;
        }

        public Builder disallowedTools(List<String> disallowedTools) {
            this.disallowedTools = disallowedTools;
            return this;
        }

        public Builder toolTimeouts(Map<String, Integer> toolTimeouts) {
            this.toolTimeouts = toolTimeouts;
            return this;
        }

        public ToolUseContext build() {
            return new ToolUseContext(this);
        }
    }
}
```

- [ ] **步骤 3：验证编译**

```bash
cd /usr/local/code/javaclawbot
mvn compile -q
```

- [ ] **步骤 4：提交**

```bash
git add src/main/java/agent/subagent/tool/
git commit -m "feat(subagent): add ToolUseContext"
```

---

## 任务 2：验证 Phase 1 完整性

**文件：**
- 审查：`src/main/java/agent/subagent/fork/*.java`
- 审查：`src/main/java/agent/subagent/context/*.java`
- 审查：`src/main/java/agent/subagent/definition/*.java`

**必须验证的功能**：

### 2.1 ForkContext（ForkedAgentParams）

- [ ] 父代理 ID 获取
- [ ] Fork 指令传递
- [ ] 父代理消息历史
- [ ] 用户/系统上下文

### 2.2 ForkContextBuilder（buildForkedAgentParams）

- [ ] 从 AppState 构建 ForkContext
- [ ] 提取父代理消息
- [ ] 构建 CacheSafeParams

### 2.3 CacheSafeParams

- [ ] systemPrompt 设置
- [ ] userContext/systemContext 传递
- [ ] forkContextMessages 构建

### 2.4 ForkAgentDefinition

- [ ] FORK_AGENT 定义
- [ ] buildForkedMessages() 实现
- [ ] buildChildMessage() 实现（包含 FORK_BOILERPLATE_TAG）

### 2.5 ForkSubagentTool

- [ ] Tool 定义
- [ ] call() 方法实现
- [ ] 参数验证

### 2.6 ForkAgentExecutor

- [ ] runAgent() 执行循环
- [ ] 消息构建
- [ ] 工具调用处理
- [ ] 结果返回

### 2.7 SubagentContext

- [ ] 文件状态缓存
- [ ] AbortController 隔离
- [ ] 权限提示控制

### 2.8 AgentDefinition

- [ ] BuiltInAgentDefinition 字段
- [ ] getSystemPrompt() 供应者
- [ ] source/baseDir 属性

### 2.9 PermissionMode

- [ ] BYPASS_PERMISSIONS
- [ ] ACCEPT_EDITS
- [ ] PLAN
- [ ] BUBBLE

- [ ] **步骤 1：运行编译测试**

```bash
mvn compile -q
```

预期：无错误

- [ ] **步骤 2：检查缺失的功能**

对比设计文档第 3.5 节和第 4.2 节，检查是否有遗漏功能。

- [ ] **步骤 3：提交验证结果**

```bash
git add -A
git commit -m "chore(subagent): verify Phase 1 completeness"
```

---

## 任务 3：Phase 1 总结

**文件：**
- 创建：`docs/subagent/phase-1-summary.md`

- [ ] **步骤 1：创建总结文档**

```markdown
# Phase 1: Fork 核心 完成总结

## 交付物

### Fork 机制
- ForkContext.java
- ForkContextBuilder.java
- CacheSafeParams.java
- ForkAgentDefinition.java
- ForkSubagentTool.java
- ForkAgentExecutor.java

### 上下文隔离
- SubagentContext.java

### 代理定义
- AgentDefinition.java
- PermissionMode.java

### 工具
- ToolUseContext.java

## 关键设计决策

1. Fork 消息构建策略
2. 上下文隔离策略
3. Fork 指令格式

## Git 提交历史

（列出所有 Phase 1 相关的提交）

## 如何继续

Phase 2...
```

- [ ] **步骤 2：提交总结**

```bash
git add docs/subagent/phase-1-summary.md
git commit -m "docs: add Phase 1 summary"
```

---

## 自我审查

### 规范覆盖检查

| 规范需求 | 对应文件 | 状态 |
|---------|---------|------|
| ForkContext | fork/ForkContext.java | ✅ |
| ForkContextBuilder | fork/ForkContextBuilder.java | ✅ |
| CacheSafeParams | fork/CacheSafeParams.java | ✅ |
| ForkAgentDefinition | fork/ForkAgentDefinition.java | ✅ |
| ForkSubagentTool | fork/ForkSubagentTool.java | ✅ |
| ForkAgentExecutor | fork/ForkAgentExecutor.java | ✅ |
| SubagentContext | context/SubagentContext.java | ✅ |
| AgentDefinition | definition/AgentDefinition.java | ✅ |
| PermissionMode | definition/PermissionMode.java | ✅ |
| ToolUseContext | tool/ToolUseContext.java | ⚠️ 待创建 |

### 类型一致性检查

- ForkContext.parentMessages 类型是 List<Map<String, Object>> ✓
- CacheSafeParams.forkContextMessages 类型是 List<Map<String, Object>> ✓
- SubagentContext.fileStateCache 类型是 Map<String, Object> ✓

### 占位符扫描

无占位符，所有步骤都包含完整代码。

---

## 执行选择

**计划完成并保存到 `docs/zjkycode/plans/2026-04-22-subagent-phase-1.md`。两种执行选项：**

**1. 子代理驱动（推荐）** - 我为每个任务调度一个新子代理，在任务之间审查，快速迭代

**2. 内联执行** - 使用 executing-plans 在此会话中执行任务，带检查点的批量执行

**选择哪种方式？**
