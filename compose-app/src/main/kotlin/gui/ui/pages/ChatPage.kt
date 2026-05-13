package gui.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
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
    chatId: String = "direct",
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

    LaunchedEffect(bridge, chatId) {
        bridge ?: return@LaunchedEffect
        val model = bridge.config?.agents?.defaults?.model ?: ""
        statusText = "● 模型就绪 · $model"
        contextUsage = bridge.getContextUsageRatio(chatId).toFloat()
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1)
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

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
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
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(8.dp).padding(end = 2.dp),
                adapter = rememberScrollbarAdapter(scrollState = listState)
            )
        }

        ChatInput(
            sending = isLoading,
            statusText = statusText,
            contextUsage = contextUsage,
            messages = messages.filter { it.role == ChatMessage.Role.USER }.map { it.content },
            onSend = { text, mediaPaths ->
                val model = bridge?.config?.agents?.defaults?.model ?: ""
                statusText = "● 思考中..."
                contextUsage = bridge?.getContextUsageRatio(chatId)?.toFloat() ?: 0f

                // 本轮对话的消息列表（快照），在进度事件中直接追加/修改
                val exchangeMsgs = latestMessages.toMutableList()
                // 添加用户消息
                val userMsg = ChatMessage(
                    id = "user_${System.currentTimeMillis()}",
                    role = ChatMessage.Role.USER,
                    content = text
                )
                exchangeMsgs.add(userMsg)
                onMessagesChanged(exchangeMsgs.toList())

                bridge?.sendMessage(
                    text = text,
                    mediaPaths = mediaPaths,
                    onProgress = { progress ->
                        if (progress.isToolHint && progress.toolName != null) {
                            // 每个工具独立 hint（来自 AgentLoop 执行前发布的 OutboundMessage），
                            // 跳过 toolName==null 的旧版拼接提示
                            exchangeMsgs.add(ChatMessage(
                                id = "tool_${System.currentTimeMillis()}_${progress.toolName}_${progress.content.hashCode().toString().take(8)}",
                                role = ChatMessage.Role.ASSISTANT,
                                content = "",
                                toolCalls = listOf(ToolCall(
                                    name = progress.toolName,
                                    status = ToolStatus.RUNNING,
                                    params = progress.content,
                                    toolCallId = progress.toolCallId
                                ))
                            ))
                            onMessagesChanged(exchangeMsgs.toList())
                        } else if (progress.isSubagentProgress) {
                            val parentToolCallId = progress.parentToolCallId
                            if (parentToolCallId != null) {
                                val parentMsgIdx = exchangeMsgs.indexOfLast { msg ->
                                    msg.toolCalls.any { it.toolCallId == parentToolCallId }
                                }
                                if (parentMsgIdx >= 0) {
                                    val parentMsg = exchangeMsgs[parentMsgIdx]
                                    val updatedCalls = parentMsg.toolCalls.map { call ->
                                        if (call.toolCallId == parentToolCallId) {
                                            val exists = call.subCalls.indexOfFirst { it.taskId == progress.subagentTaskId }
                                            val updatedSubCalls = if (exists >= 0) {
                                                call.subCalls.toMutableList().also { list ->
                                                    list[exists] = list[exists].copy(
                                                        toolName = progress.subagentToolName ?: list[exists].toolName,
                                                        toolParams = progress.subagentToolParams ?: list[exists].toolParams,
                                                        toolResult = progress.subagentToolResult ?: list[exists].toolResult,
                                                        status = when (progress.subagentStatus) {
                                                            "completed" -> ToolStatus.COMPLETED
                                                            "error" -> ToolStatus.ERROR
                                                            else -> ToolStatus.RUNNING
                                                        },
                                                        toolCallId = progress.subagentToolCallId ?: list[exists].toolCallId,
                                                        iteration = progress.subagentIteration
                                                    )
                                                }
                                            } else {
                                                call.subCalls + SubagentCall(
                                                    taskId = progress.subagentTaskId ?: "",
                                                    agentType = progress.subagentType ?: "",
                                                    toolName = progress.subagentToolName ?: "",
                                                    toolParams = progress.subagentToolParams,
                                                    toolResult = progress.subagentToolResult,
                                                    status = ToolStatus.RUNNING,
                                                    toolCallId = progress.subagentToolCallId,
                                                    iteration = progress.subagentIteration
                                                )
                                            }
                                            call.copy(subCalls = updatedSubCalls)
                                        } else call
                                    }
                                    exchangeMsgs[parentMsgIdx] = parentMsg.copy(toolCalls = updatedCalls)
                                    onMessagesChanged(exchangeMsgs.toList())
                                }
                            }
                        } else if (progress.isToolResult) {
                            val resultContent = progress.content ?: ""
                            val tn = progress.toolName
                            val lastToolIdx = exchangeMsgs.indexOfLast { msg ->
                                msg.toolCalls.any { it.status == ToolStatus.RUNNING && (tn == null || it.name == tn) }
                            }
                            if (lastToolIdx >= 0) {
                                val lastMsg = exchangeMsgs[lastToolIdx]
                                val newStatus = if (progress.isToolError) ToolStatus.ERROR else ToolStatus.COMPLETED
                                val updatedCalls = lastMsg.toolCalls.map { call ->
                                    if (call.status == ToolStatus.RUNNING && (tn == null || call.name == tn)) {
                                        call.copy(status = newStatus, result = resultContent)
                                    } else call
                                }
                                exchangeMsgs[lastToolIdx] = lastMsg.copy(toolCalls = updatedCalls)
                                onMessagesChanged(exchangeMsgs.toList())
                            }
                        }
                    },
                    onResponse = { response ->
                        statusText = "● 模型就绪 · $model"
                        contextUsage = bridge?.getContextUsageRatio(chatId)?.toFloat() ?: 0f
                        exchangeMsgs.add(ChatMessage(
                            id = "ai_${System.currentTimeMillis()}",
                            role = ChatMessage.Role.ASSISTANT,
                            content = response,
                            reasoning = bridge?.getLastReasoningContent(chatId)
                        ))
                        onMessagesChanged(exchangeMsgs.toList())
                    },
                    onError = { error ->
                        statusText = "● 错误"
                        contextUsage = bridge?.getContextUsageRatio(chatId)?.toFloat() ?: 0f
                        exchangeMsgs.add(ChatMessage(
                            id = "err_${System.currentTimeMillis()}",
                            role = ChatMessage.Role.SYSTEM,
                            content = "⚠ $error"
                        ))
                        onMessagesChanged(exchangeMsgs.toList())
                    },
                    chatId = chatId
                )
            },
            onStop = { bridge?.stopMessage(chatId) }
        )
    }
}
