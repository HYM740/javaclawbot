package gui.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gui.ui.model.ToolCall
import gui.ui.model.ToolStatus
import gui.ui.theme.AppColors
import gui.ui.theme.AppTheme

@Composable
fun ToolCallCard(toolCall: ToolCall, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)
        .clip(RoundedCornerShape(12.dp)).background(AppColors.Surface)) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("\uD83D\uDEE0", fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text(toolCall.name, style = AppTheme.typography.body)
            Spacer(Modifier.weight(1f))
            Text(when (toolCall.status) {
                ToolStatus.RUNNING -> "\u231B"; ToolStatus.COMPLETED -> "\u2713"; ToolStatus.ERROR -> "\u2717"
            }, fontSize = 14.sp)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(12.dp)) {
                if (toolCall.params != null) {
                    Text("\u53C2\u6570", style = AppTheme.typography.caption)
                    Text(toolCall.params, style = AppTheme.typography.mono, modifier = Modifier.padding(bottom = 8.dp))
                }
                if (toolCall.result != null) {
                    Text("\u7ED3\u679C", style = AppTheme.typography.caption)
                    MarkdownContent(toolCall.result)
                }
            }
        }
    }
}
