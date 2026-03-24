# Execution Orchestrator Phase

本阶段负责调度具体执行，不负责计划管理。

## Common rules

- 先按主场景决定执行顺序
- 再按 scope 裁剪输出
- 模板只能生成骨架，必须继续业务化对齐
- 不要为了“完整”而越权扩展用户未要求的范围

## Execution branches by scope

### Backend
读取：
- `reference/backend-rules.md`
- `reference/template-selection.md`（需要模板时）

### Frontend
读取：
- `reference/frontend-rules.md`
- `reference/template-selection.md`（需要模板时）

### Testing
读取：
- `reference/testing-rules.md`

### Docs
读取：
- `reference/doc-rules.md`

### Menu SQL
读取：
- `reference/menu-sql-rules.md`

### Route
读取：
- `reference/route-rules.md`

## Scenario-specific execution

### Existing Table
先调用：
- `scripts/mysql-tool.js`

再做：
1. 查询真实表结构
2. 判断单表 / 主子表 / 树表
3. 选择模板或手工实现方式
4. 再补业务化逻辑

### No Table
先产出：
- 业务对象与关系
- DDL / 字段方案
- 索引 / 约束建议

用户确认后，再进入代码生成。

### UI Image
先做：
1. 页面结构拆解
2. 字段与动作推断
3. 页面类型识别
4. 再生成前端页面骨架

如需 API/后端，只能生成占位骨架，不得伪造真实接口或真实表结构。

### Partial Change
优先增量修改现有文件：
- 能补则补
- 能复用则复用
- 不重做整套模块

## Output

- `execution_steps`
- `major_files_to_create_or_modify`
- `dependencies_or_blockers`
