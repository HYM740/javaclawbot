# Testing Rules

本文件定义测试代码生成时的默认规则。生成测试时，优先贴合项目现有测试基类、测试目录结构、断言风格和 mock 方式；只有项目中没有明确模式时，才使用本文件的默认规则。

## Target Stack

- JUnit 5
- Mockito

如果项目已有：
- `BaseDbUnitTest`
- 自定义测试基类
- 统一测试工具类
- 统一构造测试数据的工厂方法

优先复用，不要另起一套。

## When to Generate Tests

以下情况优先生成测试：

- 用户明确要求补单元测试
- 任务是完整模块生成
- 生成了较多 Service 业务逻辑
- 存在明显的条件分支、校验逻辑、异常分支

以下情况可不强制生成完整测试：

- 只是很小的局部改动
- 只是静态页面或简单 VO/DTO
- 用户明确表示先不要测试

## Test Scope Priority

测试优先覆盖这些内容：

1. Service 层核心业务逻辑
2. 参数校验和异常分支
3. 创建 / 更新 / 删除 / 查询等核心行为
4. 分页和过滤条件处理
5. 关键转换或聚合逻辑

不优先把时间花在：

- 纯 getter/setter
- 机械无意义覆盖
- 没有业务价值的样板方法

## Naming Rules

测试类命名优先贴合项目现有习惯，常见形式：

- `AssetBaseInfoServiceTest`
- `AssetBaseInfoServiceImplTest`

测试方法命名要体现：

- 测试对象
- 场景
- 预期结果

例如：

- `testCreateAssetBaseInfo_success`
- `testUpdateAssetBaseInfo_whenNotExists_throwException`
- `testGetAssetBaseInfoPage_withCondition_returnPageResult`

如果项目已有中文命名风格或其他统一规则，优先沿用项目现状。

## Structure Rules

测试通常包括：

- 初始化测试数据
- mock 依赖或准备数据库数据
- 调用被测方法
- 断言结果
- 断言依赖调用（需要时）

结构要清晰，不要把多个场景塞进一个超长测试方法。

## Service Test Rules

### If project uses DB-based test style

如果项目已有数据库集成式测试风格，例如：
- `BaseDbUnitTest`
- 真实 Mapper + 测试数据库
- 事务回滚测试

优先贴合这种方式，不要强行改成纯 mock 单元测试。

### If project uses mock-based service tests

如果项目习惯：
- `@Mock`
- `@InjectMocks`
- `@MockBean`

优先沿用项目现状。

### General Rule

无论哪种方式，都要优先验证：

- 关键入参处理
- 业务条件判断
- 异常分支
- 返回结果正确性

## CRUD Test Suggestions

### Create

至少关注：

- 正常创建成功
- 必填字段缺失或非法时的处理
- 重复数据或唯一性冲突（如果业务有）

### Update

至少关注：

- 正常更新成功
- 目标记录不存在
- 不允许更新的字段被错误传入（若有）

### Delete

至少关注：

- 正常删除成功
- 目标记录不存在
- 存在关联数据不允许删除（若有）

### Query / Page

至少关注：

- 条件为空时的默认行为
- 条件过滤正确
- 分页参数生效
- 返回结构正确

## Assertion Rules

断言应关注业务结果，不要只断言“非空”。

优先使用：

- 值相等断言
- 集合大小断言
- 异常类型与异常信息断言
- 分页总数与记录内容断言

不要写成大量弱断言，例如：

- `assertNotNull(result)` 然后什么也不验证
- 只验证方法被调用，却不验证结果是否正确

## Mock Rules

使用 Mockito 时：

- 只 mock 真正必要的依赖
- 保持 mock 行为简单可读
- 避免过度链式 mock
- 避免为了凑通过而把所有逻辑都 mock 掉

如果某段逻辑更适合集成测试，就不要勉强写成复杂 mock 单测。

## Data Preparation Rules

测试数据准备应：

- 尽量最小化
- 与测试场景直接相关
- 命名清晰
- 避免复制大段无关初始化代码

如果多个测试场景复用相似数据，可提取公共构造方法，但不要为了抽象而让代码更难读。

## Coverage Guidance

覆盖率目标用于指导，不要机械追求数字而牺牲测试质量。

建议：

- 核心模块：尽量达到较高覆盖
- 一般模块：覆盖主要业务路径和关键分支
- 优先覆盖“会出错、会回归、会影响业务”的部分

## What to Avoid

不要这样做：

- 为了凑覆盖率写空洞测试
- 一个测试方法塞太多场景
- 全是 `assertNotNull`
- 全靠 mock，完全不验证业务结果
- 忽略异常和边界场景
- 不看项目现有测试风格，另起一套

## Output Expectation

生成测试代码时，回复中应说明：

1. 生成或修改了哪些测试文件
2. 覆盖了哪些业务场景
3. 使用了哪种测试风格（DB 集成式 / mock 式）
4. 哪些分支仍建议后续补测