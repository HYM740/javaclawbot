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
fun SettingsPage(bridge: Bridge?, modifier: Modifier = Modifier) {
    Column(
        Modifier.fillMaxSize().background(AppColors.Background).padding(40.dp, 24.dp, 24.dp, 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("设置", style = AppTheme.typography.title)
        Text(
            "应用配置与状态",
            style = AppTheme.typography.caption,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(32.dp))

        Column(
            Modifier.widthIn(max = 800.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Model section
            SectionCard("模型配置") {
                val model = bridge?.config?.agents?.defaults?.model ?: "未配置"
                InfoRow("默认模型", model)
                val provider = bridge?.config?.let {
                    try { it.getProviderName(model) } catch (_: Exception) { null }
                } ?: "auto"
                InfoRow("提供方", provider)
            }

            // API Key section
            SectionCard("API 密钥") {
                val providers = listOf("anthropic", "openai", "deepseek")
                providers.forEach { pn ->
                    val pc = bridge?.config?.providers?.getByName(pn)
                    val hasKey = try { pc?.apiKey?.isNotBlank() == true } catch (_: Exception) { false }
                    InfoRow(labelFor(pn), if (hasKey) "★ 已配置" else "未配置")
                }
            }

            // Channel status section
            SectionCard("渠道状态") {
                val channels = bridge?.config?.channels
                InfoRow("Telegram", statusText(try { channels?.telegram?.isEnabled } catch (_: Exception) { false }))
                InfoRow("飞书", statusText(try { channels?.feishu?.isEnabled } catch (_: Exception) { false }))
                InfoRow("电子邮件", statusText(try { channels?.email?.isEnabled } catch (_: Exception) { false }))
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(AppColors.Surface).padding(16.dp)
    ) {
        Text(title, fontWeight = FontWeight.Bold, style = AppTheme.typography.body)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = AppTheme.typography.caption, color = AppColors.TextSecondary)
        Text(value, style = AppTheme.typography.body)
    }
}

private fun labelFor(name: String) = when (name) {
    "openai" -> "OpenAI"
    "anthropic" -> "Anthropic"
    "deepseek" -> "DeepSeek"
    else -> name
}

private fun statusText(enabled: Boolean?) = if (enabled == true) "已启用" else "未启用"
