package fan.x0.focus.data.device.system

import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.text.TextUtils
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import fan.x0.focus.data.device.R
import kotlin.math.hypot
import kotlin.math.min

private const val GESTURE_LINGER_MS = 320L
private const val LABEL_MS = 1300L
private const val TAP_RADIUS_DP = 26f
private const val DOT_RADIUS_DP = 8f
private const val STROKE_DP = 3f
private const val TAP_SLOP_DP = 8f
private const val LABEL_PAD_X_DP = 14f
private const val LABEL_PAD_Y_DP = 9f
private const val LABEL_RADIUS_DP = 18f
private const val LABEL_TEXT_SP = 14f
private const val LABEL_MARGIN_DP = 16f
private const val KEY_TOP_DP = 96f

class AutomationIndicator(private val service: AccessibilityService) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var view: View? = null

    fun showGesture(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long) = onMain {
        dismissNow()
        add(
            GestureView(
                context = service,
                fromX = fromX,
                fromY = fromY,
                toX = toX,
                toY = toY,
                gestureMs = durationMs,
                onEnd = ::dismissNow,
            ),
        )
    }

    fun showText(text: String?, anchorX: Float, anchorTop: Float) = onMain {
        dismissNow()
        val label = text
            ?.trim()
            ?.take(64)
            ?.takeIf { it.isNotEmpty() }
            ?: service.getString(R.string.device_indicator_typing)
        add(
            LabelView(
                context = service,
                text = label,
                anchorX = anchorX,
                anchorY = anchorTop,
                aboveAnchor = true,
                onEnd = ::dismissNow,
            ),
        )
    }

    fun showKey(text: String) = onMain {
        dismissNow()
        val screen = screenSize(service)
        add(
            LabelView(
                context = service,
                text = text,
                anchorX = screen.x / 2f,
                anchorY = KEY_TOP_DP * service.resources.displayMetrics.density,
                aboveAnchor = false,
                onEnd = ::dismissNow,
            ),
        )
    }

    fun showLabel(text: String) = showKey(text)

    fun dismiss() = onMain { dismissNow() }

    private fun add(target: View) {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        target.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        val manager = windowManager ?: return
        val added = runCatching {
            manager.addView(target, params)
        }.isSuccess
        if (added) view = target
    }

    private fun dismissNow() {
        val target = view ?: return
        view = null
        when (target) {
            is GestureView -> target.cancelAnimation()
            is LabelView -> target.cancelAnimation()
        }
        runCatching { windowManager?.removeViewImmediate(target) }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}

private class GestureView(
    context: Context,
    private val fromX: Float,
    private val fromY: Float,
    private val toX: Float,
    private val toY: Float,
    gestureMs: Long,
    private val onEnd: () -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val isTap = hypot(toX - fromX, toY - fromY) < TAP_SLOP_DP * density
    private val total = gestureMs.coerceAtLeast(1) + GESTURE_LINGER_MS
    private val accent = accentColor(context)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val location = IntArray(2)
    private val ripple = DecelerateInterpolator()
    private var progress = 0f
    private var alphaScale = 1f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = total
        interpolator = null
        addUpdateListener {
            val elapsed = it.animatedFraction * total
            progress = (elapsed / gestureMs.coerceAtLeast(1)).coerceAtMost(1f)
            alphaScale = (1f - (elapsed - gestureMs) / GESTURE_LINGER_MS).coerceIn(0f, 1f)
            invalidate()
        }
        addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false

            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled) onEnd()
            }
        })
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    fun cancelAnimation() = animator.cancel()

    override fun onDraw(canvas: Canvas) {
        getLocationOnScreen(location)
        canvas.translate(-location[0].toFloat(), -location[1].toFloat())
        if (isTap) drawTap(canvas) else drawSwipe(canvas)
    }

    private fun drawTap(canvas: Canvas) {
        val spread = ripple.getInterpolation(progress)
        fill.color = withAlpha(accent, 0.24f * alphaScale)
        canvas.drawCircle(fromX, fromY, TAP_RADIUS_DP * density * (0.4f + 0.6f * spread), fill)
        drawDot(canvas, fromX, fromY)
    }

    private fun drawSwipe(canvas: Canvas) {
        val x = fromX + (toX - fromX) * progress
        val y = fromY + (toY - fromY) * progress
        line.strokeWidth = (DOT_RADIUS_DP * 2 + STROKE_DP) * density
        line.color = withAlpha(Color.BLACK, 0.2f * alphaScale)
        canvas.drawLine(fromX, fromY, x, y, line)
        line.strokeWidth = DOT_RADIUS_DP * 2 * density
        line.color = withAlpha(accent, 0.36f * alphaScale)
        canvas.drawLine(fromX, fromY, x, y, line)
        drawDot(canvas, x, y)
    }

    private fun drawDot(canvas: Canvas, x: Float, y: Float) {
        fill.color = withAlpha(Color.BLACK, 0.35f * alphaScale)
        canvas.drawCircle(x, y, (DOT_RADIUS_DP + STROKE_DP / 2) * density, fill)
        fill.color = withAlpha(accent, alphaScale)
        canvas.drawCircle(x, y, DOT_RADIUS_DP * density, fill)
    }
}

