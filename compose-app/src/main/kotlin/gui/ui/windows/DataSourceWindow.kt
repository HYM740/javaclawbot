package gui.ui.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
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

private val urlPrefixToType = mapOf(
    "jdbc:mysql:" to DatabaseType.MySQL,
    "jdbc:mariadb:" to DatabaseType.MariaDB,
    "jdbc:postgresql:" to DatabaseType.PostgreSQL,
    "jdbc:oracle:thin:" to DatabaseType.Oracle,
    "jdbc:sqlserver:" to DatabaseType.SQLServer,
    "jdbc:h2:" to DatabaseType.H2,
    "jdbc:sqlite:" to DatabaseType.SQLite,
)

private fun inferType(jdbcUrl: String): DatabaseType? =
    urlPrefixToType.entries.firstOrNull { (prefix) -> jdbcUrl.trim().startsWith(prefix) }?.value

private fun buildJdbcUrl(type: DatabaseType, host: String, port: Int, dbName: String, filePath: String = ""): String {
    if (type == DatabaseType.SQLite) {
        return if (filePath.isBlank()) "" else "jdbc:sqlite:${filePath}"
    }
    val h = host.ifBlank { type.defaultHost }
    val p = if (port <= 0) type.defaultPort else port
    return type.urlTemplate
        .replace("{host}", h)
        .replace("{port}", p.toString())
        .replace("{db}", dbName)
}

private data class ParsedJdbcUrl(
    val type: DatabaseType?,
    val host: String,
    val port: Int,
    val dbName: String,
    val filePath: String
)

private fun parseJdbcUrl(url: String): ParsedJdbcUrl {
    val trimmed = url.trim()
    if (trimmed.isBlank()) return ParsedJdbcUrl(null, "", 0, "", "")

    // SQLite
    if (trimmed.startsWith("jdbc:sqlite:")) {
        val path = trimmed.removePrefix("jdbc:sqlite:")
        return ParsedJdbcUrl(DatabaseType.SQLite, "", 0, "", path)
    }

    // Match type by prefix
    val matchedEntry = urlPrefixToType.entries.firstOrNull { (prefix) ->
        trimmed.startsWith(prefix)
    }
    if (matchedEntry == null) {
        return ParsedJdbcUrl(null, "", 0, trimmed, "")
    }
    val prefix = matchedEntry.key
    val matchedType = matchedEntry.value
    val afterPrefix = trimmed.removePrefix(prefix)

    var host = matchedType.defaultHost
    var port = matchedType.defaultPort
    var db = ""

    when (matchedType) {
        DatabaseType.MySQL, DatabaseType.MariaDB, DatabaseType.PostgreSQL, DatabaseType.H2 -> {
            // jdbc:type://host:port/db
            val rest = afterPrefix.substringAfter("//")
            val parts = rest.split("/", limit = 2)
            if (parts.isNotEmpty()) {
                val hp = parts[0].split(":")
                if (hp.isNotEmpty() && hp[0].isNotBlank()) host = hp[0]
                if (hp.size > 1) port = hp[1].toIntOrNull() ?: matchedType.defaultPort
            }
            if (parts.size > 1) db = parts[1].substringBefore("?")
        }
        DatabaseType.Oracle -> {
            // jdbc:oracle:thin:@host:port:db
            val rest = afterPrefix.removePrefix("@")
            val parts = rest.split(":")
            if (parts.isNotEmpty() && parts[0].isNotBlank()) host = parts[0]
            if (parts.size > 1) port = parts[1].toIntOrNull() ?: matchedType.defaultPort
            if (parts.size > 2) db = parts.subList(2, parts.size).joinToString(":")
        }
        DatabaseType.SQLServer -> {
            // jdbc:sqlserver://host:port;databaseName=db
            val rest = afterPrefix.removePrefix("//")
            val semicolonParts = rest.split(";")
            if (semicolonParts.isNotEmpty()) {
                val hp = semicolonParts[0].split(":")
                if (hp.isNotEmpty() && hp[0].isNotBlank()) host = hp[0]
                if (hp.size > 1) port = hp[1].toIntOrNull() ?: matchedType.defaultPort
            }
            for (part in semicolonParts.drop(1)) {
                if (part.startsWith("databaseName=") || part.startsWith("database=")) {
                    db = part.substringAfter("=").substringBefore("?")
                }
            }
        }
        DatabaseType.SQLite -> { /* handled above */ }
    }

    return ParsedJdbcUrl(matchedType, host, port, db, "")
}

