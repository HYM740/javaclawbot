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
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
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
            Text("🛠", fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text(toolCall.name, style = AppTheme.typography.body)
            Spacer(Modifier.weight(1f))
            Text(when (toolCall.status) {
                ToolStatus.RUNNING -> "⌛"; ToolStatus.COMPLETED -> "✓"; ToolStatus.ERROR -> "✗"
            }, fontSize = 14.sp)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(12.dp)) {
                if (toolCall.params != null) {
                    Text("参数", style = AppTheme.typography.caption)
                    Text(toolCall.params, style = AppTheme.typography.mono, modifier = Modifier.padding(bottom = 8.dp))
                }
                if (toolCall.result != null) {
                    Text("结果", style = AppTheme.typography.caption)
                    var showResultDialog by remember { mutableStateOf(false) }
                    Column {
                        Box(Modifier.heightIn(max = 120.dp).clip(RoundedCornerShape(0.dp))) {
                            MarkdownContent(toolCall.result)
                        }
                        Text(
                            "显示全部",
                            color = AppColors.Accent,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { showResultDialog = true }
                        )
                    }
                    if (showResultDialog) {
                        val windowState = rememberWindowState(
                            position = WindowPosition.Aligned(Alignment.Center),
                            width = 800.dp,
                            height = 600.dp
                        )
                        Window(
                            onCloseRequest = { showResultDialog = false },
                            title = "工具调用结果",
                            state = windowState,
                            resizable = true
                        ) {
                            Box(
                                Modifier.fillMaxSize().background(AppColors.Surface).padding(16.dp)
                            ) {
                                MarkdownContent(toolCall.result)
                            }
                        }
                    }
                }
            }
        }
    }
}
