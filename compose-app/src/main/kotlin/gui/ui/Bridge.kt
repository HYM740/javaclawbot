package gui.ui

import gui.ui.model.StatusInfo
import kotlinx.coroutines.*

class Bridge(
    private val bridge: BackendBridge,
    private val scope: CoroutineScope
) {
    data class Progress(
        val content: String,
        val isToolHint: Boolean = false,
        val isToolResult: Boolean = false,
        val toolName: String? = null,
        val toolCallId: String? = null,
        val isReasoning: Boolean = false
    )

    fun initialize(onReady: () -> Unit) {
        scope.launch(Dispatchers.IO) {
            bridge.initialize()
            scope.launch(Dispatchers.Main) { onReady() }
        }
    }

    fun sendMessage(
        text: String,
        mediaPaths: List<String>? = null,
        onProgress: (Progress) -> Unit,
        onResponse: (String) -> Unit,
        onError: (String) -> Unit,
        chatId: String = "direct"
    ) {
        val progressAdapter = java.util.function.Consumer<BackendBridge.ProgressEvent> { event ->
            scope.launch(Dispatchers.Main) {
                onProgress(Progress(
                    content = event.content(),
                    isToolHint = event.isToolHint(),
                    isToolResult = event.isToolResult(),
                    toolName = event.toolName(),
                    toolCallId = event.toolCallId(),
                    isReasoning = event.isReasoning()
                ))
            }
        }
        val responseAdapter = java.util.function.Consumer<String> { resp ->
            scope.launch(Dispatchers.Main) { onResponse(resp) }
        }
        val errorAdapter = java.util.function.Consumer<String> { err ->
            scope.launch(Dispatchers.Main) { onError(err) }
        }

        scope.launch(Dispatchers.IO) {
            bridge.sendMessage(text, mediaPaths, progressAdapter, responseAdapter, errorAdapter, chatId)
        }
    }

    fun stopMessage() = bridge.stopMessage()
    fun stopMessage(chatId: String) = bridge.stopMessage(chatId)
    fun newSession() = bridge.newSession()
    fun newSession(chatId: String) = bridge.newSession(chatId)
    fun createSession(chatId: String) = bridge.createSession(chatId)
    fun removeSession(chatId: String) = bridge.removeSession(chatId)
    fun resumeSession(sessionId: String) = bridge.resumeSession(sessionId)
    fun resumeSession(sessionId: String, chatId: String) = bridge.resumeSession(sessionId, chatId)
    fun deleteSession(sessionId: String) = bridge.deleteSession(sessionId)
    fun renameSession(sessionId: String, newTitle: String) = bridge.renameSession(sessionId, newTitle)
    fun renameSession(sessionId: String, newTitle: String, chatId: String) = bridge.renameSession(sessionId, newTitle, chatId)
    fun ensureFreshSession() = bridge.ensureFreshSession()
    fun ensureFreshSession(chatId: String) = bridge.ensureFreshSession(chatId)
    fun refreshProvider() = bridge.refreshProvider()
    fun reloadConfigFromDisk() = bridge.reloadConfigFromDisk()

    val config get() = bridge.config
    val sessionManager get() = bridge.sessionManager
    val provider get() = bridge.provider
    val agentLoop get() = bridge.agentLoop
    val cronService get() = bridge.cronService
    val skillsLoader get() = bridge.skillsLoader
    val projectRegistry get() = bridge.projectRegistry
    val projectDir get() = bridge.projectDir
    val sessionKey get() = bridge.sessionKey

    val isWaitingForResponse get() = bridge.isWaitingForResponse
    fun isWaitingForResponse(chatId: String) = bridge.isWaitingForResponse(chatId)
    val currentSession get() = bridge.currentSession
    fun getCurrentSession(chatId: String) = bridge.getCurrentSession(chatId)
    fun getSessionHistory(sessionId: String) = bridge.getSessionHistory(sessionId)
    fun getSessionHistory(sessionId: String, chatId: String) = bridge.getSessionHistory(sessionId, chatId)
    val lastReasoningContent get() = bridge.lastReasoningContent
    fun getLastReasoningContent(chatId: String) = bridge.getLastReasoningContent(chatId)
    fun getContextUsageRatio(): Double = bridge.getContextUsageRatio()
    fun getContextUsageRatio(chatId: String): Double = bridge.getContextUsageRatio(chatId)
    fun setOnTitleChanged(callback: Runnable?) { bridge.setOnTitleChanged(callback) }
    fun resetTitleCounter() = bridge.resetTitleCounter()
    fun resetTitleCounter(chatId: String) = bridge.resetTitleCounter(chatId)

    // MCP server management
    fun addMcpServer(name: String, command: String) = bridge.addMcpServer(name, command)
    fun deleteMcpServer(name: String) = bridge.deleteMcpServer(name)
    fun getMcpStatus(name: String): String = bridge.getMcpStatus(name).toString()
    fun refreshMcpTools() = bridge.refreshMcpTools()
    fun updateMcpServer(oldName: String, newName: String, command: String) =
        bridge.updateMcpServer(oldName, newName, command)
    fun updateMcpServerRaw(oldName: String, newName: String, json: String) =
        bridge.updateMcpServerRaw(oldName, newName, json)
    fun addMcpServerRaw(name: String, json: String) = bridge.addMcpServerRaw(name, json)

    // Data source management
    fun addDataSource(
        name: String, jdbcUrl: String, username: String, password: String,
        driverClass: String, maxPoolSize: Int, connectionTimeout: Long
    ) = bridge.addDataSource(name, jdbcUrl, username, password, driverClass, maxPoolSize, connectionTimeout)

    fun deleteDataSource(name: String) = bridge.deleteDataSource(name)
    fun testDataSourceConnection(jdbcUrl: String, username: String, password: String, driverClass: String): String? =
        bridge.testDataSourceConnection(jdbcUrl, username, password, driverClass)
    fun updateDataSource(
        oldName: String, newName: String, jdbcUrl: String, username: String,
        password: String, driverClass: String, maxPoolSize: Int, connectionTimeout: Long
    ) = bridge.updateDataSource(oldName, newName, jdbcUrl, username, password, driverClass, maxPoolSize, connectionTimeout)
    fun reconnectDataSource(name: String): Boolean = bridge.reconnectDataSource(name)
    fun toggleDataSource(name: String, enable: Boolean): Boolean = bridge.toggleDataSource(name, enable)
    fun getDataSourceStatus(name: String): String = bridge.getDataSourceStatus(name).toString()
}
