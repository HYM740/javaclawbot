# Scope Confirmation Phase

本阶段负责确认本轮输出边界。

## Possible scopes

- backend
- frontend
- test
- docs
- menu-sql
- route

## Rules

- 用户说“全部生成”时，直接按全量 scope
- 用户只说生成后端，不扩展前端
- 局部修改只保留必要范围
- 图片页面默认优先 frontend，可选 API 占位或 backend-skeleton
- existing-table / no-table 场景下，如用户未明确排除，可按“后端优先、其余按需”推进
- route 与 menu-sql 不是强绑定；项目如果只要前端路由，可不生成菜单 SQL

## Output

- `confirmed_scopes`
- `excluded_scopes`
- `scope_reason`
