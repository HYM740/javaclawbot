package gui.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import gui.ui.layout.AppShell
import gui.ui.layout.HistoryGroup
import gui.ui.model.*

fun main() = application {
    val windowState = rememberWindowState(width = 1100.dp, height = 800.dp)
    var activePage by remember { mutableStateOf("chat") }
    var tabs by remember { mutableStateOf(listOf(ChatTab(id = "default", title = "\u65B0\u5BF9\u8BDD"))) }
    var activeTabId by remember { mutableStateOf("default") }
    var history by remember { mutableStateOf(emptyList<HistoryGroup>()) }
    var statusInfo by remember { mutableStateOf(StatusInfo()) }

    Window(onCloseRequest = ::exitApplication, title = "NexusAi", state = windowState) {
        AppShell(
            activePage = activePage,
            onPageSelected = { activePage = it },
            tabs = tabs,
            activeTabId = activeTabId,
            onTabSelected = { activeTabId = it },
            onTabClosed = { id ->
                tabs = tabs.filter { it.id != id }
                if (id == activeTabId && tabs.isNotEmpty()) activeTabId = tabs.first().id
            },
            onNewTab = {
                val newId = "tab_${System.currentTimeMillis()}"
                tabs = tabs + ChatTab(id = newId, title = "\u65B0\u5BF9\u8BDD")
                activeTabId = newId
            },
            onNewChat = { activePage = "chat" },
            history = history,
            onResume = {},
            onDelete = {},
            statusInfo = statusInfo
        ) {
            Text("Page: $activePage", modifier = Modifier.fillMaxSize())
        }
    }
}
