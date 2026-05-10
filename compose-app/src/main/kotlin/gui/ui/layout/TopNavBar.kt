package gui.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gui.ui.theme.AppColors
import androidx.compose.runtime.Composable

data class NavItem(val icon: String, val label: String, val pageKey: String)

@Composable
fun TopNavBar(
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    activePage: String,
    onPageSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
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

    val bottomItems = listOf(
        NavItem("⚙️", "设置", "settings"),
        NavItem("🔧", "开发控制台", "devconsole")
    )

    Row(
        modifier = modifier
            .fillMaxWidth().height(36.dp)
            .background(AppColors.SidebarBg)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Toggle button
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(6.dp))
                .clickable { onToggleExpand() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (expanded) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward,
                "Toggle", Modifier.size(16.dp),
                tint = AppColors.TextSecondary
            )
        }

        Spacer(Modifier.width(4.dp))

        // Nav items (horizontally scrollable)
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEach { item -> navItem(item, expanded, activePage, onPageSelected) }
            Spacer(Modifier.width(8.dp))
            // Separator
            Box(Modifier.width(1.dp).height(16.dp).background(AppColors.TextSecondary.copy(alpha = 0.3f)))
            Spacer(Modifier.width(8.dp))
            bottomItems.forEach { item -> navItem(item, expanded, activePage, onPageSelected) }
        }
    }
}

@Composable
private fun navItem(
    item: NavItem,
    expanded: Boolean,
    activePage: String,
    onPageSelected: (String) -> Unit
) {
    val active = item.pageKey == activePage
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) AppColors.ActiveBg else Color.Transparent)
            .clickable { onPageSelected(item.pageKey) }
            .padding(horizontal = if (expanded) 10.dp else 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(item.icon, fontSize = 14.sp)
        if (expanded) {
            Spacer(Modifier.width(6.dp))
            Text(
                item.label,
                fontSize = 12.sp,
                color = if (active) AppColors.TextPrimary else AppColors.TextSecondary
            )
        }
    }
}
