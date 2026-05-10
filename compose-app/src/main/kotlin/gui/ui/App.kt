package gui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.material.ProvideTextStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import gui.ui.layout.*
import gui.ui.model.*
import gui.ui.pages.*
import gui.ui.theme.AppColors
import gui.ui.theme.CjkFontResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun main() = application {
    val windowState = rememberWindowState(width = 1000.dp, height = 700.dp)
    val scope = rememberCoroutineScope()

    var bridge by remember { mutableStateOf<Bridge?>(null) }
    var activePage by remember { mutableStateOf("chat") }
    var tabs by remember { mutableStateOf(listOf(ChatTab(id = "default", title = "新对话"))) }
    var activeTabId by remember { mutableStateOf("default") }
    var messagesMap by remember { mutableStateOf(mapOf("default" to emptyList<ChatMessage>())) }
    var history by remember { mutableStateOf(emptyList<HistoryGroup>()) }
    var statusInfo by remember { mutableStateOf(StatusInfo()) }
    var isLoading by remember { mutableStateOf(false) }
    var initError by remember { mutableStateOf<String?>(null) }

    // Init bridge async
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val backend = BackendBridge()
                backend.setUiDispatcher { r ->
                    try {
                        scope.launch(Dispatchers.Main) { r.run() }
                    } catch (_: Exception) {}
                }
                backend.initialize()
                val b = Bridge(backend, scope)
                scope.launch(Dispatchers.Main) {
                    bridge = b
                    statusInfo = StatusInfo(
                        modelName = b.config?.agents?.defaults?.model ?: "",
                        mcpTotal = b.config?.tools?.mcpServers?.size ?: 0
                    )
                }
            } catch (e: Exception) {
                scope.launch(Dispatchers.Main) {
                    initError = e.message ?: "初始化失败（未知错误）"
                }
            }
        }
    }

    // Refresh history when bridge is ready
    LaunchedEffect(bridge) {
        bridge ?: return@LaunchedEffect
        scope.launch(Dispatchers.IO) {
            val sessions = bridge?.sessionManager?.listSessions() ?: emptyList()
            scope.launch(Dispatchers.Main) {
                history = groupSessions(sessions)
            }
        }
    }

    Window(
        onCloseRequest = {
            bridge?.stopMessage()
            exitApplication()
        },
        title = "NexusAi",
        state = windowState
    ) {
        ProvideTextStyle(TextStyle(fontFamily = CjkFontResolver.get())) {
        if (initError != null) {
            // Show initialization error
            Box(
                Modifier.fillMaxSize().background(AppColors.Background),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚠", fontSize = 48.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "初始化失败",
                        color = AppColors.TextPrimary,
                        fontSize = 20.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        initError ?: "",
                        color = AppColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            initError = null
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val backend = BackendBridge()
                                    backend.setUiDispatcher { r ->
                                        try { scope.launch(Dispatchers.Main) { r.run() } } catch (_: Exception) {}
                                    }
                                    backend.initialize()
                                    val b = Bridge(backend, scope)
                                    scope.launch(Dispatchers.Main) {
                                        bridge = b
                                        statusInfo = StatusInfo(
                                            modelName = b.config?.agents?.defaults?.model ?: "",
                                            mcpTotal = b.config?.tools?.mcpServers?.size ?: 0
                                        )
                                    }
                                } catch (e: Exception) {
                                    scope.launch(Dispatchers.Main) {
                                        initError = e.message ?: "重试失败"
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = AppColors.Accent)
                    ) {
                        Text("重试", color = AppColors.OnAccent)
                    }
                }
            }
        } else {
            AppShell(
                activePage = activePage,
                onPageSelected = { activePage = it },
                tabs = tabs,
                activeTabId = activeTabId,
                onTabSelected = { activeTabId = it },
                onTabClosed = { id ->
                    tabs = tabs.filter { it.id != id }
                    messagesMap = messagesMap - id
                    if (id == activeTabId && tabs.isNotEmpty()) activeTabId = tabs.first().id
                },
                onNewTab = {
                    val newId = "tab_${System.currentTimeMillis()}"
                    tabs = tabs + ChatTab(id = newId, title = "新对话")
                    messagesMap = messagesMap + (newId to emptyList())
                    activeTabId = newId
                    bridge?.newSession()
                },
                onNewChat = {
                    activePage = "chat"
                    bridge?.ensureFreshSession()
                    val newId = "fresh_${System.currentTimeMillis()}"
                    tabs = listOf(ChatTab(id = newId, title = "新对话"))
                    messagesMap = mapOf(newId to emptyList())
                    activeTabId = newId
                },
                history = history,
                onResume = { sessionId ->
                    scope.launch(Dispatchers.IO) {
                        bridge?.resumeSession(sessionId)
                        val msgs = bridge?.getSessionHistory(sessionId) ?: emptyList()
                        val chatMsgs = msgs.mapNotNull { m ->
                            val roleStr = m["role"]?.toString() ?: return@mapNotNull null
                            val content = m["content"]?.toString() ?: ""
                            val reasoning = m["reasoning_content"]?.toString()
                            val role = when (roleStr) {
                                "user" -> ChatMessage.Role.USER
                                "assistant" -> ChatMessage.Role.ASSISTANT
                                else -> ChatMessage.Role.SYSTEM
                            }
                            ChatMessage(
                                id = "hist_${System.currentTimeMillis()}_${m.hashCode()}",
                                role = role,
                                content = content,
                                reasoning = reasoning
                            )
                        }
                        scope.launch(Dispatchers.Main) {
                            messagesMap = messagesMap + (sessionId to chatMsgs)
                            activePage = "chat"
                            tabs = listOf(ChatTab(id = sessionId, title = "新对话"))
                            activeTabId = sessionId
                        }
                    }
                },
                onDelete = { sessionId ->
                    scope.launch(Dispatchers.IO) {
                        bridge?.deleteSession(sessionId)
                    }
                },
                statusInfo = statusInfo
            ) {
                when (activePage) {
                    "chat" -> ChatPage(
                        bridge,
                        messages = messagesMap[activeTabId] ?: emptyList(),
                        onMessagesChanged = { messagesMap = messagesMap + (activeTabId to it) },
                        isLoading = isLoading
                    )
                    "models" -> ModelsPage(bridge)
                    "agents" -> AgentsPage(bridge)
                    "channels" -> ChannelsPage(bridge)
                    "skills" -> SkillsPage(bridge)
                    "mcp" -> McpPage(bridge)
                    "databases" -> DatabasesPage(bridge)
                    "crontasks" -> CronPage(bridge)
                    "settings" -> SettingsPage(bridge)
                    "devconsole" -> DevConsolePage(bridge)
                    else -> Text(
                        "Unknown page: $activePage",
                        modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center)
                    )
                }
            }
        }
        }
    }
}

