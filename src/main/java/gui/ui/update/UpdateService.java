package gui.ui.update;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 自动更新服务：检查版本 → 下载 JAR → 直接替换 → 提示重启
 * <p>
 * JVM 已将 JAR 映射到内存，磁盘上的替换不影响当前运行，下次启动生效。
 */
@Slf4j
public class UpdateService {

    private static final String UPDATE_INFO_URL =
        "http://101.68.93.109:9102/agent/releases/latest/info";
    private static final String FALLBACK_VERSION = "dev";

    private final HttpClient http;
    private final ObjectMapper mapper;

    public UpdateService() {
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        this.mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 检查是否有可用更新。向服务端请求最新版本信息并与当前版本比较。
     *
     * @return UpdateInfo 如果有新版本；null 表示已是最新
     * @throws IOException          网络错误
     * @throws InterruptedException 线程中断
     */
    public UpdateInfo checkForUpdates() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(UPDATE_INFO_URL))
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build();

        HttpResponse<String> response = http.send(request,
            HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            if (log.isDebugEnabled()) {
                log.debug("更新检查失败，HTTP {}: {}", response.statusCode(),
                    response.body());
            }
            throw new IOException("服务器返回 " + response.statusCode());
        }

        UpdateInfo info = mapper.readValue(response.body(), UpdateInfo.class);

        // 验证必要字段
        if (info.getVersion() == null || info.getVersion().isBlank()) {
            log.warn("服务端返回的版本信息无效: version 为空");
            throw new IOException("服务器返回的版本信息无效");
        }
        if (info.getUrl() == null || info.getUrl().isBlank()) {
            log.warn("服务端返回的更新信息缺少下载 URL: version={}", info.getVersion());
            throw new IOException("服务器返回的下载地址无效");
        }

        String current = getCurrentVersion();

        if (log.isDebugEnabled()) {
            log.debug("检查更新: 服务端 v{} vs 当前 v{}", info.getVersion(), current);
        }

