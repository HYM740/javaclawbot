# DevConsole 实施计划

> **对于代理工作者：** 必需的子技能：使用 zjkycode:subagent-driven-development（推荐）或 zjkycode:executing-plans 来逐任务实施此计划。步骤使用复选框（`- [ ]`）语法进行跟踪。

**目标：** 实现 DevConsole 页面，通过 logback.xml 输出日志文件，用 WebView 实时展示、过滤、搜索高亮。

**架构：** 新建 logback.xml → 日志写入 `~/.javaclawbot/logs/app.log`；新增 LogWatcher 后台线程用 WatchService 增量读取；新增 DevConsolePage 用 WebView + JS 渲染日志，提供级别过滤、搜索高亮、导出清除。

**技术栈：** Java 17, JavaFX 17.0.2, Logback 1.2.11, SLF4J, WebView + JavaScript

---

### 任务 1：创建 logback.xml 配置文件

**文件：**
- 创建：`src/main/resources/logback.xml`

- [ ] **步骤 1：编写 logback.xml**

```xml
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

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

- [ ] **步骤 2：编译验证**

运行：`mvn compile -q`
预期：编译成功，无错误

- [ ] **步骤 3：提交**

```bash
git add src/main/resources/logback.xml
git commit -m "feat: add logback.xml with FileAppender for DevConsole log source"
```

---

### 任务 2：创建 LogEntry 数据类

**文件：**
- 创建：`src/main/java/gui/ui/LogEntry.java`

- [ ] **步骤 1：编写 LogEntry record**

```java
package gui.ui;

/**
 * 日志条目，由 LogWatcher 从日志文件解析产生。
 */
public record LogEntry(
    String timestamp,   // "10:23:45.123"
    String level,       // "INFO", "WARN", "ERROR", "DEBUG", "TRACE"
    String logger,      // "agent.AgentLoop"
    String message,     // "Agent 循环已启动"
    String raw          // 原始行（用于导出）
) {}
```

- [ ] **步骤 2：编译验证**

运行：`mvn compile -q`
预期：编译成功

- [ ] **步骤 3：提交**

```bash
git add src/main/java/gui/ui/LogEntry.java
git commit -m "feat: add LogEntry record for log line representation"
```

---

### 任务 3：创建 LogWatcher 后台线程

**文件：**
- 创建：`src/main/java/gui/ui/LogWatcher.java`

- [ ] **步骤 1：编写 LogWatcher 类**

```java
package gui.ui;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 后台线程，用 WatchService 监听日志文件增量，
 * 解析为 LogEntry 并推入 LogBuffer。
 */
public class LogWatcher {

    private static final Pattern LOG_PATTERN =
        Pattern.compile("^(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(TRACE|DEBUG|INFO|WARN|ERROR)\\s+(\\S+)\\s+-\\s+(.*)$");

    private final Path logFile;
    private final ConcurrentLinkedQueue<LogEntry> buffer;
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private RandomAccessFile raf;
    private WatchService watchService;
    private Thread thread;

    public LogWatcher(ConcurrentLinkedQueue<LogEntry> buffer) {
        this.buffer = buffer;
        String home = System.getProperty("user.home");
        Path logDir = Paths.get(home, ".javaclawbot", "logs");
        this.logFile = logDir.resolve("app.log");
    }

