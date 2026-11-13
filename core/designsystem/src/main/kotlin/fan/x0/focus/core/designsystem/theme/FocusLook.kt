package fan.x0.focus.core.designsystem.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

object FocusLook {
    private const val FILE = "focus_look"
    private const val CORNER_SCALE_KEY = "corner_scale"
    private var context: Context? = null

    var cornerScale by mutableFloatStateOf(1f)
        private set

    fun init(context: Context) {
        if (this.context != null) return
        val app = context.applicationContext ?: context
        this.context = app
        cornerScale = app.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getFloat(CORNER_SCALE_KEY, 1f)
            .coerceIn(MIN, MAX)
    }

    fun updateCornerScale(value: Float) {
        val next = value.coerceIn(MIN, MAX)
        if (next == cornerScale) return
        cornerScale = next
        context
            ?.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            ?.edit()
            ?.putFloat(CORNER_SCALE_KEY, next)
            ?.apply()
    }

    const val MIN = 0.4f
    const val MAX = 1.6f
}
