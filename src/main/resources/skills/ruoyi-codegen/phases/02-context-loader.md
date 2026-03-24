# Context Loader Phase

本阶段负责按需加载文档，不做无差别全读。

## Default reads

所有任务默认先读：

- `项目根目录/ZjkyCode.md`（存在且与当前请求相关时）
- `项目根目录/.agents/codememory/codegen-plan-active.md`
- `项目根目录/.agents/codememory/codegen-memory.md`

## Scenario reads

按主场景读取：

- `partial-change` -> `reference/scenario-a-partial-change.md`
- `no-table` -> `reference/scenario-b-no-table.md`
- `existing-table` -> `reference/scenario-c-existing-table.md`
- `ui-image` -> `reference/scenario-d-ui-image.md`

`continue-plan` 本身不单独依赖场景文档；优先依赖 active plan、memory、当前任务上下文。

## Generator reads by scope

按输出范围读取：

- `backend` -> `reference/backend-rules.md`
- `frontend` -> `reference/frontend-rules.md`
- `test` -> `reference/testing-rules.md`
- `docs` -> `reference/doc-rules.md`
- `menu-sql` -> `reference/menu-sql-rules.md`
- `route` -> `reference/route-rules.md`
- 使用模板时 -> `reference/template-selection.md`

## Project docs

按需读取项目资料，例如：

- `asset/` 下指导文档
- `README.md`
- `.javaclawbot/rules/`
- `.cursorrules`
- `.github/copilot-instructions.md`

## Rules

- 不要一次性全读完全部 reference
- 只加载当前阶段真正需要的上下文
- 信息不足时再增量补读
- 命名统一使用 `reference/`，不要再混用 `scenarios/`、`generators/`
