package gui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
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
                Text("\u6A21\u578B\u72B6\u6001", fontWeight = FontWeight.Bold)
                Text("\u5F53\u524D: ${status.modelName}", style = AppTheme.typography.caption)
            }
            StatusDetail.AGENT -> Column {
                Text("Agent", fontWeight = FontWeight.Bold)
                Text("\u540D\u79F0: ${status.agentName}", style = AppTheme.typography.caption)
            }
            StatusDetail.SHELL -> Column {
                Text("Shell", fontWeight = FontWeight.Bold)
                Text(
                    if (status.shellConnected) "\u5DF2\u8FDE\u63A5" else "\u672A\u8FDE\u63A5",
                    style = AppTheme.typography.caption
                )
            }
            StatusDetail.MCP -> Column {
                Text("MCP", fontWeight = FontWeight.Bold)
                Text("\u5728\u7EBF: ${status.mcpOnline}/${status.mcpTotal}", style = AppTheme.typography.caption)
            }
            else -> {}
        }
    }
}