        if (compareVersions(info.getVersion(), current) > 0) {
            log.info("发现新版本: v{} (当前 v{})", info.getVersion(), current);
            return info;
        }
        return null;
    }

    /**
     * 下载 JAR 并替换当前运行的应用 JAR 文件。
     *
     * @param info             更新信息
     * @param progressCallback 下载进度回调，值范围 [0.0, 1.0]，可不传 null
     * @throws Exception IO 错误
     */
    public void downloadAndReplace(UpdateInfo info,
            Consumer<Double> progressCallback) throws Exception {
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"),
            "nexusai-update");
        Files.createDirectories(tempDir);
        Path tempJar = tempDir.resolve("NexusAI.jar");

        if (log.isDebugEnabled()) {
            log.debug("开始下载: {} → {}", info.getUrl(), tempJar);
        }

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(info.getUrl()))
            .timeout(Duration.ofMinutes(10))
            .GET()
            .build();

        HttpResponse<InputStream> response = http.send(request,
            HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IOException("下载失败，HTTP " + response.statusCode());
        }

        long totalSize = info.getSize() > 0
            ? info.getSize()
            : response.headers().firstValueAsLong("Content-Length").orElse(-1);

        try (InputStream in = response.body();
             OutputStream out = Files.newOutputStream(tempJar)) {
            long downloaded = 0;
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                downloaded += bytesRead;
                if (totalSize > 0 && progressCallback != null) {
                    progressCallback.accept((double) downloaded / totalSize);
                }
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("下载完成: {} bytes", Files.size(tempJar));
        }

        Path appJar = getAppJarPath();
        if (log.isDebugEnabled()) {
            log.debug("替换 JAR: {} → {}", tempJar, appJar);
        }

        boolean isWindows = System.getProperty("os.name").toLowerCase()
            .contains("win");

        if (isWindows) {
            // Windows 会锁定正在运行的 JAR，无法直接覆盖。
            // 保存为 .new 文件，由启动脚本在下次启动前自动替换。
            Path newJarPath = appJar.resolveSibling(
                appJar.getFileName().toString() + ".new");
            Files.copy(tempJar, newJarPath,
                StandardCopyOption.REPLACE_EXISTING);

            // 创建 apply-update.bat 辅助脚本
            createWindowsUpdateScript(appJar, newJarPath);
            log.info("Windows: 新 JAR 已保存到 {}，请重启应用",
                newJarPath.getFileName());
        } else {
            // Linux/macOS: 可直接替换运行中的 JAR
            Files.copy(tempJar, appJar,
                StandardCopyOption.REPLACE_EXISTING);
            log.info("更新就绪: {} → v{}", appJar.getFileName(),
                info.getVersion());
        }

        // 清理临时文件
        try {
            Files.deleteIfExists(tempJar);
        } catch (IOException ignored) {
            // 清理失败不影响主流程
        }
    }

    /**
     * 创建 Windows 更新辅助脚本 apply-update.bat。
     * 用户停止应用后运行此脚本完成文件替换。
     */
    private void createWindowsUpdateScript(Path appJar, Path newJar)
        throws IOException {
        Path scriptPath = appJar.resolveSibling("apply-update.bat");
        String appName = appJar.getFileName().toString();
        String newName = newJar.getFileName().toString();

        String script = "@echo off\r\n"
            + "echo ============================================\r\n"
            + "echo   NexusAI Update Helper\r\n"
            + "echo ============================================\r\n"
            + "echo.\r\n"
            + "echo Current JAR: " + appName + "\r\n"
            + "echo New JAR:     " + newName + "\r\n"
            + "echo.\r\n"
            + "echo Make sure NexusAI is stopped before continuing.\r\n"
            + "echo.\r\n"
            + "choice /c YN /m \"Apply update now?\"\r\n"
            + "if errorlevel 2 goto :end\r\n"
            + "copy /Y \"%~dp0" + newName
            + "\" \"%~dp0" + appName + "\"\r\n"
            + "if not errorlevel 1 (\r\n"
            + "    echo Update applied successfully!\r\n"
            + "    del \"%~dp0" + newName + "\"\r\n"
            + "    del \"%~f0\"\r\n"
            + ") else (\r\n"
            + "    echo Failed to apply update - file may be in use.\r\n"
            + "    echo Stop NexusAI and try again, or manually copy:\r\n"
            + "    echo   " + newName + " -^> " + appName + "\r\n"
            + ")\r\n"
            + ":end\r\n"
            + "pause\r\n";

        Files.writeString(scriptPath, script);
        if (log.isDebugEnabled()) {
            log.debug("已创建更新辅助脚本: {}", scriptPath);
        }
    }

    /**
     * 获取当前运行的版本号。
     * 优先从 MANIFEST.MF 读取，其次通过 JarFile 直接读取，最后回退到 "dev"。
     */
    public static String getCurrentVersion() {
        // 方式 1：Package.getImplementationVersion()
        String version = UpdateService.class.getPackage()
            .getImplementationVersion();
        if (version != null && !version.isBlank()) {
            return version;
        }

        // 方式 2：直接从 JAR 的 MANIFEST.MF 读取（更可靠）
        try {
            Path jarPath = getAppJarPath();
            if (jarPath != null && Files.isRegularFile(jarPath)) {
                try (JarFile jarFile = new JarFile(jarPath.toFile())) {
                    Manifest manifest = jarFile.getManifest();
                    if (manifest != null) {
                        Attributes attrs = manifest.getMainAttributes();
                        version = attrs.getValue("Implementation-Version");
                        if (version != null && !version.isBlank()) {
                            return version;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // 开发环境或非 JAR 运行场景，使用回退值
        }

        return FALLBACK_VERSION;
    }

    /**
     * 获取当前运行应用的 JAR 文件路径。
     * 在 IDE 开发环境中可能返回 classes 目录路径。
     */
    public static Path getAppJarPath() throws Exception {
        return Path.of(UpdateService.class.getProtectionDomain()
            .getCodeSource().getLocation().toURI());
    }

    /**
     * 语义化版本比较（x.y.z 格式）。
     *
     * @return &gt;0 如果 v1 比 v2 新，&lt;0 如果 v1 旧，0 相同
     */
    public static int compareVersions(String v1, String v2) {
        if (v1 == null || v2 == null) {
            return 0;
        }

        // 去除前缀 'v' 和预发布后缀（如 -beta、-rc1）
        String clean1 = v1.replaceFirst("^v", "").split("-")[0];
        String clean2 = v2.replaceFirst("^v", "").split("-")[0];

        String[] parts1 = clean1.split("\\.");
        String[] parts2 = clean2.split("\\.");

        int len = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < len; i++) {
            int n1 = i < parts1.length ? parseOrZero(parts1[i]) : 0;
            int n2 = i < parts2.length ? parseOrZero(parts2[i]) : 0;
            if (n1 != n2) {
                return n1 - n2;
            }
        }
        return 0;
    }

    private static int parseOrZero(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
