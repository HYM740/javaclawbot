package gui.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gui.ui.LogEntry
import gui.ui.LogWatcher
import gui.ui.theme.AppColors
import gui.ui.theme.AppTheme
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentLinkedQueue

private const val MAX_LOG_LINES = 1000
private const val POLL_INTERVAL_MS = 60L

@Composable
fun DevConsolePage(modifier: Modifier = Modifier) {
    val logBuffer = remember { mutableStateListOf<LogEntry>() }
    val threadSafeBuffer = remember { ConcurrentLinkedQueue<LogEntry>() }
    val logWatcher = remember { LogWatcher(threadSafeBuffer) }

    LaunchedEffect(Unit) {
        logWatcher.start()
        while (true) {
            delay(POLL_INTERVAL_MS)
            var polled = threadSafeBuffer.poll()
            while (polled != null) {
                if (logBuffer.size >= MAX_LOG_LINES) {
                    logBuffer.removeAt(0)
                }
                logBuffer.add(polled)
                polled = threadSafeBuffer.poll()
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().background(AppColors.Background)
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            itemsIndexed(logBuffer, key = { index, _ -> index }) { _, entry ->
                LogEntryRow(entry)
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: LogEntry) {
    val annotatedText = buildAnnotatedString {
        append("[${entry.timestamp()}] ")
        val level = entry.level()
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
        append(" ${entry.logger()} - ${entry.message()}")
    }

    Text(
        annotatedText,
        style = AppTheme.typography.mono,
        fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().background(
            when (entry.level()) {
                "ERROR" -> Color(0xFFFEE2E2)
                "WARN" -> Color(0xFFFEF3C7)
                else -> Color.Transparent
            }
        ).padding(horizontal = 8.dp, vertical = 2.dp)
    )
}
