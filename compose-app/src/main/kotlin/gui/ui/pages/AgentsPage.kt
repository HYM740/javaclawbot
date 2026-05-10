package gui.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import gui.ui.Bridge
import gui.ui.theme.*

@Composable
fun AgentsPage(bridge: Bridge?, modifier: Modifier = Modifier) {
    val defaults = bridge?.config?.agents?.defaults

    Column(
        Modifier.fillMaxSize().background(AppColors.Background).padding(40.dp, 24.dp, 24.dp, 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Agent 管理", style = AppTheme.typography.title)
        Text(
            "管理和配置 AI Agent",
            style = AppTheme.typography.caption,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(32.dp))

        Column(
            Modifier.widthIn(max = 800.dp).fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)).background(AppColors.Surface).padding(16.dp)
        ) {
            Text("default", fontWeight = FontWeight.Bold, style = AppTheme.typography.body)
            Spacer(Modifier.height(12.dp))

            val rows = listOf(
                "模型" to (defaults?.model ?: "未配置"),
                "快速模型" to (defaults?.fastModel?.takeIf { it.isNotBlank() } ?: "未配置"),
                "提供方" to (defaults?.provider?.takeIf { it.isNotBlank() } ?: "auto"),
                "最大工具迭代" to (defaults?.maxToolIterations?.toString() ?: "5"),
                "记忆窗口" to (defaults?.memoryWindow?.toString() ?: "10"),
                "超时（秒）" to (defaults?.timeoutSeconds?.toString() ?: "300"),
                "最大并发" to (defaults?.maxConcurrent?.toString() ?: "3"),
                "推理努力" to (defaults?.reasoningEffort?.takeIf { it.isNotBlank() } ?: "默认"),
                "开发模式" to if (defaults?.isDevelopment == true) "已启用" else "未启用",
                "自动压缩" to if (defaults?.isAutoCompactEnabled == true) "已启用" else "未启用"
            )

            rows.forEach { (label, value) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, style = AppTheme.typography.caption, color = AppColors.TextSecondary)
                    Text(value, style = AppTheme.typography.body)
                }
            }
        }
    }
}
