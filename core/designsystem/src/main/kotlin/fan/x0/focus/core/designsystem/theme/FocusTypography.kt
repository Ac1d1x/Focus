package fan.x0.focus.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle

private val FocusBaseline: Typography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = FocusDisplayFont),
        displayMedium = base.displayMedium.copy(fontFamily = FocusDisplayFont),
        displaySmall = base.displaySmall.copy(fontFamily = FocusDisplayFont),
        headlineLarge = base.headlineLarge.copy(fontFamily = FocusDisplayFont),
        headlineMedium = base.headlineMedium.copy(fontFamily = FocusDisplayFont),
        headlineSmall = base.headlineSmall.copy(fontFamily = FocusDisplayFont),
        titleLarge = base.titleLarge.copy(fontFamily = FocusTitleFont),
        titleMedium = base.titleMedium.copy(fontFamily = FocusTitleFont),
        titleSmall = base.titleSmall.copy(fontFamily = FocusTitleFont),
        bodyLarge = base.bodyLarge.copy(fontFamily = FocusBodyFont),
        bodyMedium = base.bodyMedium.copy(fontFamily = FocusBodyFont),
        bodySmall = base.bodySmall.copy(fontFamily = FocusBodyFont),
        labelLarge = base.labelLarge.copy(fontFamily = FocusBodyFont),
        labelMedium = base.labelMedium.copy(fontFamily = FocusBodyFont),
        labelSmall = base.labelSmall.copy(fontFamily = FocusBodyFont),
    )
}

data class FocusTypography(
    val baseline: Typography = FocusBaseline,
    val displayLarge: TextStyle = FocusBaseline.displayLargeEmphasized.copy(fontFamily = FocusDisplayFont),
    val displayMedium: TextStyle = FocusBaseline.displayMediumEmphasized.copy(fontFamily = FocusDisplayFont),
    val displaySmall: TextStyle = FocusBaseline.displaySmallEmphasized.copy(fontFamily = FocusDisplayFont),
    val headlineLarge: TextStyle = FocusBaseline.headlineLargeEmphasized.copy(fontFamily = FocusDisplayFont),
    val headlineMedium: TextStyle = FocusBaseline.headlineMediumEmphasized.copy(fontFamily = FocusDisplayFont),
    val headlineSmall: TextStyle = FocusBaseline.headlineSmallEmphasized.copy(fontFamily = FocusDisplayFont),
    val titleLarge: TextStyle = FocusBaseline.titleLargeEmphasized.copy(fontFamily = FocusTitleFont),
    val titleMedium: TextStyle = FocusBaseline.titleMediumEmphasized.copy(fontFamily = FocusTitleFont),
    val titleSmall: TextStyle = FocusBaseline.titleSmallEmphasized.copy(fontFamily = FocusTitleFont),
    val bodyLarge: TextStyle = FocusBaseline.bodyLargeEmphasized.copy(fontFamily = FocusBodyFont),
    val bodyMedium: TextStyle = FocusBaseline.bodyMediumEmphasized.copy(fontFamily = FocusBodyFont),
    val bodySmall: TextStyle = FocusBaseline.bodySmallEmphasized.copy(fontFamily = FocusBodyFont),
    val labelLarge: TextStyle = FocusBaseline.labelLargeEmphasized.copy(fontFamily = FocusBodyFont),
    val labelMedium: TextStyle = FocusBaseline.labelMediumEmphasized.copy(fontFamily = FocusBodyFont),
    val labelSmall: TextStyle = FocusBaseline.labelSmallEmphasized.copy(fontFamily = FocusBodyFont),
)
