package gui.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import gui.ui.Bridge
import gui.ui.dialogs.AddMcpServerDialog
import gui.ui.theme.*

@Composable
fun McpPage(bridge: Bridge?, modifier: Modifier = Modifier) {
    var showAddDialog by remember { mutableStateOf(false) }
    val mcpServers = remember(bridge) {
        try {
            bridge?.config?.tools?.mcpServers ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    Column(
        Modifier.fillMaxSize().background(AppColors.Background).padding(40.dp, 24.dp, 24.dp, 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.widthIn(max = 800.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("MCP \u670D\u52A1\u5668", style = AppTheme.typography.title)
            Text(
                "+ \u6DFB\u52A0",
                color = AppColors.Accent,
                style = AppTheme.typography.body,
                modifier = Modifier.clickable { showAddDialog = true }
                    .clip(RoundedCornerShape(8.dp)).background(AppColors.HoverBg).padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        Text(
            "\u7BA1\u7406 MCP \u670D\u52A1\u5668\u8FDE\u63A5",
            style = AppTheme.typography.caption,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(32.dp))

        LazyColumn(
            Modifier.widthIn(max = 800.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            mcpServers.forEach { (name, cfg) ->
                item {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(AppColors.Surface).padding(12.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(name, fontWeight = FontWeight.Bold, style = AppTheme.typography.body)
                            val status = bridge?.getMcpStatus(name) ?: "?"
                            Text(status, style = AppTheme.typography.caption, color = AppColors.TextSecondary)
                        }
                        val cmd = try { cfg.command } catch (_: Exception) { null }
                        if (cmd != null) {
                            Text("command: $cmd", style = AppTheme.typography.caption,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            if (mcpServers.isEmpty()) {
                item {
                    Text("\u6682\u65E0 MCP \u670D\u52A1\u5668", style = AppTheme.typography.caption,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp))
                }
            }
        }
    }

    if (showAddDialog) {
        AddMcpServerDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, command ->
                bridge?.addMcpServer(name, command)
                showAddDialog = false
            }
        )
    }
}
