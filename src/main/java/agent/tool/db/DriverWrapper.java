package agent.tool.db;

import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Proxy wrapper for JDBC drivers loaded from external JARs.
 * DriverManager rejects drivers not loaded by the system classloader,
 * so we register this wrapper (loaded by system classloader) which
 * delegates to the actual driver loaded from the external JAR.
 */
public class DriverWrapper implements Driver {

    private final Driver delegate;

    public DriverWrapper(Driver delegate) {
        this.delegate = delegate;
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        return delegate.connect(url, info);
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return delegate.acceptsURL(url);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return delegate.getPropertyInfo(url, info);
    }

    @Override
    public int getMajorVersion() {
        return delegate.getMajorVersion();
    }

    @Override
    public int getMinorVersion() {
        return delegate.getMinorVersion();
    }

    @Override
    public boolean jdbcCompliant() {
        return delegate.jdbcCompliant();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }
}
