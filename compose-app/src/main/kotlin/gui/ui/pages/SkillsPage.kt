package gui.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gui.ui.Bridge
import gui.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Paths

@Composable
fun SkillsPage(bridge: Bridge?, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var refreshTrigger by remember { mutableStateOf(0) }

    val skills = remember(bridge, refreshTrigger) {
        try {
            bridge?.skillsLoader?.listSkills(true) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    Column(
        Modifier.fillMaxSize().background(AppColors.Background).padding(40.dp, 24.dp, 24.dp, 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("技能管理", style = AppTheme.typography.title)
        Text(
            "已安装的技能列表",
            style = AppTheme.typography.caption,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(32.dp))

        Column(
            Modifier.widthIn(max = 880.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            skills.forEach { skill ->
                val name = skill["name"] ?: "未知"
                val source = skill["source"] ?: ""
                val status = if (source == "builtin") "内置" else "工作区"
                val enabled = try {
                    bridge?.skillsLoader?.isSkillEnabled(name) ?: true
                } catch (_: Exception) { true }

                Card(
                    name = name,
                    status = status,
                    enabled = enabled,
                    onToggle = { newEnabled ->
                        scope.launch(Dispatchers.IO) {
                            toggleSkillEnabled(bridge, name, newEnabled)
                            refreshTrigger++
                        }
                    }
                )
            }
            if (skills.isEmpty()) {
                Text("暂无已安装的技能", style = AppTheme.typography.caption,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp))
            }
        }
    }
}

@Composable
private fun Card(
    name: String,
    status: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(20.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Icon placeholder
        Box(
            Modifier.size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x0D000000)),
            contentAlignment = Alignment.Center
        ) {
            Text("⚡", style = AppTheme.typography.body)
        }

        Spacer(Modifier.width(14.dp))

        // Name + status
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.Bold, style = AppTheme.typography.body)
            Spacer(Modifier.height(4.dp))
            Text(status,
                style = AppTheme.typography.caption,
                color = AppColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis)
        }

        Spacer(Modifier.width(12.dp))

        // Toggle switch
        val bgColor = if (enabled) Color(0xFF10B981) else Color(0xFFE5E7EB)
        val knobColor = if (enabled) Color.White else Color(0xFF9CA3AF)
        val knobOffset = if (enabled) 18.dp else 2.dp

        Box(
            Modifier.width(32.dp).height(16.dp).offset(y = 14.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(bgColor)
                .clickable { onToggle(!enabled) }
        ) {
            Box(
                Modifier.size(12.dp).offset(x = knobOffset, y = 2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(knobColor)
            )
        }
    }
}

private fun toggleSkillEnabled(bridge: Bridge?, skillName: String, enabled: Boolean) {
    if (bridge == null) return
    val loader = bridge.skillsLoader ?: return

    val allSkills = loader.listSkills(false)
    var skillPath: String? = null
    for (s in allSkills) {
        if (skillName == s["name"]) {
            skillPath = s["path"]
            break
        }
    }
    if (skillPath == null) return

    val path = Paths.get(skillPath)
    if (!Files.exists(path)) return

    try {
        var content = Files.readString(path)
        content = updateFrontmatterEnable(content, enabled)
        Files.writeString(path, content)
    } catch (_: Exception) {}
}

private fun updateFrontmatterEnable(content: String, enabled: Boolean): String {
    if (!content.startsWith("---")) return content

    val endIdx = content.indexOf("---", 3)
    if (endIdx < 0) return content

    val frontmatter = content.substring(3, endIdx)
    val body = content.substring(endIdx + 3)

    val enableRegex = Regex("(?m)^enable:.*$")
    val newFrontmatter = if (enableRegex.containsMatchIn(frontmatter)) {
        enableRegex.replaceFirst(frontmatter, "enable: $enabled")
    } else {
        frontmatter.trimEnd() + "\nenable: $enabled"
    }

    return "---\n$newFrontmatter\n---$body"
}
