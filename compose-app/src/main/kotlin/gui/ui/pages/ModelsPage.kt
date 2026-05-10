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
        Text("模型管理", style = AppTheme.typography.title)
        Text(
            "管理可用的 AI 模型提供商",
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
                            if (pc.apiKey?.isNotBlank() == true) "已配置" else "未配置 Key",
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
                                    Text("★", color = AppColors.Accent)
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
    "zhipu" -> "智谱 GLM"
    "dashscope" -> "阿里云 DashScope"
    "gemini" -> "Google Gemini"
    "moonshot" -> "Moonshot"
    "minimax" -> "MiniMax"
    "aihubmix" -> "AIHubMix"
    "siliconflow" -> "SiliconFlow"
    "volcengine" -> "火山引擎"
    "vllm" -> "vLLM"
    "githubCopilot" -> "GitHub Copilot"
    else -> name
}
