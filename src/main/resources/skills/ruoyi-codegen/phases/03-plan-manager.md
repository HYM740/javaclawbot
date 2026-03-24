# Plan Manager Phase

本阶段负责计划生命周期管理。

## Need plan when

满足任一条件时，建立或更新 active plan：

- 完整模块生成
- 多文件输出
- 需要多轮推进
- 用户明确要求按步骤
- 涉及 history / memory / cron 的持续跟踪
- existing-table 全量生成
- no-table 先 DDL 再代码
- ui-image 同时包含页面 + API + 后端骨架

## Usually no formal plan when

以下情况通常不建正式计划：

- 小范围局部修改
- 只补一个方法/字段/按钮/SQL
- 不需要持续跟踪
- 用户明确要求直接给代码

## Duties

1. 新建计划
2. 更新 active plan
3. 任务完成后归档到 history
4. 任务取消或被替代时写入 cancelled / replaced history
5. 保持计划格式简洁、可执行、可继续

## Plan format

使用 checklist 格式：

```md
## 计划：{计划名}

- [ ] 1. ...
- [ ] 2. ...
- [ ] 3. ...
```

## Rules

- 计划只写任务级推进项，不写项目级常识
- 不要把很小的局部修改包装成复杂计划
- 新任务替代旧任务时，应标记旧 active 失效，而不是两个 active 并存
- 计划变化要能解释“为什么变更”

## Output

- `plan_needed`
- `active_plan_to_create_or_update`
- `current_step`
- `history_update_needed`
