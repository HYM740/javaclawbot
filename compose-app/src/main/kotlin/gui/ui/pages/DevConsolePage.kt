package gui.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import gui.ui.Bridge
import gui.ui.theme.*

@Composable
fun DevConsolePage(bridge: Bridge?, modifier: Modifier = Modifier) {
    Column(
        Modifier.fillMaxSize().background(AppColors.Background).padding(40.dp, 24.dp, 24.dp, 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Dev Console", style = AppTheme.typography.title)
        Text(
            "Dev Console (coming soon)",
            style = AppTheme.typography.caption,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(32.dp))
        Text(
            "\u5F00\u53D1\u8005\u63A7\u5236\u53F0 \u2014 \u5F00\u53D1\u4E2D",
            style = AppTheme.typography.body,
            color = AppColors.TextSecondary,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}