data class DataSourceEditData(
    val oldName: String,
    val name: String,
    val jdbcUrl: String,
    val username: String,
    val maxPoolSize: Int,
    val connectionTimeout: Long
)

@Composable
fun DataSourceWindow(
    bridge: Bridge?,
    editData: DataSourceEditData?,  // null = add mode
    existingNames: Set<String> = emptySet(),
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
    var dbType by remember { mutableStateOf<DatabaseType>(DatabaseType.MySQL) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var dbName by remember { mutableStateOf("") }
    var dbFilePath by remember { mutableStateOf("") }
    var suppressUrlGeneration by remember { mutableStateOf(false) }
    var fieldsInitialized by remember { mutableStateOf(false) }
    var forceSave by remember { mutableStateOf(false) }
    var pendingForceSave by remember { mutableStateOf(false) }
    var maxPoolSize by remember { mutableStateOf((editData?.maxPoolSize ?: 5).toString()) }
    var connectionTimeout by remember { mutableStateOf((editData?.connectionTimeout ?: 30000L).toString()) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var errorDialogMessage by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    fun performSave() {
        if (bridge == null) { errorDialogMessage = "后端未初始化，请重启应用"; return }
        saving = true
        try {
            val pool = maxPoolSize.toIntOrNull() ?: 5
            val timeout = connectionTimeout.toLongOrNull() ?: 30000L
            val pwd = if (isEdit && password.isBlank()) "******" else password
            val effectiveDriver = dbType.driverClass
            if (isEdit) {
                bridge.updateDataSource(editData!!.oldName, name.trim(), jdbcUrl.trim(),
                    username.trim(), pwd, effectiveDriver, pool, timeout)
            } else {
                bridge.addDataSource(name.trim(), jdbcUrl.trim(), username.trim(),
                    pwd, effectiveDriver, pool, timeout)
            }
            onSaved(); onDismiss()
        } catch (e: Exception) {
            if (forceSave) {
                // 强制保存：忽略后端错误，关闭表单
                onSaved(); onDismiss()
            } else {
                errorDialogMessage = e.message ?: "操作失败"
            }
        } finally {
            saving = false
            forceSave = false
        }
    }

    val windowState = rememberDialogState(
        width = 520.dp,
        height = 600.dp
    )

    // Keep a ref to the AWT frame for focus management
    var frameRef by remember { mutableStateOf<java.awt.Frame?>(null) }

    // Initialize fields from editData (parse JDBC URL to fill db type, host, port, db name)
    if (editData != null && !fieldsInitialized) {
        fieldsInitialized = true
        val parsed = parseJdbcUrl(editData.jdbcUrl)
        if (parsed.type != null) {
            dbType = parsed.type
            host = parsed.host
            port = (if (parsed.port > 0) parsed.port.toString() else "")
            if (parsed.type == DatabaseType.SQLite) {
                dbFilePath = parsed.filePath
            } else {
                dbName = parsed.dbName
            }
        }
    }

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
            // Scrollable form area
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                // 名称 + ⓘ 悬浮提示
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("名称", style = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 13.sp, color = AppColors.TextSecondary))
                    Spacer(Modifier.width(4.dp))
                    val nameInteractionSource = remember { MutableInteractionSource() }
                    val isNameHovered by nameInteractionSource.collectIsHoveredAsState()
                    Text("ⓘ", fontSize = 12.sp, color = AppColors.TextSecondary,
                        modifier = Modifier.hoverable(nameInteractionSource))
                    if (isNameHovered) {
                        Text("此名称作为 AI 工具操作数据库时的唯一标识",
                            fontSize = 11.sp, color = AppColors.TextSecondary,
                            modifier = Modifier.background(AppColors.Surface, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(AppColors.HoverBg).padding(12.dp),
                    textStyle = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 14.sp, color = AppColors.TextPrimary),
                    singleLine = true,
                    decorationBox = { inner ->
                        if (name.isEmpty()) {
                            Text("例如: my-db", style = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 14.sp, color = AppColors.TextSecondary))
                        }
                        inner()
                    }
                )
                Spacer(Modifier.height(8.dp))

                // 数据库类型下拉框
                Text("数据库类型", style = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 13.sp, color = AppColors.TextSecondary))
                Spacer(Modifier.height(4.dp))
                var typeExpanded by remember { mutableStateOf(false) }
                Box {
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(AppColors.HoverBg).padding(12.dp)
                            .clickable { typeExpanded = true },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(dbType.label, style = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 14.sp, color = AppColors.TextPrimary))
                            Spacer(Modifier.weight(1f))
                            Text("▼", fontSize = 10.sp, color = AppColors.TextSecondary)
                        }
                    }
                    DropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        DatabaseType.entries.forEach { type ->
                            DropdownMenuItem(onClick = {
                                suppressUrlGeneration = false
                                dbType = type
                                typeExpanded = false
                                host = type.defaultHost
                                port = if (type.defaultPort > 0) type.defaultPort.toString() else ""
                                if (type == DatabaseType.SQLite) {
                                    dbFilePath = ""; dbName = ""
                                }
                                jdbcUrl = buildJdbcUrl(dbType, host, port.toIntOrNull() ?: 0, dbName, dbFilePath)
                            }) {
                                Text(type.label, style = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 14.sp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                // 主机名 + 端口（SQLite 时隐藏）
                if (dbType != DatabaseType.SQLite) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(2f)) {
                            Text("主机名", style = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 13.sp, color = AppColors.TextSecondary))
                            Spacer(Modifier.height(4.dp))
                            BasicTextField(
                                value = host,
                                onValueChange = { host = it; if (!suppressUrlGeneration) jdbcUrl = buildJdbcUrl(dbType, host, port.toIntOrNull() ?: 0, dbName, dbFilePath) },
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .background(AppColors.HoverBg).padding(12.dp),
                                textStyle = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 14.sp, color = AppColors.TextPrimary),
                                singleLine = true,
                                decorationBox = { inner ->
                                    if (host.isEmpty()) {
                                        Text(dbType.defaultHost, style = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 14.sp, color = AppColors.TextSecondary))
                                    }
                                    inner()
                                }
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text("端口", style = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 13.sp, color = AppColors.TextSecondary))
                            Spacer(Modifier.height(4.dp))
                            BasicTextField(
                                value = port,
                                onValueChange = { port = it; if (!suppressUrlGeneration) jdbcUrl = buildJdbcUrl(dbType, host, port.toIntOrNull() ?: 0, dbName, dbFilePath) },
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .background(AppColors.HoverBg).padding(12.dp),
                                textStyle = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 14.sp, color = AppColors.TextPrimary),
                                singleLine = true,
                                decorationBox = { inner ->
                                    if (port.isEmpty()) {
                                        Text(dbType.defaultPort.toString(), style = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 14.sp, color = AppColors.TextSecondary))
                                    }
                                    inner()
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }

                // 数据库名 / SQLite 文件路径
                if (dbType == DatabaseType.SQLite) {
                    Text("文件路径", style = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 13.sp, color = AppColors.TextSecondary))
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BasicTextField(
                            value = dbFilePath,
                            onValueChange = { dbFilePath = it; if (!suppressUrlGeneration) jdbcUrl = buildJdbcUrl(dbType, host, 0, "", dbFilePath) },
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                .background(AppColors.HoverBg).padding(12.dp),
                            textStyle = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 14.sp, color = AppColors.TextPrimary),
                            singleLine = true,
                            decorationBox = { inner ->
                                if (dbFilePath.isEmpty()) {
                                    Text("选择或输入 .db 文件路径", style = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 14.sp, color = AppColors.TextSecondary))
                                }
                                inner()
                            }
                        )
                        Button(onClick = {
                            val frame = frameRef ?: return@Button
                            val dialog = java.awt.FileDialog(frame, "选择数据库文件", java.awt.FileDialog.LOAD)
                            dialog.file = "*.db"
                            dialog.isVisible = true
                            if (dialog.file != null) {
                                dbFilePath = java.io.File(dialog.directory, dialog.file).absolutePath
                                if (!suppressUrlGeneration) jdbcUrl = buildJdbcUrl(dbType, "", 0, "", dbFilePath)
                            }
                        }) {
                            Text("浏览...")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                } else {
                    fieldInput("数据库名", dbName, "例如: mydb") {
                        dbName = it
                        if (!suppressUrlGeneration) jdbcUrl = buildJdbcUrl(dbType, host, port.toIntOrNull() ?: 0, dbName, "")
                    }
                }

                fieldInput("用户名", username, "root") { username = it }
                fieldInput("密码", password, if (isEdit) "留空则保留原密码" else "****") { password = it }

                // JDBC URL (below password)
                fieldInput("JDBC URL", jdbcUrl, "根据上方信息自动生成") {
                    if (!suppressUrlGeneration) {
                        suppressUrlGeneration = true
                        jdbcUrl = it
                        val parsed = parseJdbcUrl(it)
                        if (parsed.type != null) {
                            dbType = parsed.type
                            host = parsed.host
                            port = if (parsed.port > 0) parsed.port.toString() else ""
                            if (parsed.type == DatabaseType.SQLite) {
                                dbFilePath = parsed.filePath; dbName = ""
                            } else {
                                dbName = parsed.dbName; dbFilePath = ""
                            }
                        }
                        suppressUrlGeneration = false
                    }
                }
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
                            val effectiveDriver = dbType.driverClass
                            val testPassword = if (isEdit && password.isBlank()) "******" else password
                            val msg = bridge.testDataSourceConnection(
                                jdbcUrl.trim(), username.trim(), testPassword, effectiveDriver
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
                                existingNames.contains(name.trim()) && name.trim() != editData?.oldName ->
                                    errorDialogMessage = "名称「${name.trim()}」已存在，请使用其他名称"
                                dbType != DatabaseType.SQLite && host.isBlank() -> errorDialogMessage = "请输入主机名"
                                dbType != DatabaseType.SQLite -> {
                                    val portNum = port.toIntOrNull()
                                    if (portNum == null || portNum < 1 || portNum > 65535) {
                                        errorDialogMessage = "端口号必须在 1-65535 之间"
                                    } else if (jdbcUrl.isBlank()) {
                                        errorDialogMessage = "请输入 JDBC URL"
                                    } else {
                                            if (bridge == null) { errorDialogMessage = "后端未初始化，请重启应用"; return@Button }
                                            if (!forceSave) {
                                                val testMsg = bridge.testDataSourceConnection(
                                                    jdbcUrl.trim(), username.trim(),
                                                    if (isEdit && password.isBlank()) "******" else password,
                                                    dbType.driverClass
                                                )
                                                if (testMsg != null) {
                                                    errorDialogMessage = testMsg
                                                    pendingForceSave = true
                                                    return@Button
                                                }
                                            }
                                            performSave()
                                    }
                                }
                                dbType == DatabaseType.SQLite && dbFilePath.isBlank() -> errorDialogMessage = "请输入数据库文件路径"
                                jdbcUrl.isBlank() -> errorDialogMessage = "请输入 JDBC URL"
                                else -> performSave()
                            }
                        }
                    ) { Text(confirmLabel) }
                }
            }
        }
    }

    // Error dialog as independent window
    errorDialogMessage?.let { msg ->
        val confirmAction = if (pendingForceSave) ({
            forceSave = true
            pendingForceSave = false
            errorDialogMessage = null
            performSave()
        }) else null
        ErrorDialog(
            title = if (pendingForceSave) "连接失败" else "错误",
            message = msg,
            confirmText = if (pendingForceSave) "忽略并保存" else null,
            onConfirm = confirmAction,
            onDismiss = {
                errorDialogMessage = null
                pendingForceSave = false
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
