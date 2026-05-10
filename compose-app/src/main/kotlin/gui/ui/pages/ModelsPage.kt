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
fun ModelsPage(bridge: Bridge?, modifier: Modifier = Modifier) {
    Column(
        Modifier.fillMaxSize().background(AppColors.Background).padding(40.dp, 24.dp, 24.dp, 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("\u6A21\u578B\u7BA1\u7406", style = AppTheme.typography.title)
        Text(
            "\u7BA1\u7406\u53EF\u7528\u7684 AI \u6A21\u578B\u63D0\u4F9B\u5546",
            style = AppTheme.typography.caption,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(32.dp))
        LazyColumn(
            Modifier.widthIn(max = 800.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val providers = listOf(
                "anthropic", "openai", "deepseek", "openrouter", "groq", "zhipu",
                "dashscope", "gemini", "moonshot", "minimax", "aihubmix",
                "siliconflow", "volcengine", "vllm", "githubCopilot", "custom"
            )
            providers.forEach { pn ->
                val pc = bridge?.config?.providers?.getByName(pn) ?: return@forEach
                if (pc.modelConfigs.isEmpty()) return@forEach
                item {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(AppColors.Surface).padding(16.dp)
                    ) {
                        Text(labelFor(pn), fontWeight = FontWeight.Bold)
                        Text(
                            if (pc.apiKey?.isNotBlank() == true) "\u5DF2\u914D\u7F6E" else "\u672A\u914D\u7F6E Key",
                            style = AppTheme.typography.caption
                        )
                        Spacer(Modifier.height(8.dp))
                        pc.modelConfigs.take(5).forEach { mc ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("$pn/${mc.model}", style = AppTheme.typography.caption)
                                if (mc.model == bridge?.config?.agents?.defaults?.model) {
                                    Text("\u2605", color = AppColors.Accent)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun labelFor(name: String) = when (name) {
    "openai" -> "OpenAI"
    "anthropic" -> "Anthropic"
    "deepseek" -> "DeepSeek"
    "openrouter" -> "OpenRouter"
    "groq" -> "Groq"
    "zhipu" -> "\u667A\u8C31 GLM"
    "dashscope" -> "\u963F\u91CC\u4E91 DashScope"
    "gemini" -> "Google Gemini"
    "moonshot" -> "Moonshot"
    "minimax" -> "MiniMax"
    "aihubmix" -> "AIHubMix"
    "siliconflow" -> "SiliconFlow"
    "volcengine" -> "\u706B\u5C71\u5F15\u64CE"
    "vllm" -> "vLLM"
    "githubCopilot" -> "GitHub Copilot"
    else -> name
}
