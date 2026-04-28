package gui.ui.components;

import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.web.WebView;

import java.util.List;

public class MessageBubble extends HBox {

    private static final double MAX_WIDTH = 700;

    private static final Parser PARSER;
    private static final HtmlRenderer RENDERER;
    private static final String HTML_TEMPLATE;

    static {
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, List.of(TablesExtension.create()));
        PARSER = Parser.builder(options).build();
        RENDERER = HtmlRenderer.builder(options).build();

        HTML_TEMPLATE = "<!DOCTYPE html><html><head><meta charset='UTF-8'>"
            + "<style>"
            + "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;"
            + "font-size:14px;line-height:1.6;color:#1c1c1e;background:transparent;margin:0;padding:12px 16px;}"
            + "pre{background:rgba(0,0,0,0.04);border:1px solid rgba(0,0,0,0.08);border-radius:8px;"
            + "padding:12px 16px;overflow-x:auto;font-family:'JetBrains Mono','Fira Code',monospace;font-size:13px;line-height:1.5;}"
            + "code{font-family:'JetBrains Mono','Fira Code',monospace;font-size:13px;"
            + "background:rgba(0,0,0,0.04);padding:2px 6px;border-radius:3px;}"
            + "pre code{background:transparent;padding:0;border-radius:0;}"
            + "blockquote{border-left:3px solid rgba(0,0,0,0.15);margin:8px 0;padding:4px 12px;"
            + "color:rgba(0,0,0,0.65);background:rgba(0,0,0,0.02);border-radius:0 4px 4px 0;}"
            + "h1{font-size:20px;font-weight:700;margin:12px 0 4px;}"
            + "h2{font-size:17px;font-weight:700;margin:10px 0 4px;}"
            + "h3{font-size:15px;font-weight:600;margin:8px 0 4px;}"
            + "ul,ol{padding-left:20px;margin:4px 0;}"
            + "li{margin:2px 0;}"
            + "a{color:#3b82f6;}"
            + "table{border-collapse:collapse;margin:8px 0;font-size:13px;}"
            + "th,td{border:1px solid rgba(0,0,0,0.1);padding:6px 12px;text-align:left;}"
            + "th{background:rgba(0,0,0,0.04);}"
            + "hr{border:none;border-top:1px solid rgba(0,0,0,0.08);margin:12px 0;}"
            + "p{margin:4px 0;}"
            + "img{max-width:100%;border-radius:8px;}"
            + "</style></head><body>%s</body></html>";
    }

    public enum Role { USER, ASSISTANT }

    public MessageBubble(Role role, String content) {
        setSpacing(12);
        setPadding(new Insets(8, 0, 8, 0));

        if (role == Role.USER) {
            setAlignment(Pos.CENTER_RIGHT);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label bubble = new Label(content);
            bubble.getStyleClass().add("user-bubble");
            bubble.setWrapText(true);
            bubble.setMaxWidth(MAX_WIDTH);

            getChildren().addAll(spacer, bubble);
        } else {
            setAlignment(Pos.CENTER_LEFT);
            setFillHeight(false);

            Label avatar = new Label("\u2728");
            avatar.setStyle("-fx-background-color: rgba(0, 0, 0, 0.05); -fx-background-radius: 999px;"
                + " -fx-pref-width: 32px; -fx-pref-height: 32px; -fx-alignment: center;");
            avatar.setMinSize(32, 32);

            // Flexmark: markdown → HTML
            String htmlBody = RENDERER.render(PARSER.parse(content));
            String html = HTML_TEMPLATE.replace("%s", htmlBody);

            WebView webView = new WebView();
            webView.setContextMenuEnabled(false);
            // 初始宽度基于内容估算（scene 监听器稍后精确调整）
            double initW = estimateContentWidth(content);
            webView.setPrefWidth(initW);
            webView.setMaxWidth(initW);
            webView.getEngine().loadContent(html);

            // 页面加载完成后 JS 自适应内容高度
            webView.getEngine().documentProperty().addListener((obs, old, doc) -> {
                if (doc != null) {
                    Platform.runLater(() -> adjustWebViewHeight(webView));
                }
            });

            // 气泡容器：背景 + 圆角（与 .assistant-bubble 一致）
            StackPane bubble = new StackPane(webView);
            bubble.setStyle("-fx-background-color: rgba(0,0,0,0.05);"
                + " -fx-background-radius: 16px;"
                + " -fx-padding: 0;");

            // clip 使圆角对 WebView（native 节点）也生效
            Rectangle clip = new Rectangle();
            clip.widthProperty().bind(bubble.widthProperty());
            clip.heightProperty().bind(bubble.heightProperty());
            clip.setArcWidth(32);
            clip.setArcHeight(32);
            bubble.setClip(clip);

            // 右侧 spacer：吸收多余空间
            Region rightSpacer = new Region();
            HBox.setHgrow(rightSpacer, Priority.ALWAYS);

            getChildren().addAll(avatar, bubble, rightSpacer);

            // 宽度根据可用空间自适应
            sceneProperty().addListener((obs, o, s) -> {
                if (s != null) {
                    updateBubbleWidth(webView, bubble, s.getWidth(), content);
                    s.widthProperty().addListener((wObs, wOld, wNew) -> {
                        updateBubbleWidth(webView, bubble, wNew.doubleValue(), content);
                        Platform.runLater(() -> adjustWebViewHeight(webView));
                    });
                }
            });
        }
    }

    /** 根据内容估算最佳宽度（模拟 Label wrapText 的"短则窄、长则宽"效果） */
    private static double estimateContentWidth(String content) {
        double maxLine = 0;
        for (String line : content.split("\n")) {
            double w = line.length() * 8.5; // 14px 中文字符近似宽度
            if (w > maxLine) maxLine = w;
        }
        return Math.min(MAX_WIDTH, Math.max(200, maxLine + 32)); // 32 = body padding
    }

    private static void updateBubbleWidth(WebView wv, StackPane bubble, double sceneWidth, String content) {
        double available = Math.min(MAX_WIDTH, Math.max(300,
            sceneWidth - 256 - 32 - 44));
        // WebView 宽度按内容估算（窄消息不撑满），上限取可用宽度
        double contentW = Math.min(available, estimateContentWidth(content));
        wv.setPrefWidth(contentW);
        wv.setMaxWidth(contentW);
        bubble.setMaxWidth(available);
    }

    private static void adjustWebViewHeight(WebView wv) {
        try {
            Object h = wv.getEngine().executeScript(
                "Math.max(document.body.scrollHeight, "
                + "document.documentElement.scrollHeight)");
            if (h instanceof Number) {
                wv.setPrefHeight(Math.min(
                    ((Number) h).doubleValue() + 16, 600));
            }
        } catch (Exception ignored) {}
    }
}
