package gui.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import gui.ui.windows.DataSourceEditData
import gui.ui.windows.DataSourceWindow
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("DatabasesPage")

@Composable
fun DatabasesPage(bridge: Bridge?, modifier: Modifier = Modifier) {
    var showAddWindow by remember { mutableStateOf(false) }
    var editData by remember { mutableStateOf<DataSourceEditData?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    // Force refresh on data changes
    val datasources = remember(bridge, refreshKey) {
        try {
            bridge?.config?.tools?.db?.datasources ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    Column(
        Modifier.fillMaxSize().background(AppColors.Background).padding(40.dp, 24.dp, 24.dp, 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier.widthIn(max = 800.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("数据源管理", style = AppTheme.typography.title)
            Text(
                "+ 添加",
                color = AppColors.Accent,
                style = AppTheme.typography.body,
                modifier = Modifier.clickable { showAddWindow = true }
                    .clip(RoundedCornerShape(8.dp)).background(AppColors.HoverBg)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        Text(
            "管理 JDBC 数据源连接",
            style = AppTheme.typography.caption,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(32.dp))

        LazyColumn(
            Modifier.widthIn(max = 800.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            datasources.forEach { (name, cfg) ->
                item {
                    val jdbcUrl = try { cfg.jdbcUrl } catch (_: Exception) { null } ?: ""
                    val enable = try { cfg.isEnable } catch (_: Exception) { false }
                    val maxPoolSize = try { cfg.maxPoolSize } catch (_: Exception) { 5 }
                    val timeout = try { cfg.connectionTimeout } catch (_: Exception) { 30000L }
                    val status = bridge?.getDataSourceStatus(name) ?: "?"

                    val statusColor = when (status) {
                        "CONNECTED" -> AppColors.StatusOK
                        "DISABLED" -> AppColors.StatusWarn
                        else -> AppColors.StatusError
                    }
                    val statusText = when (status) {
                        "CONNECTED" -> "已连接"
                        "DISABLED" -> "已禁用"
                        else -> "断开"
                    }

                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(AppColors.Surface).padding(12.dp)
                    ) {
                        // Header row: name + status
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(8.dp).clip(CircleShape).background(statusColor)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(name, fontWeight = FontWeight.Bold, style = AppTheme.typography.body)
                            }
                            Text(statusText, style = AppTheme.typography.caption, color = statusColor)
                        }

                        // Info rows
                        if (jdbcUrl.isNotEmpty()) {
                            Text(
                                "URL: $jdbcUrl", style = AppTheme.typography.caption,
                                modifier = Modifier.padding(top = 4.dp),
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            Modifier.padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("Pool: $maxPoolSize", style = AppTheme.typography.caption, fontSize = 12.sp)
                            Text("Timeout: ${timeout}ms", style = AppTheme.typography.caption, fontSize = 12.sp)
                        }

                        // Action buttons
                        Row(
                            Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TextButton(onClick = {
                                editData = DataSourceEditData(
                                    oldName = name, name = name, jdbcUrl = jdbcUrl,
                                    username = try { cfg.username } catch (_: Exception) { "" } ?: "",
                                    maxPoolSize = maxPoolSize, connectionTimeout = timeout
                                )
                            }) { Text("编辑", color = AppColors.Accent, fontSize = 13.sp) }

                            TextButton(onClick = {
                                try {
                                    bridge?.reconnectDataSource(name)
                                    refreshKey++
                                } catch (e: Exception) {
                                    log.warn("重连数据源失败: $name", e)
                                    statusMessage = "重连失败: ${e.message}"
                                }
                            }) { Text("重连", color = AppColors.StatusWarn, fontSize = 13.sp) }

                            TextButton(onClick = {
                                try {
                                    bridge?.toggleDataSource(name, !enable)
                                    refreshKey++
                                } catch (e: Exception) {
                                    log.warn("切换数据源状态失败: $name", e)
                                    statusMessage = "操作失败: ${e.message}"
                                }
                            }) {
                                Text(
                                    if (enable) "⛔ 禁用" else "⚡ 启用",
                                    color = if (enable) AppColors.TextSecondary else AppColors.StatusOK,
                                    fontSize = 13.sp
                                )
                            }

                            Spacer(Modifier.weight(1f))

                            TextButton(onClick = { deleteTarget = name }) {
                                Text("删除", color = AppColors.StatusError, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            if (datasources.isEmpty()) {
                item {
                    Text("暂无数据源", style = AppTheme.typography.caption,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp))
                }
            }
        }
    }

    // Add window
    if (showAddWindow) {
        DataSourceWindow(
            bridge = bridge,
            editData = null,
            onDismiss = { showAddWindow = false },
            onSaved = { showAddWindow = false; refreshKey++ }
        )
    }

    // Edit window
    editData?.let { data ->
        DataSourceWindow(
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
                Text("确定要删除数据源「$target」吗？此操作不可撤销。", style = AppTheme.typography.caption)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { deleteTarget = null }) {
                        Text("取消", color = AppColors.TextSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        try {
                            bridge?.deleteDataSource(target)
                            deleteTarget = null
                            refreshKey++
                        } catch (e: Exception) {
                            log.warn("删除数据源失败: $target", e)
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
