/**
 * JavaScript 插件示例
 *
 * 插件说明：
 * - 插件文件放在 workspace/plugins/ 目录下
 * - 支持 .js 和 .py 文件
 * - JS 插件使用 GraalJS 执行
 *
 * 可用变量：
 * - workspace: 工作区路径（字符串）
 *
 * 返回结果方式（优先级从高到低）：
 * 1. 调用 setResult(value) 函数
 * 2. 设置全局变量 result
 * 3. 脚本最后一行表达式（自动返回）
 *
 * 配置示例（config.json）：
 * {
 *   "plugins": {
 *     "items": {
 *       "example": { "enabled": true, "priority": 10 },
 *       "another": { "enabled": false, "priority": 20 }
 *     }
 *   }
 * }
 */

// 方式1：使用 setResult 函数（推荐）
setResult("这是来自 JS 插件的示例输出。\n工作区路径: " + workspace);

// 方式2：设置 result 变量（备选）
// result = "这是来自 JS 插件的示例输出";

// 方式3：最后一行表达式（自动返回）
// "这是来自 JS 插件的示例输出";