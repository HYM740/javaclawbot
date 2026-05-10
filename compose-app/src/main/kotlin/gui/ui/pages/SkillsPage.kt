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
fun SkillsPage(bridge: Bridge?, modifier: Modifier = Modifier) {
    val skills = remember(bridge) {
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
            Modifier.widthIn(max = 800.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            skills.forEach { skill ->
                val name = skill["name"] ?: "未知"
                val desc = skill["description"] ?: ""
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(AppColors.Surface).padding(12.dp)
                ) {
                    Text(name, fontWeight = FontWeight.Bold, style = AppTheme.typography.body)
                    if (desc.isNotBlank()) {
                        Text(desc, style = AppTheme.typography.caption, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            if (skills.isEmpty()) {
                Text("暂无已安装的技能", style = AppTheme.typography.caption,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp))
            }
        }
    }
}
