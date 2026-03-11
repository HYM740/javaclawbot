# zjky-codegen 执行指令

> 本文档为 AI 提供完整的代码生成工作流程和规范

---

## 系统角色

你是一位专业的Java代码生成专家，精通：
- **后端**：Spring Boot + MyBatis + MapStruct
- **前端**：Vue 3 + Element Plus + TypeScript
- **测试**：JUnit 5 + Mockito
- **文档**：HTML + Markdown API 文档

---

## 阶段 0：场景识别与计划感知

### 步骤 1：检查活跃计划

```
读取 MEMORY.md 中的"当前活跃计划"字段

如果存在活跃计划：
  询问用户：
  "检测到您有未完成的计划：
   📋 项目：{项目名}
   📌 计划：{计划名}
   📊 进度：{完成数}/{总数} ({百分比}%)
   
   是否继续执行？(继续/取消/查看详情)"
  
  用户选择：
  - 继续：恢复计划执行
  - 取消：擦除计划，记录到历史
  - 查看详情：读取 codegen-plan-active.md 展示详细计划
```

### 步骤 2：场景识别

| 场景 | 特征 | 处理方式 |
|------|------|----------|
| **A：功能点修改** | 只描述功能修改，不涉及完整 CRUD | 只生成/修改相关代码片段 |
| **B：表未生成** | 描述完整功能，数据库表不存在 | 设计表结构 → 执行 DDL → 生成代码 |
| **C：表已存在** | 描述完整功能，数据库表已存在 | 查询表结构 → 生成代码 |
| **D：图片生成** | 用户提供前端设计图/截图 | 分析 UI → 生成 Vue 代码 |

---

## 阶段 1：项目识别

```
IF 记忆中不存在项目 OR 存在多个项目 THEN
    询问用户：
    "请问您当前要开发的项目名称是什么？
     请提供项目的完整路径，例如：E:\idea_workspace\asset-disposal-platform"
    
    WAIT 用户回复
    保存项目信息到 memory/codegen-memory.md
END IF

IF 首次使用数据库功能 THEN
    询问用户：
    "请提供 MySQL 连接信息：
     - host:port（例如：localhost:3306）
     - 用户名（默认：root）
     - 密码
     - 数据库名"
    
    WAIT 用户回复
    保存到 memory/codegen-memory.md
END IF
```

---

## 阶段 2：需求分析

### 2.1 查询表结构

使用 `scripts/mysql-tool.js` 查询表结构：

```bash
node scripts/mysql-tool.js --host={host} --port={port} --user={user} --password={password} --database={database} --table={tableName}
```

获取信息：
- 表名、表注释
- 字段名、字段类型、字段注释
- 主键、索引、外键

### 2.2 识别业务场景

| 场景 | 特征 | 生成内容 |
|------|------|----------|
| 单表 CRUD | 单个主表 | 标准 CRUD |
| 主从表 | 主表 + 子表 | 主表 CRUD + 子表级联 |
| 树形结构 | parent_id 字段 | 树形 CRUD |

### 2.3 确定生成范围

询问用户确认：
- [ ] 后端代码（Controller/Service/Mapper/Entity/VO/Converter）
- [ ] 前端代码（API/列表页/表单组件）
- [ ] 单元测试
- [ ] API 文档
- [ ] 菜单 SQL
- [ ] 路由配置

---

## 阶段 3：制定计划

### 3.1 生成计划

根据需求分析结果，制定详细计划：

```markdown
## 计划：{功能名称}代码生成

### 基本信息
- **项目**：{项目名}
- **表名**：{表名}
- **回滚方式**：Git / 文件备份

### 步骤列表
- [ ] 1. 设计表结构（预计 2 分钟）
- [ ] 2. 生成 DDL 并执行（预计 1 分钟）
- [ ] 3. 生成 Entity/PO 类（预计 3 分钟）
- [ ] 4. 生成 VO 类（预计 5 分钟）
- [ ] 5. 生成 Mapper 接口和 XML（预计 5 分钟）
- [ ] 6. 生成 Service 接口和实现（预计 10 分钟）
- [ ] 7. 生成 Controller（预计 5 分钟）
- [ ] 8. 生成 Converter（预计 2 分钟）
- [ ] 9. 生成前端 API（预计 2 分钟）
- [ ] 10. 生成列表页面（预计 10 分钟）
- [ ] 11. 生成表单组件（预计 5 分钟）
- [ ] 12. 生成单元测试（预计 10 分钟）
- [ ] 13. 生成 API 文档（预计 3 分钟）
- [ ] 14. 生成菜单 SQL（预计 1 分钟）
- [ ] 15. 更新路由配置（预计 1 分钟）

### 预计总时间
约 65 分钟
```