    /**
     * 启动后台监听线程。
     */
    public void start() {
        stopped.set(false);
        thread = new Thread(this::run, "log-watcher");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 停止监听，释放资源。
     */
    public void stop() {
        stopped.set(true);
        if (thread != null) {
            thread.interrupt();
        }
        closeFile();
        closeWatcher();
    }

    public boolean isStopped() {
        return stopped.get();
    }

    private void run() {
        try {
            ensureDirAndFile();
            openFile();
            registerWatcher();
            mainLoop();
        } catch (Exception e) {
            // 将 LogWatcher 自身错误也推入 buffer
            buffer.offer(new LogEntry(
                java.time.LocalTime.now().toString().substring(0, 12),
                "ERROR", "LogWatcher",
                "LogWatcher 异常: " + e.getMessage(),
                "ERROR LogWatcher - LogWatcher 异常: " + e.getMessage()
            ));
        } finally {
            closeFile();
            closeWatcher();
        }
    }

    private void ensureDirAndFile() throws IOException {
        Path logDir = logFile.getParent();
        if (!Files.exists(logDir)) {
            Files.createDirectories(logDir);
        }
        if (!Files.exists(logFile)) {
            Files.createFile(logFile);
        }
    }

    private void openFile() throws IOException {
        raf = new RandomAccessFile(logFile.toFile(), "r");
    }

    private void registerWatcher() throws IOException {
        watchService = FileSystems.getDefault().newWatchService();
        logFile.getParent().register(watchService,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_CREATE);
    }

    private void mainLoop() {
        while (!stopped.get()) {
            try {
                WatchKey key = watchService.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (key != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        Path changed = (Path) event.context();
                        if (changed != null && changed.toString().equals(logFile.getFileName().toString())) {
                            readNewLines();
                        }
                    }
                    key.reset();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // 读取异常不中断主循环
            }
        }
    }

    private void readNewLines() throws IOException {
        if (raf == null) return;
        // 如果 raf 被关闭（stop 时），跳过
        String line;
        while ((line = raf.readLine()) != null) {
            LogEntry entry = parseLine(line);
            if (entry != null) {
                buffer.offer(entry);
            }
        }
    }

    private LogEntry parseLine(String line) {
        if (line == null || line.isBlank()) return null;
        Matcher m = LOG_PATTERN.matcher(line);
        if (m.matches()) {
            return new LogEntry(m.group(1), m.group(2), m.group(3), m.group(4), line);
        }
        // 无法匹配标准格式的行，作为 INFO 处理
        return new LogEntry(
            java.time.LocalTime.now().toString().substring(0, 12),
            "INFO", "unknown", line, line);
    }

    private void closeFile() {
        try {
            if (raf != null) {
                raf.close();
                raf = null;
            }
        } catch (IOException ignored) {}
    }

    private void closeWatcher() {
        try {
            if (watchService != null) {
                watchService.close();
                watchService = null;
            }
        } catch (IOException ignored) {}
    }
}
```

- [ ] **步骤 2：编译验证**

运行：`mvn compile -q`
预期：编译成功

- [ ] **步骤 3：提交**

```bash
git add src/main/java/gui/ui/LogWatcher.java
git commit -m "feat: add LogWatcher for real-time log file tailing"
```

---

### 任务 4：创建 DevConsolePage 页面

**文件：**
- 创建：`src/main/java/gui/ui/pages/DevConsolePage.java`

- [ ] **步骤 1：编写 DevConsolePage 类**

```java
package gui.ui.pages;

import gui.ui.LogEntry;
import gui.ui.LogWatcher;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

public class DevConsolePage extends VBox {

    private static final int MAX_BUFFER_SIZE = 5000;

    private final ConcurrentLinkedQueue<LogEntry> logBuffer = new ConcurrentLinkedQueue<>();
    private final LogWatcher logWatcher = new LogWatcher(logBuffer);

    private WebView webView;
    private WebEngine engine;
    private Label lineCountLabel;
    private Label statusLabel;

    private ComboBox<String> levelFilter;
    private TextField searchField;
    private ToggleButton autoScrollBtn;

    private Timeline uiTimer;
    private boolean autoScroll = true;

    public DevConsolePage() {
        setSpacing(0);
        setStyle("-fx-background-color: #f1ede1;");

        // Toolbar
        HBox toolbar = createToolbar();

        // WebView
        webView = createWebView();

        // StatusBar
        HBox statusBar = createStatusBar();

        getChildren().addAll(toolbar, webView, statusBar);
        VBox.setVgrow(webView, Priority.ALWAYS);

        // 页面可见时启停 LogWatcher 和 UI 定时器
        visibleProperty().addListener((obs, old, visible) -> {
            if (visible) {
                startLogWatcher();
                startUITimer();
            } else {
                stopUITimer();
                // LogWatcher 保持运行（后台收集）
            }
        });
    }

    // ========== Toolbar ==========

