package config.provider.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 模型配置
 *
 * 配置示例：
 * <pre>
 * {
 *   "model": "glm-4.7-thinking",
 *   "alias": "glm-4.7",
 *   "type": "chat",
 *   "maxTokens": 8192,
 *   "temperature": 0.7,
 *   "topP": 0.9,
 *   "think": {"type": "enabled", "clear_thinking": false},
 *   "extraBody": {"custom_param": "value"}
 * }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ModelConfig {

    /**
     * 模型类型枚举
     */
    public enum ModelType {
        /** 文本对话模型（默认） */
        CHAT,
        /** 文本补全模型 */
        TEXT,
        /** 图像理解模型（多模态，支持图片输入） */
        VISION,
        /** 图像生成模型 */
        IMAGE_GENERATION,
        /** 向量嵌入模型 */
        EMBEDDING,
        /** 音频模型（语音识别/合成） */
        AUDIO,
        /** 重排序模型 */
        RERANK,
        /** 审核模型 */
        MODERATION
    }

    /** 模型名称（实际调用 API 使用的名称） */
    private String model;

    /** 别名（用于显示和快速引用） */
    private String alias;

    /** 模型类型（默认 CHAT） */
    private ModelType type = ModelType.CHAT;

    /** 最大输出 token 数 */
    private Integer maxTokens = -1;

    /** 温度参数（0-2，越高越随机） */
    private Double temperature = -1D;

    /** Top-P 采样参数 */
    private Double topP;

    /** 上下文窗口大小 */
    private Integer contextWindow = 0;

    /**
     * 思考/推理模式配置（启用时会合并到请求 body 中）
     * <p>
     * null 或不配置 = 不启用思考模式
     * {} = 启用思考，使用空对象
     * {"type": "enabled", "clear_thinking": false} = 智谱 GLM 格式
     * {"reasoning": true} = DeepSeek 格式
     */
    private Map<String, Object> think = new HashMap<>();

    /**
     * 额外请求参数（直接合并到请求 body 中）
     * <p>
     * 例如：{"custom_param": "value"}
     */
    private Map<String, Object> extraBody = new HashMap<>();
}