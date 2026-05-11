package gui.ui;

public class Launcher {
    public static void main(String[] args) {
        // 确保日志目录存在（logback FileAppender 不会自动创建父目录）
        try {
            java.nio.file.Files.createDirectories(
                java.nio.file.Path.of(System.getProperty("user.home"), ".javaclawbot", "logs"));
        } catch (java.io.IOException ignored) {}
        // JavaFX 在 unnamed module（shaded jar）中无法自动检测渲染管线，
        // 需要根据平台手动设置 prism.order
        // 使用软件渲染（sw）避免 WebView 偶发 NPE：WCPageBackBufferImpl.texture 为 null
        if (System.getProperty("prism.order") == null) {
            System.setProperty("prism.order", "sw");
        }
        JavaClawBotApp.launchApp(args);
    }
}
