package gui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gui.ui.theme.AppColors
import gui.ui.theme.CjkFontResolver
import org.slf4j.LoggerFactory
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.roundToInt

private val log = LoggerFactory.getLogger("ChatInput")

data class Attachment(val path: Path, val type: AttachmentType)
enum class AttachmentType { IMAGE, FILE }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ChatInput(
    sending: Boolean,
    statusText: String = "",
    contextUsage: Float = 0f,
    messages: List<String> = emptyList(),
    onSend: (String, List<String>?) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    var escCount by remember { mutableStateOf(0) }
    var lastEscTime by remember { mutableStateOf(0L) }
    var attachments by remember { mutableStateOf(listOf<Attachment>()) }
    var historyIndex by remember { mutableStateOf(-1) }
    var draftText by remember { mutableStateOf("") }

    val clamped = contextUsage.coerceIn(0f, 1f)
    val percent = (clamped * 100).roundToInt()
    val barColor = when {
        clamped <= 0.60f -> Color(0xFF22C55E)
        clamped <= 0.85f -> Color(0xFFEAB308)
        else -> Color(0xFFEF4444)
    }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        if (statusText.isNotBlank() || contextUsage > 0f) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (statusText.isNotBlank()) {
                    Text(
                        statusText,
                        style = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 11.sp, color = AppColors.TextSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }

                if (contextUsage > 0f) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(46.dp, 4.dp)
                            .border(1.dp, Color(0xFFD1D5DB), RoundedCornerShape(3.dp)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            Modifier.padding(1.dp).fillMaxHeight()
                                .then(Modifier.fillMaxWidth(clamped))
                                .background(barColor, RoundedCornerShape(1.dp))
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "$percent%",
                        style = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 10.sp, color = barColor)
                    )
                }
            }
        }

        Column(Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp)) {

            // Attachment preview
            if (attachments.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(attachments, key = { it.path.toString() }) { att ->
                        Box {
                            when (att.type) {
                                AttachmentType.IMAGE -> {
                                    val thumbnailBitmap = remember(att.path) {
                                        loadImageBitmap(att.path)
                                    }
                                    Box(
                                        Modifier.size(80.dp, 60.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFFF3F4F6))
                                            .clickable { openWithSystem(att.path) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (thumbnailBitmap != null) {
                                            Image(
                                                bitmap = thumbnailBitmap,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Text("🖼", fontSize = 14.sp)
                                        }
                                    }
                                }
                                AttachmentType.FILE -> {
                                    Row(
                                        Modifier.clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFF3F4F6))
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                            .clickable { openWithSystem(att.path) },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("📄", fontSize = 14.sp)
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            att.path.fileName.toString(),
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 150.dp)
                                        )
                                    }
                                }
                            }
                            // Remove button
                            Box(
                                Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp)
                                    .size(16.dp).clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF6B7280))
                                    .clickable { attachments = attachments - att },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("×", color = Color.White, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        event.key == Key.DirectionUp && event.isAltPressed -> {
                            val userMessages = messages.filter { it.isNotBlank() }
                            if (userMessages.isEmpty()) return@onPreviewKeyEvent true
                            if (historyIndex == -1) {
                                draftText = text
                                historyIndex = 0
                            } else {
                                historyIndex = minOf(historyIndex + 1, userMessages.size - 1)
                            }
                            text = userMessages[historyIndex]
                            true
                        }
                        event.key == Key.DirectionDown && event.isAltPressed -> {
                            val userMessages = messages.filter { it.isNotBlank() }
                            if (userMessages.isEmpty() || historyIndex == -1) return@onPreviewKeyEvent true
                            historyIndex--
                            if (historyIndex < 0) {
                                text = draftText
                                draftText = ""
                                historyIndex = -1
                            } else {
                                text = userMessages[historyIndex]
                            }
                            true
                        }
                        event.key == Key.V && (event.isCtrlPressed || event.isMetaPressed) -> {
                            // 仅在剪贴板有图片时处理粘贴，否则交给 BasicTextField 默认处理文本粘贴
                            if (hasImageInClipboard()) {
                                pasteImageFromClipboard { att ->
                                    if (att != null) attachments = attachments + att
                                }
                                true
                            } else false
                        }
                        event.key == Key.Enter && !event.isShiftPressed -> {
                            if (text.isNotBlank() || attachments.isNotEmpty()) {
                                val mediaPaths = attachments.map { it.path.toString() }
                                onSend(text.trim(), mediaPaths)
                                text = ""
                                attachments = emptyList()
                            }
                            true
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
                textStyle = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 15.sp, color = AppColors.TextPrimary),
                decorationBox = { innerTextField ->
                    Box {
                        if (text.isEmpty()) {
                            Text(
                                "输入你的问题...（ALT+↑/↓ 历史消息）",
                                style = TextStyle(fontFamily = CjkFontResolver.get(), fontSize = 15.sp, color = AppColors.TextSecondary),
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
                // Attach button
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                    .background(AppColors.HoverBg).clickable {
                        selectFiles { files -> attachments = attachments + files }
                    },
                    contentAlignment = Alignment.Center) {
                    Text("📎", fontSize = 14.sp)
                }
                Spacer(Modifier.width(4.dp))
                // @ mention button
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                    .background(AppColors.HoverBg).clickable {
                        text = text + "@"
                    },
                    contentAlignment = Alignment.Center) {
                    Text("@", fontSize = 14.sp, color = AppColors.TextSecondary)
                }

                Spacer(Modifier.weight(1f))

                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (sending) Color(0x1ADC2626) else AppColors.HoverBg)
                    .clickable {
                        if (sending) onStop()
                        else if (text.isNotBlank() || attachments.isNotEmpty()) {
                            val mediaPaths = attachments.map { it.path.toString() }
                            onSend(text.trim(), mediaPaths)
                            text = ""
                            attachments = emptyList()
                        }
                    },
                    contentAlignment = Alignment.Center) {
                    Icon(if (sending) Icons.Filled.Close else Icons.AutoMirrored.Filled.Send,
                        if (sending) "Stop" else "Send", Modifier.size(20.dp),
                        tint = if (sending) Color(0xFFDC2626) else AppColors.TextSecondary)
                }
            }
        }
    }
}

