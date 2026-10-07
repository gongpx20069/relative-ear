package io.github.gongpx20069.relativeear

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Pine = Color(0xFF173F36)
val Mint = Color(0xFFBDE4CF)
val WarmGold = Color(0xFFECC776)

private val EarColors = lightColorScheme(
    primary = Pine, onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEDE4), onPrimaryContainer = Pine,
    secondary = Color(0xFF76603A), secondaryContainer = Color(0xFFF7EBCD),
    background = Color(0xFFF7F7F2), onBackground = Color(0xFF202E29),
    surface = Color.White, onSurface = Color(0xFF202E29),
    surfaceVariant = Color(0xFFEDF0E9), onSurfaceVariant = Color(0xFF59675F),
    outline = Color(0xFF7D8B82), outlineVariant = Color(0xFFE0E6DE),
    error = Color(0xFFAA3931), errorContainer = Color(0xFFFFE5E0),
)
private val BaseType = Typography()
private val EarType = BaseType.copy(
    headlineLarge = BaseType.headlineLarge.copy(fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    headlineMedium = BaseType.headlineMedium.copy(fontWeight = FontWeight.Bold),
    titleLarge = BaseType.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseType.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = BaseType.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun EarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EarColors, typography = EarType,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp)), content = content,
    )
}
