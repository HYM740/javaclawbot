package gui.ui.layout

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import gui.ui.theme.AppColors

@Composable
fun SidebarNav(
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    activePage: String,
    onPageSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val width by animateDpAsState(
        targetValue = if (expanded) 200.dp else 48.dp,
        animationSpec = tween(durationMillis = 200)
    )

    val topItems = listOf(
        NavItem("💬", "对话", "chat"),
        NavItem("🤖", "模型", "models"),
        NavItem("👤", "代理", "agents"),
        NavItem("📡", "通道", "channels"),
        NavItem("⚡", "技能", "skills"),
        NavItem("🔌", "MCP 服务器", "mcp"),
        NavItem("🗄", "数据源", "databases"),
        NavItem("⏰", "定时任务", "crontasks")
    )

    val bottomItems = listOf(
        NavItem("⚙️", "设置", "settings"),
        NavItem("🔧", "开发控制台", "devconsole")
    )

    Column(
        modifier = modifier
            .width(width).fillMaxHeight()
            .background(AppColors.SidebarBg)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        // Logo row
        Row(
            Modifier.fillMaxWidth().height(40.dp).clickable { if (!expanded) onToggleExpand() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(AppColors.Accent),
                contentAlignment = Alignment.Center
            ) {
                Text("J", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
            if (expanded) {
                Spacer(Modifier.width(8.dp))
                Text("NexusAi", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, "收起",
                    Modifier.size(20.dp).clickable { onToggleExpand() },
                    tint = AppColors.TextSecondary
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Nav items (scrollable)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
        ) {
            topItems.forEach { item -> navItemRow(item, expanded, activePage, onPageSelected) }
        }

        // Bottom items
        bottomItems.forEach { item -> navItemRow(item, expanded, activePage, onPageSelected) }
    }
}

@Composable
private fun navItemRow(
    item: NavItem,
    expanded: Boolean,
    activePage: String,
    onPageSelected: (String) -> Unit
) {
    val active = item.pageKey == activePage
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (active) AppColors.ActiveBg else Color.Transparent)
            .clickable { onPageSelected(item.pageKey) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(item.icon)
        if (expanded) {
            Text(item.label, Modifier.padding(start = 12.dp))
        }
    }
}
