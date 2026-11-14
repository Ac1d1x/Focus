package fan.x0.focus.core.designsystem.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import fan.x0.focus.core.designsystem.R

private fun googleSans(
    weight: Int,
    width: Float,
    roundness: Float,
    opticalSize: Float,
): Font = Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.width(width),
        FontVariation.Setting("ROND", roundness),
        FontVariation.Setting("opsz", opticalSize),
    ),
)

private fun sourceHan(weight: Int): Font = Font(
    resId = R.font.source_han_sans_cn,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val FocusDisplayFont: FontFamily = FontFamily(
    googleSans(300, 90f, 52f, 48f),
    googleSans(400, 91f, 50f, 48f),
    googleSans(500, 92f, 48f, 48f),
    googleSans(600, 93f, 46f, 48f),
    googleSans(700, 94f, 44f, 48f),
    googleSans(800, 95f, 42f, 48f),
    googleSans(900, 96f, 40f, 48f),
)

val FocusTitleFont: FontFamily = FontFamily(
    googleSans(300, 99f, 30f, 24f),
    googleSans(400, 100f, 28f, 24f),
    googleSans(500, 100f, 26f, 24f),
    googleSans(600, 101f, 24f, 24f),
    googleSans(700, 101f, 22f, 24f),
    googleSans(800, 102f, 20f, 24f),
    googleSans(900, 102f, 18f, 24f),
)

val FocusBodyFont: FontFamily = FontFamily(
    sourceHan(300),
    sourceHan(400),
    sourceHan(500),
    sourceHan(600),
    sourceHan(700),
    sourceHan(800),
    sourceHan(900),
)
