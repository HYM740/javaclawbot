package agent;

public interface ProgressCallback {
    void onProgress(String content, boolean toolHint);

    /** 发送推理/思考内容（区别于普通回复文本），默认空实现保持向后兼容 */
    default void onReasoning(String content) {}
}