package gui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gui.ui.model.ChatMessage
import gui.ui.model.ToolCall
import gui.ui.model.ToolStatus
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import gui.ui.theme.AppColors
import gui.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            Box(
                Modifier.widthIn(max = maxWidth).clip(RoundedCornerShape(8.dp))
                    .background(AppColors.CodeBackground).padding(8.dp)
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !expanded) { expanded = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("推理", style = AppTheme.typography.caption,
                            modifier = Modifier.weight(1f))
                        if (expanded) {
                            Text("▲", fontSize = 10.sp, color = AppColors.TextSecondary,
                                modifier = Modifier.clickable { expanded = false })
                        }
                    }
                    if (expanded) MarkdownContent(message.reasoning)
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // Tool calls
        if (!isUser && message.toolCalls.isNotEmpty()) {
            message.toolCalls.forEach { tc -> ToolCallCard(tc) }
            Spacer(Modifier.height(4.dp))
        }

        // Bubble (only if content is non-empty, tool-only messages hide the bubble)
        if (message.content.isBlank() && message.toolCalls.isNotEmpty()) return@Column
        var showRawDialog by remember { mutableStateOf(false) }
        Box(Modifier.widthIn(max = maxWidth).clip(RoundedCornerShape(
            topStart = 16.dp, topEnd = 16.dp,
            bottomStart = if (isUser) 16.dp else 4.dp,
            bottomEnd = if (isUser) 4.dp else 16.dp
        )).background(bgColor).padding(12.dp)) {
            Column {
                if (isUser) SelectionContainer {
                    Text(message.content, color = textColor, style = AppTheme.typography.body)
                }
                else MarkdownContent(message.content)
                Text(
                    "查看原文",
                    color = textColor.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.End).clickable { showRawDialog = true }
                )
            }
        }
        Text(
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
            fontSize = 10.sp,
            color = AppColors.TextSecondary.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 2.dp)
        )
        if (showRawDialog) {
            val rawWindowState = rememberWindowState(
                position = WindowPosition.Aligned(Alignment.Center),
                width = 600.dp,
                height = 400.dp
            )
            Window(
                onCloseRequest = { showRawDialog = false },
                title = "原始消息",
                state = rawWindowState,
                resizable = true
            ) {
                (window as? java.awt.Window)?.minimumSize = java.awt.Dimension(400, 300)
                val rawScrollState = rememberScrollState()
                Row(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.weight(1f).fillMaxHeight().background(AppColors.Surface).padding(16.dp).verticalScroll(rawScrollState)
                    ) {
                        SelectionContainer {
                            Text(
                                message.content,
                                style = AppTheme.typography.mono,
                                color = AppColors.TextPrimary
                            )
                        }
                    }
                    VerticalScrollbar(
                        modifier = Modifier.width(8.dp).padding(vertical = 2.dp),
                        adapter = rememberScrollbarAdapter(scrollState = rawScrollState)
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolCallCard(tc: ToolCall) {
    var expandedParams by remember { mutableStateOf(false) }
    var expandedResult by remember { mutableStateOf(false) }
    val maxWidth = 700.dp

    val statusIcon = when (tc.status) {
        ToolStatus.RUNNING -> "⏳"
        ToolStatus.COMPLETED -> "✅"
        ToolStatus.ERROR -> "❌"
    }
    val statusColor = when (tc.status) {
        ToolStatus.RUNNING -> Color(0xFF6B7280)
        ToolStatus.COMPLETED -> Color(0xFF22C55E)
        ToolStatus.ERROR -> Color(0xFFEF4444)
    }

    Box(
        Modifier.widthIn(max = maxWidth).clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF8FAFC)).border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(statusIcon, fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    tc.name,
                    fontSize = 13.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    when (tc.status) {
                        ToolStatus.RUNNING -> "运行中..."
                        ToolStatus.COMPLETED -> "完成"
                        ToolStatus.ERROR -> "错误"
                    },
                    fontSize = 11.sp,
                    color = statusColor
                )
            }

            // Collapsible params
            if (!tc.params.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.clickable { expandedParams = !expandedParams },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (expandedParams) "▼" else "▶",
                        fontSize = 10.sp,
                        color = AppColors.TextSecondary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("参数", fontSize = 11.sp, color = AppColors.TextSecondary)
                }
                if (expandedParams) {
                    Text(
                        tc.params!!,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AppColors.TextSecondary,
                        modifier = Modifier.padding(start = 14.dp, top = 2.dp)
                    )
                }
            }

            // Collapsible result
            if (!tc.result.isNullOrBlank()) {
                var showFullResult by remember { mutableStateOf(false) }
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.clickable { expandedResult = !expandedResult },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (expandedResult) "▼" else "▶",
                        fontSize = 10.sp,
                        color = AppColors.TextSecondary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("结果", fontSize = 11.sp, color = AppColors.TextSecondary)
                }
                if (expandedResult) {
                    val displayResult = trimToolResult(tc.result!!)
                    val isTruncated = displayResult != tc.result
                    Text(
                        displayResult,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AppColors.TextSecondary,
                        modifier = Modifier.padding(start = 14.dp, top = 2.dp)
                    )
                    if (isTruncated) {
                        Text(
                            "查看完整结果",
                            fontSize = 11.sp,
                            color = AppColors.Accent,
                            modifier = Modifier.padding(start = 14.dp, top = 2.dp).clickable { showFullResult = true }
                        )
                    }
                }
                if (showFullResult) {
                    val rawWindowState = rememberWindowState(
                        position = WindowPosition.Aligned(Alignment.Center),
                        width = 600.dp,
                        height = 400.dp
                    )
                    Window(
                        onCloseRequest = { showFullResult = false },
                        title = "工具结果 - ${tc.name}",
                        state = rawWindowState,
                        resizable = true
                    ) {
                        (window as? java.awt.Window)?.minimumSize = java.awt.Dimension(400, 300)
                        val rawScrollState = rememberScrollState()
                        Row(Modifier.fillMaxSize()) {
                            Box(
                                Modifier.weight(1f).fillMaxHeight().background(AppColors.Surface).padding(16.dp).verticalScroll(rawScrollState)
                            ) {
                                SelectionContainer {
                                    Text(
                                        tc.result!!,
                                        style = AppTheme.typography.mono,
                                        color = AppColors.TextPrimary
                                    )
                                }
                            }
                            VerticalScrollbar(
                                modifier = Modifier.width(8.dp).padding(vertical = 2.dp),
                                adapter = rememberScrollbarAdapter(scrollState = rawScrollState)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun trimToolResult(result: String, maxLines: Int = 5): String {
    val lines = result.split("\n").flatMap { wrapLine(it, 80) }
    return if (lines.size > maxLines) {
        lines.take(maxLines).joinToString("\n") + "\n... (共 ${lines.size} 行)"
    } else lines.joinToString("\n")
}

private fun wrapLine(line: String, maxChars: Int): List<String> {
    if (line.length <= maxChars) return listOf(line)
    val result = mutableListOf<String>()
    var start = 0
    while (start < line.length) {
        val end = minOf(start + maxChars, line.length)
        result.add(line.substring(start, end))
        start = end
    }
    return result
}
