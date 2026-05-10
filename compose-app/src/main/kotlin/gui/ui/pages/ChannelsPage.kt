package gui.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

private data class ChannelEntry(val label: String, val enabled: Boolean)

@Composable
fun ChannelsPage(bridge: Bridge?, modifier: Modifier = Modifier) {
    val entries = remember(bridge) {
        val ch = bridge?.config?.channels ?: return@remember emptyList<ChannelEntry>()
        listOf(
            ChannelEntry("Telegram", try { ch.telegram.isEnabled } catch (_: Exception) { false }),
            ChannelEntry("飞书", try { ch.feishu.isEnabled } catch (_: Exception) { false }),
            ChannelEntry("企业微信", try { ch.mochat.isEnabled } catch (_: Exception) { false }),
            ChannelEntry("Discord", try { ch.discord.isEnabled } catch (_: Exception) { false }),
            ChannelEntry("电子邮件", try { ch.email.isEnabled } catch (_: Exception) { false }),
            ChannelEntry("DingTalk", try { ch.dingtalk.isEnabled } catch (_: Exception) { false }),
            ChannelEntry("Slack", try { ch.slack.isEnabled } catch (_: Exception) { false }),
            ChannelEntry("QQ", try { ch.qq.isEnabled } catch (_: Exception) { false }),
            ChannelEntry("Matrix", try { ch.matrix.isEnabled } catch (_: Exception) { false }),
            ChannelEntry("WhatsApp", try { ch.whatsapp.isEnabled } catch (_: Exception) { false })
        )
    }

    Column(
        Modifier.fillMaxSize().background(AppColors.Background).padding(40.dp, 24.dp, 24.dp, 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("渠道管理", style = AppTheme.typography.title)
        Text(
            "管理消息渠道配置",
            style = AppTheme.typography.caption,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(32.dp))

        LazyColumn(
            Modifier.widthIn(max = 800.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entries) { entry ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(AppColors.Surface).padding(12.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(entry.label, fontWeight = FontWeight.Bold, style = AppTheme.typography.body)
                        Text(
                            if (entry.enabled) "已启用" else "未启用",
                            style = AppTheme.typography.caption,
                            color = if (entry.enabled) AppColors.StatusOK else AppColors.TextSecondary
                        )
                    }
                }
            }
        }
    }
}
