package agent;

import java.util.List;
import java.util.Map;

public class RunResult {
    public final String finalContent;
    public final List<String> toolsUsed;
    public final List<Map<String, Object>> messages;
    public final Usage usage;

    public RunResult(String finalContent, List<String> toolsUsed, List<Map<String, Object>> messages) {
        this.finalContent = finalContent;
        this.toolsUsed = toolsUsed;
        this.messages = messages;
        this.usage = new Usage();
    }

    public RunResult(String finalContent, List<String> toolsUsed, List<Map<String, Object>> messages, Usage usage) {
        this.finalContent = finalContent;
        this.toolsUsed = toolsUsed;
        this.messages = messages;
        this.usage = usage != null ? usage : new Usage();
    }
}