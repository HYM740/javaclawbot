package gui.ui.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Button
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
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
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("McpServerWindow")

data class McpEditData(
    val oldName: String,
    val name: String,
    val command: String,
    val json: String? = null
)

enum class McpInputMode { FORM, RAW }

@Composable
fun McpServerWindow(
    bridge: Bridge?,
    editData: McpEditData?,  // null = add mode
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val isEdit = editData != null
    val title = if (isEdit) "编辑 MCP 服务器" else "添加 MCP 服务器"
    val confirmLabel = if (isEdit) "保存" else "添加"

    var name by remember { mutableStateOf(editData?.name ?: "") }
    var command by remember { mutableStateOf(editData?.command ?: "") }
    var rawJson by remember { mutableStateOf(editData?.json ?: "") }
    var mode by remember { mutableStateOf(McpInputMode.FORM) }
    var errorDialogMessage by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val windowState = rememberDialogState(
        width = 520.dp,
        height = 500.dp
    )

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
            frameRef = f
        }
        Column(
            Modifier.width(520.dp).heightIn(min = 400.dp, max = 650.dp)
                .fillMaxSize()
                .background(AppColors.Surface)
                .padding(24.dp)
        ) {
            // Scrollable form area
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                // Mode tabs
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { mode = McpInputMode.FORM }) {
                        Text("表单模式",
                            color = if (mode == McpInputMode.FORM) AppColors.Accent else AppColors.TextSecondary,
                            fontSize = 14.sp)
                    }
                    TextButton(onClick = { mode = McpInputMode.RAW }) {
                        Text("RAW 模式",
                            color = if (mode == McpInputMode.RAW) AppColors.Accent else AppColors.TextSecondary,
                            fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))

                when (mode) {
                    McpInputMode.FORM -> {
                        Text("服务器名称", style = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 13.sp, color = AppColors.TextSecondary))
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
                                    Text("例如: my-server", style = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 14.sp, color = AppColors.TextSecondary))
                                }
                                inner()
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("启动命令", style = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 13.sp, color = AppColors.TextSecondary))
                        Spacer(Modifier.height(4.dp))
                        BasicTextField(
                            value = command,
                            onValueChange = { command = it },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(8.dp))
                                .background(AppColors.HoverBg).padding(12.dp),
                            textStyle = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 14.sp, color = AppColors.TextPrimary),
                            decorationBox = { inner ->
                                if (command.isEmpty()) {
                                    Text("例如: npx -y @modelcontextprotocol/server-filesystem /tmp",
                                        style = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 14.sp, color = AppColors.TextSecondary))
                                }
                                inner()
                            }
                        )
                    }
                    McpInputMode.RAW -> {
                        Text("服务器名称", style = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 13.sp, color = AppColors.TextSecondary))
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
                                    Text("例如: my-server", style = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 14.sp, color = AppColors.TextSecondary))
                                }
                                inner()
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("MCPServerConfig JSON", style = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 13.sp, color = AppColors.TextSecondary))
                        Spacer(Modifier.height(4.dp))
                        BasicTextField(
                            value = rawJson,
                            onValueChange = { rawJson = it },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).clip(RoundedCornerShape(8.dp))
                                .background(AppColors.HoverBg).padding(12.dp),
                            textStyle = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 14.sp, color = AppColors.TextPrimary),
                            decorationBox = { inner ->
                                if (rawJson.isEmpty()) {
                                    Text("""{"command": "...", "env": {...}, "transport_type": "stdio"}""",
                                        style = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 14.sp, color = AppColors.TextSecondary))
                                }
                                inner()
                            }
                        )
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

            // Fixed bottom action bar
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = !saving,
                    onClick = {
                        when {
                            name.isBlank() -> errorDialogMessage = "请输入服务器名称"
                            mode == McpInputMode.FORM && command.isBlank() -> errorDialogMessage = "请输入启动命令"
                            mode == McpInputMode.RAW && rawJson.isBlank() -> errorDialogMessage = "请输入 JSON 配置"
                            bridge == null -> errorDialogMessage = "后端未初始化，请重启应用"
                            else -> {
                                saving = true
                                try {
                                    when {
                                        mode == McpInputMode.RAW && isEdit ->
                                            bridge.updateMcpServerRaw(editData!!.oldName, name.trim(), rawJson.trim())
                                        mode == McpInputMode.RAW && !isEdit ->
                                            bridge.addMcpServerRaw(name.trim(), rawJson.trim())
                                        isEdit ->
                                            bridge.updateMcpServer(editData!!.oldName, name.trim(), command.trim())
                                        else ->
                                            bridge.addMcpServer(name.trim(), command.trim())
                                    }
                                    log.info("MCP server saved: {}", name.trim())
                                    onSaved(); onDismiss()
                                } catch (e: Exception) {
                                    log.warn("MCP server operation failed", e)
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
