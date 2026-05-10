package gui.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.JsonParser
import gui.ui.Bridge
import gui.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ModelsPage(bridge: Bridge?, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var editingModel by remember { mutableStateOf<EditModelState?>(null) }

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

        Box(Modifier.widthIn(max = 800.dp).fillMaxWidth()) {
            LazyColumn(
                Modifier.fillMaxWidth(),
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
                            pc.modelConfigs.forEach { mc ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("$pn/${mc.model}", style = AppTheme.typography.caption, modifier = Modifier.weight(1f))

                                    // Think badge
                                    if (mc.think != null && mc.think.isNotEmpty()) {
                                        Box(
                                            Modifier.clip(RoundedCornerShape(6.dp))
                                                .background(Color(0x1A3B82F6)).padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("思考", fontSize = 10.sp, color = Color(0xFF3B82F6))
                                        }
                                        Spacer(Modifier.width(4.dp))
                                    }

                                    // ExtraBody badge
                                    if (mc.extraBody != null && mc.extraBody.isNotEmpty()) {
                                        Box(
                                            Modifier.clip(RoundedCornerShape(6.dp))
                                                .background(Color(0x1A10B981)).padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("Extra", fontSize = 10.sp, color = Color(0xFF10B981))
                                        }
                                        Spacer(Modifier.width(4.dp))
                                    }

                                    if (mc.model == bridge?.config?.agents?.defaults?.model) {
                                        Text("★", color = AppColors.Accent)
                                    }

                                    Spacer(Modifier.width(8.dp))

                                    // Edit button
                                    Text("✎",
                                        color = AppColors.TextSecondary,
                                        modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                            .clickable {
                                                editingModel = EditModelState(
                                                    providerName = pn,
                                                    modelName = mc.model ?: "",
                                                    thinkJson = mapToJson(mc.think),
                                                    extraBodyJson = mapToJson(mc.extraBody)
                                                )
                                            }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Edit dialog overlay
            editingModel?.let { edit ->
                EditModelDialog(
                    state = edit,
                    onSave = { thinkJson, extraBodyJson ->
                        scope.launch(Dispatchers.IO) {
                            try {
                                val cfg = bridge?.config ?: return@launch
                                val pc = cfg.providers.getByName(edit.providerName) ?: return@launch
                                val mc = pc.modelConfigs.find { it.model == edit.modelName } ?: return@launch
                                mc.think = jsonToMap(thinkJson)
                                mc.extraBody = jsonToMap(extraBodyJson)
                                config.ConfigIO.saveConfig(cfg, null)
                            } catch (_: Exception) {}
                        }
                        editingModel = null
                    },
                    onDismiss = { editingModel = null }
                )
            }
        }
    }
}

private data class EditModelState(
    val providerName: String,
    val modelName: String,
    val thinkJson: String,
    val extraBodyJson: String
)

@Composable
private fun EditModelDialog(
    state: EditModelState,
    onSave: (thinkJson: String, extraBodyJson: String) -> Unit,
    onDismiss: () -> Unit
) {
    var thinkJson by remember { mutableStateOf(state.thinkJson) }
    var extraBodyJson by remember { mutableStateOf(state.extraBodyJson) }
    var thinkError by remember { mutableStateOf(false) }
    var extraError by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize().background(Color(0x40000000)).clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.width(500.dp).clip(RoundedCornerShape(12.dp))
                .background(Color.White).padding(24.dp)
                .clickable(enabled = false) {}
        ) {
            Text("${state.providerName}/${state.modelName}",
                fontWeight = FontWeight.Bold, style = AppTheme.typography.body)

            Spacer(Modifier.height(16.dp))

            // Think JSON
            Text("思考配置 (JSON)", style = AppTheme.typography.caption)
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.fillMaxWidth().height(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (thinkError) Color(0x1AEF4444) else AppColors.HoverBg)
                    .padding(8.dp)
            ) {
                BasicTextField(
                    value = thinkJson,
                    onValueChange = { thinkJson = it; thinkError = false },
                    textStyle = TextStyle(fontFamily = monospaceFont(), fontSize = 13.sp, color = AppColors.TextPrimary),
                    decorationBox = { inner ->
                        if (thinkJson.isEmpty()) {
                            Text("""{"type": "enabled", ...}""",
                                style = TextStyle(fontFamily = monospaceFont(), fontSize = 13.sp, color = AppColors.TextSecondary))
                        }
                        inner()
                    }
                )
            }
            if (thinkError) {
                Text("JSON 格式无效", color = Color(0xFFEF4444), fontSize = 11.sp)
            }

            Spacer(Modifier.height(12.dp))

            // ExtraBody JSON
            Text("Extra Body (JSON)", style = AppTheme.typography.caption)
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.fillMaxWidth().height(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (extraError) Color(0x1AEF4444) else AppColors.HoverBg)
                    .padding(8.dp)
            ) {
                BasicTextField(
                    value = extraBodyJson,
                    onValueChange = { extraBodyJson = it; extraError = false },
                    textStyle = TextStyle(fontFamily = monospaceFont(), fontSize = 13.sp, color = AppColors.TextPrimary),
                    decorationBox = { inner ->
                        if (extraBodyJson.isEmpty()) {
                            Text("""{"custom_param": "value"}""",
                                style = TextStyle(fontFamily = monospaceFont(), fontSize = 13.sp, color = AppColors.TextSecondary))
                        }
                        inner()
                    }
                )
            }
            if (extraError) {
                Text("JSON 格式无效", color = Color(0xFFEF4444), fontSize = 11.sp)
            }

            Spacer(Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(AppColors.HoverBg)
                        .clickable { onDismiss() }.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("取消", style = AppTheme.typography.body, color = AppColors.TextSecondary)
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(AppColors.Accent)
                        .clickable {
                            thinkError = thinkJson.isNotBlank() && !isValidJson(thinkJson)
                            extraError = extraBodyJson.isNotBlank() && !isValidJson(extraBodyJson)
                            if (!thinkError && !extraError) {
                                onSave(thinkJson, extraBodyJson)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("保存", color = AppColors.OnAccent)
                }
            }
        }
    }
}

private fun monospaceFont() = androidx.compose.ui.text.font.FontFamily.Monospace

private val gson = Gson()

private fun mapToJson(map: Map<String, Any>?): String {
    if (map.isNullOrEmpty()) return ""
    return try { gson.toJson(map) } catch (_: Exception) { "" }
}

private fun jsonToMap(json: String): Map<String, Any> {
    if (json.isBlank()) return HashMap()
    return try {
        @Suppress("UNCHECKED_CAST")
        gson.fromJson(json, Map::class.java) as Map<String, Any>
    } catch (_: Exception) {
        HashMap()
    }
}

private fun isValidJson(json: String): Boolean {
    if (json.isBlank()) return true
    return try {
        JsonParser.parseString(json)
        true
    } catch (_: Exception) { false }
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
