package gui.ui.pages

import androidx.compose.foundation.background
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
import gui.ui.theme.*

@Composable
fun CronPage(bridge: Bridge?, modifier: Modifier = Modifier) {
    val jobs = remember(bridge) {
        try {
            bridge?.cronService?.listJobs(true) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    Column(
        Modifier.fillMaxSize().background(AppColors.Background).padding(40.dp, 24.dp, 24.dp, 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("定时任务", style = AppTheme.typography.title)
        Text(
            "管理 Cron 定时任务",
            style = AppTheme.typography.caption,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(32.dp))

        LazyColumn(
            Modifier.widthIn(max = 800.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            jobs.forEach { job ->
                item {
                    val name = try { job.name } catch (_: Exception) { "?" }
                    val enabled = try { job.isEnabled } catch (_: Exception) { false }
                    val scheduleKind = try { job.schedule?.kind?.toString() } catch (_: Exception) { "?" }
                    val scheduleExpr = try { job.schedule?.expr } catch (_: Exception) { null }
                    val nextRun = try { job.state?.nextRunAtMs } catch (_: Exception) { null }

                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(AppColors.Surface).padding(12.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(name, fontWeight = FontWeight.Bold, style = AppTheme.typography.body)
                            Text(
                                if (enabled) "已启用" else "已禁用",
                                style = AppTheme.typography.caption,
                                color = if (enabled) AppColors.StatusOK else AppColors.TextSecondary
                            )
                        }
                        Text(
                            "$scheduleKind${if (scheduleExpr != null) " - $scheduleExpr" else ""}",
                            style = AppTheme.typography.caption,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        if (nextRun != null && nextRun > 0) {
                            Text(
                                "下次执行: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(nextRun))}",
                                style = AppTheme.typography.caption
                            )
                        }
                    }
                }
            }
            if (jobs.isEmpty()) {
                item {
                    Text("暂无定时任务", style = AppTheme.typography.caption,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp))
                }
            }
        }
    }
}
