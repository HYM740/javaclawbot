package config.tool;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class DbDataSourceConfig {
    private String jdbcUrl;
    private String username;
    private String password;
    private String driverClass;
    private String driverJar;
    private int maxPoolSize = 10;
    private long connectionTimeout = 30000;
    private boolean enable = true;
}