// Helper - group sessions by date
private fun groupSessions(sessions: List<Map<String, Any>>): List<HistoryGroup> {
    val now = LocalDate.now()
    val today = mutableListOf<Map<String, Any>>()
    val yesterday = mutableListOf<Map<String, Any>>()
    val earlier = mutableListOf<Map<String, Any>>()

    sessions.forEach { s ->
        val updated = s["updated_at"]?.toString() ?: ""
        try {
            val isoInstant = try {
                LocalDateTime.parse(updated, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            } catch (_: Exception) {
                LocalDateTime.parse(updated, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"))
            }
            val d = isoInstant.toLocalDate()
            when {
                d == now -> today
                d == now.minusDays(1) -> yesterday
                else -> earlier
            }.add(s)
        } catch (_: Exception) {
            earlier.add(s)
        }
    }

    return listOf(
        HistoryGroup("今天", today.map { toEntry(it) }),
        HistoryGroup("昨天", yesterday.map { toEntry(it) }),
        HistoryGroup("更早", earlier.map { toEntry(it) })
    ).filter { it.items.isNotEmpty() }
}

private fun toEntry(s: Map<String, Any>): HistoryEntry {
    val sid = s["session_id"]?.toString() ?: ""
    val meta = s["metadata"] as? Map<*, *>
    val title = meta?.get("title")?.toString() ?: sid
    return HistoryEntry(sid, title)
}
