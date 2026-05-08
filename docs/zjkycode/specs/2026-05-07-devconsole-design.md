# DevConsole 设计文档

> 日期：2026-05-07 | 作者：javaclawbot | 版本：1.0

## 1. 背景

Sidebar 中已有 "Dev Console" 导航项（`Sidebar.java:214-215`），但 `MainStage.setupPages()` 未注册对应页面，点击后为空白。需实现 DevConsole 页面，用于实时查看应用日志。

## 2. 目标

为开发者提供可视化日志控制台，支持实时尾随、级别过滤、关键词搜索/高亮、复制导出。

## 3. 非目标

- 不支持历史日志回放（只读取当前会话日志）
- 不支持远程日志收集
- 不做日志分析/图表

---

## 4. 架构设计

### 4.1 整体数据流

```
logback.xml (FileAppender)
    │ 写入（同步，配置 immediateFlush=true）
    ▼
~/.javaclawbot/logs/app.log
    │ java.nio.file.WatchService 监听 ENTRY_MODIFY
    ▼
LogWatcher (后台守护线程)
    │ RandomAccessFile seek + 增量读取
    ▼
LogBuffer (ConcurrentLinkedQueue<LogEntry>, 上限 5000 行)
    │ 生产者：LogWatcher；消费者：JavaFX 定时器（60ms 间隔）
    ▼
WebView DOM (JS 追加 + CSS class 着色)
```

### 4.2 组件树

```
DevConsolePage extends VBox
├── ToolBar (HBox, padding: 12px)
│   ├── ComboBox<String> levelFilter    [ALL / TRACE / DEBUG / INFO / WARN / ERROR]
│   ├── TextField searchField           [搜索日志...]
│   ├── ToggleButton autoScrollBtn      [自动滚动]
│   ├── Button exportBtn                [导出]
│   └── Button clearBtn                 [清除]
├── WebView logView                     [VBox.vgrow=ALWAYS]
└── StatusBar (HBox, padding: 8px)
    ├── Label fileLabel                 [📄 ~/.javaclawbot/logs/app.log]
    ├── Label lineCount                 [共 12,345 行]
    └── Label statusLabel               [● 监听中]
```

### 4.3 文件结构

| 文件 | 职责 |
|------|------|
| `gui/ui/pages/DevConsolePage.java` | 页面 UI，管理 ToolBar/WebView/StatusBar |
| `gui/ui/LogWatcher.java` | 后台线程，WatchService 监听文件增量 |
| `gui/ui/LogEntry.java` | 日志条目 record（时间戳、级别、消息） |
| `src/main/resources/logback.xml` | Logback 配置，FileAppender 输出 |
| `src/main/resources/static/css/styles/devconsole.css` | WebView 内终端样式 |

---

## 5. 详细设计

### 5.1 logback.xml

```xml
<configuration>
    <!-- 控制台 appender（保留现有行为） -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- 文件 appender（DevConsole 读取源） -->
    <appender name="FILE" class="ch.qos.logback.core.FileAppender">
        <file>${user.home}/.javaclawbot/logs/app.log</file>
        <append>true</append>
        <immediateFlush>true</immediateFlush>
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
    </root>
</configuration>
```

**设计说明：**
- `immediateFlush=true` 确保每行立即刷盘，WatchService 能尽快感知
- pattern 格式 `HH:mm:ss.SSS LEVEL LOGGER - msg`，便于 JS 端正则解析
- 日志目录 `~/.javaclawbot/logs/` 在 LogWatcher 初始化时自动创建

### 5.2 LogEntry

```java
package gui.ui;

public record LogEntry(
    String timestamp,   // "10:23:45.123"
    String level,       // "INFO", "WARN", "ERROR", "DEBUG", "TRACE"
    String logger,      // "agent.AgentLoop"
    String message,     // "Agent 循环已启动"
    String raw          // 原始行（用于导出）
) {}
```

### 5.3 LogWatcher

