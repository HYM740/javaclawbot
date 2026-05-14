package providers.cli;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import utils.GsonFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 项目注册表 - 管理项目名称到路径的映射
 *
 * 支持命令:
 * - /bind p1=/path/to/project [--main]
 * - /unbind p1
 * - /projects
 *
 * 主项目: 标记 main=true 的项目，主代理会读取该项目的 CODE-AGENT.md/CLAUDE.md
 */
@Slf4j
public class ProjectRegistry {

    /**
     * 项目信息
     */
    @Getter
    public static class ProjectInfo {
        private final String path;
        private final boolean main;
        private final LocalDateTime createdAt;

        public ProjectInfo(String path, boolean main) {
            this.path = path;
            this.main = main;
            this.createdAt = LocalDateTime.now();
        }

        public ProjectInfo(String path, boolean main, LocalDateTime createdAt) {
            this.path = path;
            this.main = main;
            this.createdAt = createdAt;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("path", path);
            map.put("main", main);
            map.put("createdAt", createdAt.toString());
            return map;
        }

        @SuppressWarnings("unchecked")
        public static ProjectInfo fromMap(Map<String, Object> map) {
            String path = (String) map.get("path");
            Boolean main = (Boolean) map.get("main");
            String createdAtStr = (String) map.get("createdAt");
            LocalDateTime createdAt = createdAtStr != null
                    ? LocalDateTime.parse(createdAtStr)
                    : LocalDateTime.now();
            return new ProjectInfo(path, main != null && main, createdAt);
        }
    }

    private final Map<String, ProjectInfo> projects = new ConcurrentHashMap<>();
    private final Path storagePath;
    private volatile boolean loaded = false;

    public ProjectRegistry(Path storagePath) {
        this.storagePath = storagePath;
    }

    /**
     * 绑定项目
     *
     * @param name 项目名称 (如 p1, web, main)
     * @param path 项目路径
     * @param main 是否设为主项目
     * @return true 如果成功
     */
    public boolean bind(String name, String path, boolean main) {
        if (name == null || name.isBlank()) {
            return false;
        }
        if (path == null || path.isBlank()) {
            return false;
        }

        // 规范化名称 (去除空格，转小写)
        String normalizedName = name.trim().toLowerCase();

        // 规范化路径
        String normalizedPath = Path.of(path.trim()).normalize().toString();

        // 验证路径是否存在
        if (!Files.exists(Path.of(normalizedPath))) {
            log.warn("Project path does not exist: {}", normalizedPath);
            // 仍然允许绑定，因为路径可能稍后创建
        }

        // 如果设置为 main，先清除其他项目的 main 标记
        if (main) {
            clearMainFlag();
        }

        projects.put(normalizedName, new ProjectInfo(normalizedPath, main));
        save();

        // 复制初始化模板文件到项目根目录（存在则跳过，异常不影响绑定）
        copyInitTemplates(normalizedPath);

        log.info("Project bound: {} -> {} (main={})", normalizedName, normalizedPath, main);
        return true;
    }

    /**
     * 绑定项目 (非主项目)
     */
    public boolean bind(String name, String path) {
        return bind(name, path, false);
    }

    /**
     * 设置项目为主项目
     */
    public boolean setMain(String name) {
        ProjectInfo info = projects.get(name.trim().toLowerCase());
        if (info == null) {
            return false;
        }

        clearMainFlag();
        projects.put(name.trim().toLowerCase(), new ProjectInfo(info.getPath(), true, info.getCreatedAt()));
        save();

        log.info("Project set as main: {}", name);
        return true;
    }

    /**
     * 清除所有项目的主项目标记
     */
    private void clearMainFlag() {
        for (Map.Entry<String, ProjectInfo> entry : projects.entrySet()) {
            if (entry.getValue().isMain()) {
                ProjectInfo old = entry.getValue();
                projects.put(entry.getKey(), new ProjectInfo(old.getPath(), false, old.getCreatedAt()));
            }
        }
    }

    /**
     * 获取主项目
     */
    public ProjectInfo getMainProject() {
        for (ProjectInfo info : projects.values()) {
            if (info.isMain()) {
                return info;
            }
        }
        return null;
    }

