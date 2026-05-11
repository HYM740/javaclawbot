# CLAUDE.md
## 编译命令
```shell
"C:\Program Files\Java\jdk-17\bin\java.exe" -Dmaven.multiModuleProjectDirectory=D:\code\ai_project\javaclawbot -Djansi.passthrough=true -Dmaven.home=D:\IDEA20240307\plugins\maven\lib\maven3 -Dclassworlds.conf=D:\IDEA20240307\plugins\maven\lib\maven3\bin\m2.conf -Dmaven.ext.class.path=D:\IDEA20240307\plugins\maven\lib\maven-event-listener.jar -javaagent:D:\IDEA20240307\lib\idea_rt.jar=53924 -Dfile.encoding=UTF-8 -classpath D:\IDEA20240307\plugins\maven\lib\maven3\boot\plexus-classworlds-2.8.0.jar;D:\IDEA20240307\plugins\maven\lib\maven3\boot\plexus-classworlds.license org.codehaus.classworlds.Launcher -Didea.version=2024.3.7 -Dmaven.repo.local=D:\apps\maven\repository compile

```

1. 编码前先思考

不要假设。不要掩盖困惑。把权衡讲清楚。

在开始实现之前：

明确说明你的假设。如果不确定，就提问。
如果存在多种理解方式，把它们列出来——不要默默选择一个。
如果有更简单的方法，要指出来。在必要时提出异议。
如果有不清楚的地方，先停下来。指出困惑点并提问。
2. 简单优先

用最少的代码解决问题。不做任何臆测性的扩展。

不添加需求之外的功能。
不为一次性代码做抽象。
不添加未被要求的“灵活性”或“可配置性”。
不为不可能发生的情况编写错误处理。
如果你写了 200 行但其实可以用 50 行解决，就重写。

问自己：“资深工程师会觉得这太复杂吗？”如果答案是会，那就简化。

3. 手术式修改

只改必须改的部分。只清理你自己引入的问题。

在修改已有代码时：

不要“顺便优化”相邻的代码、注释或格式。
不要重构没有问题的部分。
保持现有风格一致，即使你有不同偏好。
如果发现无关的死代码，可以指出——但不要删除。

当你的修改引入“孤立项”时：

删除因你的修改而变得未使用的导入/变量/函数。
不要删除原本就存在的死代码，除非被要求。

检验标准：每一行修改都应能直接对应用户需求。

4. 以目标驱动执行

定义成功标准。循环迭代直到验证通过。

将任务转化为可验证的目标：

“添加校验” → “为非法输入编写测试，然后让测试通过”
“修复 bug” → “写一个能复现问题的测试，然后让它通过”
“重构 X” → “确保修改前后测试都通过”

对于多步骤任务，给出简要计划：

1. [步骤] → 验证：[检查点]
2. [步骤] → 验证：[检查点]
3. [步骤] → 验证：[检查点]

清晰的成功标准能让你独立迭代。模糊的标准（例如“让它能用”）则需要不断确认。
5. 编码后必须添加数据流日志，日志通常采用sfl4j 和 logback 切勿使用其他日志 格式为：
```java
if(log.enableDebug) {
    log.debug()
        }
log.info  log.warn log.error
```

## 可参考的经验
[经验.md](%E7%BB%8F%E9%AA%8C.md)

## 执行顺序（复杂任务）

在进行大型多步骤工作之前，应遵循 **GUARDRAILS.md** 中的那些规则、当前的**范围**、以及计划运行的验证命令。如需暂停，请在聊天或**本地**草稿文件中总结进展（不要将 `HANDOFF.md` 添加到仓库中），然后使用 `/clear` 并基于该总结继续工作。


This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **javaclawbot** (15835 symbols, 39920 relationships, 300 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> If any GitNexus tool warns the index is stale, run `npx gitnexus analyze` in terminal first.

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `gitnexus_impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `gitnexus_detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `gitnexus_query({query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `gitnexus_context({name: "symbolName"})`.

## Never Do

- NEVER edit a function, class, or method without first running `gitnexus_impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `gitnexus_rename` which understands the call graph.
- NEVER commit changes without running `gitnexus_detect_changes()` to check affected scope.

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/javaclawbot/context` | Codebase overview, check index freshness |
| `gitnexus://repo/javaclawbot/clusters` | All functional areas |
| `gitnexus://repo/javaclawbot/processes` | All execution flows |
| `gitnexus://repo/javaclawbot/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
|------|---------------------|
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->

## 项目核心类
AgentLoop为助手系统loop入口
ContextBuilder - 上下文构建
ProjectRegistry - 项目路径

## 核心包
src/main/java/agent/subagent - 子代理相关
src/main/java/agent/tool - 工具相关
src/main/java/context - 上下文相关
src/main/java/gui/ui - ui客户端
src/main/java/providers - 提供者
src/main/java/skills - 技能
src/main/java/utils - 通用工具

## 变动
版本变动，修复bug 请放入 [CHANGELOG.md](CHANGELOG.md) 中 ,如果该文档中版本新增了，pom.xml中也同步更新，每次版本变动需要同步在git中打上tag和branch
## git
由于在中国，github访问不稳定，需要 git -c http.proxy=http://127.0.0.1:7890  你需要检测是否开启代理，如果未开启代理 尝试一次原始提交 失败后提醒用户开启代理 
## 更新日志
详情需要放入 [CHANGELOG.md](CHANGELOG.md) 中
这里需要动态总结，并每次更新，记住 版本每次变动，pom同步变动，规则：
| Date | Version | Change |
|------|---------|--------|
| 2026-04-23 | 1.7.0 | xxxxx. |