```
线程模型：单线程后台 daemon 线程，生命周期与 DevConsolePage 绑定

初始化：
  1. 确保 ~/.javaclawbot/logs/ 目录存在
  2. 以 append 模式打开 RandomAccessFile（首次跳转到文件末尾）
  3. 注册 WatchService 到 logs 目录，监听 ENTRY_MODIFY | ENTRY_CREATE

主循环：
  while (!stopped) {
      WatchKey key = watchService.poll(500, MILLISECONDS);
      if (key != null) {
          处理事件 → 当事件路径匹配 app.log 时：
              RandomAccessFile.readLine() 增量读取新行
              → 正则解析每行 → LogEntry → 入队 LogBuffer
          重置 WatchKey
      }
  }

停止：
  stop() 方法设置 flag → 关闭 RandomAccessFile → 关闭 WatchService

错误处理：
  - 文件被删除：尝试重新打开（最多 3 次，间隔 2 秒）
  - 读取异常：记录到 LogBuffer 自身（ERROR 级别）
```

**关键实现细节：**
- 使用 `StandardWatchEventKinds.ENTRY_MODIFY`，但 Windows 上某些编辑器会触发 `ENTRY_CREATE` 而非 `ENTRY_MODIFY`，因此同时监听两者
- `RandomAccessFile.readLine()` 按 UTF-8 读取，每读到一行立即解析
- 日志 pattern 解析正则：`^(\d{2}:\d{2}:\d{2}\.\d{3})\s+(TRACE|DEBUG|INFO|WARN|ERROR)\s+(\S+)\s+-\s+(.*)$`

### 5.4 DevConsolePage

```
页面继承 VBox。

生命周期：
  created → 构建 UI，WebView 加载 HTML 模板
  visible → 启动 LogWatcher
  hidden  → 暂停 LogWatcher（不停止，保留 buffer）
  detached → 停止 LogWatcher，清理资源

与 MainStage 集成：
  - MainStage.setupPages() 中注册：pages.put("devconsole", new DevConsolePage())
  - 页面切换时通过 setVisible/setManaged 控制
  - 不需要 BackendBridge 注入（LogWatcher 直接读文件）
```

### 5.5 WebView HTML/JS

```html
<!DOCTYPE html>
<html>
<head>
<style>
  /* 白色终端风格 */
  body {
      background: #f8f6f0; font-family: 'Consolas', 'Courier New', monospace;
      font-size: 13px; line-height: 1.6; margin: 0; padding: 8px;
  }
  .log-line { white-space: pre-wrap; word-break: break-all; padding: 2px 8px; }
  .log-line .level-tag { display: inline-block; padding: 0px 6px; border-radius: 3px; margin-right: 4px; font-weight: 600; }
  .log-line.ERROR { background: #fee2e2; }
  .log-line.ERROR .level-tag { background: #dc2626; color: #fff; }
  .log-line.WARN  { background: #fef3c7; }
  .log-line.WARN  .level-tag { background: #f59e0b; color: #fff; }
  .log-line.INFO  { color: #374151; }
  .log-line.INFO  .level-tag { background: #dbeafe; color: #1e40af; }
  .log-line.DEBUG { color: #6b7280; }
  .log-line.DEBUG .level-tag { color: #6b7280; font-weight: 400; }
  .log-line.TRACE { color: #9ca3af; }
  .log-line.TRACE .level-tag { color: #9ca3af; font-weight: 400; }
  .log-line.highlight { background: #fde68a; }  /* 搜索高亮 */
</style>
</head>
<body>
<div id="log-container"></div>
<script>
  var MAX_LINES = 5000;
  var currentLevel = 'ALL';
  var searchTerm = '';
  var autoScroll = true;

  // Java→JS 接口
  function appendLog(timestamp, level, logger, message) {
      // 级别过滤
      if (currentLevel !== 'ALL' && level !== currentLevel) return;
      var line = document.createElement('div');
      line.className = 'log-line ' + level;
      line.textContent = '[' + timestamp + '] ' + level + '  ' + logger + ' - ' + message;
      // 搜索高亮
      applyHighlight(line);
      document.getElementById('log-container').appendChild(line);
      // 行数限制
      trimLines();
      // 自动滚动
      if (autoScroll) window.scrollTo(0, document.body.scrollHeight);
  }

  function setFilter(level) {
      currentLevel = level;
      // 重新渲染所有行（显示/隐藏）
      var lines = document.querySelectorAll('.log-line');
      for (var i = 0; i < lines.length; i++) {
          var cls = lines[i].classList;
          if (level === 'ALL' || cls.contains(level)) {
              lines[i].style.display = '';
          } else {
              lines[i].style.display = 'none';
          }
      }
      if (autoScroll) window.scrollTo(0, document.body.scrollHeight);
  }

  function search(keyword) {
      searchTerm = keyword;
      var lines = document.querySelectorAll('.log-line');
      for (var i = 0; i < lines.length; i++) {
          applyHighlight(lines[i]);
      }
      // 滚动到第一个匹配项
      if (keyword) {
          for (var i = 0; i < lines.length; i++) {
              if (lines[i].textContent.toLowerCase().indexOf(keyword.toLowerCase()) >= 0
                  && lines[i].style.display !== 'none') {
                  lines[i].scrollIntoView({block: 'center'});
                  break;
              }
          }
      }
  }

  function applyHighlight(el) {
      if (!searchTerm) return;
      var text = el.textContent;
      var idx = text.toLowerCase().indexOf(searchTerm.toLowerCase());
      if (idx >= 0) {
          el.innerHTML = escapeHtml(text.substring(0, idx))
              + '<span class="highlight">'
              + escapeHtml(text.substring(idx, idx + searchTerm.length))
              + '</span>'
              + escapeHtml(text.substring(idx + searchTerm.length));
      }
  }

  function escapeHtml(s) { /* ... */ }
  function trimLines() { /* 超出 MAX_LINES 移除最旧元素 */ }
  function getSelectedText() { return window.getSelection().toString(); }
</script>
</body>
</html>
```