    /**
     * 获取主项目路径
     */
    public String getMainProjectPath() {
        ProjectInfo info = getMainProject();
        return info != null ? info.getPath() : null;
    }

    /**
     * 解绑项目
     *
     * @param name 项目名称
     * @return true 如果成功
     */
    public boolean unbind(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }

        String normalizedName = name.trim().toLowerCase();
        ProjectInfo removed = projects.remove(normalizedName);

        if (removed != null) {
            save();
            log.info("Project unbound: {} (was main={})", normalizedName, removed.isMain());
            return true;
        }

        return false;
    }

    /**
     * 获取项目路径
     *
     * @param name 项目名称
     * @return 项目路径，如果不存在返回 null
     */
    public String getPath(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        ProjectInfo info = projects.get(name.trim().toLowerCase());
        return info != null ? info.getPath() : null;
    }

    /**
     * 获取项目信息
     */
    public ProjectInfo getInfo(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return projects.get(name.trim().toLowerCase());
    }

    /**
     * 获取项目路径 (别名方法)
     */
    public String resolve(String name) {
        return getPath(name);
    }

    /**
     * 检查项目是否存在
     */
    public boolean exists(String name) {
        return getPath(name) != null;
    }

    /**
     * 列出所有项目
     */
    public Map<String, ProjectInfo> listAll() {
        return Collections.unmodifiableMap(new TreeMap<>(projects));
    }

    /**
     * 获取项目数量
     */
    public int size() {
        return projects.size();
    }

    /**
     * 持久化到文件
     */
    public synchronized void save() {
        if (storagePath == null) {
            return;
        }

        try {
            // 确保目录存在
            Path parent = storagePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            // 构建存储格式
            Map<String, Object> data = new LinkedHashMap<>();

            Map<String, Object> projectsData = new TreeMap<>();
            for (Map.Entry<String, ProjectInfo> entry : projects.entrySet()) {
                projectsData.put(entry.getKey(), entry.getValue().toMap());
            }
            data.put("projects", projectsData);
            data.put("updatedAt", LocalDateTime.now().toString());

            // 写入临时文件
            Path tempPath = storagePath.resolveSibling(storagePath.getFileName() + ".tmp");
            String json = GsonFactory.getGson().toJson(data);
            Files.writeString(tempPath, json);

            // 原子替换（先尝试 ATOMIC_MOVE，Windows 下目标文件存在时会失败，退化为普通替换）
            try {
                Files.move(tempPath, storagePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                // Windows 下当目标文件已存在时 ATOMIC_MOVE 不支持，退化为普通 REPLACE
                if (log.isDebugEnabled()) {
                    log.debug("ATOMIC_MOVE not supported, falling back to regular replace: {}", storagePath);
                }
                Files.move(tempPath, storagePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            if (log.isDebugEnabled()) {
                log.debug("Project registry saved to {}", storagePath);
            }
        } catch (IOException e) {
            log.error("Failed to save project registry", e);
        }
    }

    /**
     * 从文件加载
     */
    @SuppressWarnings("unchecked")
    public synchronized void load() {
        if (loaded || storagePath == null || !Files.exists(storagePath)) {
            loaded = true;
            return;
        }

        try {
            String json = Files.readString(storagePath);
            Map<String, Object> data = GsonFactory.getGson().fromJson(json, Map.class);

            if (data != null && data.containsKey("projects")) {
                Map<String, Object> loadedProjects = (Map<String, Object>) data.get("projects");
                projects.clear();

                for (Map.Entry<String, Object> entry : loadedProjects.entrySet()) {
                    try {
                        Map<String, Object> infoMap = (Map<String, Object>) entry.getValue();
                        ProjectInfo info = ProjectInfo.fromMap(infoMap);
                        projects.put(entry.getKey().toLowerCase(), info);
                    } catch (Exception e) {
                        log.warn("Failed to load project {}: {}", entry.getKey(), e.getMessage());
                    }
                }

                log.info("Loaded {} projects from {}", projects.size(), storagePath);
            }

            loaded = true;
        } catch (Exception e) {
            log.error("Failed to load project registry", e);
        }
    }

    /**
     * 重新加载
     */
    public void reload() {
        loaded = false;
        load();
    }

    /**
     * 清空所有项目
     */
    public void clear() {
        projects.clear();
        save();
    }

    /**
     * 将初始化模板文件复制到项目根目录。
     * 目标文件已存在则跳过，异常不影响绑定流程。
     */
    private void copyInitTemplates(String projectPath) {
        String[] templates = {"AGENTS.md", "CHANGELOG.md", "CLAUDE.md", "GUARDRAILS.md", ".gitnexusignore"};
        Path projectRoot = Path.of(projectPath);

        for (String template : templates) {
            String resourcePath = "/templates/init/" + template;
            try (InputStream is = ProjectRegistry.class.getResourceAsStream(resourcePath)) {
                if (is == null) {
                    log.warn("Init template not found on classpath: {}", resourcePath);
                    continue;
                }
                Path targetFile = projectRoot.resolve(template);
                if (Files.exists(targetFile)) {
                    log.info("Init template already exists, skipping: {}", targetFile);
                    continue;
                }
                Files.copy(is, targetFile);
                log.info("Init template copied: {}", targetFile);
            } catch (IOException e) {
                log.warn("Failed to copy init template {}: {}", template, e.getMessage());
            }
        }
    }

    /**
     * 格式化项目列表为字符串
     */
    public String formatList() {
        if (projects.isEmpty()) {
            return "📁 暂无绑定项目\n\n" +
                    "使用 /bind <名称>=<路径> [--main] 绑定项目\n" +
                    "示例:\n" +
                    "  /bind p1=/home/user/project\n" +
                    "  /bind p1=/home/user/project --main  (设为主项目)\n" +
                    "  /bind --main /home/user/project     (直接设为主项目)";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📁 已绑定项目 (").append(projects.size()).append("):\n");

        // 先显示 main 项目
        projects.entrySet().stream()
                .sorted((a, b) -> {
                    // main 项目排在前面
                    if (a.getValue().isMain() && !b.getValue().isMain()) return -1;
                    if (!a.getValue().isMain() && b.getValue().isMain()) return 1;
                    return a.getKey().compareTo(b.getKey());
                })
                .forEach(entry -> {
                    ProjectInfo info = entry.getValue();
                    sb.append("  • ").append(entry.getKey());
                    if (info.isMain()) {
                        sb.append(" ⭐ [主项目]");
                    }
                    sb.append(" → ").append(info.getPath());
                    // 检查路径是否存在
                    if (!Files.exists(Path.of(info.getPath()))) {
                        sb.append(" ⚠️ (路径不存在)");
                    }
                    sb.append("\n");
                });

        sb.append("\n命令:\n");
        sb.append("  /cc <项目> <提示词>     - 使用 Claude Code\n");
        sb.append("  /oc <项目> <提示词>     - 使用 OpenCode\n");
        sb.append("  /stop <项目> [类型]     - 停止 Agent\n");
        sb.append("  /stopall                - 停止所有 Agent");

        return sb.toString();
    }

    /**
     * 格式化项目列表为字符串
     */
    public String formatProject() {
        if (projects.isEmpty()) {
            return "📁 暂无绑定项目\n\n" +
                    "使用 /bind <名称>=<路径> [--main] 绑定项目\n" +
                    "示例:\n" +
                    "  /bind p1=/home/user/project\n" +
                    "  /bind p1=/home/user/project --main  (设为主项目)\n" +
                    "  /bind --main /home/user/project     (直接设为主项目)";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📁 已绑定项目 (").append(projects.size()).append("):\n");

        // 先显示 main 项目
        projects.entrySet().stream()
                .sorted((a, b) -> {
                    // main 项目排在前面
                    if (a.getValue().isMain() && !b.getValue().isMain()) return -1;
                    if (!a.getValue().isMain() && b.getValue().isMain()) return 1;
                    return a.getKey().compareTo(b.getKey());
                })
                .forEach(entry -> {
                    ProjectInfo info = entry.getValue();
                    sb.append("  • ").append(entry.getKey());
                    if (info.isMain()) {
                        sb.append(" ⭐ [主项目]");
                    }
                    sb.append(" → ").append(info.getPath());
                    // 检查路径是否存在
                    if (!Files.exists(Path.of(info.getPath()))) {
                        sb.append(" ⚠️ (路径不存在)");
                    }
                    sb.append("\n");
                });

        return sb.toString();
    }
}


