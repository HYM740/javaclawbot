package monitor;

import config.channel.EmailMonitorConfig;
import config.channel.NotificationTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import providers.LLMProvider;
import utils.GsonFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * LLM 邮件分析器
 * 使用自然语言描述任务，让 LLM 判断邮件是否需要通知
 */
public class MailAnalyzer {
    
    private static final Logger log = LoggerFactory.getLogger(MailAnalyzer.class);
    
    private static final String ANALYZE_PROMPT = """
你是一个邮件分析助手。根据以下邮件内容和任务描述，决定如何处理这封邮件。

## 任务描述
%s

## 邮件信息
- 发件人: %s
- 主题: %s
- 时间: %s
- 正文:
%s

## 可通知的目标
%s

## 输出格式 (JSON)
请严格输出以下JSON格式，不要输出其他内容：
{
  "shouldNotify": true或false,
  "reason": "判断理由",
  "targets": ["目标名称1", "目标名称2"],
  "message": "要发送的通知消息内容",
  "priority": "high/medium/low"
}
""";

    private final LLMProvider provider;
    private final EmailMonitorConfig config;

    public MailAnalyzer(LLMProvider provider, EmailMonitorConfig config) {
        this.provider = provider;
        this.config = config;
    }

    /**
     * 分析邮件，决定是否需要通知
     */
    public CompletionStage<AnalyzeResult> analyze(MailInfo mail) {
        String prompt = buildPrompt(mail);
        
        return provider.chat(
            List.of(Map.of("role", "user", "content", prompt)),
            null,  // no tools
            null,  // use default model
            1000,  // maxTokens
            0.3,   // temperature
            null,  // reasoningEffort
            null,  // think
            null,  // extraBody
            null   // cancelChecker
        ).thenApply(response -> {
            String content = response.getContent();
            log.debug("LLM response: {}", content);
            return parseResult(content);
        }).exceptionally(ex -> {
            log.error("LLM analysis failed: {}", ex.getMessage());
            AnalyzeResult errorResult = new AnalyzeResult();
            errorResult.setShouldNotify(false);
            errorResult.setReason("LLM analysis failed: " + ex.getMessage());
            return errorResult;
        });
    }

    private String buildPrompt(MailInfo mail) {
        String targetsDescription = config.getNotificationTargets().stream()
            .map(t -> "- " + t.getName() + " (通道: " + t.getChannel() + ")")
            .collect(Collectors.joining("\n"));
        
        return String.format(ANALYZE_PROMPT,
            config.getTaskDescription(),
            mail.getFrom(),
            mail.getSubject(),
            mail.getDate(),
            mail.getBody(),
            targetsDescription
        );
    }

    private AnalyzeResult parseResult(String content) {
        try {
            // 提取 JSON 部分（可能被 markdown 代码块包裹）
            String json = extractJson(content);
            return GsonFactory.getGson().fromJson(json, AnalyzeResult.class);
        } catch (Exception e) {
            log.warn("Failed to parse LLM response as JSON: {}", content, e);
            AnalyzeResult result = new AnalyzeResult();
            result.setShouldNotify(false);
            result.setReason("Failed to parse LLM response: " + e.getMessage());
            return result;
        }
    }

    private String extractJson(String content) {
        // 处理 markdown 代码块
        if (content.contains("```json")) {
            int start = content.indexOf("```json") + 7;
            int end = content.indexOf("```", start);
            if (end > start) {
                return content.substring(start, end).trim();
            }
        }
        if (content.contains("```")) {
            int start = content.indexOf("```") + 3;
            int end = content.indexOf("```", start);
            if (end > start) {
                return content.substring(start, end).trim();
            }
        }
        // 尝试直接解析
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }
}