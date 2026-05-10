package gui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import gui.ui.theme.AppColors
import gui.ui.theme.CjkFontResolver

@Composable
fun ErrorDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    val windowState = rememberWindowState(
        position = WindowPosition.Aligned(Alignment.Center),
        width = 460.dp,
        height = 280.dp
    )

    Window(
        onCloseRequest = onDismiss,
        title = title,
        state = windowState,
        alwaysOnTop = true,
        resizable = false
    ) {
        val clipboardManager = LocalClipboardManager.current

        Column(
            Modifier.fillMaxSize().background(AppColors.Surface).padding(24.dp)
        ) {
            Text("错误",
                style = TextStyle(fontFamily = CjkFontResolver.get(), fontWeight = FontWeight.Bold, fontSize = 16.sp),
                color = AppColors.StatusError)
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(AppColors.HoverBg)
                    .padding(12.dp).clickable {
                        clipboardManager.setText(AnnotatedString(message))
                    }
            ) {
                Text(message,
                    style = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 13.sp),
                    color = AppColors.TextPrimary)
            }
            Spacer(Modifier.height(4.dp))
            Text("点击错误文本可复制",
                style = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 11.sp),
                color = AppColors.TextSecondary)
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.align(Alignment.End).clip(RoundedCornerShape(8.dp))
                    .background(AppColors.Accent).clickable { onDismiss() }
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text("关闭",
                    style = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 14.sp),
                    color = Color.White)
            }
        }
    }
}
