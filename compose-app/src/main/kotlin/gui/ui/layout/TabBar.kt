package gui.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import gui.ui.model.ChatTab
import gui.ui.theme.AppColors
import gui.ui.theme.CjkFontResolver

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TabBar(
    tabs: List<ChatTab>,
    activeTabId: String?,
    showHistoryActive: Boolean,
    onTabSelected: (String) -> Unit,
    onTabClosed: (String) -> Unit,
    onNewTab: () -> Unit,
    onHistorySelected: () -> Unit,
    onRename: (tabId: String, newTitle: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingTabId by remember { mutableStateOf<String?>(null) }
    var editingText by remember { mutableStateOf("") }
    var menuTabId by remember { mutableStateOf<String?>(null) }
    var menuPosition by remember { mutableStateOf(IntOffset.Zero) }

    Row(
        modifier = modifier.fillMaxWidth().height(36.dp).background(AppColors.Background),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pinned "历史" tab — visually distinct
        Row(
            Modifier
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(if (showHistoryActive) AppColors.Surface else AppColors.HoverBg.copy(alpha = 0.4f))
                .clickable { onHistorySelected() }
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "📋 历史",
                maxLines = 1,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (showHistoryActive) AppColors.TextPrimary else AppColors.TextSecondary
            )
        }

        // Chat tabs
        LazyRow(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(tabs, key = { _, tab -> tab.id }) { index, tab ->
                val isActive = tab.id == activeTabId
                val isEditing = editingTabId == tab.id

                Box {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(if (isActive) AppColors.Surface else Color.Transparent)
                            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isEditing) {
                            val focusRequester = remember { FocusRequester() }
                            BasicTextField(
                                value = editingText,
                                onValueChange = { editingText = it },
                                modifier = Modifier.widthIn(min = 60.dp, max = 160.dp)
                                    .focusRequester(focusRequester)
                                    .onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown) {
                                            when (event.key) {
                                                Key.Enter -> {
                                                    val newTitle = editingText.trim()
                                                    if (newTitle.isNotBlank() && newTitle != tab.title) {
                                                        onRename(tab.id, newTitle)
                                                    }
                                                    editingTabId = null
                                                    true
                                                }
                                                Key.Escape -> {
                                                    editingTabId = null
                                                    true
                                                }
                                                else -> false
                                            }
                                        } else false
                                    },
                                textStyle = TextStyle(
                                    fontFamily = CjkFontResolver.get(),
                                    fontSize = 13.sp,
                                    color = AppColors.TextPrimary
                                ),
                                singleLine = true
                            )
                            LaunchedEffect(Unit) {
                                focusRequester.requestFocus()
                            }
                        } else {
                            Text(
                                tab.title.take(20),
                                maxLines = 1,
                                fontSize = 13.sp,
                                color = if (isActive) AppColors.TextPrimary else AppColors.TextSecondary,
                                modifier = Modifier
                                    .onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown && event.key == Key.F2) {
                                            editingTabId = tab.id
                                            editingText = tab.title
                                            true
                                        } else false
                                    }
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                editingTabId = tab.id
                                                editingText = tab.title
                                            },
                                            onLongPress = { offset ->
                                                menuTabId = tab.id
                                            }
                                        )
                                    }
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.Close, "Close",
                            Modifier.size(12.dp).clickable { onTabClosed(tab.id) },
                            tint = AppColors.TextSecondary
                        )
                    }

                    // Right-click context menu via Popup
                    if (menuTabId == tab.id) {
                        Popup(
                            alignment = Alignment.TopStart,
                            offset = IntOffset(0, 0),
                            onDismissRequest = { menuTabId = null }
                        ) {
                            Column(
                                Modifier.width(160.dp)
                                    .shadow(4.dp, RoundedCornerShape(8.dp))
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                                    .padding(4.dp)
                            ) {
                                menuItem("重命名") {
                                    editingTabId = tab.id
                                    editingText = tab.title
                                    menuTabId = null
                                }
                                menuItem("关闭标签") {
                                    onTabClosed(tab.id)
                                    menuTabId = null
                                }
                                menuItem("关闭其他标签") {
                                    tabs.filter { it.id != tab.id }.forEach { onTabClosed(it.id) }
                                    menuTabId = null
                                }
                                menuItem("关闭右侧标签") {
                                    tabs.drop(index + 1).forEach { onTabClosed(it.id) }
                                    menuTabId = null
                                }
                                menuItem("关闭左侧标签") {
                                    tabs.take(index).forEach { onTabClosed(it.id) }
                                    menuTabId = null
                                }
                            }
                        }
                    }
                }
            }
        }

        // New chat button with text
        Row(
            Modifier.padding(end = 8.dp).clickable { onNewTab() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Add, "New tab", Modifier.size(20.dp),
                tint = AppColors.TextSecondary
            )
            Spacer(Modifier.width(2.dp))
            Text(
                "新对话",
                fontSize = 12.sp,
                color = AppColors.TextSecondary
            )
        }
    }
}

@Composable
private fun menuItem(text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 13.sp, color = AppColors.TextPrimary)
    }
}
