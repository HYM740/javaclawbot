---
name: zjky-codegen
description: "Route and execute ZJKY-style code generation tasks for company framework projects. This skill is a workflow-first orchestrator, not a one-shot generator. Always treat ZjkyCode.md as the project-level guidance source, bootstrap codememory under the project root, classify the request into one primary scenario, load only the needed rules, execute in phases, and finally update plan/history/memory/cron idempotently."
---

# zjky-codegen

建科院代码生成总技能。  
这是一个**总 skill + 阶段化路由 + 状态驱动收尾**的工作流技能，不是一次性模板填空器。

## 1. Core objective

本 skill 的目标只有六件事：

1. 把 `ZjkyCode.md` 作为项目级总纲文档持续维护
2. 把 `项目根目录/.agents/codememory/` 作为唯一状态目录
3. 从请求中稳定识别一个**主场景**
4. 只按需加载当前任务真正需要的规则文档
5. 分阶段执行生成或修改
6. 在结束时幂等更新 `active / history / memory / cron`

本 skill 不负责：

- 一上来把全部 reference 文档混进上下文
- 未识别场景就直接写代码
- 把模板输出当成最终业务实现
- 忽略 active/history/memory/cron 的写回

---

## 2. Canonical paths

统一以**项目根目录**为基准。

### 2.1 Project-level state
- `项目根目录/ZjkyCode.md`
- `项目根目录/.agents/codememory/codegen-plan-active.md`
- `项目根目录/.agents/codememory/codegen-plan-history.md`
- `项目根目录/.agents/codememory/codegen-memory.md`

### 2.2 Skill-side seed files
- `技能目录/codememory/*`

### 2.3 Phase docs
- `phases/00-router.md`
- `phases/01-state-bootstrap.md`
- `phases/02-context-loader.md`
- `phases/03-plan-manager.md`
- `phases/04-scope-confirmation.md`
- `phases/05-execution-orchestrator.md`
- `phases/06-finalizer.md`
- `phases/07-cron-notify.md`

### 2.4 Reference docs
- `reference/scenario-a-partial-change.md`
- `reference/scenario-b-no-table.md`
- `reference/scenario-c-existing-table.md`
- `reference/scenario-d-ui-image.md`
- `reference/backend-rules.md`
- `reference/frontend-rules.md`
- `reference/testing-rules.md`
- `reference/doc-rules.md`
- `reference/menu-sql-rules.md`
- `reference/template-selection.md`
- `reference/route-rules.md`

---

## 3. Five-state model

### 3.1 `ZjkyCode.md`
项目级总纲。  
记录仓库高层架构、关键命令、重要约束、关键目录关系。  
**不记录任务级进度。**

### 3.2 `codegen-plan-active.md`
当前活跃计划。  
只保留仍在推进、仍有效的任务。

### 3.3 `codegen-plan-history.md`
历史归档。  
记录完成、取消、废弃、被替代的正式计划。  
**不作为默认第一优先上下文。**

### 3.4 `codegen-memory.md`
跨任务记忆。  
只保留未来仍有价值的偏好、命名、路径、作者、数据库摘要、模块约定、常见问题经验。

### 3.5 `cron / reminder`
后续提醒。  
只在确有持续跟踪价值且用户接受时启用。  
**不是默认动作。**

---

## 4. ZjkyCode.md policy

### 4.1 什么时候创建
满足任一条件时创建：
- 项目根目录不存在 `ZjkyCode.md`
- 明显是首次在该仓库开展系统化代码生成
- 后续还会多轮继续推进

### 4.2 什么时候读取
若已存在，默认**先读再用**。

### 4.3 什么时候增量改进
仅在发现以下高价值缺口时增量改进：
- 高频命令缺失
- 关键架构关系缺失
- README / `.javaclawbot/rules/` / `.cursorrules` / `.github/copilot-instructions.md` 中有重要项目级规则尚未吸收
- 当前文档已明显过时

