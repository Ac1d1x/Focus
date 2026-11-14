package fan.x0.focus.core.designsystem.theme

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.io.File

enum class ThemeMode { System, Light, Dark }

object FocusLook {
    private const val FILE = "focus_look"
    private const val CORNER_SCALE_KEY = "corner_scale"
    private const val THEME_MODE_KEY = "theme_mode"
    private const val DYNAMIC_COLOR_KEY = "dynamic_color"
    private const val SEED_KEY = "seed"
    private const val AMOLED_KEY = "amoled"
    private const val AMBIENT_KEY = "ambient_background"
    private const val WALLPAPER_KEY = "wallpaper"
    private const val WALLPAPER_BLUR_KEY = "wallpaper_blur"
    private const val WALLPAPER_DIM_KEY = "wallpaper_dim"
    private const val PREV_SEED_KEY = "prev_seed"
    private const val PREV_DYNAMIC_KEY = "prev_dynamic"
    private const val PREV_ACTIVE_KEY = "prev_active"
    private var context: Context? = null

    var cornerScale by mutableFloatStateOf(1f)
        private set

    var themeMode by mutableStateOf(ThemeMode.System)
        private set

    var dynamicColor by mutableStateOf(true)
        private set

    var seed by mutableLongStateOf(DEFAULT_SEED)
        private set

    var amoled by mutableStateOf(false)
        private set

    var ambientBackground by mutableStateOf(true)
        private set

    var wallpaper by mutableStateOf("")
        private set

    var wallpaperBlur by mutableFloatStateOf(18f)
        private set

    var wallpaperDim by mutableFloatStateOf(0.45f)
        private set

    private var previousSeed = DEFAULT_SEED
    private var previousDynamic = true
    private var previousActive = false

    fun init(context: Context) {
        if (this.context != null) return
        val app = context.applicationContext ?: context
        this.context = app
        val prefs = app.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        cornerScale = prefs.getFloat(CORNER_SCALE_KEY, 1f).coerceIn(MIN, MAX)
        themeMode = runCatching {
            ThemeMode.valueOf(prefs.getString(THEME_MODE_KEY, ThemeMode.System.name).orEmpty())
        }.getOrDefault(ThemeMode.System)
        dynamicColor = prefs.getBoolean(DYNAMIC_COLOR_KEY, true)
        seed = prefs.getLong(SEED_KEY, DEFAULT_SEED)
        amoled = prefs.getBoolean(AMOLED_KEY, false)
        ambientBackground = prefs.getBoolean(AMBIENT_KEY, true)
        wallpaper = prefs.getString(WALLPAPER_KEY, "").orEmpty()
        wallpaperBlur = prefs.getFloat(WALLPAPER_BLUR_KEY, 18f).coerceIn(0f, 40f)
        wallpaperDim = prefs.getFloat(WALLPAPER_DIM_KEY, 0.45f).coerceIn(0f, 0.8f)
        previousSeed = prefs.getLong(PREV_SEED_KEY, DEFAULT_SEED)
        previousDynamic = prefs.getBoolean(PREV_DYNAMIC_KEY, true)
        previousActive = prefs.getBoolean(PREV_ACTIVE_KEY, false)
    }

    fun updateCornerScale(value: Float) {
        val next = value.coerceIn(MIN, MAX)
        if (next == cornerScale) return
        cornerScale = next
        persist { putFloat(CORNER_SCALE_KEY, next) }
    }

    fun updateThemeMode(value: ThemeMode) {
        if (value == themeMode) return
        themeMode = value
        persist { putString(THEME_MODE_KEY, value.name) }
    }

    fun updateDynamicColor(value: Boolean) {
        if (value == dynamicColor) return
        dynamicColor = value
        persist { putBoolean(DYNAMIC_COLOR_KEY, value) }
    }

    fun updateSeed(value: Long) {
        if (value == seed) return
        seed = value
        persist { putLong(SEED_KEY, value) }
    }

    fun updateAmoled(value: Boolean) {
        if (value == amoled) return
        amoled = value
        persist { putBoolean(AMOLED_KEY, value) }
    }

    fun updateAmbientBackground(value: Boolean) {
        if (value == ambientBackground) return
        ambientBackground = value
        persist { putBoolean(AMBIENT_KEY, value) }
    }

