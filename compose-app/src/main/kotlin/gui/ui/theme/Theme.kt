package gui.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

data class AppSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp
)

data class AppTypography(
    val title: TextStyle = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 48.sp,
        color = AppColors.TextPrimary
    ),
    val sectionTitle: TextStyle = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 30.sp,
        color = AppColors.TextPrimary
    ),
    val body: TextStyle = TextStyle(
        fontSize = 15.sp,
        color = AppColors.TextPrimary
    ),
    val caption: TextStyle = TextStyle(
        fontSize = 13.sp,
        color = AppColors.TextSecondary
    ),
    val mono: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp
    ),
    val bold: TextStyle = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = AppColors.TextPrimary
    )
)

object AppTheme {
    val spacing = AppSpacing()
    val typography = AppTypography()
}
