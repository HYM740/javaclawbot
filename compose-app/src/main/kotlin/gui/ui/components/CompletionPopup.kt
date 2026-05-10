package gui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import gui.ui.theme.AppColors
import gui.ui.theme.AppTheme
import java.nio.file.Files
import java.nio.file.Path

data class CompletionItem(val text: String, val description: String, val kind: Kind) {
    enum class Kind { COMMAND, FILE, DIR }
}

@Composable
fun CompletionPopup(
    query: String,
    workspacePath: Path?,
    projectPath: Path?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = remember(query) {
        when {
            query.startsWith("/") -> commandItems(query)
            query.startsWith("@") -> fileItems(query.drop(1), workspacePath, projectPath)
            else -> emptyList()
        }
    }

    if (items.isNotEmpty()) {
        Box(modifier.width(440.dp).shadow(4.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.Surface)) {
            LazyColumn {
                items(items.take(10)) { item ->
                    Row(Modifier.fillMaxWidth().clickable { onSelect(item.text) }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text(item.text, style = AppTheme.typography.body)
                        Spacer(Modifier.weight(1f))
                        Text(item.description, style = AppTheme.typography.caption)
                    }
                }
            }
        }
    }
}

private fun commandItems(query: String) = listOf(
    "/stop" to "停止", "/help" to "帮助", "/clear" to "清空上下文",
    "/memory" to "记忆", "/cc" to "压缩", "/config" to "配置"
).filter { it.first.startsWith(query, ignoreCase = true) }
 .map { CompletionItem(it.first, it.second, CompletionItem.Kind.COMMAND) }

private fun fileItems(partial: String, vararg roots: Path?) = buildList {
    for (root in roots.filterNotNull()) {
        try {
            val dir = if (partial.contains("/")) root.resolve(partial.substringBeforeLast("/")) else root
            if (Files.isDirectory(dir)) Files.list(dir).use { stream ->
                stream.filter { it.fileName.toString().startsWith(partial.substringAfterLast("/")) }
                    .limit(15).forEach { add(CompletionItem("@${it.toAbsolutePath()}",
                        if (Files.isDirectory(it)) "dir" else "file",
                        if (Files.isDirectory(it)) CompletionItem.Kind.DIR else CompletionItem.Kind.FILE)) }
            }
        } catch (_: Exception) {}
    }
}
