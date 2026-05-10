package gui.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gui.ui.Bridge
import gui.ui.components.*
import gui.ui.model.*
import gui.ui.theme.AppColors
import gui.ui.theme.CjkFontResolver

enum class ViewMode { BUBBLE, LIST }

@Composable
fun ChatPage(
    bridge: Bridge?,
    messages: List<ChatMessage>,
    onMessagesChanged: (List<ChatMessage>) -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    var viewMode by remember { mutableStateOf(ViewMode.BUBBLE) }
    val listState = rememberLazyListState()
    var statusText by remember { mutableStateOf("") }
    var contextUsage by remember { mutableStateOf(0f) }
    // 保持最新消息引用，避免异步 lambda 捕获过时值
    var latestMessages by remember { mutableStateOf(messages) }
    SideEffect { latestMessages = messages }

    LaunchedEffect(bridge) {
        bridge ?: return@LaunchedEffect
        val model = bridge.config?.agents?.defaults?.model ?: ""
        statusText = "● 模型就绪 · $model"
        contextUsage = bridge.getContextUsageRatio().toFloat()
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Toggle row
        Row(
            Modifier.fillMaxWidth().padding(end = 16.dp, top = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (viewMode == ViewMode.BUBBLE) "☁ 气泡" else "☰ 列表",
                color = AppColors.TextSecondary,
                style = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 13.sp),
                modifier = Modifier.padding(end = 8.dp)
            )
            androidx.compose.material.TextButton(onClick = {
                viewMode = if (viewMode == ViewMode.BUBBLE) ViewMode.LIST else ViewMode.BUBBLE
            }) {
                Text(
                    if (viewMode == ViewMode.BUBBLE) "☰ 列表" else "☁ 气泡",
                    color = AppColors.Accent
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                when (viewMode) {
                    ViewMode.BUBBLE -> MessageBubble(msg)
                    ViewMode.LIST -> MessageList(msg)
                }
            }
        }

        ChatInput(
            sending = isLoading,
            statusText = statusText,
            contextUsage = contextUsage,
            messages = messages.filter { it.role == ChatMessage.Role.USER }.map { it.content },
            onSend = { text, mediaPaths ->
                val model = bridge?.config?.agents?.defaults?.model ?: ""
                statusText = "● 思考中..."
                contextUsage = bridge?.getContextUsageRatio()?.toFloat() ?: 0f
                bridge?.sendMessage(
                    text = text,
                    mediaPaths = mediaPaths,
                    onProgress = { /* handled externally */ },
                    onResponse = { response ->
                        statusText = "● 模型就绪 · $model"
                        contextUsage = bridge?.getContextUsageRatio()?.toFloat() ?: 0f
                        onMessagesChanged(latestMessages + ChatMessage(
                            id = "ai_${System.currentTimeMillis()}",
                            role = ChatMessage.Role.ASSISTANT,
                            content = response,
                            reasoning = bridge?.lastReasoningContent
                        ))
                    },
                    onError = { error ->
                        statusText = "● 错误"
                        contextUsage = bridge?.getContextUsageRatio()?.toFloat() ?: 0f
                        onMessagesChanged(latestMessages + ChatMessage(
                            id = "err_${System.currentTimeMillis()}",
                            role = ChatMessage.Role.SYSTEM,
                            content = "⚠ $error"
                        ))
                    }
                )
                onMessagesChanged(latestMessages + ChatMessage(
                    id = "user_${System.currentTimeMillis()}",
                    role = ChatMessage.Role.USER,
                    content = text
                ))
            },
            onStop = { bridge?.stopMessage() }
        )
    }
}
