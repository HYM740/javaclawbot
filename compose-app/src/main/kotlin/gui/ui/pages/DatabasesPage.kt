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
import gui.ui.dialogs.AddDataSourceDialog
import gui.ui.theme.*

@Composable
fun DatabasesPage(bridge: Bridge?, modifier: Modifier = Modifier) {
    var showAddDialog by remember { mutableStateOf(false) }
    val datasources = remember(bridge) {
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
            Text("\u6570\u636E\u6E90\u7BA1\u7406", style = AppTheme.typography.title)
            Text(
                "+ \u6DFB\u52A0",
                color = AppColors.Accent,
                style = AppTheme.typography.body,
                modifier = Modifier.clickable { showAddDialog = true }
                    .clip(RoundedCornerShape(8.dp)).background(AppColors.HoverBg)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        Text(
            "\u7BA1\u7406 JDBC \u6570\u636E\u6E90\u8FDE\u63A5",
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
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(AppColors.Surface).padding(12.dp)
                    ) {
                        Text(name, fontWeight = FontWeight.Bold, style = AppTheme.typography.body)
                        val jdbcUrl = try { cfg.jdbcUrl } catch (_: Exception) { null }
                        val driver = try { cfg.driverClass } catch (_: Exception) { null }
                        if (jdbcUrl != null) {
                            Text("URL: $jdbcUrl", style = AppTheme.typography.caption, modifier = Modifier.padding(top = 4.dp))
                        }
                        if (driver != null) {
                            Text("Driver: $driver", style = AppTheme.typography.caption)
                        }
                    }
                }
            }
            if (datasources.isEmpty()) {
                item {
                    Text("\u6682\u65E0\u6570\u636E\u6E90", style = AppTheme.typography.caption,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp))
                }
            }
        }
    }

    if (showAddDialog) {
        AddDataSourceDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, url, username, password, driver, poolSize, timeout ->
                bridge?.addDataSource(name, url, username, password, driver, poolSize, timeout)
                showAddDialog = false
            }
        )
    }
}
