package gui.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import gui.ui.model.ChatTab
import gui.ui.model.StatusInfo
import gui.ui.theme.AppColors

@Composable
fun AppShell(
    activePage: String,
    onPageSelected: (String) -> Unit,
    tabs: List<ChatTab>,
    activeTabId: String?,
    onTabSelected: (String) -> Unit,
    onTabClosed: (String) -> Unit,
    onNewTab: () -> Unit,
    onNewChat: () -> Unit,
    history: List<HistoryGroup>,
    onResume: (String) -> Unit,
    onDelete: (String) -> Unit,
    statusInfo: StatusInfo,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var sidebarExpanded by remember { mutableStateOf(true) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.weight(1f)) {
            Sidebar(
                expanded = sidebarExpanded,
                onToggleExpand = { sidebarExpanded = !sidebarExpanded },
                activePage = activePage,
                onPageSelected = onPageSelected,
                onNewChat = onNewChat,
                history = history,
                onResume = onResume,
                onDelete = onDelete
            )
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (activePage == "chat") {
                    TabBar(tabs, activeTabId, onTabSelected, onTabClosed, onNewTab)
                }
                Box(
                    Modifier.weight(1f).fillMaxWidth().background(AppColors.Background)
                ) {
                    content()
                }
            }
        }
        StatusBar(status = statusInfo)
    }
}