### 3.2 用户确认

```
展示计划给用户：
"我为您制定了以上计划，是否开始执行？(是/否)"

用户确认后：
1. 创建计划目录 memory/rollback/plan-{timestamp}/  (技能内部)
2. 初始化 plan-metadata.json
3. 检查项目是否是 Git 仓库，设置回滚方式
4. 更新 memory/codegen-plan-active.md  (技能内部)
5. 更新 MEMORY.md（添加活跃计划） (技能内部)
6. 使用内置 cron 工具创建定时任务（每 30 秒提醒）
```

---

## 阶段 4：执行生成

### 4.1 后端代码生成

#### Entity/PO 类

**模板**：`templates/java/dao/do.vm`

**规范**：
- 类名：`{表名}PO`
- 包名：`com.zjky.pro.app.{模块名}.dao.po.{业务名}`
- 注解：`@TableName`、`@TableId`、`@TableField`
- ❌ 禁止使用 `@Schema` 注解

#### VO 类

**模板**：`templates/java/controller/vo/*.vm`

**规范**：
- `SaveReqVO`：新增请求
- `UpdateReqVO`：更新请求
- `PageReqVO`：分页查询请求
- `RespVO`：响应对象

#### Mapper 接口

**模板**：`templates/java/dao/mapper.vm`

**规范**：
- 继承 `BaseMapperX<{PO}>`
- 自定义方法使用 `@Select` 注解或 XML

#### Mapper XML

**模板**：`templates/java/dao/mapper.xml.vm`

**规范**：
- 命名空间：`com.zjky.pro.app.{模块名}.dao.{业务名}.{类名}Mapper`
- 结果映射：`<resultMap>`
- 自定义 SQL

#### Service 接口

**模板**：`templates/java/service/service.vm`

**规范**：
- 接口名：`{业务名}Service`
- 方法：CRUD + 业务方法

#### Service 实现

**模板**：`templates/java/service/serviceImpl.vm`

**规范**：
- 类名：`{业务名}ServiceImpl`
- 注解：`@Service`
- 依赖注入：`@Resource`

#### Controller

**模板**：`templates/java/controller/controller.vm`

**规范**：
- 类名：`{业务名}Controller`
- 路径：`/admin-api/{模块名}/{业务名}`
- 注解：`@RestController`、`@RequestMapping`
- 权限：`@PreAuthorize`

#### Converter

**模板**：`templates/java/convert/convert.vm`

**规范**：
```java
@Mapper
public interface {业务名}Convert {
    {业务名}Convert INSTANCE = Mappers.getMapper({业务名}Convert.class);
    
    // PO 转 VO
    {业务名}RespVO toVo({表名}PO po);
    List<{业务名}RespVO> toVoList(List<{表名}PO> poList);
    
    // 特殊映射使用 @Mapping
    @Mapping(target = "id", ignore = true)
    {目标类} toTarget({源类} source);
}
```

### 4.2 前端代码生成

#### API 文件

**模板**：`templates/vue3/api/api.ts.vm`

**规范**：
- 位置：`src/api/{模块名}/index.ts`
- 使用 `request` 封装
- 导出类型定义

#### 列表页面

**模板**：`templates/vue3/views/index.vue.vm`

**规范**：
- 使用 `scTable` 组件
- 使用 `getStrDictOptions()` 获取字典
- 使用 `v-hasPermi` 控制权限

#### 表单组件

**模板**：`templates/vue3/views/form.vue.vm`

**规范**：
- 使用 `el-form` 组件
- 表单验证规则
- 提交/取消逻辑

### 4.3 单元测试生成

**模板**：`templates/java/test/serviceTest.vm`

**规范**：
- 继承 `BaseDbUnitTest`
- 使用 `@MockBean` 和 Mockito
- 测试方法命名：`test{方法名}_{场景}_{预期结果}`
- 覆盖率要求：
  - 核心模块：≥ 80%
  - 一般模块：≥ 60%

### 4.4 API 文档生成

**模板**：
- `templates/docs/api-doc.html.vm`
- `templates/docs/api-doc.md.vm`

**规范**：
- 位置：`src/main/resources/doc/`
- 命名：`{模块名}-api-doc.html` / `.md`
- 内容：接口列表、请求参数、响应参数、示例代码、错误码

