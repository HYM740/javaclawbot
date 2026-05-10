package gui.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gui.ui.model.HistoryGroup
import gui.ui.theme.AppColors
import gui.ui.theme.AppTheme

@Composable
fun HistoryPage(
    history: List<HistoryGroup>,
    onResume: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        Modifier.fillMaxSize().background(AppColors.Background).padding(24.dp)
    ) {
        Text("历史会话", style = AppTheme.typography.title)
        Spacer(Modifier.height(4.dp))
        Text(
            "点击恢复历史对话",
            style = AppTheme.typography.caption,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 280.dp),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            history.forEach { group ->
                // Section header
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        group.label,
                        style = AppTheme.typography.body,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                // Session cards
                items(group.items, key = { it.sessionId }) { entry ->
                    HistoryCard(
                        title = entry.title,
                        sessionId = entry.sessionId,
                        onClick = { onResume(entry.sessionId) },
                        onDelete = { onDelete(entry.sessionId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    title: String,
    sessionId: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .shadow(1.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x0D000000)),
            contentAlignment = Alignment.Center
        ) {
            Text("💬", fontSize = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                sessionId.take(24),
                fontSize = 11.sp,
                color = AppColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "×",
            Modifier.clickable { onDelete() },
            color = AppColors.TextSecondary,
            fontSize = 14.sp
        )
    }
}
