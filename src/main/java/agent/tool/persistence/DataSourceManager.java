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

    /** JDBC URL prefix → Built-in driver class mapping */
    private static final Map<String, String> BUILTIN_DRIVERS = new LinkedHashMap<>();

    /** Driver class → human-readable database type name */
    private static final Map<String, String> DRIVER_TO_DB_TYPE = new LinkedHashMap<>();

    static {
        BUILTIN_DRIVERS.put("jdbc:mysql:", "com.mysql.cj.jdbc.Driver");
        BUILTIN_DRIVERS.put("jdbc:postgresql:", "org.postgresql.Driver");
        BUILTIN_DRIVERS.put("jdbc:mariadb:", "org.mariadb.jdbc.Driver");
        BUILTIN_DRIVERS.put("jdbc:oracle:thin:", "oracle.jdbc.OracleDriver");
        BUILTIN_DRIVERS.put("jdbc:sqlserver:", "com.microsoft.sqlserver.jdbc.SQLServerDriver");
        BUILTIN_DRIVERS.put("jdbc:h2:", "org.h2.Driver");
        BUILTIN_DRIVERS.put("jdbc:sqlite:", "org.sqlite.JDBC");

        for (Map.Entry<String, String> e : BUILTIN_DRIVERS.entrySet()) {
            String urlPrefix = e.getKey();
            // Strip "jdbc:" prefix and trailing ":"
            String type = urlPrefix.substring(5).replace(":", "");
            DRIVER_TO_DB_TYPE.put(e.getValue(), type);
        }
    }

    /** Infer driver class from JDBC URL prefix */
    public static String inferDriverClass(String jdbcUrl) {
        if (jdbcUrl == null) return null;
        for (Map.Entry<String, String> entry : BUILTIN_DRIVERS.entrySet()) {
            if (jdbcUrl.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
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
     * Infer database type name from JDBC URL.
     */
    public static String inferDbType(String jdbcUrl) {
        if (jdbcUrl == null) return null;
        for (Map.Entry<String, String> e : BUILTIN_DRIVERS.entrySet()) {
            if (jdbcUrl.startsWith(e.getKey())) {
                String type = e.getKey().substring(5).replace(":", "");
                return type;
            }
        }
        return "unknown";
    }

    /**
     * List datasource info with name, JDBC URL, and database type.
     */
    public Map<String, Map<String, String>> listDataSourceInfo() {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : dataSourceUrls.entrySet()) {
            Map<String, String> info = new LinkedHashMap<>();
            info.put("jdbcUrl", entry.getValue());
            info.put("dbType", inferDbType(entry.getValue()));
            result.put(entry.getKey(), info);
        }
        return result;
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

        // Infer driver class from JDBC URL if not specified
        String driverClass = cfg.getDriverClass();
        if (driverClass == null || driverClass.isBlank()) {
            driverClass = inferDriverClass(cfg.getJdbcUrl());
            if (driverClass == null) {
                throw new IllegalArgumentException(
                    "Cannot infer driver class from JDBC URL: " + cfg.getJdbcUrl()
                    + ". Set driverClass explicitly or use driverJar for external drivers.");
            }
        }

        // Load external driver if specified
        boolean externalDriver = cfg.getDriverJar() != null && !cfg.getDriverJar().isBlank();
        if (externalDriver) {
            loadExternalDriver(driverClass, cfg.getDriverJar());
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(cfg.getJdbcUrl());
        hikariConfig.setUsername(cfg.getUsername());
        hikariConfig.setPassword(cfg.getPassword());
        // For external drivers, skip setDriverClassName() — HikariCP discovers
        // the driver via DriverManager (which already has the DriverWrapper registered).
        // For built-in drivers, setDriverClassName() validates the class is loadable.
        if (!externalDriver) {
            hikariConfig.setDriverClassName(driverClass);
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
