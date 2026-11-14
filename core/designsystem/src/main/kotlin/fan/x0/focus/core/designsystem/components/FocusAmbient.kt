package fan.x0.focus.core.designsystem.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.theme.FocusLook
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class FocusAmbientActivity {
    private var count = 0
    internal var working by mutableStateOf(false)
        private set
    internal val progress = mutableFloatStateOf(0f)

    internal fun acquire() {
        count += 1
        working = true
    }

    internal fun release() {
        count = (count - 1).coerceAtLeast(0)
        working = count > 0
    }
}

internal val LocalFocusAmbientActivity = staticCompositionLocalOf { FocusAmbientActivity() }

@Composable
fun FocusAmbientWorking(active: Boolean) {
    val activity = LocalFocusAmbientActivity.current
    DisposableEffect(activity, active) {
        if (active) activity.acquire()
        onDispose { if (active) activity.release() }
    }
}

@Composable
fun FocusAmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val activity = remember { FocusAmbientActivity() }
    val wallpaper = FocusLook.wallpaper
    val hasWallpaper = remember(wallpaper) {
        wallpaper.isNotBlank() && File(wallpaper).isFile
    }
    if (!hasWallpaper && FocusLook.ambientBackground) FocusAmbientClock(activity)
    CompositionLocalProvider(LocalFocusAmbientActivity provides activity) {
        Box(modifier = modifier.fillMaxSize()) {
            when {
                hasWallpaper -> FocusWallpaperBackdrop(
                    path = wallpaper,
                    blur = FocusLook.wallpaperBlur,
                    dim = FocusLook.wallpaperDim,
                )
                FocusLook.ambientBackground -> FocusAmbientBackdrop(activity)
                else -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            }
            content()
        }
    }
}

@Composable
fun FocusAmbientSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val activity = LocalFocusAmbientActivity.current
    val wallpaper = FocusLook.wallpaper
    val hasWallpaper = remember(wallpaper) {
        wallpaper.isNotBlank() && File(wallpaper).isFile
    }
    Box(modifier = modifier.fillMaxSize()) {
        when {
            hasWallpaper -> FocusWallpaperBackdrop(
                path = wallpaper,
                blur = FocusLook.wallpaperBlur,
                dim = FocusLook.wallpaperDim,
            )
            FocusLook.ambientBackground -> FocusAmbientBackdrop(activity)
            else -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        }
        content()
    }
}

@Composable
private fun FocusWallpaperBackdrop(path: String, blur: Float, dim: Float) {
    val image by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching { decodeWallpaper(path) }.getOrNull()
        }
    }
    Box(Modifier.fillMaxSize()) {
        val bitmap = image
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(blur.dp),
            )
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = dim.coerceIn(0f, 0.8f))),
        )
    }
}

private fun decodeWallpaper(path: String): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
    val bitmap = BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return null
    return bitmap.asImageBitmap()
}
