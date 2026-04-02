package agent.tool;

import config.ConfigIO;
import context.ContextPruner;
import context.ContextPruningSettings;
import lombok.extern.slf4j.Slf4j;
import session.Session;
import session.SessionManager;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 消息修剪工具
 *
 * 用于 /context-compress 命令，分析对话消息并删除冗余内容
 */
@Slf4j
public class PruneMessagesTool extends Tool {

    private final SessionManager sessionManager;
    private final String sessionKey;

    public PruneMessagesTool(SessionManager sessionManager, String sessionKey) {
        this.sessionManager = sessionManager;
        this.sessionKey = sessionKey;
    }

    @Override
    public String name() {
        return "prune_messages";
    }

    @Override
    public String description() {
        return "分析对话消息，决定哪些可以从 对话上下文 中删除。" +
                "memory/YYYY-MM-DD.md 已保存完整原始对话，Session 只需保留活跃窗口内的消息。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> droppedIndices = new LinkedHashMap<>();
        droppedIndices.put("type", "array");
        droppedIndices.put("items", Map.of("type", "integer"));
        droppedIndices.put("description", "可以删除的消息索引列表");
        props.put("dropped_indices", droppedIndices);

        Map<String, Object> importantIndices = new LinkedHashMap<>();
        importantIndices.put("type", "array");
        importantIndices.put("items", Map.of("type", "integer"));
        importantIndices.put("description", "特别重要的消息索引");
        props.put("important_indices", importantIndices);

        Map<String, Object> reasoning = new LinkedHashMap<>();
        reasoning.put("type", "string");
        reasoning.put("description", "删除理由");
        props.put("reasoning", reasoning);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", "object");
        out.put("properties", props);
        out.put("required", List.of("dropped_indices", "reasoning"));

        return out;
    }

    @Override
    public CompletionStage<String> execute(Map<String, Object> args) {
        Session session = sessionManager.getOrCreate(sessionKey);
        if (session == null) {
            return CompletableFuture.completedFuture("Error: session not found: " + sessionKey);
        }

        List<Map<String, Object>> messages = session.getMessages();
        int totalMessages = messages.size();

        // 解析 dropped_indices
        List<Integer> droppedIndices = parseIntegerList(args.get("dropped_indices"));
        if (droppedIndices == null || droppedIndices.isEmpty()) {
            return CompletableFuture.completedFuture("没有消息需要删除");
        }

        // 验证索引范围
        droppedIndices = droppedIndices.stream()
                .filter(i -> i >= 0 && i < totalMessages)
                .sorted(Collections.reverseOrder()) // 从后往前删除，避免索引变化
                .toList();

        if (droppedIndices.isEmpty()) {
            return CompletableFuture.completedFuture("所有索引都超出范围，没有消息被删除");
        }

        // 执行删除
        List<Map<String, Object>> newMessages = new ArrayList<>(messages);
        int droppedCount = 0;
        for (int index : droppedIndices) {
            if (index >= 0 && index < newMessages.size()) {
                newMessages.remove(index);
                droppedCount++;
            }
        }

        // 更新 session
        session.setMessages(newMessages);
        sessionManager.save(session);

        String reasoning = args.get("reasoning") instanceof String s ? s : "";
        String result = String.format(
                "已删除 %d 条消息（共 %d 条）。\n理由：%s",
                droppedCount, totalMessages, reasoning
        );
        // 重新计算上下文使用率
        int estimatedChars = ContextPruner.estimateContextChars(messages);
        int contextWindow = ConfigIO.loadConfig(ConfigIO.getConfigPath(sessionManager.getWorkspace())).getAgents().getDefaults().getContextWindow();
        double contextRatio = contextWindow > 0 ? (double) estimatedChars / contextWindowChars(contextWindow) : 0;
        log.info("压缩后上下文使用率: {}%", String.format("%.1f", contextRatio * 100));

        return CompletableFuture.completedFuture(result);
    }

    private double contextWindowChars(int contextWindow) {
        return contextWindow * ContextPruningSettings.CHARS_PER_TOKEN_ESTIMATE;
    }

    @SuppressWarnings("unchecked")
    private List<Integer> parseIntegerList(Object obj) {
        if (obj == null) return null;
        if (obj instanceof List<?> list) {
            List<Integer> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Number num) {
                    result.add(num.intValue());
                } else if (item instanceof String s) {
                    try {
                        result.add(Integer.parseInt(s));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return result;
        }
        return null;
    }
}