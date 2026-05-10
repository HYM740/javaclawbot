package gui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import gui.ui.theme.AppColors
import gui.ui.theme.AppTheme

@Composable
fun QuestionDialog(
    questionsJson: String,
    onAnswer: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit
) {
    val questions = remember(questionsJson) { parseQuestions(questionsJson) }
    val answers = remember { mutableStateMapOf<String, String>() }

    Column(Modifier.width(500.dp).clip(RoundedCornerShape(16.dp))
        .background(AppColors.Surface).padding(24.dp)) {
        Text("AI \u63D0\u95EE", fontWeight = FontWeight.Bold, style = AppTheme.typography.body)
        Spacer(Modifier.height(16.dp))

        questions.forEach { q ->
            Text(q.label, style = AppTheme.typography.body)
            Spacer(Modifier.height(8.dp))
            when (q.type) {
                "single" -> q.options.forEach { opt ->
                    Row(Modifier.fillMaxWidth().clickable { answers[q.key] = opt }.padding(8.dp)) {
                        Text(if (answers[q.key] == opt) "\u25CF" else "\u25CB", modifier = Modifier.padding(end = 8.dp))
                        Text(opt)
                    }
                }
                "multi" -> q.options.forEach { opt ->
                    val selected = answers[q.key]?.split(",")?.contains(opt) == true
                    Row(Modifier.fillMaxWidth().clickable {
                        val cur = answers[q.key]?.split(",")?.toMutableSet() ?: mutableSetOf()
                        if (opt in cur) cur.remove(opt) else cur.add(opt)
                        answers[q.key] = cur.joinToString(",")
                    }.padding(8.dp)) {
                        Text(if (selected) "\u2611" else "\u2610", modifier = Modifier.padding(end = 8.dp))
                        Text(opt)
                    }
                }
                else -> {
                    val currentValue = answers[q.key] ?: ""
                    TextField(
                        value = currentValue,
                        onValueChange = { answers[q.key] = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontSize = 15.sp, color = AppColors.TextPrimary)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(AppColors.HoverBg).clickable { onDismiss() }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("\u53D6\u6D88")
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(AppColors.Accent).clickable { onAnswer(answers.toMap()) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("\u63D0\u4EA4", color = Color.White)
            }
        }
    }
}

private data class Question(val key: String, val label: String, val type: String, val options: List<String>)

private fun parseQuestions(json: String): List<Question> {
    try {
        val gson = Gson()
        val root = gson.fromJson(json, Map::class.java)
        @Suppress("UNCHECKED_CAST")
        val list = root["questions"] as? List<Map<String, Any>> ?: return emptyList()
        return list.map { Question(
            it["question"] as? String ?: "",
            it["question"] as? String ?: "",
            it["type"] as? String ?: "single",
            (it["options"] as? List<*>)?.map { o -> o.toString() } ?: emptyList()
        ) }
    } catch (_: Exception) { return emptyList() }
}
