# zjky-codegen

> 建科院代码生成器 - 基于数据库表结构自动生成完整业务代码

## 核心能力

| 能力 | 说明 |
|------|------|
| 🎯 场景识别 | 自动识别 4 种需求场景，智能响应 |
| 🔧 后端生成 | Controller / Service / Mapper / Entity / VO / Converter |
| 🖥️ 前端生成 | Vue 3 + Element Plus + TypeScript |
| ✅ 测试生成 | JUnit 5 单元测试，覆盖率 ≥ 60% |
| 📝 文档生成 | HTML + Markdown API 文档 |
| 📋 计划管理 | 步骤追踪、进度提醒、回滚支持 |

## 快速开始

```
用户：根据 asset_base_info 表生成代码
skill：查询表结构... 制定计划... 开始生成...
```

## 关键约束

- ❌ 禁止使用 `@Schema` 注解
- ✅ 使用 MapStruct 进行对象转换
- ✅ 作者名：**首次使用时询问用户**
- ✅ 包名规则：`com.zjky.pro.app.{模块名}`

## 详细文档

| 文档 | 用途 |
|------|------|
| [PROMPT.md](PROMPT.md) | AI 执行指令（完整工作流程） |
| [README.md](README.md) | 用户使用指南（示例和 FAQ） |

## 资源目录

```
├── memory/           # 记忆系统
│   ├── codegen-memory.md      # 用户偏好、项目信息
│   ├── codegen-plan-active.md # 当前活跃计划
│   └── codegen-plan-history.md # 计划历史
├── scripts/          # 工具脚本
│   ├── mysql-tool.js          # 数据库工具
│   ├── plan-reminder.js       # 计划提醒
│   └── plan-rollback.js       # 计划回滚
├── templates/        # 代码模板
│   ├── java/                   # Java 后端模板
│   ├── vue3/                   # Vue3 前端模板
│   ├── docs/                   # API 文档模板
│   ├── sql/                    # 菜单 SQL 模板
│   └── router/                 # 路由配置模板
└── reference/        # 参考文档
    ├── 后端手册*.md            # 后端编码规范
    └── 前端手册*.md            # 前端编码规范
```

## 工作流程概览

```
阶段 0: 场景识别 → 检查活跃计划 → 识别需求类型
阶段 1: 项目识别 → 确认项目路径 → 读取数据库连接
阶段 2: 需求分析 → 查询表结构 → 确定生成范围
阶段 3: 制定计划 → 列出步骤 → 用户确认
阶段 4: 执行生成 → 后端 → 前端 → 测试 → 文档
阶段 5: 用户确认 → 展示结果 → 调整优化
阶段 6: 记忆更新 → 保存偏好 → 记录历史
```

---

**详细指令请查看 [PROMPT.md](PROMPT.md)**