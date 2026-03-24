# Route Rules

本文件补充 route 相关生成规则。只有当当前 scope 包含 `route` 时才读取。

## Core principle

先看项目现有路由写法，再决定是否生成或修改路由配置。

不要默认所有项目都使用同一种路由结构。

## Must inspect first

生成路由前至少确认：

1. 路由文件位置
2. 路由模块拆分方式
3. path 命名规则
4. name 命名规则
5. component 引入方式
6. meta 字段结构
7. 是否存在 keepAlive / hidden / alwaysShow / redirect
8. 是否存在权限、菜单、缓存、图标等扩展字段

## Common cases

### 1. Frontend route only
适用于：
- 项目菜单不落库
- 仅需补前端页面访问路径
- 页面挂在现有路由树下

### 2. Menu SQL + route together
适用于：
- 菜单表中同时依赖路由 path / component
- 前后端菜单与路由要保持一致

### 3. Partial route change
适用于：
- 新增一个页面路由
- 补一个子路由
- 调整 meta/title/icon/cache
- 调整 redirect 或组件路径

## Rules

- 用户只要 route，不要强行生成 menu SQL
- path、name、component 必须贴合项目现有命名规律
- meta 字段优先沿用项目现状，不自创结构
- 若项目采用懒加载、自动路由、文件系统路由，优先沿用
- 若 route 与菜单权限强耦合，需同时提醒用户核对菜单配置

## What to avoid

不要这样做：

- 不看项目现有路由文件就直接造结构
- 把 menu SQL 规则硬套到纯前端路由项目
- 用户只改一个子路由，你却重做整套路由树
- 自创新的 meta 字段格式
- component 路径与真实页面路径不一致

## Output expectation

生成或修改 route 时，回复中应说明：

1. 修改了哪些路由文件
2. 属于新增 / 调整 / 补充哪一类
3. path / name / component / meta 如何确定
4. 是否还需要配合菜单或权限一起调整
