package gui.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gui.ui.LogEntry
import gui.ui.LogWatcher
import gui.ui.theme.AppColors
import gui.ui.theme.AppTheme
import gui.ui.theme.CjkFontResolver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

private const val MAX_LOG_LINES = 1000
private const val POLL_INTERVAL_MS = 60L

@Composable
fun DevConsoleScreen(modifier: Modifier = Modifier) {
    val logBuffer = remember { mutableStateListOf<LogEntry>() }
    val threadSafeBuffer = remember { ConcurrentLinkedQueue<LogEntry>() }
    val logWatcher = remember { LogWatcher(threadSafeBuffer) }

    var levelFilter by remember { mutableStateOf("ALL") }
    var searchTerm by remember { mutableStateOf("") }
    var autoScroll by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    var levelExpanded by remember { mutableStateOf(false) }
    val levels = listOf("ALL", "TRACE", "DEBUG", "INFO", "WARN", "ERROR")
    val scope = rememberCoroutineScope()
    val filteredLogs = logBuffer.filter { entry ->
        (levelFilter == "ALL" || entry.level() == levelFilter)
    }
    var isWatching by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        logWatcher.start()
        isWatching = true
        while (true) {
            delay(POLL_INTERVAL_MS)
            var polled = threadSafeBuffer.poll()
            var added = false
            while (polled != null) {
                if (logBuffer.size >= MAX_LOG_LINES) {
                    logBuffer.removeAt(0)
                }
                logBuffer.add(polled)
                added = true
                polled = threadSafeBuffer.poll()
            }
            if (added && autoScroll && logBuffer.isNotEmpty()) {
                listState.animateScrollToItem(logBuffer.size - 1)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { logWatcher.stop() }
    }

    Column(
        modifier = modifier.fillMaxSize().background(AppColors.Background)
    ) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(Color(0xFFEAE8E1))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Level filter dropdown
            Box {
                Row(
                    Modifier.clip(RoundedCornerShape(6.dp))
                        .background(Color.White)
                        .clickable { levelExpanded = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("级别: $levelFilter", fontSize = 12.sp, color = AppColors.TextPrimary)
                }
                DropdownMenu(
                    expanded = levelExpanded,
                    onDismissRequest = { levelExpanded = false }
                ) {
                    levels.forEach { level ->
                        DropdownMenuItem(
                            onClick = {
                                levelFilter = level
                                levelExpanded = false
                            }
                        ) {
                            Text(level, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Search field
            BasicTextField(
                value = searchTerm,
                onValueChange = { newSearch ->
                    searchTerm = newSearch
                    if (newSearch.isNotBlank()) {
                        val idx = filteredLogs.indexOfFirst { entry ->
                            entry.message().contains(newSearch, ignoreCase = true)
                        }
                        if (idx >= 0) {
                            scope.launch { listState.animateScrollToItem(idx) }
                        }
                    }
                },
                modifier = Modifier.width(160.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                textStyle = TextStyle(fontSize = 12.sp, color = AppColors.TextPrimary),
                singleLine = true,
                decorationBox = { inner ->
                    Box {
                        if (searchTerm.isEmpty()) {
                            Text("搜索日志...", fontSize = 12.sp, color = AppColors.TextSecondary)
                        }
                        inner()
                    }
                }
            )

            // Auto-scroll toggle
            Row(
                Modifier.clip(RoundedCornerShape(6.dp))
                    .background(if (autoScroll) Color.White else Color(0xFFE5E7EB))
                    .clickable { autoScroll = !autoScroll }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📜", fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                Text("自动滚动", fontSize = 12.sp, color = AppColors.TextPrimary)
            }

            // Clear button
            Row(
                Modifier.clip(RoundedCornerShape(6.dp))
                    .background(Color.White)
                    .clickable {
                        threadSafeBuffer.clear()
                        logBuffer.clear()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("✕", fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                Text("清除", fontSize = 12.sp, color = AppColors.TextPrimary)
            }

            // Export button
            Row(
                Modifier.clip(RoundedCornerShape(6.dp))
                    .background(Color.White)
                    .clickable { exportLogs(filteredLogs) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("↧", fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                Text("导出", fontSize = 12.sp, color = AppColors.TextPrimary)
            }
        }

        SelectionContainer {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    itemsIndexed(filteredLogs, key = { _, entry -> entry }) { _, entry ->
                        LogEntryRow(entry, searchTerm)
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.width(8.dp).fillMaxHeight().padding(vertical = 2.dp),
                    adapter = rememberScrollbarAdapter(scrollState = listState)
                )
            }
        }

        // Status bar
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(Color(0xFFEAE8E1))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "📄 ~/.javaclawbot/logs/app.log",
                fontSize = 11.sp, color = Color(0xFF6B7280)
            )
            Text(
                "共 ${filteredLogs.size} 行",
                fontSize = 11.sp, color = Color(0xFF6B7280)
            )
            Text(
                if (isWatching) "● 监听中" else "● 未启动",
                fontSize = 11.sp,
                color = if (isWatching) Color(0xFF16A34A) else Color(0xFF6B7280)
            )
        }
    }
}

@Composable
private fun LogEntryRow(entry: LogEntry, searchTerm: String) {
    val timestamp = entry.timestamp()
    val level = entry.level()
    val logger = entry.logger()
    val message = entry.message()

    val annotatedText = buildAnnotatedString {
        append("[$timestamp] ")
        val (bgColor, textColor) = when (level) {
            "ERROR" -> Color(0xFFDC2626) to Color.White
            "WARN" -> Color(0xFFF59E0B) to Color.White
            "INFO" -> Color(0xFFDBEAFE) to Color(0xFF1E40AF)
            else -> Color.Transparent to when (level) {
                "DEBUG" -> Color(0xFF6B7280)
                "TRACE" -> Color(0xFF9CA3AF)
                else -> Color(0xFF374151)
            }
        }
        withStyle(SpanStyle(background = bgColor, color = textColor, fontWeight = FontWeight.Bold, fontSize = 11.sp)) {
            append(" $level ")
        }
        append(" $logger - ")

        if (searchTerm.isNotBlank()) {
            var remaining = message
            while (remaining.isNotEmpty()) {
                val idx = remaining.indexOf(searchTerm, ignoreCase = true)
                if (idx < 0) {
                    append(remaining)
                    break
                }
                append(remaining.substring(0, idx))
                withStyle(SpanStyle(background = Color(0xFFFDE68A), color = Color(0xFF374151))) {
                    append(remaining.substring(idx, idx + searchTerm.length))
                }
                remaining = remaining.substring(idx + searchTerm.length)
            }
        } else {
            append(message)
        }
    }

    val logFont = CjkFontResolver.get()
    Text(
        annotatedText,
        style = AppTheme.typography.mono.copy(fontFamily = logFont),
        fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().background(
            when (level) {
                "ERROR" -> Color(0xFFFEE2E2)
                "WARN" -> Color(0xFFFEF3C7)
                else -> Color.Transparent
            }
        ).padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

private fun exportLogs(entries: List<LogEntry>) {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "导出日志")
    dialog.file = "app.log"
    dialog.mode = java.awt.FileDialog.SAVE
    dialog.isVisible = true
    if (dialog.file != null) {
        val file = java.io.File(dialog.directory, dialog.file)
        try {
            file.writeText(entries.joinToString("\n") { it.raw() })
        } catch (e: Exception) {
            // 导出失败静默处理
        }
    }
}
