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
        Text("Agent \u7BA1\u7406", style = AppTheme.typography.title)
        Text(
            "\u7BA1\u7406\u548C\u914D\u7F6E AI Agent",
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
                "\u6A21\u578B" to (defaults?.model ?: "\u672A\u914D\u7F6E"),
                "\u5FEB\u901F\u6A21\u578B" to (defaults?.fastModel?.takeIf { it.isNotBlank() } ?: "\u672A\u914D\u7F6E"),
                "\u63D0\u4F9B\u65B9" to (defaults?.provider?.takeIf { it.isNotBlank() } ?: "auto"),
                "\u6700\u5927\u5DE5\u5177\u8FED\u4EE3" to (defaults?.maxToolIterations?.toString() ?: "5"),
                "\u8BB0\u5FC6\u7A97\u53E3" to (defaults?.memoryWindow?.toString() ?: "10"),
                "\u8D85\u65F6\uFF08\u79D2\uFF09" to (defaults?.timeoutSeconds?.toString() ?: "300"),
                "\u6700\u5927\u5E76\u53D1" to (defaults?.maxConcurrent?.toString() ?: "3"),
                "\u63A8\u7406\u52AA\u529B" to (defaults?.reasoningEffort?.takeIf { it.isNotBlank() } ?: "\u9ED8\u8BA4"),
                "\u5F00\u53D1\u6A21\u5F0F" to if (defaults?.isDevelopment == true) "\u5DF2\u542F\u7528" else "\u672A\u542F\u7528",
                "\u81EA\u52A8\u538B\u7F29" to if (defaults?.isAutoCompactEnabled == true) "\u5DF2\u542F\u7528" else "\u672A\u542F\u7528"
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
