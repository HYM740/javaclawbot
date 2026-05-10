package gui.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gui.ui.theme.AppColors

@Composable
fun AddMcpServerDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, command: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        Modifier.fillMaxSize().background(Color(0x80000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.widthIn(max = 500.dp).fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.Surface)
                .padding(24.dp)
        ) {
            Text("\u6DFB\u52A0 MCP \u670D\u52A1\u5668", fontSize = 20.sp, color = AppColors.TextPrimary)

            Spacer(Modifier.height(16.dp))

            Text("\u670D\u52A1\u5668\u540D\u79F0", style = TextStyle(fontSize = 13.sp, color = AppColors.TextSecondary))
            Spacer(Modifier.height(4.dp))
            BasicTextField(
                value = name,
                onValueChange = { name = it; error = null },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(AppColors.HoverBg).padding(12.dp),
                textStyle = TextStyle(fontSize = 14.sp, color = AppColors.TextPrimary),
                decorationBox = { inner ->
                    if (name.isEmpty()) {
                        Text("\u4F8B\u5982: my-server", style = TextStyle(fontSize = 14.sp, color = AppColors.TextSecondary))
                    }
                    inner()
                }
            )

            Spacer(Modifier.height(12.dp))

            Text("\u542F\u52A8\u547D\u4EE4", style = TextStyle(fontSize = 13.sp, color = AppColors.TextSecondary))
            Spacer(Modifier.height(4.dp))
            BasicTextField(
                value = command,
                onValueChange = { command = it; error = null },
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(8.dp))
                    .background(AppColors.HoverBg).padding(12.dp),
                textStyle = TextStyle(fontSize = 14.sp, color = AppColors.TextPrimary),
                decorationBox = { inner ->
                    if (command.isEmpty()) {
                        Text("\u4F8B\u5982: npx -y @modelcontextprotocol/server-filesystem /tmp",
                            style = TextStyle(fontSize = 14.sp, color = AppColors.TextSecondary))
                    }
                    inner()
                }
            )

            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error!!, color = AppColors.StatusError, fontSize = 13.sp)
            }

            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("\u53D6\u6D88", color = AppColors.TextSecondary)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        when {
                            name.isBlank() -> error = "\u8BF7\u8F93\u5165\u670D\u52A1\u5668\u540D\u79F0"
                            command.isBlank() -> error = "\u8BF7\u8F93\u5165\u542F\u52A8\u547D\u4EE4"
                            else -> onAdd(name.trim(), command.trim())
                        }
                    }
                ) {
                    Text("\u6DFB\u52A0", color = AppColors.Accent)
                }
            }
        }
    }
}
