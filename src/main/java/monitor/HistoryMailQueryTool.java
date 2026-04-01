package monitor;

import agent.tool.Tool;
import config.channel.EmailMonitorConfig;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 历史邮件查询工具
 * 供 Agent 调用，查询历史邮件并触发 LLM 分析
 */
public class HistoryMailQueryTool extends Tool {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    private final EmailMonitorService monitorService;
    private final EmailMonitorConfig config;

    public HistoryMailQueryTool(EmailMonitorService monitorService, EmailMonitorConfig config) {
        this.monitorService = monitorService;
        this.config = config;
    }

    @Override
    public String name() {
        return "query_history_mails";
    }

    @Override
    public String description() {
        return "查询历史邮件并触发 LLM 分析。参数：startDate(开始日期, yyyy-MM-dd), endDate(结束日期, yyyy-MM-dd), keywords(关键词, 可选)";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "startDate", Map.of(
                    "type", "string",
                    "description", "开始日期，格式 yyyy-MM-dd"
                ),
                "endDate", Map.of(
                    "type", "string",
                    "description", "结束日期，格式 yyyy-MM-dd"
                ),
                "keywords", Map.of(
                    "type", "string",
                    "description", "过滤关键词（可选）"
                )
            ),
            "required", java.util.List.of("startDate", "endDate")
        );
    }

    @Override
    public CompletionStage<String> execute(Map<String, Object> args) {
        try {
            // 解析参数
            String startDateStr = (String) args.get("startDate");
            String endDateStr = (String) args.get("endDate");
            String keywords = (String) args.get("keywords");
            
            LocalDate startDate = LocalDate.parse(startDateStr, DATE_FORMAT);
            LocalDate endDate = LocalDate.parse(endDateStr, DATE_FORMAT);
            
            // 默认值处理
            if (endDate == null) {
                endDate = LocalDate.now();
            }
            if (startDate == null) {
                int defaultDays = config.getHistoryQuery().getDefaultDays();
                startDate = endDate.minusDays(defaultDays);
            }
            
            // 查询历史邮件
            monitorService.queryHistory(startDate, endDate, keywords);
            
            return CompletableFuture.completedFuture(
                "历史邮件查询已触发，时间范围: " + startDate + " 至 " + endDate + 
                (keywords != null ? "，关键词: " + keywords : "")
            );
        } catch (Exception e) {
            return CompletableFuture.completedFuture("查询失败: " + e.getMessage());
        }
    }
}