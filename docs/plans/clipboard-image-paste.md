# ChatPage 剪贴板文件粘贴修复计划

## 问题描述

ChatPage 的输入区域（ChatInput 中的 TextArea）不支持粘贴剪贴板中的文件/图片。用户从截图工具、浏览器复制图片，或从资源管理器复制文件后，Ctrl+V / Cmd+V 不会将内容加入附件列表。

## 根因分析

`ChatInput` 构造函数（第 223 行）注册了 `KEY_PRESSED` 事件过滤器，但只处理了 Enter（发送）/ Esc（停止）逻辑，没有拦截粘贴事件去检测剪贴板内容。

JavaFX 的 `TextArea` 原生只处理文本粘贴，剪贴板中的文件列表和图像数据会被忽略。

JavaFX `Clipboard` 提供三种数据类型：
- `hasFiles()` / `getFiles()` — 资源管理器复制的文件（已有磁盘路径）
- `hasImage()` / `getImage()` — 截图工具、浏览器「复制图片」（原始像素数据）
- `hasString()` / `getString()` — 文本

## 数据流

```
用户 Ctrl+V / Cmd+V
  → ChatInput (KEY_PRESSED event filter)
    → 读取 Clipboard.getSystemClipboard()
      ├─ hasFiles()?
      │   → 遍历 getFiles()，每个调 handleFile(file.toPath())
      │   → consume() 事件
      ├─ hasImage()?
      │   → 保存为 PNG 到 tmp/javaclawbot/clipboard/
      │   → 调用 handleFile(tmpPath)
      │   → consume() 事件
      └─ 都没处理 → 不 consume，放行给 TextArea 正常粘贴文本
```

核心设计：剪贴板处理器只做「剪贴板 → 文件路径」的桥梁，文件分类和预览全部复用现有 `handleFile()` 方法。

## 修改范围

**仅修改 1 个文件**：`src/main/java/gui/ui/components/ChatInput.java`

### 修改点 1：构造函数中添加粘贴事件拦截

在 `inputArea.addEventFilter(KeyEvent.KEY_PRESSED, ...)` 中，`completionPopup.isShowing()` 检查之后，`Esc` 处理之前，添加：

```java
// 粘贴事件：检查剪贴板中的文件 / 图片
if (isPasteShortcut(e)) {
    if (handleClipboardPaste()) {
        e.consume();
        return;
    }
    // 剪贴板无文件/图片则放行，让 TextArea 正常处理文本粘贴
}
```

### 修改点 2：新增方法 `isPasteShortcut()`

```java
private static boolean isPasteShortcut(KeyEvent e) {
    if (System.getProperty("os.name", "").toLowerCase().contains("mac")) {
        return e.getCode() == KeyCode.V && e.isMetaDown();
    }
    return e.getCode() == KeyCode.V && e.isControlDown();
}
```

### 修改点 3：新增方法 `handleClipboardPaste()`

```java
private boolean handleClipboardPaste() {
    Clipboard clipboard = Clipboard.getSystemClipboard();
    boolean handled = false;

    // 资源管理器复制的文件（已有磁盘路径）
    if (clipboard.hasFiles()) {
        for (java.io.File f : clipboard.getFiles()) {
            handleFile(f.toPath());
        }
        handled = true;
    }

    // 截图工具 / 浏览器复制的原始图片数据
    if (clipboard.hasImage()) {
        Image fxImage = clipboard.getImage();
        if (fxImage != null) {
            try {
                Path tmpDir = Path.of(System.getProperty("java.io.tmpdir"), "javaclawbot", "clipboard");
                Files.createDirectories(tmpDir);
                Path tmpFile = tmpDir.resolve("clipboard_" + System.currentTimeMillis() + ".png");
                BufferedImage buffered = javafxImageToBuffered(fxImage);
                ImageIO.write(buffered, "png", tmpFile.toFile());
                handleFile(tmpFile);
                handled = true;
            } catch (Exception e) {
                log.warn("剪贴板图片保存失败", e);
            }
        }
    }

    return handled;
}
```

### 修改点 4：新增辅助方法 `javafxImageToBuffered()`

```java
private static BufferedImage javafxImageToBuffered(Image fxImage) {
    int w = (int) fxImage.getWidth();
    int h = (int) fxImage.getHeight();
    BufferedImage buffered = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    PixelReader reader = fxImage.getPixelReader();
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            buffered.setRGB(x, y, reader.getArgb(x, y));
        }
    }
    return buffered;
}
```

### 修改点 5：新增 import

```java
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
```

## 边界情况

| 场景 | 行为 |
|------|------|
| 剪贴板无内容 | 不拦截，TextArea 正常粘贴 |
| 剪贴板只有文本 | 不拦截，TextArea 正常插入文本 |
| 从资源管理器复制图片文件 | `hasFiles()` → `handleFile()` → 照片预览 |
| 从资源管理器复制视频文件 | `hasFiles()` → `handleFile()` → 显示"暂不支持" |
| 从资源管理器复制 PDF/ZIP 等 | `hasFiles()` → `handleFile()` → 文件标签 |
| 截图工具截图 | `hasImage()` → 临时 PNG → `handleFile()` → 照片预览 |
| 浏览器复制图片 | `hasImage()` → 临时 PNG → `handleFile()` → 照片预览 |
| 同时复制文本+图片 | 图片走 handleFile + 文本放行给 TextArea |
| 同时复制文本+文件 | 文件走 handleFile + 文本放行给 TextArea |
| 剪贴板图片保存失败 | 静默忽略，放行给 TextArea |

## 验证方法

1. 用系统截图工具截图，在输入框 Ctrl+V → 缩略图出现
2. 在资源管理器中复制任意文件（图片/视频/PDF/ZIP），Ctrl+V → 对应预览/标签出现
3. 复制纯文本，Ctrl+V → 文本正常插入输入框

## 后续考虑（本次不实现）

- 粘贴文件的临时目录清理策略（当前依赖 OS 临时目录回收）
- 视频文件的实际上传支持（当前 handleFile 中标记为暂不支持）
