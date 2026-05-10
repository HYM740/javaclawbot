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
    val result: String? = null
)

enum class ToolStatus { RUNNING, COMPLETED, ERROR }
