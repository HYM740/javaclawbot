package gui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gui.ui.model.ChatMessage
import gui.ui.model.ToolCall
import gui.ui.model.ToolStatus
import gui.ui.theme.AppColors
import gui.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun MessageList(message: ChatMessage, modifier: Modifier = Modifier) {
    val timeFormat = remember { SimpleDateFormat("HH:mm") }
    val isUser = message.role == ChatMessage.Role.USER

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
        .clip(RoundedCornerShape(8.dp)).background(AppColors.Surface).padding(12.dp)) {
        Row {
            Text(if (isUser) "User" else "AI", style = AppTheme.typography.caption)
            Spacer(Modifier.width(8.dp))
            Text(timeFormat.format(Date(message.timestamp)), style = AppTheme.typography.caption, color = AppColors.TextSecondary)
        }
        Spacer(Modifier.height(4.dp))
        if (!isUser && message.toolCalls.isNotEmpty()) {
            message.toolCalls.forEach { tc -> ListToolCallRow(tc) }
            Spacer(Modifier.height(4.dp))
        }
        if (message.content.isNotBlank()) {
            if (isUser) Text(message.content, style = AppTheme.typography.body)
            else MarkdownContent(message.content)
        }
    }
}

@Composable
private fun ListToolCallRow(tc: ToolCall) {
    val icon = when (tc.status) {
        ToolStatus.RUNNING -> "⏳"
        ToolStatus.COMPLETED -> "✅"
        ToolStatus.ERROR -> "❌"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$icon ${tc.name}", fontSize = 12.sp, color = AppColors.TextSecondary)
    }
}
