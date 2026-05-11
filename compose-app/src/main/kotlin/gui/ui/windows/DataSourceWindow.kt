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
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberDialogState
import gui.ui.Bridge
import gui.ui.components.ErrorDialog
import gui.ui.theme.AppColors
import gui.ui.theme.CjkFontResolver

enum class DatabaseType(
    val label: String,
    val defaultHost: String,
    val defaultPort: Int,
    val urlTemplate: String,
    val driverClass: String
) {
    MySQL("MySQL", "localhost", 3306,
        "jdbc:mysql://{host}:{port}/{db}",
        "com.mysql.cj.jdbc.Driver"),
    MariaDB("MariaDB", "localhost", 3306,
        "jdbc:mariadb://{host}:{port}/{db}",
        "org.mariadb.jdbc.Driver"),
    PostgreSQL("PostgreSQL", "localhost", 5432,
        "jdbc:postgresql://{host}:{port}/{db}",
        "org.postgresql.Driver"),
    Oracle("Oracle", "localhost", 1521,
        "jdbc:oracle:thin:@{host}:{port}:{db}",
        "oracle.jdbc.OracleDriver"),
    SQLServer("SQL Server", "localhost", 1433,
        "jdbc:sqlserver://{host}:{port};databaseName={db}",
        "com.microsoft.sqlserver.jdbc.SQLServerDriver"),
    H2("H2", "localhost", 9092,
        "jdbc:h2:tcp://{host}:{port}/{db}",
        "org.h2.Driver"),
    SQLite("SQLite", "", 0,
        "jdbc:sqlite:{path}",
        "org.sqlite.JDBC")
}

private val URL_PREFIX_TO_TYPE = mapOf(
    "jdbc:mysql:" to DatabaseType.MySQL,
    "jdbc:mariadb:" to DatabaseType.MariaDB,
    "jdbc:postgresql:" to DatabaseType.PostgreSQL,
    "jdbc:oracle:thin:" to DatabaseType.Oracle,
    "jdbc:sqlserver:" to DatabaseType.SQLServer,
    "jdbc:h2:" to DatabaseType.H2,
    "jdbc:sqlite:" to DatabaseType.SQLite,
)

