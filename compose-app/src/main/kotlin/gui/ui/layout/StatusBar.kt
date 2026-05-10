package gui.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import gui.ui.components.StatusPopover
import gui.ui.model.StatusInfo
import gui.ui.theme.AppColors
import gui.ui.theme.AppTheme

enum class StatusDetail {
    NONE,
    MODEL,
    AGENT,
    SHELL,
    MCP
}

@Composable
fun StatusBar(status: StatusInfo, modifier: Modifier = Modifier) {
    var detail by remember { mutableStateOf(StatusDetail.NONE) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (detail != StatusDetail.NONE) {
            StatusPopover(detail, status, onDismiss = { detail = StatusDetail.NONE })
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(28.dp).background(AppColors.SidebarBg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            segment(
                "\u25CF", "\u6A21\u578B ${status.modelName}", StatusDetail.MODEL, detail,
                { detail = if (detail == StatusDetail.MODEL) StatusDetail.NONE else StatusDetail.MODEL }
            )
            segment(
                "\u2593", "Agent ${status.agentName}", StatusDetail.AGENT, detail,
                { detail = if (detail == StatusDetail.AGENT) StatusDetail.NONE else StatusDetail.AGENT }
            )
            segment(
                "\u26A1", "Shell", StatusDetail.SHELL, detail,
                { detail = if (detail == StatusDetail.SHELL) StatusDetail.NONE else StatusDetail.SHELL }
            )
            segment(
                "\uD83D\uDEE0", "MCP ${status.mcpOnline}/${status.mcpTotal}",
                StatusDetail.MCP, detail,
                { detail = if (detail == StatusDetail.MCP) StatusDetail.NONE else StatusDetail.MCP }
            )
        }
    }
}

@Composable
private fun segment(
    icon: String, label: String, d: StatusDetail, cur: StatusDetail, onClick: () -> Unit
) {
    Row(
        Modifier.clickable { onClick() }.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, style = AppTheme.typography.caption)
        Spacer(Modifier.width(4.dp))
        Text(label, style = AppTheme.typography.caption)
    }
}
