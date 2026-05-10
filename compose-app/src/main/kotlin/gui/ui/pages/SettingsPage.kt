package gui.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import gui.ui.theme.AppColors
import gui.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SettingsPage(bridge: Bridge?, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var fastModel by remember { mutableStateOf(bridge?.config?.agents?.defaults?.fastModel ?: "") }

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
            Modifier.widthIn(max = 800.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()),
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

                // Fast model selector
                Spacer(Modifier.height(8.dp))
                FastModelSelector(
                    bridge = bridge,
                    currentFast = fastModel,
                    onFastModelChanged = { newFast ->
                        fastModel = newFast
                    }
                )
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

            // Config editor button
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .background(AppColors.Surface)
                        .clickable {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val configPath = config.ConfigIO.getConfigPath()
                                    if (java.nio.file.Files.exists(configPath)) {
                                        val file = configPath.toFile()
                                        try {
                                            java.awt.Desktop.getDesktop().edit(file)
                                        } catch (_: Exception) {
                                            java.awt.Desktop.getDesktop().open(file)
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text("📄 编辑配置文件", style = AppTheme.typography.body)
                }
            }
        }
    }
}

@Composable
private fun FastModelSelector(
    bridge: Bridge?,
    currentFast: String,
    onFastModelChanged: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }

    // Collect all models
    val allModels = remember(bridge) {
        val list = mutableListOf("" to "无 (使用默认模型)")
        try {
            val cfg = bridge?.config ?: return@remember list
            val providerNames = listOf(
                "anthropic", "openai", "deepseek", "openrouter", "groq", "zhipu",
                "dashscope", "gemini", "moonshot", "minimax", "aihubmix",
                "siliconflow", "volcengine", "vllm", "githubCopilot", "custom"
            )
            for (pn in providerNames) {
                val pc = try { cfg.providers.getByName(pn) } catch (_: Exception) { null } ?: continue
                for (mc in pc.modelConfigs) {
                    val modelName = mc.model ?: continue
                    if (modelName.isNotBlank() && list.none { it.first == modelName }) {
                        list.add(modelName to modelName)
                    }
                }
            }
        } catch (_: Exception) {}
        list
    }

    val filtered = remember(searchText, allModels) {
        if (searchText.isBlank()) allModels
        else allModels.filter { (name, _) ->
            name.contains(searchText, ignoreCase = true) || name.isEmpty() && "无".contains(searchText)
        }
    }

    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("快速模型", fontWeight = FontWeight.Medium, style = AppTheme.typography.body)
                Text("标题生成等轻量级任务，留空则回退到默认模型",
                    style = AppTheme.typography.caption, color = AppColors.TextSecondary)
            }
            Spacer(Modifier.width(16.dp))
            Box {
                Box(
                    Modifier.width(260.dp).clip(RoundedCornerShape(10.dp))
                        .background(AppColors.HoverBg)
                        .clickable { expanded = !expanded; searchText = "" }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        if (currentFast.isBlank()) "无 (使用默认模型)" else currentFast,
                        style = AppTheme.typography.body
                    )
                }

                // Dropdown
                if (expanded) {
                    Column(
                        Modifier.width(260.dp).padding(top = 44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppColors.Surface)
                    ) {
                        // Search field
                        Box(
                            Modifier.fillMaxWidth().padding(8.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(AppColors.HoverBg)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            androidx.compose.foundation.text.BasicTextField(
                                value = searchText,
                                onValueChange = { searchText = it },
                                textStyle = AppTheme.typography.caption,
                                decorationBox = { inner ->
                                    if (searchText.isEmpty()) {
                                        Text("搜索模型...", style = AppTheme.typography.caption, color = AppColors.TextSecondary)
                                    }
                                    inner()
                                }
                            )
                        }

                        filtered.take(12).forEach { (name, label) ->
                            Box(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        val cfg = bridge?.config ?: return@clickable
                                        cfg.agents.defaults.fastModel = if (name.isEmpty()) null else name
                                        try { config.ConfigIO.saveConfig(cfg, null) } catch (_: Exception) {}
                                        onFastModelChanged(name)
                                        expanded = false
                                        searchText = ""
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(label, style = AppTheme.typography.body)
                            }
                        }
                    }
                }
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