private class LabelView(
    context: Context,
    text: String,
    anchorX: Float,
    anchorY: Float,
    aboveAnchor: Boolean,
    private val onEnd: () -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val screen = screenSize(context)
    private val margin = LABEL_MARGIN_DP * density
    private val padX = LABEL_PAD_X_DP * density
    private val padY = LABEL_PAD_Y_DP * density
    private val radius = LABEL_RADIUS_DP * density
    private val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(190, 24, 24, 24)
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = LABEL_TEXT_SP * resources.displayMetrics.scaledDensity
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val label = TextUtils.ellipsize(
        text,
        textPaint,
        (screen.x - margin * 2 - padX * 2).coerceAtLeast(1f),
        TextUtils.TruncateAt.END,
    ).toString()
    private val textWidth = textPaint.measureText(label)
    private val metrics = textPaint.fontMetrics
    private val textHeight = metrics.descent - metrics.ascent
    private val bubbleWidth = min(textWidth + padX * 2, screen.x - margin * 2)
    private val bubbleHeight = textHeight + padY * 2
    private val left = (anchorX - bubbleWidth / 2f).coerceIn(margin, (screen.x - bubbleWidth - margin).coerceAtLeast(margin))
    private val top = if (aboveAnchor) {
        (anchorY - bubbleHeight - margin * 0.5f).coerceIn(margin, (screen.y - bubbleHeight - margin).coerceAtLeast(margin))
    } else {
        (anchorY - bubbleHeight / 2f).coerceIn(margin, (screen.y - bubbleHeight - margin).coerceAtLeast(margin))
    }
    private val rect = RectF(left, top, left + bubbleWidth, top + bubbleHeight)
    private val baseline = top + padY - metrics.ascent
    private val location = IntArray(2)
    private var progress = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = LABEL_MS
        addUpdateListener {
            progress = it.animatedFraction
            invalidate()
        }
        addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false

            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled) onEnd()
            }
        })
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    fun cancelAnimation() = animator.cancel()

    override fun onDraw(canvas: Canvas) {
        val fade = when {
            progress < 0.12f -> progress / 0.12f
            progress > 0.78f -> (1f - progress) / 0.22f
            else -> 1f
        }.coerceIn(0f, 1f)
        if (fade <= 0f) return
        getLocationOnScreen(location)
        canvas.translate(-location[0].toFloat(), -location[1].toFloat())
        background.alpha = (190 * fade).toInt()
        textPaint.alpha = (255 * fade).toInt()
        canvas.drawRoundRect(rect, radius, radius, background)
        canvas.drawText(label, left + padX, baseline, textPaint)
    }
}

private fun screenSize(context: Context): Point {
    val windowManager = context.getSystemService(WindowManager::class.java)
        ?: return Point(
            context.resources.displayMetrics.widthPixels,
            context.resources.displayMetrics.heightPixels,
        )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bounds = windowManager.maximumWindowMetrics.bounds
        return Point(bounds.width(), bounds.height())
    }
    @Suppress("DEPRECATION")
    val display: Display = windowManager.defaultDisplay
    return Point().also { display.getRealSize(it) }
}

private fun accentColor(context: Context): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getColor(android.R.color.system_accent1_200)
    } else {
        0xFFD0BCFF.toInt()
    }

private fun withAlpha(color: Int, alpha: Float): Int =
    Color.argb(
        (alpha * 255).toInt().coerceIn(0, 255),
        Color.red(color),
        Color.green(color),
        Color.blue(color),
    )
