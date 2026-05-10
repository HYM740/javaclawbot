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
            ChannelEntry("\u98DE\u4E66", try { ch.feishu.isEnabled } catch (_: Exception) { false }),
            ChannelEntry("\u4F01\u4E1A\u5FAE\u4FE1", try { ch.mochat.isEnabled } catch (_: Exception) { false }),
            ChannelEntry("Discord", try { ch.discord.isEnabled } catch (_: Exception) { false }),
            ChannelEntry("\u7535\u5B50\u90AE\u4EF6", try { ch.email.isEnabled } catch (_: Exception) { false }),
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
        Text("\u6E20\u9053\u7BA1\u7406", style = AppTheme.typography.title)
        Text(
            "\u7BA1\u7406\u6D88\u606F\u6E20\u9053\u914D\u7F6E",
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
                            if (entry.enabled) "\u5DF2\u542F\u7528" else "\u672A\u542F\u7528",
                            style = AppTheme.typography.caption,
                            color = if (entry.enabled) AppColors.StatusOK else AppColors.TextSecondary
                        )
                    }
                }
            }
        }
    }
}
