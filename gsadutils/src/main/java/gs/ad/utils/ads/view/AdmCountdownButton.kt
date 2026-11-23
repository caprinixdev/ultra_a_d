package gs.ad.utils.ads.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/**
 * Nút tròn có vòng đếm lùi (dùng chung cho native collapsible / native full story / native PIP).
 *
 * [startLock] khoá nút trong N ms: icon mờ + vòng chạy quanh nút, CHƯA gọi [onUnlockedClick]. Hết giờ mới bấm được.
 * Đếm theo đồng hồ thật (không dùng ValueAnimator) -> tắt animation trong Developer options cũng không bỏ qua được.
 * Nút luôn NUỐT chạm (kể cả lúc khoá) để chạm không lọt xuống ad bên dưới thành click ad.
 */
class AdmCountdownButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    enum class Icon { CHEVRON_DOWN, ARROW_RIGHT, CLOSE }

    var icon: Icon = Icon.CLOSE
        set(value) { field = value; invalidate() }

    /** Nền tròn. */
    var circleColor: Int = 0xB3585858.toInt()
        set(value) { field = value; invalidate() }

    var iconColor: Int = Color.WHITE
        set(value) { field = value; invalidate() }

    var ringColor: Int = Color.WHITE
        set(value) { field = value; invalidate() }

    /** true: vòng chạy dần cho đủ 1 vòng (collapsible). false: vòng đếm lùi ngắn dần (story / PIP). */
    var ringGrows: Boolean = false

    /** Độ dày vòng (px). */
    var ringWidthPx: Float = 2f * resources.displayMetrics.density

    /** Bấm lúc đã mở khoá. */
    var onUnlockedClick: (() -> Unit)? = null

    private var lockMs = 0L
    private var lockedAt = 0L

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = RectF()

    /** Tiến độ khoá 0..1 (1 = đã mở). */
    val progress: Float
        get() = if (lockMs <= 0L) 1f
        else ((SystemClock.uptimeMillis() - lockedAt).toFloat() / lockMs).coerceIn(0f, 1f)

    val isUnlocked: Boolean get() = progress >= 1f

    /** Khoá nút [ms] mili-giây kể từ bây giờ. `0` = mở ngay. */
    fun startLock(ms: Long) {
        lockMs = ms.coerceAtLeast(0L)
        lockedAt = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    fun unlockNow() {
        lockMs = 0L
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val size = min(w, h)
        val cx = w / 2f
        val cy = h / 2f
        val p = progress
        val locked = p < 1f

        paint.style = Paint.Style.FILL
        paint.color = circleColor
        canvas.drawCircle(cx, cy, size / 2f, paint)

        if (locked) {
            val sw = ringWidthPx
            arc.set(cx - size / 2f + sw / 2f, cy - size / 2f + sw / 2f, cx + size / 2f - sw / 2f, cy + size / 2f - sw / 2f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = sw
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = ringColor
            val sweep = if (ringGrows) 360f * p else 360f * (1f - p)
            canvas.drawArc(arc, -90f, sweep, false, paint)
        }

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 1.8f * resources.displayMetrics.density
        paint.color = iconColor
        paint.alpha = if (locked) 128 else 255
        val s = size / 32f   // toạ độ theo khung 32x32
        val ox = cx - size / 2f
        val oy = cy - size / 2f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            canvas.drawLine(ox + x1 * s, oy + y1 * s, ox + x2 * s, oy + y2 * s, paint)
        when (icon) {
            Icon.CHEVRON_DOWN -> { line(10.5f, 13.5f, 16f, 19f); line(21.5f, 13.5f, 16f, 19f) }
            Icon.ARROW_RIGHT -> { line(10f, 16f, 22.35f, 16f); line(17.34f, 11f, 22.5f, 16f); line(22.5f, 16f, 17.34f, 21f) }
            Icon.CLOSE -> { line(11.05f, 11.05f, 20.95f, 20.95f); line(11.05f, 20.95f, 20.95f, 11.05f) }
        }
        paint.alpha = 255

        if (locked) postInvalidateOnAnimation()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Luôn nuốt chạm: lúc khoá cũng không để lọt xuống ad bên dưới.
        if (event.actionMasked == MotionEvent.ACTION_UP &&
            event.x in 0f..width.toFloat() && event.y in 0f..height.toFloat() && isUnlocked
        ) {
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (isUnlocked) onUnlockedClick?.invoke()
        return true
    }
}
