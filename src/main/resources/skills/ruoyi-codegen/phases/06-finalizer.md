# Finalizer Phase

本阶段统一做收尾。

## Must check

- 是否优先使用了 MapStruct
- 是否避免了 `@Schema`
- 是否贴合项目现有风格
- 是否遵守了主场景边界
- 是否列出文件清单
- 是否标明待确认项
- 是否需要更新 active plan
- 是否需要写入 history
- 是否需要更新 memory
- 是否需要增量改进 `ZjkyCode.md`

## Idempotent write rules

状态写回必须尽量幂等：

- 同一轮没有实质进展时，不要重复刷写 memory/history
- 计划未完成时，不要错误归档到 history
- 只是普通问答或说明，不要创建 active plan
- 只是小改动且无长期价值，不要污染 memory
- 任务级进度不要写入 `ZjkyCode.md`
- 旧 active 被新任务替代时，再归档 history；不要提前归档

## Memory updates

只更新真正有长期价值的信息，例如：

- 作者名
- 当前项目路径
- 当前项目名
- 数据库连接摘要
- 最近生成模块
- 用户偏好
- 常见问题与解决经验

不要把一次性上下文和临时判断写进 memory。

## History updates

以下情况才写 history：

- 正式计划已完成
- 正式计划已取消
- 正式计划已失效
- 正式计划被新计划替代

## ZjkyCode updates

只有出现新的项目级发现时才更新 `ZjkyCode.md`：
- 新命令
- 新架构关系
- 新仓库级约束
- 旧内容已明显过时

## Output

- `final_checks`
- `state_updates`
- `open_risks_or_confirmations`
