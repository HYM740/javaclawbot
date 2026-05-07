package agent.tool.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import config.tool.DbDataSourceConfig;
import config.tool.DbToolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global singleton DataSource manager.
 * Manages HikariCP connection pools for multiple datasources.
 * Handles external JDBC driver loading from JAR files.
 */
public class DataSourceManager {

    private static final Logger log = LoggerFactory.getLogger(DataSourceManager.class);

    private final DbToolConfig config;
    private final ConcurrentHashMap<String, HikariDataSource> dataSources = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> dataSourceUrls = new ConcurrentHashMap<>();
    private volatile boolean started = false;

    /** Cached driver class loaders to avoid reloading JARs */
    private final Map<String, URLClassLoader> driverLoaders = new HashMap<>();
    /** Registered wrapper drivers to track for cleanup */
    private final List<DriverWrapper> registeredDrivers = new ArrayList<>();

    public DataSourceManager(DbToolConfig config) {
        this.config = config != null ? config : new DbToolConfig();
    }

    /**
     * Initialize all connection pools from config.
     */
    public synchronized void start() {
        if (started) return;
        log.info("DataSourceManager starting with {} datasource(s)", config.getDatasources().size());

        for (Map.Entry<String, DbDataSourceConfig> entry : config.getDatasources().entrySet()) {
            try {
                createDataSource(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                log.error("Failed to initialize datasource '{}': {}", entry.getKey(), e.getMessage(), e);
            }
        }

        started = true;
    }

    /**
     * Get a HikariDataSource by name.
     */
    public HikariDataSource getDataSource(String name) {
        return dataSources.get(name);
    }

    /**
     * Add a new datasource at runtime.
     */
    public synchronized void addDataSource(String name, DbDataSourceConfig cfg) {
        if (dataSources.containsKey(name)) {
            removeDataSource(name);
        }
        createDataSource(name, cfg);
        // Also update the config map for persistence
        config.getDatasources().put(name, cfg);
    }

    /**
     * Remove and shutdown a datasource.
     */
    public synchronized void removeDataSource(String name) {
        HikariDataSource ds = dataSources.remove(name);
        if (ds != null) {
            ds.close();
            log.info("Closed datasource '{}'", name);
        }
        dataSourceUrls.remove(name);
        config.getDatasources().remove(name);
    }

    /**
     * List all datasource names and their JDBC URLs.
     */
    public Map<String, String> listDataSources() {
        return new LinkedHashMap<>(dataSourceUrls);
    }

    /**
     * Shutdown all connection pools.
     */
    public synchronized void shutdown() {
        log.info("DataSourceManager shutting down {} datasource(s)", dataSources.size());
        for (Map.Entry<String, HikariDataSource> entry : dataSources.entrySet()) {
            entry.getValue().close();
            log.debug("Closed datasource '{}'", entry.getKey());
        }
        dataSources.clear();
        dataSourceUrls.clear();

        // Cleanup driver wrappers
        for (DriverWrapper dw : registeredDrivers) {
            try {
                DriverManager.deregisterDriver(dw);
            } catch (Exception e) {
                log.warn("Failed to deregister driver wrapper: {}", e.getMessage());
            }
        }
        registeredDrivers.clear();
        driverLoaders.clear();

        started = false;
        log.info("DataSourceManager shutdown complete");
    }

    private void createDataSource(String name, DbDataSourceConfig cfg) {
        Objects.requireNonNull(cfg.getJdbcUrl(), "jdbcUrl must not be null for datasource '" + name + "'");
        Objects.requireNonNull(cfg.getDriverClass(), "driverClass must not be null for datasource '" + name + "'");

        // Load external driver if specified
        boolean externalDriver = cfg.getDriverJar() != null && !cfg.getDriverJar().isBlank();
        if (externalDriver) {
            loadExternalDriver(cfg.getDriverClass(), cfg.getDriverJar());
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(cfg.getJdbcUrl());
        hikariConfig.setUsername(cfg.getUsername());
        hikariConfig.setPassword(cfg.getPassword());
        // For external drivers, skip setDriverClassName() — HikariCP discovers
        // the driver via DriverManager (which already has the DriverWrapper registered).
        // For built-in drivers, setDriverClassName() validates the class is loadable.
        if (!externalDriver) {
            hikariConfig.setDriverClassName(cfg.getDriverClass());
        }
        hikariConfig.setMaximumPoolSize(cfg.getMaxPoolSize());
        hikariConfig.setConnectionTimeout(cfg.getConnectionTimeout());
        // Essential settings
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        HikariDataSource ds = new HikariDataSource(hikariConfig);
        dataSources.put(name, ds);
        dataSourceUrls.put(name, cfg.getJdbcUrl());
        log.info("Initialized datasource '{}': {}", name, cfg.getJdbcUrl());
    }

    private void loadExternalDriver(String driverClass, String jarPath) {
        try {
            File jarFile = new File(jarPath);
            if (!jarFile.exists()) {
                log.warn("Driver JAR not found: {}, driver '{}' may not load", jarPath, driverClass);
                return;
            }

            String cacheKey = jarFile.getAbsolutePath();
            URLClassLoader loader = driverLoaders.get(cacheKey);
            if (loader == null) {
                URL jarUrl = jarFile.toURI().toURL();
                loader = new URLClassLoader(new URL[]{jarUrl}, ClassLoader.getPlatformClassLoader());
                driverLoaders.put(cacheKey, loader);
            }

            Class<?> clazz = loader.loadClass(driverClass);
            Driver driver = (Driver) clazz.getDeclaredConstructor().newInstance();
            DriverWrapper wrapper = new DriverWrapper(driver);
            DriverManager.registerDriver(wrapper);
            registeredDrivers.add(wrapper);
            log.info("Loaded external driver '{}' from {}", driverClass, jarPath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load external driver '" + driverClass + "' from " + jarPath, e);
        }
    }
}
