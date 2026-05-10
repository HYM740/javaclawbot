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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import gui.ui.theme.AppColors
import gui.ui.theme.CjkFontResolver

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "删除",
    cancelText: String = "取消",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val windowState = rememberDialogState(
        position = WindowPosition.Aligned(Alignment.Center),
        width = 400.dp,
        height = 200.dp
    )

    DialogWindow(
        onCloseRequest = onDismiss,
        title = title,
        state = windowState,
        resizable = false
    ) {
        Column(
            Modifier.fillMaxSize().background(AppColors.Surface).padding(24.dp)
        ) {
            Text(title,
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = CjkFontResolver.get(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                color = AppColors.TextPrimary)
            Spacer(Modifier.height(16.dp))
            Text(message,
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = CjkFontResolver.get(),
                    fontSize = 14.sp
                ),
                color = AppColors.TextSecondary)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .background(AppColors.HoverBg)
                        .clickable { onDismiss() }
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(cancelText,
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = CjkFontResolver.get(),
                            fontSize = 14.sp
                        ),
                        color = AppColors.TextPrimary)
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFDC2626))
                        .clickable { onConfirm() }
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(confirmText,
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = CjkFontResolver.get(),
                            fontSize = 14.sp
                        ),
                        color = Color.White)
                }
            }
        }
    }
}