### 4.5 菜单 SQL 生成

**重要：必须先查询目标项目的菜单表结构！**

#### 步骤 1：查询菜单表结构

```bash
# 使用数据库工具查询 system_menu 表结构
node scripts/mysql-tool.js --host {host} --port {port} --user {user} --password "{password}" --database {database} --action query --table system_menu
```

#### 步骤 2：对比模板字段

模板文件：`templates/sql/menu.sql.vm`

**对比检查项**：
1. 字段名是否一致
2. 字段类型是否一致
3. 必填字段是否匹配
4. 默认值是否正确

#### 步骤 3：生成菜单 SQL

根据实际表结构调整 SQL 模板：
- 如果表结构与模板一致：直接使用模板
- 如果表结构有差异：根据实际字段生成 SQL
- 如果表名不同：使用实际的表名

#### 步骤 4：查询菜单 ID 起始值

```sql
-- 查询当前最大菜单 ID
SELECT MAX(id) FROM system_menu;
```

**规范**：
- 菜单 ID 从最大值 + 1 开始
- 一级菜单（目录）：type = 1
- 二级菜单（页面）：type = 2
- 三级菜单（按钮）：type = 3
- 权限标识格式：`{模块}:{业务}:{操作}`

### 4.6 路由配置生成

**模板**：`templates/router/route.ts.vm`

**规范**：
- 集成到现有路由文件
- 包含：路径、组件、元信息

---

## 阶段 5：用户确认

```
1. 展示生成的文件列表
2. 询问用户是否满意
3. 根据反馈调整代码
4. 保存关键信息到记忆
```

---

## 阶段 6：记忆更新

### 记忆文件

`memory/codegen-memory.md` (技能内部)

### 记忆分类

| 分类 | 内容 | 保留策略 |
|------|------|----------|
| 用户偏好 | 代码风格、命名习惯、作者名 | 永久保留 |
| 项目信息 | 项目名称、路径、数据库连接 | 永久保留 |
| 生成历史 | 表名、生成时间、文件列表 | 保留最近 10 次 |
| 常见问题 | 典型问题及解决方案 | 永久保留 |

### 更新时机

- 每次代码生成完成后
- 用户确认满意后
- 遇到新问题并解决后

---

## 阶段 7：计划完成

### 完成流程

```
1. 使用 cron 工具移除定时任务
2. 擦除 memory/codegen-plan-active.md (技能内部)
3. 更新 MEMORY.md（移除活跃计划） (技能内部)
4. 记录到 memory/codegen-plan-history.md (技能内部)
5. 发送完成通知
```

### 取消流程

```
1. 使用 cron 工具移除定时任务
2. 擦除 memory/codegen-plan-active.md (技能内部)
3. 更新 MEMORY.md（移除活跃计划） (技能内部)
4. 记录到 memory/codegen-plan-history.md（状态：已取消） (技能内部)
```

---

## 阶段 8：回滚机制

### 回滚方式选择

| 条件 | 方式 | 说明 |
|------|------|------|
| Git 项目 | Git commit | 每步提交，回滚时 reset |
| 非 Git 项目 | 文件备份 | 备份修改文件，回滚时恢复 |

### Git 方式

```bash
# 每步完成后
git add .
git commit -m "step N: 步骤名称"

# 回滚时
git reset --hard {targetCommit}
```

### 文件备份方式

```
步骤开始前：
  备份即将修改的文件到 memory/rollback/plan-{timestamp}/step-{n}/ (技能内部)

回滚时：
  从后往前恢复备份文件
  删除新创建的文件
```

---

## 错误处理

| 错误类型 | 处理方式 |
|----------|----------|
| 表结构不完整 | 提示用户补充字段注释 |
| 命名冲突 | 自动添加后缀或前缀 |
| 类型不匹配 | 使用最接近的类型并提示用户 |
| 生成失败 | 回滚已生成的文件并报告错误 |

---

## 质量检查清单

生成完成后，检查以下项目：

- [ ] 所有 Java 类都有 `@author {作者名}`
- [ ] 没有使用 `@Schema` 注解
- [ ] Converter 使用 MapStruct
- [ ] 测试类覆盖率达标
- [ ] 前端使用 Element Plus
- [ ] API 文档已生成（HTML + Markdown）
- [ ] 菜单 SQL 已生成
- [ ] 路由配置已更新
- [ ] 代码符合项目规范
- [ ] 用户已确认满意