private fun openWithSystem(path: Path) {
    try {
        Desktop.getDesktop().open(path.toFile())
    } catch (e: Exception) {
        log.warn("无法打开文件: {}", path, e)
    }
}

private fun hasImageInClipboard(): Boolean {
    return try {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)
    } catch (e: Exception) {
        false
    }
}

private fun pasteImageFromClipboard(onResult: (Attachment?) -> Unit) {
    try {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        if (clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
            val image = clipboard.getData(DataFlavor.imageFlavor) as? BufferedImage
            if (image != null) {
                val tempFile = File.createTempFile("paste-", ".png")
                tempFile.deleteOnExit()
                ImageIO.write(image, "png", tempFile)
                onResult(Attachment(tempFile.toPath(), AttachmentType.IMAGE))
                return
            }
        }
    } catch (e: Exception) {
        log.warn("剪贴板粘贴图片失败", e)
    }
    onResult(null)
}

private fun loadImageBitmap(path: Path): ImageBitmap? {
    return try {
        val file = path.toFile()
        if (file.exists()) {
            ImageIO.read(file)?.toComposeImageBitmap()
        } else null
    } catch (_: Exception) { null }
}

private fun selectFiles(onResult: (List<Attachment>) -> Unit) {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "选择文件")
    dialog.isMultipleMode = true
    dialog.isVisible = true
    val files = dialog.files
    if (files != null) {
        val attachments = files.mapNotNull { f ->
            val path = f.toPath()
            val name = path.fileName.toString().lowercase()
            val type = when {
                name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".bmp") -> AttachmentType.IMAGE
                else -> AttachmentType.FILE
            }
            Attachment(path, type)
        }
        onResult(attachments)
    }
}