    fun updateWallpaperBlur(value: Float) {
        val next = value.coerceIn(0f, 40f)
        if (next == wallpaperBlur) return
        wallpaperBlur = next
        persist { putFloat(WALLPAPER_BLUR_KEY, next) }
    }

    fun updateWallpaperDim(value: Float) {
        val next = value.coerceIn(0f, 0.8f)
        if (next == wallpaperDim) return
        wallpaperDim = next
        persist { putFloat(WALLPAPER_DIM_KEY, next) }
    }

    fun importWallpaper(context: Context, uri: Uri): String? = runCatching {
        val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
        val target = File(dir, "wallpaper-${System.currentTimeMillis()}.img")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        if (target.length() <= 0L) return@runCatching null
        dir.listFiles { file -> file.name.startsWith("wallpaper-") && file != target }
            ?.forEach { it.delete() }
        target.absolutePath
    }.getOrNull()

    fun applyWallpaper(path: String, color: Long?) {
        if (path.isBlank()) return
        if (wallpaper.isBlank()) {
            previousSeed = seed
            previousDynamic = dynamicColor
            previousActive = true
            persist {
                putLong(PREV_SEED_KEY, previousSeed)
                putBoolean(PREV_DYNAMIC_KEY, previousDynamic)
                putBoolean(PREV_ACTIVE_KEY, true)
            }
        }
        wallpaper = path
        persist { putString(WALLPAPER_KEY, path) }
        if (color != null) {
            seed = color
            dynamicColor = false
            persist {
                putLong(SEED_KEY, color)
                putBoolean(DYNAMIC_COLOR_KEY, false)
            }
        }
    }

    fun clearWallpaper() {
        if (wallpaper.isBlank()) return
        runCatching { File(wallpaper).delete() }
        wallpaper = ""
        persist { remove(WALLPAPER_KEY) }
        if (previousActive) {
            seed = previousSeed
            dynamicColor = previousDynamic
            previousActive = false
            persist {
                putLong(SEED_KEY, seed)
                putBoolean(DYNAMIC_COLOR_KEY, dynamicColor)
                remove(PREV_SEED_KEY)
                remove(PREV_DYNAMIC_KEY)
                remove(PREV_ACTIVE_KEY)
            }
        }
    }

    private fun persist(block: android.content.SharedPreferences.Editor.() -> Unit) {
        context
            ?.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            ?.edit()
            ?.apply(block)
            ?.apply()
    }

    const val MIN = 0.4f
    const val MAX = 1.6f
    const val DEFAULT_SEED = 0xFF3F6B4F
}

fun extractSeedFromWallpaper(path: String): Long? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 96) sample *= 2
    val bitmap = BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return@runCatching null
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    bitmap.recycle()
    val hsv = FloatArray(3)
    var cosSum = 0.0
    var sinSum = 0.0
    var saturationSum = 0.0
    var valueSum = 0.0
    var weightSum = 0.0
    val step = maxOf(1, pixels.size / 4096)
    var index = 0
    while (index < pixels.size) {
        val pixel = pixels[index]
        if (((pixel ushr 24) and 0xFF) >= 128) {
            android.graphics.Color.colorToHSV(pixel, hsv)
            val saturation = hsv[1]
            val value = hsv[2]
            if (saturation >= 0.15f && value >= 0.12f && value <= 0.97f) {
                val weight = saturation * (1f - kotlin.math.abs(value - 0.6f)).coerceAtLeast(0.1f)
                val angle = Math.toRadians(hsv[0].toDouble())
                cosSum += kotlin.math.cos(angle) * weight
                sinSum += kotlin.math.sin(angle) * weight
                saturationSum += saturation * weight
                valueSum += value * weight
                weightSum += weight
            }
        }
        index += step
    }
    if (weightSum <= 0.0) return@runCatching null
    val hue = ((Math.toDegrees(kotlin.math.atan2(sinSum, cosSum)) + 360.0) % 360.0).toFloat()
    val saturation = (saturationSum / weightSum).toFloat().coerceIn(0.3f, 0.75f)
    val value = (valueSum / weightSum).toFloat().coerceIn(0.35f, 0.78f)
    android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)).toLong() and 0xFFFFFFFFL
}.getOrNull()

