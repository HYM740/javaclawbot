package config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 配置热加载器
 *
 * 作用：
 * 1. 维护最近一次成功加载的配置快照
 * 2. 检查 config.json 是否发生变化
 * 3. 若新配置加载失败，则保留旧配置继续运行
 */
public final class ConfigReloader {

    private static final Logger log = LoggerFactory.getLogger(ConfigReloader.class);

    private final Path configPath;
    private volatile ConfigSchema.Config currentConfig;
    private volatile long lastModifiedMillis = -1L;
    private final AtomicLong version = new AtomicLong(0);
    private final ReentrantLock reloadLock = new ReentrantLock();

    public ConfigReloader(Path configPath) {
        this.configPath = Objects.requireNonNull(configPath, "configPath");
        this.currentConfig = ConfigIO.loadConfig(configPath);
        this.lastModifiedMillis = readLastModified(configPath);
        this.version.set(1L);
    }

    public ConfigSchema.Config getCurrentConfig() {
        ConfigSchema.Config cfg = currentConfig;
        return cfg != null ? cfg : new ConfigSchema.Config();
    }

    public long getVersion() {
        return version.get();
    }

    public boolean refreshIfChanged() {
        long nowModified = readLastModified(configPath);

        if (nowModified == lastModifiedMillis) {
            return false;
        }

        reloadLock.lock();
        try {
            long latest = readLastModified(configPath);
            if (latest == lastModifiedMillis) {
                return false;
            }

            try {
                ConfigSchema.Config newConfig = ConfigIO.loadConfig(configPath);
                if (newConfig == null) {
                    log.warn("Config reload returned null, keep previous config: {}", configPath);
                    return false;
                }

                this.currentConfig = newConfig;
                this.lastModifiedMillis = latest;
                long v = this.version.incrementAndGet();

                log.info("Config reloaded successfully. version={}, path={}", v, configPath);
                return true;
            } catch (Exception e) {
                log.warn("Failed to reload config, keep previous snapshot. path={}, error={}",
                        configPath, e.toString());
                return false;
            }
        } finally {
            reloadLock.unlock();
        }
    }

    private static long readLastModified(Path path) {
        try {
            if (path == null || Files.notExists(path)) {
                return -1L;
            }
            FileTime ft = Files.getLastModifiedTime(path);
            return ft.toMillis();
        } catch (Exception e) {
            return -1L;
        }
    }
}