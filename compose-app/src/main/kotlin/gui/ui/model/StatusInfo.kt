package gui.ui.model

data class StatusInfo(
    val modelName: String = "",
    val agentName: String = "default",
    val shellConnected: Boolean = false,
    val mcpOnline: Int = 0,
    val mcpTotal: Int = 0,
    val contextUsage: Float = 0f,
    val memoryBlocks: Int = 0
)