val SeedPresets: List<Long> = listOf(
    FocusLook.DEFAULT_SEED,
    0xFF4A6FE3,
    0xFF7B5CD6,
    0xFFB5489C,
    0xFFD2475B,
    0xFFD9772B,
    0xFFB58A00,
    0xFF5E8F2F,
    0xFF16907A,
    0xFF1E88B5,
    0xFF5C6B7A,
    0xFF8A6A4F,
)

fun seedScheme(seed: Color, dark: Boolean): ColorScheme {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(seed.toArgb(), hsv)
    val hue = hsv[0]
    val chroma = (hsv[1] * 0.9f).coerceIn(0.25f, 0.8f)
    fun tone(h: Float, s: Float, t: Int): Color =
        Color.hsl(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), t / 100f)
    fun p(t: Int) = tone(hue, chroma * 0.62f, t)
    fun s(t: Int) = tone(hue, chroma * 0.24f, t)
    fun te(t: Int) = tone(hue + 62f, chroma * 0.44f, t)
    fun n(t: Int) = tone(hue, 0.05f, t)
    fun nv(t: Int) = tone(hue, 0.11f, t)
    fun e(t: Int) = tone(6f, 0.72f, t)
    return if (!dark) {
        lightColorScheme(
            primary = p(38),
            onPrimary = p(100),
            primaryContainer = p(90),
            onPrimaryContainer = p(14),
            inversePrimary = p(80),
            secondary = s(40),
            onSecondary = s(100),
            secondaryContainer = s(90),
            onSecondaryContainer = s(14),
            tertiary = te(38),
            onTertiary = te(100),
            tertiaryContainer = te(90),
            onTertiaryContainer = te(14),
            error = e(42),
            onError = e(100),
            errorContainer = e(92),
            onErrorContainer = e(16),
            background = n(98),
            onBackground = n(10),
            surface = n(98),
            onSurface = n(10),
            surfaceVariant = nv(90),
            onSurfaceVariant = nv(30),
            surfaceTint = p(38),
            inverseSurface = n(20),
            inverseOnSurface = n(95),
            outline = nv(50),
            outlineVariant = nv(82),
            scrim = Color.Black,
            surfaceBright = n(98),
            surfaceDim = n(88),
            surfaceContainerLowest = n(100),
            surfaceContainerLow = n(96),
            surfaceContainer = n(94),
            surfaceContainerHigh = n(92),
            surfaceContainerHighest = n(90),
        )
    } else {
        darkColorScheme(
            primary = p(80),
            onPrimary = p(18),
            primaryContainer = p(30),
            onPrimaryContainer = p(90),
            inversePrimary = p(38),
            secondary = s(80),
            onSecondary = s(18),
            secondaryContainer = s(28),
            onSecondaryContainer = s(90),
            tertiary = te(80),
            onTertiary = te(18),
            tertiaryContainer = te(30),
            onTertiaryContainer = te(90),
            error = e(80),
            onError = e(20),
            errorContainer = e(30),
            onErrorContainer = e(90),
            background = n(7),
            onBackground = n(90),
            surface = n(7),
            onSurface = n(90),
            surfaceVariant = nv(28),
            onSurfaceVariant = nv(80),
            surfaceTint = p(80),
            inverseSurface = n(90),
            inverseOnSurface = n(20),
            outline = nv(58),
            outlineVariant = nv(28),
            scrim = Color.Black,
            surfaceBright = n(24),
            surfaceDim = n(7),
            surfaceContainerLowest = n(4),
            surfaceContainerLow = n(10),
            surfaceContainer = n(13),
            surfaceContainerHigh = n(17),
            surfaceContainerHighest = n(22),
        )
    }
}

fun resolveScheme(context: Context, dark: Boolean): ColorScheme {
    val base = when {
        FocusLook.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        FocusLook.seed == FocusLook.DEFAULT_SEED -> if (dark) FallbackDarkScheme else FallbackLightScheme
        else -> seedScheme(Color(FocusLook.seed), dark)
    }
    if (!dark || !FocusLook.amoled) return base
    return base.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0A0A0A),
        surfaceContainer = Color(0xFF111111),
        surfaceContainerHigh = Color(0xFF181818),
        surfaceContainerHighest = Color(0xFF222222),
    )
}
