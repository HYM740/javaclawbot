package gui.ui.model

data class ChatMessage(
    val id: String,
    val role: Role,
    val content: String,
    val reasoning: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val toolCalls: List<ToolCall> = emptyList()
) {
    enum class Role { USER, ASSISTANT, SYSTEM }
}

data class ToolCall(
    val name: String,
    val status: ToolStatus,
    val params: String? = null,
    val result: String? = null,
    val toolCallId: String? = null,
    val subCalls: List<SubagentCall> = emptyList()
)

data class SubagentCall(
    val taskId: String,
    val agentType: String,
    val toolName: String,
    val toolParams: String? = null,
    val toolResult: String? = null,
    val status: ToolStatus,
    val toolCallId: String? = null,
    val iteration: Int = 0
)

enum class ToolStatus { RUNNING, COMPLETED, ERROR }
