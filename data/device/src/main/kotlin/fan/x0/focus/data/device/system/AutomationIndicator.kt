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
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import kotlin.math.hypot

private const val GESTURE_LINGER_MS = 320L
private const val TAP_RADIUS_DP = 26f
private const val DOT_RADIUS_DP = 8f
private const val STROKE_DP = 3f
private const val TAP_SLOP_DP = 8f

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
        if (target is GestureView) target.cancelAnimation()
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
