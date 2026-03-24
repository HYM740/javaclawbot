# Backend Rules

本文件定义后端代码生成时的默认规则。生成代码时，优先贴合项目现有写法；只有在项目中找不到明确模式时，才使用本文件的默认规则。

## Target Stack

- Spring Boot
- MyBatis / MyBatis Plus
- MapStruct
- JUnit 5
- Mockito

## Package Structure

默认包名前缀：

`com.zjky.pro.app.{模块名}`

常见分层建议：

- `controller.{业务名}`
- `controller.{业务名}.vo`
- `service.{业务名}`
- `service.{业务名}.impl`
- `dao.{业务名}`
- `dao.mapper.{业务名}`
- `dao.po.{业务名}`
- `convert.{业务名}`

如果项目现有目录结构不同，优先沿用项目现状。

## Files to Generate

完整模块通常包含：

- Controller
- Service 接口
- Service 实现
- Mapper 接口
- Mapper XML（需要时）
- PO / Entity
- ReqVO / RespVO
- Convert

不要为了“完整”而硬生成项目里根本不用的层。

## PO / Entity Rules

### Naming

- 主实体默认：`{业务实体}PO`
- 子表实体默认：`{业务实体}ItemPO` 或贴合项目现有命名

### Annotation Rules

允许使用项目现有 ORM 注解，例如：

- `@TableName`
- `@TableId`
- `@TableField`

禁止使用：

- `@Schema`

### Field Mapping

- 数据库字段与 Java 字段按项目现有命名策略转换
- 常见时间字段优先保留项目现有时间类型与注解方式
- 若字段语义不清，保留 TODO 注释或向用户说明，而不是擅自猜业务含义

## VO Rules

常见 VO：

- `SaveReqVO`
- `UpdateReqVO`
- `PageReqVO`
- `RespVO`
- `ListReqVO`（项目中已有时再使用）

规则：

- 请求对象只放当前接口真正需要的字段
- 响应对象尽量面向前端展示，而不是直接暴露 PO
- 不要把所有数据库字段机械复制到所有 VO 中

## Mapper Rules

### Interface

如果项目已有统一基类，例如 `BaseMapperX<T>`，优先继承该基类。

示例风格：

```java
public interface AssetBaseInfoMapper extends BaseMapperX<AssetBaseInfoPO> {
}
```

### XML

只有在以下情况才补 Mapper XML：

- 联表查询
- 复杂动态条件
- 项目明确要求 XML 写法

简单单表 CRUD 不要强行生成 XML。

## Service Rules

### Interface

- 接口名：`{业务名}Service`
- 保持面向业务的方法命名
- 不要只堆砌机械 CRUD 方法，如果项目已有统一服务风格，优先贴合

### Implementation

- 实现类名：`{业务名}ServiceImpl`
- 可使用 `@Service`
- 依赖注入方式优先贴合项目现状，例如 `@Resource` 或构造注入

业务实现中注意：

- 参数校验遵循项目现有方式
- 异常处理遵循项目统一方式
- 分页、列表、详情、创建、更新、删除逻辑尽量清晰分离

## Controller Rules

### Naming

- 类名：`{业务名}Controller`

### Route

默认接口前缀可参考：

```
/{模块名}/{业务名}
```

如果项目已有不同网关前缀或模块路由规则，优先沿用项目现状。

### Controller Style

- 使用项目现有统一响应包装
- 使用项目现有权限注解风格
- 使用项目现有参数校验风格
- 方法名尽量表达动作和对象，不要只写成模糊名称

常见接口：

- `createXxx`
- `updateXxx`
- `deleteXxx`
- `getXxx`
- `getXxxPage`
- `exportXxxExcel`（项目存在导出模式时）

## Convert Rules

对象转换优先使用 MapStruct。

示例：

```java
@Mapper
public interface AssetBaseInfoConvert {

    AssetBaseInfoConvert INSTANCE = Mappers.getMapper(AssetBaseInfoConvert.class);

    AssetBaseInfoRespVO convert(AssetBaseInfoPO bean);

    List<AssetBaseInfoRespVO> convertList(List<AssetBaseInfoPO> list);
}
```

规则：

- 简单同名字段直接映射
- 字段差异明显时使用 `@Mapping`
- 不要手写大段重复 set/get 转换代码，除非项目本身不用 MapStruct

## Post-template backend adaptation

如果后端代码是基于模板生成的，生成后应继续检查并补齐：

- 是否需要自定义 Service 业务方法
- 是否需要联表查询或复杂 Mapper XML
- 是否需要错误码枚举
- 是否需要参数校验
- 是否需要权限注解
- 是否需要字典翻译或 VO 扩展字段
- 是否需要导入导出逻辑
- 是否需要日志、幂等、缓存、事务、数据权限等项目特性

模板生成的是骨架，不代表这些业务能力已经自动具备。

## Query and Pagination Rules

如果项目已有统一分页对象或查询包装，优先复用。

注意：

- 分页查询和列表查询不要混为一体
- 查询条件只保留真实业务需要的字段
- 动态查询尽量可读，不要生成过度复杂的条件拼接

## Error Code and Validation

如果项目有统一错误码体系：

- 优先复用现有错误码枚举
- 不要另起一套风格

如果项目有统一参数校验体系：

- 优先复用现有校验注解与校验流程
- 不要额外引入不兼容的参数校验方式

## Author Rule

如果项目要求类注释作者，而记忆中没有作者名，先询问用户作者名。

不要擅自填入默认作者。

## What to Avoid

不要这样做：

- 使用 `@Schema`
- 忽略项目现有分层与基类体系
- 强行生成 XML，即使项目不需要
- 把所有字段都塞进所有 VO
- 机械生成完全一样的注释和方法
- 在字段语义不清时假装理解业务

## Output Expectation

生成后端代码时，回复中应清楚说明：

1. 生成或修改了哪些后端文件
2. 采用了哪些项目现有风格
3. 哪些地方是按默认规则生成的
4. 哪些字段、接口或业务点仍待用户确认