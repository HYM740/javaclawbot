package gui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import gui.ui.model.ChatMessage
import gui.ui.theme.AppColors
import gui.ui.theme.AppTheme

@Composable
fun MessageBubble(message: ChatMessage, modifier: Modifier = Modifier) {
    val isUser = message.role == ChatMessage.Role.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bgColor = if (isUser) AppColors.UserBubble else AppColors.AssistantBubble
    val textColor = if (isUser) Color.White else AppColors.TextPrimary
    val maxWidth = 700.dp

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = alignment) {

        // Reasoning (collapsible)
        if (!isUser && message.reasoning != null) {
            var expanded by remember { mutableStateOf(false) }
            Box(Modifier.widthIn(max = maxWidth).clip(RoundedCornerShape(8.dp))
                .background(AppColors.CodeBackground).clickable { expanded = !expanded }.padding(8.dp)) {
                Column {
                    Text("\u63A8\u7406", style = AppTheme.typography.caption)
                    if (expanded) MarkdownContent(message.reasoning)
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // Bubble
        Box(Modifier.widthIn(max = maxWidth).clip(RoundedCornerShape(
            topStart = 16.dp, topEnd = 16.dp,
            bottomStart = if (isUser) 16.dp else 4.dp,
            bottomEnd = if (isUser) 4.dp else 16.dp
        )).background(bgColor).padding(12.dp)) {
            if (isUser) Text(message.content, color = textColor, style = AppTheme.typography.body)
            else MarkdownContent(message.content)
        }
    }
}
