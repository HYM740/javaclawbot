package gui.ui.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import gui.ui.theme.AppColors
import gui.ui.theme.AppTheme

data class NavItem(val icon: String, val label: String, val pageKey: String)
data class HistoryGroup(val label: String, val items: List<HistoryEntry>)
data class HistoryEntry(val sessionId: String, val title: String)

@Composable
fun Sidebar(
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    activePage: String,
    onPageSelected: (String) -> Unit,
    onNewChat: () -> Unit,
    history: List<HistoryGroup>,
    onResume: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val width = if (expanded) 256.dp else 64.dp

    val navItems = listOf(
        NavItem("💬", "对话", "chat"),
        NavItem("🤖", "模型", "models"),
        NavItem("👤", "代理", "agents"),
        NavItem("📡", "通道", "channels"),
        NavItem("⚡", "技能", "skills"),
        NavItem("🔌", "MCP 服务器", "mcp"),
        NavItem("🗄", "数据源", "databases"),
        NavItem("⏰", "定时任务", "crontasks")
    )

    Column(
        modifier = modifier
            .width(width).fillMaxHeight()
            .background(AppColors.SidebarBg)
            .animateContentSize()
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        // Logo
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(AppColors.Accent),
                contentAlignment = Alignment.Center
            ) {
                Text("J", color = Color.White, fontWeight = FontWeight.Bold)
            }
            AnimatedVisibility(expanded) {
                Text("NexusAi", Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Icon(
                if (expanded) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward,
                "Toggle", Modifier.size(20.dp).clickable { onToggleExpand() },
                tint = AppColors.TextSecondary
            )
        }

        // New chat
        Box(
            Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp))
                .background(AppColors.HoverBg).clickable { onNewChat() }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                if (expanded) "+ 新对话" else "+",
                style = AppTheme.typography.body
            )
        }

        // Nav items
        Spacer(Modifier.height(4.dp))
        navItems.forEach { item ->
            val active = item.pageKey == activePage
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(if (active) AppColors.ActiveBg else Color.Transparent)
                    .clickable { onPageSelected(item.pageKey) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(item.icon)
                AnimatedVisibility(expanded) {
                    Text(item.label, Modifier.padding(start = 12.dp))
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // History (expanded only)
        AnimatedVisibility(expanded) {
            LazyColumn {
                history.forEach { group ->
                    item {
                        Text(
                            group.label, style = AppTheme.typography.caption,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                    items(group.items) { entry ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                                .clickable { onResume(entry.sessionId) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                entry.title, maxLines = 1, style = AppTheme.typography.caption,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "×", Modifier.clickable { onDelete(entry.sessionId) },
                                color = AppColors.TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // Bottom: Settings + DevConsole
        rowNav("⚙️", "设置", "settings", activePage, expanded, onPageSelected)
        rowNav("🔧", "开发控制台", "devconsole", activePage, expanded, onPageSelected)
    }
}

@Composable
private fun rowNav(
    icon: String,
    label: String,
    key: String,
    active: String,
    expanded: Boolean,
    onSelect: (String) -> Unit
) {
    val isActive = key == active
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (isActive) AppColors.ActiveBg else Color.Transparent)
            .clickable { onSelect(key) }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon)
        AnimatedVisibility(expanded) {
            Text(label, Modifier.padding(start = 12.dp))
        }
    }
}