    private HBox createToolbar() {
        HBox bar = new HBox(8);
        bar.setPadding(new Insets(10, 16, 10, 16));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: #eae8e1; -fx-border-color: rgba(0,0,0,0.06); -fx-border-width: 0 0 1px 0;");

        // 级别过滤
        Label filterLabel = new Label("级别:");
        filterLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: rgba(0,0,0,0.5);");
        levelFilter = new ComboBox<>();
        levelFilter.getItems().addAll("ALL", "TRACE", "DEBUG", "INFO", "WARN", "ERROR");
        levelFilter.setValue("ALL");
        levelFilter.setPrefWidth(90);
        levelFilter.setStyle("-fx-background-color: white; -fx-background-radius: 6px; -fx-border-color: transparent; -fx-font-size: 12px;");
        levelFilter.setOnAction(e -> {
            String level = levelFilter.getValue();
            if (engine != null) {
                Platform.runLater(() -> engine.executeScript("setFilter('" + level + "')"));
            }
        });

        // 搜索框
        searchField = new TextField();
        searchField.setPromptText("搜索日志...");
        searchField.setPrefWidth(200);
        searchField.setStyle("-fx-background-color: white; -fx-background-radius: 6px; -fx-border-color: transparent; -fx-font-size: 12px; -fx-padding: 6px 10px;");
        searchField.textProperty().addListener((obs, old, text) -> {
            if (engine != null) {
                Platform.runLater(() -> engine.executeScript("search('" + escapeJs(text) + "')"));
            }
        });

        // 自动滚动
        autoScrollBtn = new ToggleButton("自动滚动");
        autoScrollBtn.setSelected(true);
        autoScrollBtn.setStyle("-fx-background-color: white; -fx-background-radius: 6px; -fx-border-color: transparent; -fx-font-size: 12px; -fx-padding: 6px 10px;");
        autoScrollBtn.selectedProperty().addListener((obs, old, sel) -> {
            autoScroll = sel;
            if (engine != null) {
                Platform.runLater(() -> engine.executeScript("setAutoScroll(" + sel + ")"));
            }
        });

        // 导出
        Button exportBtn = new Button("导出");
        exportBtn.setStyle("-fx-background-color: white; -fx-background-radius: 6px; -fx-border-color: transparent; -fx-font-size: 12px; -fx-padding: 6px 12px;");
        exportBtn.setOnAction(e -> exportLogs());

        // 清除
        Button clearBtn = new Button("清除");
        clearBtn.setStyle("-fx-background-color: white; -fx-background-radius: 6px; -fx-border-color: transparent; -fx-font-size: 12px; -fx-padding: 6px 12px;");
        clearBtn.setOnAction(e -> clearLogs());

        bar.getChildren().addAll(filterLabel, levelFilter, searchField, autoScrollBtn, exportBtn, clearBtn);
        return bar;
    }

    // ========== WebView ==========

    private WebView createWebView() {
        WebView wv = new WebView();
        wv.setStyle("-fx-background-color: #f8f6f0;");
        engine = wv.getEngine();

        String html = loadHtmlTemplate();
        engine.loadContent(html);

        return wv;
    }

