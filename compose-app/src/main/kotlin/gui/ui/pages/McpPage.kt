package gui.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gui.ui.Bridge
import gui.ui.theme.*
import gui.ui.windows.McpEditData
import gui.ui.windows.McpServerWindow
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("McpPage")

@Composable
fun McpPage(bridge: Bridge?, modifier: Modifier = Modifier) {
    var showAddWindow by remember { mutableStateOf(false) }
    var editData by remember { mutableStateOf<McpEditData?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    val mcpServers = remember(bridge, refreshKey) {
        try {
            bridge?.config?.tools?.mcpServers ?: emptyMap()
        } catch (e: Exception) {
            log.warn("Failed to read MCP servers config", e)
            emptyMap()
        }
    }

    Column(
        Modifier.fillMaxSize().background(AppColors.Background).padding(40.dp, 24.dp, 24.dp, 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.widthIn(max = 800.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("MCP 服务器", style = AppTheme.typography.title)
            Text(
                "+ 添加",
                color = AppColors.Accent,
                style = AppTheme.typography.body,
                modifier = Modifier.clickable { showAddWindow = true }
                    .clip(RoundedCornerShape(8.dp)).background(AppColors.HoverBg).padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        Text(
            "管理 MCP 服务器连接",
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
                    val cmd = try { cfg.command } catch (e: Exception) { log.debug("Failed to read command for MCP server: $name", e); null } ?: ""
                    val status = bridge?.getMcpStatus(name) ?: "?"
                    val isConnected = status.contains("connected", ignoreCase = true) || status.contains("READY", ignoreCase = true)
                    val isError = status.contains("error", ignoreCase = true) || status.contains("FAILED", ignoreCase = true)
                    val statusColor = when {
                        isConnected -> AppColors.StatusOK
                        isError -> AppColors.StatusError
                        else -> AppColors.TextSecondary
                    }
                    val statusText = when {
                        isConnected -> "已连接"
                        isError -> "错误"
                        else -> status
                    }

                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(AppColors.Surface).padding(12.dp)
                    ) {
                        // Header: name + status
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(name, fontWeight = FontWeight.Bold, style = AppTheme.typography.body)
                            Text(statusText, style = AppTheme.typography.caption, color = statusColor)
                        }

                        if (cmd.isNotEmpty()) {
                            Text(
                                "command: $cmd", style = AppTheme.typography.caption,
                                modifier = Modifier.padding(top = 4.dp),
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Action buttons
                        Row(
                            Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TextButton(onClick = {
                                editData = McpEditData(
                                    oldName = name, name = name, command = cmd
                                )
                            }) { Text("编辑", color = AppColors.Accent, fontSize = 13.sp) }

                            TextButton(onClick = {
                                try {
                                    bridge?.refreshMcpTools()
                                    refreshKey++
                                    log.info("Reloaded MCP tools")
                                } catch (e: Exception) {
                                    log.warn("重载 MCP 工具失败", e)
                                    statusMessage = "重载失败: ${e.message}"
                                }
                            }) { Text("重载", color = AppColors.StatusWarn, fontSize = 13.sp) }

                            Spacer(Modifier.weight(1f))

                            TextButton(onClick = { deleteTarget = name }) {
                                Text("删除", color = AppColors.StatusError, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            if (mcpServers.isEmpty()) {
                item {
                    Text("暂无 MCP 服务器", style = AppTheme.typography.caption,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp))
                }
            }
        }
    }

    // Add window
    if (showAddWindow) {
        McpServerWindow(
            bridge = bridge,
            editData = null,
            onDismiss = { showAddWindow = false },
            onSaved = { showAddWindow = false; refreshKey++ }
        )
    }

    // Edit window
    editData?.let { data ->
        McpServerWindow(
            bridge = bridge,
            editData = data,
            onDismiss = { editData = null },
            onSaved = { editData = null; refreshKey++ }
        )
    }

    // Delete confirmation overlay
    deleteTarget?.let { target ->
        Box(
            Modifier.fillMaxSize().background(Color(0x80000000)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.widthIn(max = 360.dp).clip(RoundedCornerShape(12.dp))
                    .background(AppColors.Surface).padding(24.dp)
            ) {
                Text("确认删除", fontWeight = FontWeight.Bold, style = AppTheme.typography.body)
                Spacer(Modifier.height(8.dp))
                Text("确定要删除 MCP 服务器「$target」吗？此操作不可撤销。", style = AppTheme.typography.caption)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { deleteTarget = null }) {
                        Text("取消", color = AppColors.TextSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        try {
                            bridge?.deleteMcpServer(target)
                            deleteTarget = null
                            refreshKey++
                        } catch (e: Exception) {
                            log.warn("删除 MCP 服务器失败: $target", e)
                            statusMessage = "删除失败: ${e.message}"
                        }
                    }) {
                        Text("删除", color = AppColors.StatusError)
                    }
                }
            }
        }
    }
}