**Java ↔ JS 桥接：**
- 追加日志：`webEngine.executeScript("appendLog('" + escapeJs(ts) + "','" + level + "','" + escapeJs(logger) + "','" + escapeJs(msg) + "')")`
- 设置过滤：`webEngine.executeScript("setFilter('" + level + "')")`
- 搜索：`webEngine.executeScript("search('" + escapeJs(keyword) + "')")`
- 获取选中文本：`(String) webEngine.executeScript("getSelectedText()")`

### 5.6 过滤与搜索交互

- **级别下拉**（ComboBox）：切换时调用 JS `setFilter(level)`
- **搜索框**（TextField）：实时搜索（输入延迟 300ms debounce），调用 JS `search(keyword)`
- **搜索框回车**：滚动到下一个匹配项
- **自动滚动开关**（ToggleButton）：默认开启，手动滚动 WebView 时自动关闭，点击按钮可重新开启

### 5.7 导出

点击"导出"按钮弹出 `FileChooser`（SaveDialog），将当前 LogBuffer 中所有行的 `raw` 字段拼接写入用户选择的文件。

### 5.8 清除

点击"清除"按钮：
1. 清空 WebView DOM（`document.getElementById('log-container').innerHTML = ''`）
2. 清空 LogBuffer
3. 行数归零

---

## 6. MainStage 集成

### 6.1 setupPages() 变更

```java
// 在 setupPages() 中添加：
pages.put("devconsole", new DevConsolePage());
```

### 6.2 页面生命周期管理

由于 DevConsole 不需要 BackendBridge（直接读文件），不需要在 `injectBridgeToPage()` 中添加。

页面切换通过现有 `showPage()` 机制（`setVisible`/`setManaged`）实现。DevConsolePage 需监听可见性变化：
- 变为可见时：启动 LogWatcher，resume 从 buffer 消费
- 变为不可见时：不停止 LogWatcher（后台继续收集），暂停 UI 更新

---

## 7. 错误处理

| 场景 | 处理 |
|------|------|
| 日志目录不存在 | LogWatcher 自动创建 `~/.javaclawbot/logs/` |
| 日志文件不存在 | 创建空文件，等待 logback 首次写入 |
| WatchService 事件丢失 | buffer 消费时做一次全量检查（对比文件大小和已读位置） |
| WebView 加载失败 | 降级为 TextArea（简化但可用） |
| UI 线程阻塞 | 使用 Platform.runLater 异步更新，LogBuffer 作为缓冲 |

---

## 8. 测试要点

1. 启动应用，打开 DevConsole → 能看到启动日志流
2. 切换到其他页面再切回 → 日志继续显示（buffer 保留）
3. 过滤 INFO/ERROR → 只显示对应级别日志
4. 搜索关键词 → 高亮显示，自动滚动到匹配项
5. 手动滚动 WebView → 自动滚动自动关闭
6. 点击"导出" → 选择路径，文件成功写入
7. 点击"清除" → 页面清空，行数归零
8. 5000 行后 → 旧行自动丢弃
