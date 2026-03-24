# Cron Notify Phase

本阶段负责判断是否触发提醒，不负责生成代码。

## Trigger when

满足全部前提后，才考虑触发提醒：

- 存在正式计划
- 本轮完成了一个阶段
- 后续仍有明确下一步
- 用户明确接受此类工作流提醒

## Do not trigger when

- 只是一次性很小的局部改动
- 不存在计划
- 本轮没有持续跟踪需求
- 用户没有接受提醒机制
- 当前只是纯说明或问答

## Output

- `cron_needed`
- `trigger_reason`
- `next_reminder_subject`
