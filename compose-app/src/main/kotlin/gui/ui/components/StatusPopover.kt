package gui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import gui.ui.layout.StatusDetail
import gui.ui.model.StatusInfo
import gui.ui.theme.AppColors
import gui.ui.theme.AppTheme

@Composable
fun StatusPopover(detail: StatusDetail, status: StatusInfo, onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .shadow(4.dp, RoundedCornerShape(8.dp))
            .background(AppColors.Surface, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        when (detail) {
            StatusDetail.MODEL -> Column {
                Text("模型状态", fontWeight = FontWeight.Bold)
                Text("当前: ${status.modelName}", style = AppTheme.typography.caption)
            }
            StatusDetail.AGENT -> Column {
                Text("Agent", fontWeight = FontWeight.Bold)
                Text("名称: ${status.agentName}", style = AppTheme.typography.caption)
                if (status.activeAgentTasks.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("活跃子代理:", style = AppTheme.typography.caption, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    status.activeAgentTasks.forEach { task ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (task.status == "running") "🟢" else "⚪",
                                style = AppTheme.typography.caption
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${task.agentType} · #${task.taskId.take(8)}",
                                style = AppTheme.typography.caption
                            )
                            if (!task.currentTool.isNullOrBlank()) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    task.currentTool,
                                    style = AppTheme.typography.caption,
                                    color = AppColors.TextSecondary
                                )
                            }
                        }
                    }
                }
            }
            StatusDetail.SHELL -> Column {
                Text("Shell", fontWeight = FontWeight.Bold)
                Text(
                    if (status.shellConnected) "已连接" else "未连接",
                    style = AppTheme.typography.caption
                )
            }
            StatusDetail.MCP -> Column {
                Text("MCP", fontWeight = FontWeight.Bold)
                Text("在线: ${status.mcpOnline}/${status.mcpTotal}", style = AppTheme.typography.caption)
            }
            else -> {}
        }
    }
}
