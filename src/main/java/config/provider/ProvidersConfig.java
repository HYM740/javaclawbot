package config.provider;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import config.provider.model.ModelConfig;
import config.provider.model.ModelConfig.ModelType;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class ProvidersConfig {

    public ProvidersConfig() {
        // 有意留空 — 不在此处初始化默认提供商。
        // 默认供应商仅由 ConfigIO.loadConfig 在无文件时调用 applyDefaults() 创建。
        // 避免已删除供应商的现有用户在反序列化时被强制恢复。
    }

    /** 显式应用默认供应商（仅在首次创建 config.json 时调用） */
    public void applyDefaults() {
        initDefaultModelConfigs();
    }

    @JsonAnySetter
    private Map<String, ProviderConfig> providerMap = new LinkedHashMap<>();

    @JsonAnyGetter
    public Map<String, ProviderConfig> getProviderMap() {
        return providerMap;
    }

    private void initDefaultModelConfigs() {
        // ===== 精简默认供应商：仅保留国内常用 =====
        // 如需其他供应商（anthropic/openai/openrouter 等），通过 GUI 手动添加
        
        providerMap.put("custom", new ProviderConfig("http://localhost:8000/v1"));
        providerMap.put("deepseek", new ProviderConfig("https://api.deepseek.com"));
        providerMap.put("zhipu", new ProviderConfig("https://open.bigmodel.cn/api/paas/v4"));
        providerMap.put("dashscope", new ProviderConfig("https://dashscope.aliyuncs.com/compatible-mode/v1"));
        providerMap.put("volcengine", new ProviderConfig("https://ark.cn-beijing.volces.com/api/v3"));
        providerMap.put("vllm", new ProviderConfig("http://localhost:8000/v1"));
        providerMap.put("minimax", new ProviderConfig("https://api.minimax.chat/v1"));
        providerMap.put("小米", new ProviderConfig("https://api.xiaomimistral.com/v1"));

        // DeepSeek
        providerMap.get("deepseek").setModelConfigs(List.of(
                model("deepseek-v4-pro", "deepseek-v4-pro", ModelType.CHAT, 65536, 512000),
                model("deepseek-v4-flash", "deepseek-v4-flash", ModelType.CHAT, 65536, 512000)
        ));

        // 智谱 GLM
        providerMap.get("zhipu").setModelConfigs(List.of(
                model("glm5", "glm-5", ModelType.CHAT, 65536, 512000),
                model("glm5.1", "glm-5.1", ModelType.CHAT, 65536, 512000)
        ));

        // 阿里云 DashScope（通义千问 + DeepSeek 系列）
        providerMap.get("dashscope").setModelConfigs(List.of(
                model("deepseek-v4-pro", "deepseek-v4-pro", ModelType.CHAT, 65536, 512000),
                model("deepseek-v4-flash", "deepseek-v4-flash", ModelType.CHAT, 65536, 512000),
                model("qwen3.6-plus", "qwen3.6-plus", ModelType.VISION, 65536, 512000)
        ));

        // MiniMax
        providerMap.get("minimax").setModelConfigs(List.of(
                model("MiniMax-M2.7-highspeed", "MiniMax-M2.7-highspeed", ModelType.CHAT, 65536, 512000),
                model("MiniMax-M2.7", "MiniMax-M2.7", ModelType.CHAT, 65536, 512000)
        ));
    }

    private static ModelConfig model(String model, String alias, ModelType type, int maxTokens) {
        ModelConfig config = new ModelConfig();
        config.setModel(model);
        config.setAlias(alias);
        config.setType(type);
        config.setMaxTokens(maxTokens);
        return config;
    }

    private static ModelConfig model(String model, String alias, ModelType type, int maxTokens, int contextWindow) {
        ModelConfig config = new ModelConfig();
        config.setModel(model);
        config.setAlias(alias);
        config.setType(type);
        config.setMaxTokens(maxTokens);
        config.setContextWindow(contextWindow);
        return config;
    }

    public ProviderConfig getByName(String name) {
        if (name == null) return providerMap.get("custom");
        return providerMap.get(name);
    }

    public void put(String name, ProviderConfig cfg) {
        providerMap.put(name, cfg);
    }

    public void remove(String name) {
        providerMap.remove(name);
    }

    public Set<String> names() {
        return providerMap.keySet();
    }

    public Map<String, ProviderConfig> getAll() {
        return providerMap;
    }
}
