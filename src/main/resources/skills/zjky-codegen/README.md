# zjky-codegen 使用指南

> 建科院代码生成器 - 用户使用手册

---

## 快速开始

### 场景 A：功能点修改

当你只需要修改某个功能点时：

```
用户：给列表页添加导出功能
skill：好的，我来为列表页添加导出功能...
```

```
用户：修改表单验证规则，添加手机号格式校验
skill：好的，我来修改表单验证规则...
```

### 场景 B：表未生成

当你需要开发新功能，但数据库表还不存在时：

```
用户：我需要开发一个资产管理模块
skill：请提供 MySQL 连接信息...
用户：localhost:3306, root, 123456, asset_disposal
skill：好的，我来设计表结构...
skill：[展示 DDL]
skill：是否执行创建表？(是/否)
用户：是
skill：[执行 DDL]
skill：[制定计划]
skill：[开始生成代码]...
```

### 场景 C：表已存在

当数据库表已经存在，需要生成代码时：

```
用户：根据 asset_base_info 表生成代码
skill：查询表结构...
skill：[展示表结构]
skill：请确认生成范围：
       - [ ] 后端代码
       - [ ] 前端代码
       - [ ] 单元测试
       - [ ] API 文档
       - [ ] 菜单 SQL
       - [ ] 路由配置
用户：全选
skill：[制定计划]
skill：[开始生成代码]...
```

### 场景 D：图片生成代码

当你有前端设计图时：

```
用户：请根据这张设计图生成代码
[上传图片]
skill：分析图片中的 UI 元素...
skill：识别到：表单、表格、按钮、对话框
skill：生成对应的 Vue 3 + Element Plus 代码...
```

---

## 生成内容

### 后端代码

| 文件类型 | 说明 |
|----------|------|
| Entity/PO | 数据库实体类 |
| VO 类 | SaveReqVO / UpdateReqVO / PageReqVO / RespVO |
| Mapper 接口 | MyBatis Mapper 接口 |
| Mapper XML | MyBatis XML 映射文件 |
| Service 接口 | 业务接口 |
| Service 实现 | 业务实现类 |
| Controller | RESTful API 控制器 |
| Converter | MapStruct 对象转换器 |

### 前端代码

| 文件类型 | 说明 |
|----------|------|
| API 文件 | src/api/{模块}/index.ts |
| 列表页面 | src/views/{模块}/{功能}/index.vue |
| 表单组件 | src/views/{模块}/{功能}/{功能}Form.vue |

### 其他

| 文件类型 | 说明 |
|----------|------|
| 单元测试 | JUnit 5 测试类 |
| API 文档 | HTML + Markdown 格式 |
| 菜单 SQL | 插入系统菜单表 |
| 路由配置 | 集成到现有路由 |

---

## 计划管理

### 查看进度

生成过程中会自动发送进度提醒：

```
📋 计划进度提醒
📌 项目：asset-disposal-platform
📊 进度：5/15 (33%)
✅ 当前完成：生成 Mapper 接口和 XML
⏳ 下一步：生成 Service 接口和实现
```

### 暂停/恢复

```
用户：暂停当前计划
skill：好的，已暂停计划。需要恢复时请说"恢复计划"。

用户：恢复计划
skill：好的，继续执行计划...
```

### 取消计划

```
用户：取消当前计划
skill：好的，已取消计划。是否需要回滚已生成的代码？(是/否)
用户：是
skill：正在回滚...回滚完成。
```

### 回滚

```
用户：回滚到第 3 步
skill：正在回滚到第 3 步...
skill：回滚完成，已恢复到"生成 Entity/PO 类"之后的状态。
```

---

## 常见问题

### Q: 如何修改作者名？

```
用户：把作者名改成 zhangsan
skill：好的，已更新作者名为 zhangsan，后续生成的代码将使用此作者名。
```

### Q: 如何修改数据库连接？

```
用户：更新数据库连接信息
skill：请提供新的 MySQL 连接信息...
```

### Q: 生成的代码不符合规范怎么办？

```
用户：生成的 Controller 没有权限注解
skill：抱歉，我来修复这个问题...
```

### Q: 如何只生成部分代码？

```
用户：只生成后端代码，不需要前端
skill：好的，只生成后端代码...
```

### Q: 如何查看生成历史？

```
用户：查看生成历史
skill：[展示最近 10 次生成记录]
```

---

## 编码规范

### Java 后端

- 包名：`com.zjky.pro.app.{模块名}`
- 作者注解：`@author {作者名}`
- ❌ 禁止使用 `@Schema` 注解
- ✅ 使用 MapStruct 进行对象转换

### Vue 前端

- 框架：Vue 3 + Composition API + TypeScript
- UI 库：Element Plus
- 字典：使用 `getStrDictOptions()` 和 `dict-tag` 组件
- 权限：使用 `v-hasPermi` 指令

### 单元测试

- 框架：JUnit 5
- 基类：继承 `BaseDbUnitTest`
- 命名：`test{方法名}_{场景}_{预期结果}`
- 覆盖率：核心模块 ≥ 80%，一般模块 ≥ 60%

---

## 文件结构

```
zjky-codegen/
├── SKILL.md          # 技能概述（AI 快速了解）
├── PROMPT.md         # 执行指令（AI 详细流程）
├── README.md         # 使用指南（用户手册）
├── memory/           # 记忆系统
│   ├── codegen-memory.md      # 用户偏好
│   ├── codegen-plan-active.md # 当前计划
│   └── codegen-plan-history.md # 计划历史
├── scripts/          # 工具脚本
│   ├── mysql-tool.js          # 数据库工具
│   ├── plan-reminder.js       # 计划提醒
│   └── plan-rollback.js       # 计划回滚
├── templates/        # 代码模板
│   ├── java/                   # Java 后端
│   ├── vue3/                   # Vue3 前端
│   ├── docs/                   # API 文档
│   ├── sql/                    # 菜单 SQL
│   └── router/                 # 路由配置
└── reference/        # 参考文档
    ├── 后端手册*.md            # 后端规范
    └── 前端手册*.md            # 前端规范
```

---

## 联系支持

如有问题，请联系技能维护者。