# State Bootstrap Phase

本阶段先读取状态，不直接做代码生成。

## Canonical state dir

统一状态目录为：

`项目根目录/.agents/codememory/`

## Bootstrap rule

如果 `项目根目录/.agents/codememory/` 不存在：
1. 创建目录
2. 将 `技能目录/codememory/*` 复制进去
3. 只补缺失文件，不覆盖项目中已存在的状态文件

如果目录已存在：
- 不重复复制
- 不用 skill 侧种子文件覆盖项目侧状态

## Must read first

- `项目根目录/.agents/codememory/codegen-plan-active.md`
- `项目根目录/.agents/codememory/codegen-memory.md`

## Duties

1. 判断 active plan 是否存在
2. 判断 active 是否真实有效，还是占位/过期/已完成内容
3. 从 memory 中提取可复用信息：
   - 作者名
   - 当前项目路径
   - 当前项目名
   - 数据库连接摘要
   - 最近生成模块
   - 用户固定偏好
   - 常见问题经验

## Active validity check

active 只有同时满足以下条件才视为有效：
- 目标明确
- 仍有未完成步骤
- 与当前请求属于同一任务链
- 没有被新任务明确替代

否则：
- 视为无效 active
- 交给 finalizer 决定是否归档 history

## Output

- `has_real_active_plan`
- `active_plan_summary`
- `memory_summary`
- `reusable_context`
- `bootstrap_actions`
