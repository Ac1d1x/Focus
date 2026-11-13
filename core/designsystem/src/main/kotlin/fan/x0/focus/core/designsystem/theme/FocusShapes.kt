package fan.x0.focus.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object FocusRadii {
    val medium = 12.dp
    val large = 16.dp
    val largeIncreased = 20.dp
    val extraLarge = 28.dp
    val extraLargeIncreased = 32.dp
    val extraExtraLarge = 48.dp
}

val LocalFocusCornerScale = staticCompositionLocalOf { 1f }

fun focusShapes(scale: Float): Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp * scale),
    small = RoundedCornerShape(8.dp * scale),
    medium = RoundedCornerShape(FocusRadii.medium * scale),
    large = RoundedCornerShape(FocusRadii.large * scale),
    largeIncreased = RoundedCornerShape(FocusRadii.largeIncreased * scale),
    extraLarge = RoundedCornerShape(FocusRadii.extraLarge * scale),
    extraLargeIncreased = RoundedCornerShape(FocusRadii.extraLargeIncreased * scale),
    extraExtraLarge = RoundedCornerShape(FocusRadii.extraExtraLarge * scale),
)

@Composable
private fun Dp.scaled(): Dp = this * LocalFocusCornerScale.current

object FocusShapeTokens {
    val card: Shape
        @Composable get() = RoundedCornerShape(FocusRadii.largeIncreased.scaled())

    val cardLarge: Shape
        @Composable get() = RoundedCornerShape(FocusRadii.extraLarge.scaled())

    val sheet: Shape
        @Composable get() = RoundedCornerShape(FocusRadii.extraLargeIncreased.scaled())

    val hero: Shape
        @Composable get() = RoundedCornerShape(FocusRadii.extraExtraLarge.scaled())

    val pill = RoundedCornerShape(percent = 50)

    val bubbleIncoming: Shape
        @Composable get() = RoundedCornerShape(28.dp.scaled(), 28.dp.scaled(), 28.dp.scaled(), 8.dp.scaled())

    val bubbleOutgoing: Shape
        @Composable get() = RoundedCornerShape(28.dp.scaled(), 28.dp.scaled(), 8.dp.scaled(), 28.dp.scaled())

    val assistantBubble: Shape
        @Composable get() = RoundedCornerShape(6.dp.scaled(), 24.dp.scaled(), 24.dp.scaled(), 24.dp.scaled())

    @Composable
    fun inner(outer: Dp, padding: Dp): Shape =
        RoundedCornerShape((outer.scaled() - padding).coerceAtLeast(0.dp))

    @Composable
    fun rounded(radius: Dp): Shape = RoundedCornerShape(radius.scaled())
}
