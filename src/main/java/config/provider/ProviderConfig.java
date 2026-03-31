package config.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import config.provider.model.ModelConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Provider 配置
 *
 * 配置示例：
 * <pre>
 * {
 *   "apiKey": "sk-xxx",
 *   "apiBase": "https://api.example.com/v1",
 *   "extraHeaders": {"X-Custom-Header": "value"},
 *   "modelConfigs": [
 *     {"model": "gpt-4", "alias": "gpt4", "type": "chat"},
 *     {"model": "gpt-4-vision", "type": "vision"}
 *   ]
 * }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ProviderConfig {
    /** API Key */
    private String apiKey = "";

    /** API 基础地址 */
    private String apiBase = null;

    /** 模型配置列表 */
    private List<ModelConfig> modelConfigs = new ArrayList<>();

    /** 额外请求头 */
    private Map<String, String> extraHeaders = null;

    public ProviderConfig(String apiBase) {
        this.apiBase = apiBase;
    }
}