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
        Text("\u8BBE\u7F6E", style = AppTheme.typography.title)
        Text(
            "\u5E94\u7528\u914D\u7F6E\u4E0E\u72B6\u6001",
            style = AppTheme.typography.caption,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(32.dp))

        Column(
            Modifier.widthIn(max = 800.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Model section
            SectionCard("\u6A21\u578B\u914D\u7F6E") {
                val model = bridge?.config?.agents?.defaults?.model ?: "\u672A\u914D\u7F6E"
                InfoRow("\u9ED8\u8BA4\u6A21\u578B", model)
                val provider = bridge?.config?.let {
                    try { it.getProviderName(model) } catch (_: Exception) { null }
                } ?: "auto"
                InfoRow("\u63D0\u4F9B\u65B9", provider)
            }

            // API Key section
            SectionCard("API \u5BC6\u94A5") {
                val providers = listOf("anthropic", "openai", "deepseek")
                providers.forEach { pn ->
                    val pc = bridge?.config?.providers?.getByName(pn)
                    val hasKey = try { pc?.apiKey?.isNotBlank() == true } catch (_: Exception) { false }
                    InfoRow(labelFor(pn), if (hasKey) "\u2605 \u5DF2\u914D\u7F6E" else "\u672A\u914D\u7F6E")
                }
            }

            // Channel status section
            SectionCard("\u6E20\u9053\u72B6\u6001") {
                val channels = bridge?.config?.channels
                InfoRow("Telegram", statusText(try { channels?.telegram?.isEnabled } catch (_: Exception) { false }))
                InfoRow("\u98DE\u4E66", statusText(try { channels?.feishu?.isEnabled } catch (_: Exception) { false }))
                InfoRow("\u7535\u5B50\u90AE\u4EF6", statusText(try { channels?.email?.isEnabled } catch (_: Exception) { false }))
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

private fun statusText(enabled: Boolean?) = if (enabled == true) "\u5DF2\u542F\u7528" else "\u672A\u542F\u7528"