    private String loadHtmlTemplate() {
        return "<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"UTF-8\">\n<style>\n" +
            "  body { background: #f8f6f0; font-family: 'Consolas', 'Courier New', monospace;\n" +
            "    font-size: 13px; line-height: 1.6; margin: 0; padding: 8px; }\n" +
            "  .log-line { white-space: pre-wrap; word-break: break-all; padding: 2px 8px; }\n" +
            "  .log-line .level-tag { display: inline-block; padding: 0px 6px; border-radius: 3px;\n" +
            "    margin-right: 4px; font-weight: 600; font-size: 12px; }\n" +
            "  .log-line.ERROR { background: #fee2e2; }\n" +
            "  .log-line.ERROR .level-tag { background: #dc2626; color: #fff; }\n" +
            "  .log-line.WARN  { background: #fef3c7; }\n" +
            "  .log-line.WARN  .level-tag { background: #f59e0b; color: #fff; }\n" +
            "  .log-line.INFO  { color: #374151; }\n" +
            "  .log-line.INFO  .level-tag { background: #dbeafe; color: #1e40af; }\n" +
            "  .log-line.DEBUG { color: #6b7280; }\n" +
            "  .log-line.DEBUG .level-tag { color: #6b7280; font-weight: 400; }\n" +
            "  .log-line.TRACE { color: #9ca3af; }\n" +
            "  .log-line.TRACE .level-tag { color: #9ca3af; font-weight: 400; }\n" +
            "  .highlight { background: #fde68a; border-radius: 2px; }\n" +
            "</style>\n</head>\n<body>\n<div id=\"log-container\"></div>\n<script>\n" +
            "  var MAX_LINES = " + MAX_BUFFER_SIZE + ";\n" +
            "  var currentLevel = 'ALL';\n" +
            "  var searchTerm = '';\n" +
            "  var autoScroll = true;\n" +
            "  function appendLog(ts, level, logger, msg) {\n" +
            "    if (currentLevel !== 'ALL' && level !== currentLevel) return;\n" +
            "    var line = document.createElement('div');\n" +
            "    line.className = 'log-line ' + level;\n" +
            "    line.innerHTML = '[' + escapeHtml(ts) + '] ' +\n" +
            "      '<span class=\"level-tag\">' + level + '</span> ' +\n" +
            "      escapeHtml(logger) + ' - ' + escapeHtml(msg);\n" +
            "    applyHighlight(line);\n" +
            "    document.getElementById('log-container').appendChild(line);\n" +
            "    trimLines();\n" +
            "    if (autoScroll) window.scrollTo(0, document.body.scrollHeight);\n" +
            "  }\n" +
            "  function setFilter(level) {\n" +
            "    currentLevel = level;\n" +
            "    var lines = document.querySelectorAll('.log-line');\n" +
            "    for (var i = 0; i < lines.length; i++) {\n" +
            "      var cls = lines[i].classList;\n" +
            "      lines[i].style.display = (level === 'ALL' || cls.contains(level)) ? '' : 'none';\n" +
            "    }\n" +
            "    if (autoScroll) window.scrollTo(0, document.body.scrollHeight);\n" +
            "  }\n" +
            "  function setAutoScroll(val) { autoScroll = val; }\n" +
            "  function search(keyword) {\n" +
            "    searchTerm = keyword;\n" +
            "    var lines = document.querySelectorAll('.log-line');\n" +
            "    for (var i = 0; i < lines.length; i++) { applyHighlight(lines[i]); }\n" +
            "    if (keyword) {\n" +
            "      for (var i = 0; i < lines.length; i++) {\n" +
            "        if (lines[i].style.display !== 'none' &&\n" +
            "            lines[i].textContent.toLowerCase().indexOf(keyword.toLowerCase()) >= 0) {\n" +
            "          lines[i].scrollIntoView({block: 'center'});\n" +
            "          break;\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "  function applyHighlight(el) {\n" +
            "    if (!searchTerm) return;\n" +
            "    var text = el.textContent;\n" +
            "    var idx = text.toLowerCase().indexOf(searchTerm.toLowerCase());\n" +
            "    if (idx >= 0) {\n" +
            "      el.innerHTML = escapeHtml(text.substring(0, idx)) +\n" +
            "        '<span class=\"highlight\">' +\n" +
            "        escapeHtml(text.substring(idx, idx + searchTerm.length)) +\n" +
            "        '</span>' + escapeHtml(text.substring(idx + searchTerm.length));\n" +
            "    }\n" +
            "  }\n" +
            "  function escapeHtml(s) {\n" +
            "    return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');\n" +
            "  }\n" +
            "  function trimLines() {\n" +
            "    var container = document.getElementById('log-container');\n" +
            "    while (container.children.length > MAX_LINES) {\n" +
            "      container.removeChild(container.firstChild);\n" +
            "    }\n" +
            "  }\n" +
            "  function getSelectedText() { return window.getSelection().toString(); }\n" +
            "  function clearAll() { document.getElementById('log-container').innerHTML = ''; }\n" +
            "  function getLineCount() { return document.getElementById('log-container').children.length; }\n" +
            "</script>\n</body>\n</html>";
    }

    // ========== StatusBar ==========

    private HBox createStatusBar() {
        HBox bar = new HBox(16);
        bar.setPadding(new Insets(6, 16, 6, 16));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: #eae8e1; -fx-border-color: rgba(0,0,0,0.06); -fx-border-width: 1px 0 0 0;");

        Label fileLabel = new Label("\uD83D\uDCC4 ~/.javaclawbot/logs/app.log");
        fileLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(0,0,0,0.5);");

        lineCountLabel = new Label("共 0 行");
        lineCountLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(0,0,0,0.5);");

        statusLabel = new Label("\u25CF 未启动");
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");

        bar.getChildren().addAll(fileLabel, lineCountLabel, statusLabel);
        return bar;
    }

    // ========== Lifecycle ==========

    private void startLogWatcher() {
        if (!logWatcher.isStopped() && statusLabel.getText().contains("监听中")) return;
        logWatcher.start();
        Platform.runLater(() -> {
            statusLabel.setText("\u25CF 监听中");
            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #16a34a;");
        });
    }

    private void startUITimer() {
        if (uiTimer != null) return;
        uiTimer = new Timeline(new KeyFrame(Duration.millis(60), e -> {
            while (logBuffer.peek() != null) {
                LogEntry entry = logBuffer.poll();
                if (entry != null && engine != null) {
                    engine.executeScript("appendLog('" + escapeJs(entry.timestamp()) + "','"
                        + entry.level() + "','" + escapeJs(entry.logger()) + "','"
                        + escapeJs(entry.message()) + "')");
                }
            }
            // 更新行数
            if (engine != null) {
                Object count = engine.executeScript("getLineCount()");
                if (count instanceof Number) {
                    lineCountLabel.setText("共 " + ((Number) count).intValue() + " 行");
                }
            }
        }));
        uiTimer.setCycleCount(Timeline.INDEFINITE);
        uiTimer.play();
    }

    private void stopUITimer() {
        if (uiTimer != null) {
            uiTimer.stop();
            uiTimer = null;
        }
    }

    // ========== Actions ==========

    private void exportLogs() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("导出日志");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("日志文件", "*.log"));
        chooser.setInitialFileName("app.log");
        File file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) return;