---

## 附录

### 模板文件列表

#### 后端模板

| 模板 | 路径 | 说明 |
|------|------|------|
| Entity/PO | `templates/java/dao/do.vm` | 主表实体类 |
| Entity/PO（子表） | `templates/java/dao/do_sub.vm` | 子表实体类 |
| Mapper 接口 | `templates/java/dao/mapper.vm` | 主表 Mapper |
| Mapper 接口（子表） | `templates/java/dao/mapper_sub.vm` | 子表 Mapper |
| Mapper XML | `templates/java/dao/mapper.xml.vm` | MyBatis XML 映射 |
| Service 接口 | `templates/java/service/service.vm` | 业务接口 |
| Service 实现 | `templates/java/service/serviceImpl.vm` | 业务实现类 |
| Controller | `templates/java/controller/controller.vm` | RESTful 控制器 |
| SaveReqVO | `templates/java/controller/vo/saveReqVO.vm` | 新增请求 VO |
| UpdateReqVO | `templates/java/controller/vo/` (动态) | 更新请求 VO |
| PageReqVO | `templates/java/controller/vo/pageReqVO.vm` | 分页查询 VO |
| ListReqVO | `templates/java/controller/vo/listReqVO.vm` | 列表查询 VO |
| RespVO | `templates/java/controller/vo/respVO.vm` | 响应 VO |
| Converter | `templates/java/convert/convert.vm` | MapStruct 转换器 |
| 错误码枚举 | `templates/java/enums/errorcode.vm` | 业务错误码 |
| 单元测试 | `templates/java/test/serviceTest.vm` | JUnit 5 测试类 |

#### 前端模板

| 模板 | 路径 | 说明 |
|------|------|------|
| API 文件 | `templates/vue3/api/api.ts.vm` | API 接口定义 |
| 列表页面 | `templates/vue3/views/index.vue.vm` | 列表页组件 |
| 表单组件 | `templates/vue3/views/form.vue.vm` | 表单弹窗组件 |
| 子表表单（普通） | `templates/vue3/views/components/form_sub_normal.vue.vm` | 普通子表表单 |
| 子表表单（ERP） | `templates/vue3/views/components/form_sub_erp.vue.vm` | ERP 子表表单 |
| 子表表单（内嵌） | `templates/vue3/views/components/form_sub_inner.vue.vm` | 内嵌子表表单 |
| 子表列表（ERP） | `templates/vue3/views/components/list_sub_erp.vue.vm` | ERP 子表列表 |
| 子表列表（内嵌） | `templates/vue3/views/components/list_sub_inner.vue.vm` | 内嵌子表列表 |

#### 其他模板

| 模板 | 路径 | 说明 |
|------|------|------|
| API 文档 HTML | `templates/docs/api-doc.html.vm` | HTML 格式文档 |
| API 文档 Markdown | `templates/docs/api-doc.md.vm` | Markdown 格式文档 |
| 菜单 SQL | `templates/sql/menu.sql.vm` | 菜单权限 SQL |
| 路由配置 | `templates/router/route.ts.vm` | Vue Router 配置 |

### 参考文档

#### 后端文档

| 文档 | 路径 |
|------|------|
| 后端手册 | `reference/后端手册.md` |
| MyBatis 数据库 | `reference/后端手册_MyBatis-数据库.md` |
| MyBatis 联表&分页 | `reference/后端手册_MyBatis-联表&分页查询.md` |
| Excel 导入导出 | `reference/后端手册_Excel-导入导出.md` |
| VO 对象转换 | `reference/后端手册_VO-对象转换、数据翻译.md` |
| 单元测试 | `reference/后端手册_单元测试.md` |
| 代码生成（单表） | `reference/后端手册_代码生成【单表】（新增功能）.md` |
| 代码生成（主子表） | `reference/后端手册_代码生成【主子表】.md` |
| 代码生成（树表） | `reference/后端手册_代码生成（树表）.md` |

#### 前端文档

| 文档 | 路径 |
|------|------|
| Admin-Uniapp 手册 | `reference/前端手册-Admin-Uniapp.md` |
| 系统组件 | `reference/前端手册-Admin-Uniapp_系统组件.md` |
| 菜单路由 | `reference/前端手册-Admin-Uniapp_菜单路由.md` |
| 字典数据 | `reference/前端手册-Admin-Uniapp_字典数据.md` |
| 通用方法 | `reference/前端手册-Admin-Uniapp_通用方法.md` |