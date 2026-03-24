# Template Selection

本文件用于指导当前任务如何选择 `templates/` 下的模板文件，以及哪些场景不能只依赖模板。

## Core Principle

模板主要用于生成标准 CRUD 骨架，不代表真实业务已经完整实现。

生成后应继续结合：

- 项目指导文档
- 真实表结构
- 业务需求
- 现有代码风格
- 现有模块实现方式

做必要调整。

## Template Mapping

## Backend

### Controller
- 模板：`templates/java/controller/controller.vm`
- 用于生成标准 REST Controller 骨架

### Request / Response VO
- 模板：
  - `templates/java/controller/vo/saveReqVO.vm`
  - `templates/java/controller/vo/pageReqVO.vm`
  - `templates/java/controller/vo/listReqVO.vm`
  - `templates/java/controller/vo/respVO.vm`

### Convert
- 模板：`templates/java/convert/convert.vm`

### DAO / Mapper
- 模板：
  - `templates/java/dao/do.vm`
  - `templates/java/dao/do_sub.vm`
  - `templates/java/dao/mapper.vm`
  - `templates/java/dao/mapper_sub.vm`
  - `templates/java/dao/mapper.xml.vm`

### Service
- 模板：
  - `templates/java/service/service.vm`
  - `templates/java/service/serviceImpl.vm`

### Error Code
- 模板：`templates/java/enums/errorcode.vm`

### Test
- 模板：`templates/java/test/serviceTest.vm`

## Frontend

### API
- 模板：`templates/vue3/api/api.ts.vm`

### Main Views
- 模板：
  - `templates/vue3/views/index.vue.vm`
  - `templates/vue3/views/form.vue.vm`

### Sub Components
- 模板：
  - `templates/vue3/views/components/form_sub_normal.vue.vm`
  - `templates/vue3/views/components/form_sub_erp.vue.vm`
  - `templates/vue3/views/components/form_sub_inner.vue.vm`
  - `templates/vue3/views/components/list_sub_erp.vue.vm`
  - `templates/vue3/views/components/list_sub_inner.vue.vm`

## Docs

- 模板：
  - `templates/docs/api-doc.html.vm`
  - `templates/docs/api-doc.md.vm`

## SQL and Router

- 菜单 SQL：`templates/sql/menu.sql.vm`
- 路由：`templates/router/route.ts.vm`

## When templates are enough

模板基本够用的情况：

- 标准单表 CRUD
- 简单分页查询
- 标准列表页 + 表单页
- 常规菜单 SQL
- 常规 API 文档骨架

## When templates are not enough

以下情况不能只依赖模板，必须进一步改造：

- 主子表级联保存、更新、删除
- 树表逻辑
- 联表查询
- 复杂筛选条件
- 字典翻译
- 导入导出
- 权限细化
- 自定义错误码
- 复杂表单联动
- 审批流 / 状态流转
- 文件上传下载
- 多租户 / 数据权限
- 实时通信
- 异步任务
- 幂等性、限流、防重复提交
- 复杂菜单路由约束

## Required post-template review

每次使用模板生成后，至少检查：

1. 是否符合真实表结构
2. 是否符合项目指导文档
3. 是否符合已有模块风格
4. 是否缺少业务特有逻辑
5. 是否需要补充联表、权限、校验、字典、异常处理
6. 是否需要对前端交互做二次调整