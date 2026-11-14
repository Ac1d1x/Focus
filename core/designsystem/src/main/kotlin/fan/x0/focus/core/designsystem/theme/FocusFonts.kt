package fan.x0.focus.core.designsystem.theme

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font as PlatformFont
import android.graphics.fonts.FontFamily as PlatformFontFamily
import android.os.Build
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

private object FallbackLoader : AndroidFont.TypefaceLoader {
    override fun loadBlocking(context: Context, font: AndroidFont): Typeface {
        val settings = (font as? FallbackFont)?.variationString
            ?: font.variationSettings.toString()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return Typeface.Builder(context.assets, "fonts/GoogleSansFlex.ttf")
                .setFontVariationSettings(settings)
                .build()
        }
        return runCatching {
            val base = PlatformFont.Builder(context.assets, "fonts/GoogleSansFlex.ttf")
                .setFontVariationSettings(settings)
                .build()
            val fallback = PlatformFont.Builder(context.assets, "fonts/SourceHanSansCN.ttf")
                .setFontVariationSettings("'wght' ${font.weight.weight}")
                .build()
            Typeface.CustomFallbackBuilder(
                PlatformFontFamily.Builder(base).build(),
            )
                .addCustomFallback(PlatformFontFamily.Builder(fallback).build())
                .setSystemFallback("sans-serif")
                .build()
        }.getOrElse {
            Typeface.Builder(context.assets, "fonts/GoogleSansFlex.ttf")
                .setFontVariationSettings(settings)
                .build()
        }
    }

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface =
        loadBlocking(context, font)
}

private class FallbackFont(
    weightValue: Int,
    width: Float,
    roundness: Float,
    opticalSize: Float,
) : AndroidFont(
    loadingStrategy = FontLoadingStrategy.Blocking,
    typefaceLoader = FallbackLoader,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weightValue),
        FontVariation.width(width),
        FontVariation.Setting("ROND", roundness),
        FontVariation.Setting("opsz", opticalSize),
    ),
) {
    val variationString: String =
        "'wght' $weightValue, 'wdth' $width, 'ROND' $roundness, 'opsz' $opticalSize"
    override val weight: FontWeight = FontWeight(weightValue)
    override val style: FontStyle = FontStyle.Normal
}

val FocusDisplayFont: FontFamily = FontFamily(
    FallbackFont(300, 90f, 52f, 48f),
    FallbackFont(400, 91f, 50f, 48f),
    FallbackFont(500, 92f, 48f, 48f),
    FallbackFont(600, 93f, 46f, 48f),
    FallbackFont(700, 94f, 44f, 48f),
    FallbackFont(800, 95f, 42f, 48f),
    FallbackFont(900, 96f, 40f, 48f),
)

val FocusTitleFont: FontFamily = FontFamily(
    FallbackFont(300, 99f, 30f, 24f),
    FallbackFont(400, 100f, 28f, 24f),
    FallbackFont(500, 100f, 26f, 24f),
    FallbackFont(600, 101f, 24f, 24f),
    FallbackFont(700, 101f, 22f, 24f),
    FallbackFont(800, 102f, 20f, 24f),
    FallbackFont(900, 102f, 18f, 24f),
)

val FocusBodyFont: FontFamily = FontFamily(
    FallbackFont(300, 100f, 10f, 18f),
    FallbackFont(400, 100f, 10f, 18f),
    FallbackFont(500, 100f, 8f, 18f),
    FallbackFont(600, 100f, 8f, 18f),
    FallbackFont(700, 100f, 6f, 18f),
    FallbackFont(800, 100f, 6f, 18f),
    FallbackFont(900, 100f, 4f, 18f),
)

private object MathFontLoader : AndroidFont.TypefaceLoader {
    override fun loadBlocking(context: Context, font: AndroidFont): Typeface =
        Typeface.Builder(context.assets, "fonts/NotoSansMath-Regular.ttf").build()

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface =
        loadBlocking(context, font)
}

private class MathFont : AndroidFont(
    loadingStrategy = FontLoadingStrategy.Blocking,
    typefaceLoader = MathFontLoader,
    variationSettings = FontVariation.Settings(),
) {
    override val weight: FontWeight = FontWeight.Normal
    override val style: FontStyle = FontStyle.Normal
}

val FocusMathFont: FontFamily = FontFamily(MathFont())