### 4.4 什么时候不要动
- 只是小范围局部修改
- 没有新项目级发现
- 只是本轮任务进度变化

### 4.5 内容约束
必须：
- 聚焦项目级信息
- 包含构建、运行、测试、单测、必要脚本
- 强调高层架构与关键约束

禁止：
- 写任务级 checklist
- 罗列显而易见的目录
- 虚构仓库中不存在的信息

文件开头固定为：

```md
# ZjkyCode.md

该文件为使用 LLM 在本仓库中的代码工作提供了指导。
```

---

## 5. Primary scenarios

任何请求都必须先归入一个**主场景**：

1. `continue-plan`
2. `partial-change`
3. `ui-image`
4. `existing-table`
5. `no-table`

允许添加**副标签**描述依赖或输出范围，例如：
- `partial-change + existing-table`
- `ui-image + backend-skeleton`

但主场景只能有一个。

---

## 6. Workflow

每次进入本 skill，按以下顺序执行：

### Phase 0. project guidance check
检查 `ZjkyCode.md` 是否存在，并决定：
- 首次生成
- 读取使用
- 增量改进
- 暂不处理

### Phase 1. router
读取：`phases/00-router.md`

### Phase 2. state-bootstrap
读取：`phases/01-state-bootstrap.md`

### Phase 3. context-loader
读取：`phases/02-context-loader.md`

### Phase 4. plan-manager
读取：`phases/03-plan-manager.md`

### Phase 5. scope-confirmation
读取：`phases/04-scope-confirmation.md`

### Phase 6. execution-orchestrator
读取：`phases/05-execution-orchestrator.md`

### Phase 7. finalizer
读取：`phases/06-finalizer.md`

### Phase 8. cron-notify
读取：`phases/07-cron-notify.md`

---

## 7. Hard constraints

始终遵守：

- 禁止使用 `@Schema` 及同类 swagger 接口注解
- 对象转换优先 MapStruct
- 作者名首次缺失时先询问用户
- 默认包名前缀：`com.zjky.pro.app.{模块名}`
- 代码风格优先贴合项目现有规范
- 菜单 SQL 必须先看真实表结构
- 基于表生成代码必须先查表
- 无表场景必须先定 DDL
- UI 图片任务不得伪造真实后端接口和表结构
- 模板只用于骨架，不能视为最终业务实现

---

## 8. Read policy

默认只读取：
- `ZjkyCode.md`（若存在且与当前请求相关）
- `codegen-plan-active.md`
- `codegen-memory.md`

之后再按主场景与 scope 增量读取：
- 场景文档
- 生成规则文档
- 模板选择文档
- 项目指导文档
- 数据库脚本或仓库资料

原则：
- 能少读就少读
- 先读最关键的
- 信息不足时再补读

---

## 9. Plan policy

以下情况需要正式计划：
- 完整模块生成
- 多文件输出
- 多轮推进
- 用户明确要求按步骤
- 涉及持续跟踪
- 无表先 DDL 再代码
- 基于现有表生成完整模块
- UI 图同时包含页面 + API + 后端骨架

以下情况通常不建正式计划：
- 局部修改
- 改动小
- 不需要持续跟踪
- 不影响 active/history/memory

active 只有在“目标明确 + 仍未完成 + 属于同一任务链 + 未被新任务替代”时才视为有效。

---

## 10. Response contract

### 信息不足时
1. 当前识别场景
2. 已命中的状态
3. 缺失的最小关键信息
4. 下一步动作

### 开始执行时
1. 当前识别场景
2. 当前生成范围
3. 将读取哪些关键规则
4. 将创建或修改的主要文件
5. 是否创建或更新计划

### 完成时
1. 已完成内容
2. 生成或修改的文件清单
3. 约束检查结果
4. `ZjkyCode.md / memory / active / history / cron` 更新情况
5. 剩余风险或待确认项

---

## 11. One-line principle

**先检查项目总纲，再判断当前计划，再识别一个主场景，再按需读取规则并分阶段执行，最后做幂等写回。**
