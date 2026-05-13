package gui.ui.model

data class AgentTaskInfo(
    val taskId: String,
    val agentType: String,
    val status: String,
    val currentTool: String? = null,
    val iteration: Int = 0,
    val chatId: String? = null
)

data class StatusInfo(
    val modelName: String = "",
    val agentName: String = "default",
    val shellConnected: Boolean = false,
    val mcpOnline: Int = 0,
    val mcpTotal: Int = 0,
    val contextUsage: Float = 0f,
    val memoryBlocks: Int = 0,
    val activeAgentTasks: List<AgentTaskInfo> = emptyList()
)
