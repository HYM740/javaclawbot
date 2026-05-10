package gui.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import gui.ui.model.ChatTab
import gui.ui.theme.AppColors
import gui.ui.theme.AppTheme

@Composable
fun TabBar(
    tabs: List<ChatTab>,
    activeTabId: String?,
    onTabSelected: (String) -> Unit,
    onTabClosed: (String) -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().height(36.dp).background(AppColors.Background),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(tabs, key = { it.id }) { tab ->
                val isActive = tab.id == activeTabId
                Row(
                    Modifier.clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(if (isActive) AppColors.Surface else Color.Transparent)
                        .clickable { onTabSelected(tab.id) }
                        .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        tab.title.take(20), maxLines = 1, style = AppTheme.typography.caption,
                        color = if (isActive) AppColors.TextPrimary else AppColors.TextSecondary
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.Close, "Close", Modifier.size(12.dp).clickable { onTabClosed(tab.id) },
                        tint = AppColors.TextSecondary
                    )
                }
            }
        }
        Icon(
            Icons.Filled.Add, "New tab", Modifier.size(20.dp).clickable { onNewTab() }.padding(4.dp),
            tint = AppColors.TextSecondary
        )
        Spacer(Modifier.width(8.dp))
    }
}
