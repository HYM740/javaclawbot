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
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
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
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("App")

fun main() = application {
    val prev = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
        if (log.isWarnEnabled) log.warn("未捕获异常 (thread: {}): {}", thread.name, ex.message)
        prev?.uncaughtException(thread, ex) ?: run {
            System.err.print("Exception in thread \"${thread.name}\" ")
            ex.printStackTrace(System.err)
        }
    }
    val windowState = rememberWindowState(width = 1000.dp, height = 700.dp)
    val scope = rememberCoroutineScope()

    var bridge by remember { mutableStateOf<Bridge?>(null) }
    var activePage by remember { mutableStateOf("chat") }
    var tabs by remember { mutableStateOf(listOf(ChatTab(id = "default", title = "新对话"))) }
    var activeTabId by remember { mutableStateOf("default") }
    var messagesMap by remember { mutableStateOf(mapOf("default" to emptyList<ChatMessage>())) }
    var tabChatIds by remember { mutableStateOf(mapOf("default" to "direct")) }
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
                b.setOnTitleChanged(Runnable {
                    // Auto-generated title changed: refresh titles for all tab sessions
                    scope.launch(Dispatchers.Main) {
                        tabs = tabs.map { tab ->
                            val chatId = tabChatIds[tab.id] ?: return@map tab
                            val session = backend.getCurrentSession(chatId)
                            if (session != null) {
                                val newTitle = session.metadata?.get("title")?.toString()
                                if (newTitle != null && newTitle != tab.title) {
                                    tab.copy(title = newTitle)
                                } else tab
                            } else tab
                        }
                    }
                })
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

    // Poll active agent tasks every 1s for status bar
    LaunchedEffect(bridge) {
        val b = bridge ?: return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(1000)
            val tasks = b.getActiveAgentTasks()
            statusInfo = statusInfo.copy(activeAgentTasks = tasks)
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
        SideEffect {
            (window as? java.awt.Window)?.minimumSize = Dimension(900, 600)
        }
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
                                    b.setOnTitleChanged(Runnable {
                                        scope.launch(Dispatchers.Main) {
                                            tabs = tabs.map { tab ->
                                                val chatId = tabChatIds[tab.id] ?: return@map tab
                                                val session = backend.getCurrentSession(chatId)
                                                if (session != null) {
                                                    val newTitle = session.metadata?.get("title")?.toString()
                                                    if (newTitle != null && newTitle != tab.title) {
                                                        tab.copy(title = newTitle)
                                                    } else tab
                                                } else tab
                                            }
                                        }
                                    })
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
            val showHistoryActive = activePage == "__history__" || activeTabId == "__history__"

            AppShell(
                activePage = activePage,
                onPageSelected = { activePage = it },
                tabs = tabs,
                activeTabId = activeTabId,
                showHistoryActive = showHistoryActive,
                onTabSelected = { id ->
                    if (tabs.none { it.id == id }) {
                        // History tab
                        activePage = "__history__"
                    } else {
                        activeTabId = id
                        activePage = "chat"
                    }
                },
                onTabClosed = { id ->
                    val closedChatId = tabChatIds[id]
                    tabs = tabs.filter { it.id != id }
                    messagesMap = messagesMap - id
                    tabChatIds = tabChatIds - id
                    if (id == activeTabId && tabs.isNotEmpty()) {
                        activeTabId = tabs.first().id
                        activePage = "chat"
                    } else if (tabs.isEmpty()) {
                        activePage = "__history__"
                    }
                    if (closedChatId != null) {
                        scope.launch(Dispatchers.IO) { bridge?.removeSession(closedChatId) }
                    }
                },
                onNewTab = {
                    val newId = "tab_${System.nanoTime()}"
                    val newChatId = "tab_${System.nanoTime()}"
                    tabs = tabs + ChatTab(id = newId, title = "新对话")
                    messagesMap = messagesMap + (newId to emptyList())
                    tabChatIds = tabChatIds + (newId to newChatId)
                    activeTabId = newId
                    activePage = "chat"
                    scope.launch(Dispatchers.IO) { bridge?.createSession(newChatId) }
                },
                onHistorySelected = { activePage = "__history__" },
                onRename = { tabId, newTitle ->
                    bridge?.renameSession(tabId, newTitle)
                    tabs = tabs.map { if (it.id == tabId) it.copy(title = newTitle) else it }
                },
                statusInfo = statusInfo
            ) { showTabBar ->
                when (activePage) {
                    "__history__" -> HistoryPage(
                        history = history,
                        onResume = { sessionId, title ->
                            val historyTabId = sessionId
                            if (tabs.none { it.id == historyTabId }) {
                                val historyChatId = "hist_${System.nanoTime()}"
                                tabs = tabs + ChatTab(id = historyTabId, title = title)
                                messagesMap = messagesMap + (historyTabId to emptyList())
                                tabChatIds = tabChatIds + (historyTabId to historyChatId)
                                scope.launch(Dispatchers.IO) {
                                    bridge?.resumeSession(sessionId, historyChatId)
                                    val msgs = bridge?.getSessionHistory(sessionId, historyChatId) ?: emptyList()
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
                                        messagesMap = messagesMap + (historyTabId to chatMsgs)
                                    }
                                }
                            }
                            activeTabId = historyTabId
                            activePage = "chat"
                        },
                        onDelete = { sessionId ->
                            scope.launch(Dispatchers.IO) { bridge?.deleteSession(sessionId) }
                        }
                    )
                    "chat" -> {
                        val currentChatId = tabChatIds[activeTabId] ?: "direct"
                        ChatPage(
                            bridge,
                            chatId = currentChatId,
                            messages = messagesMap[activeTabId] ?: emptyList(),
                            onMessagesChanged = { messagesMap = messagesMap + (activeTabId to it) },
                            isLoading = isLoading
                        )
                    }
                    "models" -> ModelsPage(bridge)
                    "agents" -> AgentsPage(bridge)
                    "channels" -> ChannelsPage(bridge)
                    "skills" -> SkillsPage(bridge)
                    "mcp" -> McpPage(bridge)
                    "databases" -> DatabasesPage(bridge)
                    "crontasks" -> CronPage(bridge)
                    "settings" -> SettingsPage(bridge)
                    "devconsole" -> DevConsoleScreen()
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