        try (FileWriter fw = new FileWriter(file)) {
            // 获取当前 WebView 中的所有行文本
            if (engine != null) {
                Object text = engine.executeScript(
                    "Array.from(document.querySelectorAll('.log-line')).map(function(e){return e.textContent;}).join('\\n')");
                if (text instanceof String) {
                    fw.write((String) text);
                }
            }
        } catch (IOException ex) {
            // 导出失败静默处理
        }
    }

    private void clearLogs() {
        logBuffer.clear();
        if (engine != null) {
            Platform.runLater(() -> {
                engine.executeScript("clearAll()");
                lineCountLabel.setText("共 0 行");
            });
        }
    }

    // ========== Util ==========

    private static String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
```

- [ ] **步骤 2：编译验证**

运行：`mvn compile -q`
预期：编译成功

- [ ] **步骤 3：提交**

```bash
git add src/main/java/gui/ui/pages/DevConsolePage.java
git commit -m "feat: add DevConsolePage with WebView log viewer, filtering, search and export"
```

---

### 任务 5：在 MainStage 中注册 DevConsole 页面

**文件：**
- 修改：`src/main/java/gui/ui/MainStage.java`

- [ ] **步骤 1：在 setupPages() 中注册页面**

在 `MainStage.java` 的 `setupPages()` 方法中，在 `pages.put("crontasks", new CronPage());` 下方添加一行：

```java
pages.put("devconsole", new DevConsolePage());
```

具体修改位置：`MainStage.java` 第 301 行后。修改后 `setupPages()` 中页面注册部分变为：

```java
// 创建所有页面
chatPage = new ChatPage();
pages.put("chat", chatPage);
pages.put("models", new ModelsPage());
pages.put("agents", new AgentsPage());
pages.put("channels", new ChannelsPage());
pages.put("skills", new SkillsPage());
pages.put("mcp", new McpPage(stage));
pages.put("crontasks", new CronPage());
pages.put("devconsole", new DevConsolePage());
pages.put("settings", new SettingsPage());
```

- [ ] **步骤 2：确保 import 语句存在**

检查 `MainStage.java` 头部是否有 `import gui.ui.pages.DevConsolePage;`。由于 pages 包在 `import gui.ui.pages.*;` 已通配导入（第 9 行），无需额外 import。

- [ ] **步骤 3：编译验证**

运行：`mvn compile -q`
预期：编译成功，无错误

- [ ] **步骤 4：提交**

```bash
git add src/main/java/gui/ui/MainStage.java
git commit -m "feat: register DevConsolePage in MainStage sidebar navigation"
```

---

### 任务 6：集成测试 & 验证

**文件：** 无新建

- [ ] **步骤 1：编译整个项目**

运行：`mvn compile -q`
预期：BUILD SUCCESS

- [ ] **步骤 2：验证 logback.xml 被正确加载**

检查 `src/main/resources/logback.xml` 文件存在且内容正确。启动应用后检查 `~/.javaclawbot/logs/app.log` 是否生成。

- [ ] **步骤 3：手动验证 DevConsole 功能**

启动 GUI 应用后：
1. 点击侧栏 "Dev Console" → 页面显示白色终端风格的日志视图
2. 日志实时追加，状态栏显示 "● 监听中"
3. 选择级别过滤 → 仅显示对应级别日志
4. 搜索框输入关键词 → 高亮并跳转
5. 点击"导出" → 选择路径保存日志文件
6. 点击"清除" → 日志清空，行数归零

- [ ] **步骤 4：提交**

```bash
git add -A
git commit -m "test: verify DevConsole integration and log output"
```
