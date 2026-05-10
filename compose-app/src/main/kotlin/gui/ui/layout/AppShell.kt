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
    showHistoryActive: Boolean,
    onTabSelected: (String) -> Unit,
    onTabClosed: (String) -> Unit,
    onNewTab: () -> Unit,
    onHistorySelected: () -> Unit,
    statusInfo: StatusInfo,
    modifier: Modifier = Modifier,
    content: @Composable (showTabBar: Boolean) -> Unit
) {
    var sidebarExpanded by remember { mutableStateOf(true) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.weight(1f)) {
            SidebarNav(
                expanded = sidebarExpanded,
                onToggleExpand = { sidebarExpanded = !sidebarExpanded },
                activePage = activePage,
                onPageSelected = onPageSelected
            )
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (activePage == "chat" || activePage == "__history__") {
                    TabBar(
                        tabs = tabs,
                        activeTabId = activeTabId,
                        showHistoryActive = showHistoryActive,
                        onTabSelected = onTabSelected,
                        onTabClosed = onTabClosed,
                        onNewTab = onNewTab,
                        onHistorySelected = onHistorySelected
                    )
                }
                Box(
                    Modifier.weight(1f).fillMaxWidth().background(AppColors.Background)
                ) {
                    content(activePage == "chat" || activePage == "__history__")
                }
            }
        }
        StatusBar(status = statusInfo)
    }
}
