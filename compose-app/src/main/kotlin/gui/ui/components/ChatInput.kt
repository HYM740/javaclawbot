package gui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gui.ui.theme.AppColors
import gui.ui.theme.AppTheme

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ChatInput(
    sending: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    var escCount by remember { mutableStateOf(0) }
    var lastEscTime by remember { mutableStateOf(0L) }

    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Column(Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp)) {

            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        event.key == Key.Enter && !event.isShiftPressed -> {
                            if (text.isNotBlank()) { onSend(text.trim()); text = "" }; true
                        }
                        event.key == Key.Escape && sending -> {
                            val now = System.currentTimeMillis()
                            if (now - lastEscTime < 500) { escCount++; if (escCount >= 2) { escCount = 0; onStop() } }
                            else escCount = 1
                            lastEscTime = now; true
                        }
                        else -> false
                    }
                },
                textStyle = TextStyle(fontSize = 15.sp, color = AppColors.TextPrimary),
                decorationBox = { innerTextField ->
                    Box {
                        if (text.isEmpty()) {
                            Text(
                                "\u8F93\u5165\u4F60\u7684\u95EE\u9898...",
                                style = TextStyle(fontSize = 15.sp, color = AppColors.TextSecondary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (sending) Color(0x1ADC2626) else AppColors.HoverBg)
                    .clickable { if (sending) onStop() else { if (text.isNotBlank()) { onSend(text.trim()); text = "" } } },
                    contentAlignment = Alignment.Center) {
                    Icon(if (sending) Icons.Filled.Close else Icons.AutoMirrored.Filled.Send,
                        if (sending) "Stop" else "Send", Modifier.size(20.dp),
                        tint = if (sending) Color(0xFFDC2626) else AppColors.TextSecondary)
                }
            }
        }
    }
}
