package gui.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
fun AddDataSourceDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, jdbcUrl: String, username: String, password: String,
            driverClass: String, maxPoolSize: Int, connectionTimeout: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var jdbcUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var driverClass by remember { mutableStateOf("") }
    var maxPoolSize by remember { mutableStateOf("5") }
    var connectionTimeout by remember { mutableStateOf("30000") }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        Modifier.fillMaxSize().background(Color(0x80000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.widthIn(max = 550.dp).fillMaxWidth().heightIn(max = 600.dp)
                .shadow(8.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.Surface)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("\u6DFB\u52A0\u6570\u636E\u6E90", fontSize = 20.sp, color = AppColors.TextPrimary)

            Spacer(Modifier.height(16.dp))

            fieldInput("\u540D\u79F0", name, "\u4F8B\u5982: my-db") { name = it }
            fieldInput("JDBC URL", jdbcUrl, "jdbc:postgresql://localhost:5432/mydb") { jdbcUrl = it }
            fieldInput("\u7528\u6237\u540D", username, "root") { username = it }
            fieldInput("\u5BC6\u7801", password, "****") { password = it }
            fieldInput("\u9A71\u52A8\u7C7B", driverClass, "\u81EA\u52A8\u63A8\u65AD") { driverClass = it }
            fieldInput("\u8FDE\u63A5\u6C60\u5927\u5C0F", maxPoolSize, "5") { maxPoolSize = it }
            fieldInput("\u8FDE\u63A5\u8D85\u65F6 (ms)", connectionTimeout, "30000") { connectionTimeout = it }

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
                            name.isBlank() -> error = "\u8BF7\u8F93\u5165\u540D\u79F0"
                            jdbcUrl.isBlank() -> error = "\u8BF7\u8F93\u5165 JDBC URL"
                            else -> {
                                val pool = maxPoolSize.toIntOrNull() ?: 5
                                val timeout = connectionTimeout.toLongOrNull() ?: 30000L
                                onAdd(name.trim(), jdbcUrl.trim(), username.trim(),
                                    password, driverClass.trim(), pool, timeout)
                            }
                        }
                    }
                ) {
                    Text("\u6DFB\u52A0", color = AppColors.Accent)
                }
            }
        }
    }
}

@Composable
private fun fieldInput(label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    Text(label, style = TextStyle(fontSize = 13.sp, color = AppColors.TextSecondary))
    Spacer(Modifier.height(4.dp))
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(AppColors.HoverBg).padding(12.dp),
        textStyle = TextStyle(fontSize = 14.sp, color = AppColors.TextPrimary),
        singleLine = true,
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(placeholder, style = TextStyle(fontSize = 14.sp, color = AppColors.TextSecondary))
            }
            inner()
        }
    )
    Spacer(Modifier.height(8.dp))
}
