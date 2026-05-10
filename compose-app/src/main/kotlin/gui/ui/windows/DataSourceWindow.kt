package gui.ui.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.Button
import androidx.compose.material.OutlinedButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import gui.ui.Bridge
import gui.ui.theme.AppColors
import gui.ui.theme.CjkFontResolver

data class DataSourceEditData(
    val oldName: String,
    val name: String,
    val jdbcUrl: String,
    val username: String,
    val driverClass: String,
    val maxPoolSize: Int,
    val connectionTimeout: Long
)

@Composable
fun DataSourceWindow(
    bridge: Bridge?,
    editData: DataSourceEditData?,  // null = add mode
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val isEdit = editData != null
    val title = if (isEdit) "编辑数据源" else "添加数据源"
    val confirmLabel = if (isEdit) "保存" else "添加"

    var name by remember { mutableStateOf(editData?.name ?: "") }
    var jdbcUrl by remember { mutableStateOf(editData?.jdbcUrl ?: "") }
    var username by remember { mutableStateOf(editData?.username ?: "") }
    var password by remember { mutableStateOf("") }
    var driverClass by remember { mutableStateOf(editData?.driverClass ?: "") }
    var maxPoolSize by remember { mutableStateOf((editData?.maxPoolSize ?: 5).toString()) }
    var connectionTimeout by remember { mutableStateOf((editData?.connectionTimeout ?: 30000L).toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    Window(
        onCloseRequest = onDismiss,
        title = title
    ) {
        Column(
            Modifier.width(520.dp).heightIn(min = 500.dp, max = 700.dp)
                .fillMaxSize()
                .background(AppColors.Surface)
                .padding(24.dp)
        ) {
            Text(title, fontSize = 20.sp, color = AppColors.TextPrimary)

            Spacer(Modifier.height(16.dp))

            // Scrollable form area
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                fieldInput("名称", name, "例如: my-db") { name = it; error = null }
                fieldInput("JDBC URL", jdbcUrl, "jdbc:postgresql://localhost:5432/mydb") { jdbcUrl = it; error = null }
                fieldInput("用户名", username, "root") { username = it; error = null }
                fieldInput("密码", password, if (isEdit) "留空则保留原密码" else "****") { password = it; error = null }
                fieldInput("驱动类", driverClass, "自动推断") { driverClass = it; error = null }
                fieldInput("连接池大小", maxPoolSize, "5") { maxPoolSize = it; error = null }
                fieldInput("连接超时 (ms)", connectionTimeout, "30000") { connectionTimeout = it; error = null }

                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = AppColors.StatusError, fontSize = 13.sp)
                }
            }

            // Fixed bottom action bar
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Test connection (left side)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            if (bridge == null) { testResult = "连接失败: 后端未初始化"; return@TextButton }
                            testing = true; testResult = null
                            val msg = bridge.testDataSourceConnection(
                                jdbcUrl.trim(), username.trim(), password, driverClass.trim()
                            )
                            testing = false
                            testResult = if (msg == null) "连接成功" else "连接失败: $msg"
                        },
                        enabled = !testing && jdbcUrl.isNotBlank()
                    ) {
                        Text(if (testing) "测试中..." else "测试连接", color = AppColors.Accent)
                    }
                    if (testResult != null) {
                        Spacer(Modifier.width(4.dp))
                        Text(testResult!!,
                            color = if (testResult!!.startsWith("连接成功")) AppColors.StatusOK else AppColors.StatusError,
                            fontSize = 12.sp)
                    }
                }
                // Cancel / Save (right side)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss) { Text("取消") }
                    Button(
                        onClick = {
                            when {
                                name.isBlank() -> error = "请输入名称"
                                jdbcUrl.isBlank() -> error = "请输入 JDBC URL"
                                else -> {
                                    if (bridge == null) { error = "后端未初始化，请重启应用"; return@Button }
                                    try {
                                        val pool = maxPoolSize.toIntOrNull() ?: 5
                                        val timeout = connectionTimeout.toLongOrNull() ?: 30000L
                                        val pwd = if (isEdit && password.isBlank()) "******" else password
                                        if (isEdit) {
                                            bridge.updateDataSource(editData!!.oldName, name.trim(), jdbcUrl.trim(),
                                                username.trim(), pwd, driverClass.trim(), pool, timeout)
                                        } else {
                                            bridge.addDataSource(name.trim(), jdbcUrl.trim(), username.trim(),
                                                pwd, driverClass.trim(), pool, timeout)
                                        }
                                        onSaved()
                                    } catch (e: Exception) { error = e.message ?: "操作失败" }
                                }
                            }
                        }
                    ) { Text(confirmLabel) }
                }
            }
        }
    }
}

@Composable
private fun fieldInput(label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    Text(label, style = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 13.sp, color = AppColors.TextSecondary))
    Spacer(Modifier.height(4.dp))
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(AppColors.HoverBg).padding(12.dp),
        textStyle = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 14.sp, color = AppColors.TextPrimary),
        singleLine = true,
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(placeholder, style = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 14.sp, color = AppColors.TextSecondary))
            }
            inner()
        }
    )
    Spacer(Modifier.height(8.dp))
}