private fun inferType(jdbcUrl: String): DatabaseType? =
    URL_PREFIX_TO_TYPE.entries.firstOrNull { (prefix) -> jdbcUrl.trim().startsWith(prefix) }?.value

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
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var errorDialogMessage by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val windowState = rememberDialogState(
        width = 520.dp,
        height = 600.dp
    )

    // Keep a ref to the AWT frame for focus management
    var frameRef by remember { mutableStateOf<java.awt.Frame?>(null) }

    DialogWindow(
        onCloseRequest = onDismiss,
        title = title,
        state = windowState,
        enabled = errorDialogMessage == null
    ) {
        SideEffect {
            val f = window as? java.awt.Frame
            f?.isResizable = false
            f?.isUndecorated = false
            frameRef = f
        }
        Column(
            Modifier.width(520.dp).heightIn(min = 500.dp, max = 700.dp)
                .fillMaxSize()
                .background(AppColors.Surface)
                .padding(24.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // Scrollable form area
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                fieldInput("名称", name, "例如: my-db") { name = it }
                fieldInput("JDBC URL", jdbcUrl, "jdbc:postgresql://localhost:5432/mydb") {
                    jdbcUrl = it
                    inferType(it.trim())?.let { driverClass = it.driverClass }
                }
                fieldInput("驱动类", driverClass, "自动推断") { driverClass = it }
                fieldInput("用户名", username, "root") { username = it }
                fieldInput("密码", password, if (isEdit) "留空则保留原密码" else "****") { password = it }
                // 连接池大小和超时共用一行
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        fieldLabel("连接池大小")
                        Spacer(Modifier.height(4.dp))
                        BasicTextField(
                            value = maxPoolSize,
                            onValueChange = { maxPoolSize = it },
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(AppColors.HoverBg).padding(12.dp),
                            textStyle = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 14.sp, color = AppColors.TextPrimary),
                            singleLine = true,
                            decorationBox = { inner ->
                                if (maxPoolSize.isEmpty()) {
                                    Text("5", style = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 14.sp, color = AppColors.TextSecondary))
                                }
                                inner()
                            }
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        fieldLabel("连接超时 (ms)")
                        Spacer(Modifier.height(4.dp))
                        BasicTextField(
                            value = connectionTimeout,
                            onValueChange = { connectionTimeout = it },
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(AppColors.HoverBg).padding(12.dp),
                            textStyle = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 14.sp, color = AppColors.TextPrimary),
                            singleLine = true,
                            decorationBox = { inner ->
                                if (connectionTimeout.isEmpty()) {
                                    Text("30000", style = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 14.sp, color = AppColors.TextSecondary))
                                }
                                inner()
                            }
                        )
                    }
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
                            if (bridge == null) { errorDialogMessage = "后端未初始化，请重启应用"; return@TextButton }
                            testing = true; testResult = null
                            val effectiveDriver = driverClass.trim().ifEmpty {
                                inferType(jdbcUrl.trim())?.driverClass ?: ""
                            }
                            val msg = bridge.testDataSourceConnection(
                                jdbcUrl.trim(), username.trim(), password, effectiveDriver
                            )
                            testing = false
                            if (msg == null) {
                                testResult = "连接成功"
                            } else {
                                errorDialogMessage = "连接失败: $msg"
                            }
                        },
                        enabled = !testing && jdbcUrl.isNotBlank()
                    ) {
                        Text(if (testing) "测试中..." else "测试连接", color = AppColors.Accent)
                    }
                    if (testResult != null) {
                        Spacer(Modifier.width(4.dp))
                        Text(testResult!!,
                            color = AppColors.StatusOK,
                            fontSize = 12.sp)
                    }
                }
                // Cancel / Save (right side)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss) { Text("取消") }
                    Button(
                        enabled = !saving,
                        onClick = {
                            when {
                                name.isBlank() -> errorDialogMessage = "请输入名称"
                                jdbcUrl.isBlank() -> errorDialogMessage = "请输入 JDBC URL"
                                else -> {
                                    if (bridge == null) { errorDialogMessage = "后端未初始化，请重启应用"; return@Button }
                                    saving = true
                                    try {
                                        val pool = maxPoolSize.toIntOrNull() ?: 5
                                        val timeout = connectionTimeout.toLongOrNull() ?: 30000L
                                        val pwd = if (isEdit && password.isBlank()) "******" else password
                                        val effectiveDriver = driverClass.trim().ifEmpty {
                                            inferType(jdbcUrl.trim())?.driverClass ?: ""
                                        }
                                        if (isEdit) {
                                            bridge.updateDataSource(editData!!.oldName, name.trim(), jdbcUrl.trim(),
                                                username.trim(), pwd, effectiveDriver, pool, timeout)
                                        } else {
                                            bridge.addDataSource(name.trim(), jdbcUrl.trim(), username.trim(),
                                                pwd, effectiveDriver, pool, timeout)
                                        }
                                        onSaved(); onDismiss()
                                    } catch (e: Exception) {
                                        errorDialogMessage = e.message ?: "操作失败"
                                    } finally {
                                        saving = false
                                    }
                                }
                            }
                        }
                    ) { Text(confirmLabel) }
                }
            }
        }
    }

    // Error dialog as independent window
    errorDialogMessage?.let { msg ->
        ErrorDialog(
            title = "错误",
            message = msg,
            onDismiss = {
                errorDialogMessage = null
                frameRef?.requestFocus()
            }
        )
    }
}

@Composable
private fun fieldLabel(label: String) {
    Text(label, style = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 13.sp, color = AppColors.TextSecondary))
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
