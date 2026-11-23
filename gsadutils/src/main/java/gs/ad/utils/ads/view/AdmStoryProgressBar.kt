package gs.ad.utils.ads.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** Thanh progress 8dp bo tròn của native full story: nền xám, phần chạy trắng. */
class AdmStoryProgressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var trackColor: Int = 0xFF6B6B6B.toInt()
    var fillColor: Int = Color.WHITE

    /** 0..1. */
    var fraction: Float = 0f
        set(value) {
            val v = value.coerceIn(0f, 1f)
            if (v != field) { field = v; invalidate() }
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val r = height / 2f
        paint.color = trackColor
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, r, r, paint)
        if (fraction > 0f) {
            paint.color = fillColor
            rect.set(0f, 0f, width * fraction, height.toFloat())
            canvas.drawRoundRect(rect, r, r, paint)
        }
    }
}
