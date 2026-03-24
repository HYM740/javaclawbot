# Router Phase

本阶段只负责把请求识别为一个主场景，不直接生成代码。

## Routing targets

- continue-plan
- partial-change
- ui-image
- existing-table
- no-table

## Priority order

1. continue-plan
2. partial-change
3. ui-image
4. existing-table
5. no-table

## Input signals

### continue-plan
命中信号：
- “继续上次”
- “接着做”
- “按之前计划继续”
- 明确引用当前 active plan

### partial-change
命中信号：
- “补一个接口 / mapper / VO / SQL / 页面”
- “加一个字段 / 按钮 / 搜索项”
- “只改前端 / 只改后端某一层”

### ui-image
命中信号：
- 截图、原型图、设计稿、页面复刻
- 重点在界面结构与交互拆解

### existing-table
命中信号：
- 已给出表名
- 已明确库表存在
- 需要“按表生成 / 补全代码”

### no-table
命中信号：
- 明确“还没有表”
- 需要先设计 DDL / 表结构

## Conflict rules

- 用户说“继续”，但 active 不存在、已完成或不属于同一任务链，不能强行归入 continue-plan
- “已有表 + 只改一点” 时，主场景用 `partial-change`，副标签可挂 `existing-table`
- “图片页面 + 还想带 API/后端骨架” 时，主场景仍用 `ui-image`
- 分类模糊时做最小范围判断，不要并列多个主场景

## Output

- `route_label`
- `secondary_tags`
- `reason`
- `whether_need_plan_judgement`
