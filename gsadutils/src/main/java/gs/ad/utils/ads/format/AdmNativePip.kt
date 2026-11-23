package gs.ad.utils.ads.format

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import gs.ad.utils.R
import gs.ad.utils.ads.view.AdmCountdownButton
import gs.ad.utils.utils.PreferencesManager
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Native nổi kéo thả được (bản XML, cùng logic bản Compose `AdmNativePip`): card 225x238dp đặt ở [corner] cách lề
 * [marginDp], trượt vào từ mép. Kéo -> đi theo tay; thả -> hít về góc gần nhất. 1 giây đầu chặn chạm (chống bấm nhầm);
 * nút X góc trên trái khoá 3 giây (vòng cam). Bấm ad -> [reloadOnClick] load ad mới. Premium -> không hiện.
 *
 * Gắn vào content view của Activity (phủ lên nội dung, chỗ trống vẫn chạm xuyên xuống dưới).
 *
 * ```
 * val pip = AdmNativePip(this).apply { corner = AdmNativePip.Corner.TOP_RIGHT; onClose = { } }
 * pip.show()
 * // onDestroy: pip.dismiss()
 * ```
 */
class AdmNativePip(private val activity: Activity) {

    enum class Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    var corner: Corner = Corner.TOP_RIGHT
    var marginDp: Int = 10
    var adId: Int = -1
    var listAdCircularArray: List<String>? = null

    /** Bấm vào ad -> load ad mới, hiện lại từ đầu. */
    var reloadOnClick: Boolean = true

    /** Layout ad (giữ id ad_* như [R.layout.layout_native_ad_pip]). */
    var layoutNativeAdView: Int = R.layout.layout_native_ad_pip

    /** Người dùng bấm X. */
    var onClose: (() -> Unit)? = null
    var onAdShow: ((Int) -> Unit)? = null
    var onAdClicked: ((Int) -> Unit)? = null

    private var adm: AdmNativeAd? = null
    private var card: PipFrame? = null
    private var dismissed = false

    private val density get() = activity.resources.displayMetrics.density
    private val root: FrameLayout? get() = activity.findViewById(android.R.id.content)

    /** Load ad rồi hiện PIP. */
    fun show() {
        val pm = PreferencesManager.getInstance()
        if (pm.isSUB() || pm.isRemoveAds()) return
        dismissed = false
        load()
    }

    private fun load() {
        adm?.destroyNativeAd()
        val a = AdmNativeAd(adId, activity, false, listAdCircularArray)
        adm = a
        a.onAdLoaded = { activity.runOnUiThread { if (adm === a && !dismissed) attach(a) } }
        a.onAdShow = { onAdShow?.invoke(it) }
        a.onAdClicked = {
            onAdClicked?.invoke(it)
            if (reloadOnClick) activity.runOnUiThread { if (!dismissed) { detach(); load() } }
        }
        a.preloadAd()
    }

    /** Dựng card mới cho [a]: trượt vào từ mép tới góc [corner]. */
    @SuppressLint("ClickableViewAccessibility")
    private fun attach(a: AdmNativeAd) {
        val parent = root ?: return
        detach()
        val w = (PIP_WIDTH_DP * density).toInt()
        val h = (PIP_HEIGHT_DP * density).toInt()
        val frame = PipFrame(activity)
        val shownAt = SystemClock.uptimeMillis()
        Log.d(TAG, "show pip")
        val adContainer = ConstraintLayout(activity)
        frame.addView(adContainer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val close = AdmCountdownButton(activity).apply {
            icon = AdmCountdownButton.Icon.CLOSE
            ringColor = 0xFFFB7311.toInt()
            ringWidthPx = 1.2f * density
            startLock(CLOSE_LOCK_MS)
            onUnlockedClick = {
                Log.d(TAG, "close: ${SystemClock.uptimeMillis() - shownAt}ms sau khi hiện")
                dismiss()
                onClose?.invoke()
            }
        }
        val closeSize = (24 * density).toInt()
        frame.addView(close, FrameLayout.LayoutParams(closeSize, closeSize).apply {
            leftMargin = (8 * density).toInt(); topMargin = (8 * density).toInt()
        })
        parent.addView(frame, FrameLayout.LayoutParams(w, h))
        card = frame
        a.populateNativeAdView(adContainer, layoutNativeAdView)

        frame.post {
            val area = area(parent)
            val left = corner == Corner.TOP_LEFT || corner == Corner.BOTTOM_LEFT
            val top = corner == Corner.TOP_LEFT || corner == Corner.TOP_RIGHT
            val target = frame.cornerTarget(area, left, top)
            frame.x = if (left) -w.toFloat() else parent.width.toFloat()   // bắt đầu ngoài mép -> trượt vào
            frame.y = target.second
            frame.animate().x(target.first).setDuration(300).setInterpolator(DecelerateInterpolator()).start()
        }
    }

    /**
     * Vùng đặt được card (trừ status bar / navigation bar / tai thỏ, và ActionBar nếu nó đè lên nội dung — app tràn
     * viền, targetSdk 35+): left, top, right, bottom theo toạ độ [parent].
     */
    private fun area(parent: View): IntArray {
        val ins = ViewCompat.getRootWindowInsets(parent)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        var top = ins?.top ?: 0
        activity.window?.decorView?.findViewById<View>(androidx.appcompat.R.id.action_bar_container)
            ?.takeIf { it.isShown && it.height > 0 }
            ?.let { bar ->
                val barLoc = IntArray(2).also(bar::getLocationInWindow)
                val parentLoc = IntArray(2).also(parent::getLocationInWindow)
                top = maxOf(top, barLoc[1] + bar.height - parentLoc[1])
            }
        return intArrayOf(ins?.left ?: 0, top, parent.width - (ins?.right ?: 0), parent.height - (ins?.bottom ?: 0))
    }

    private fun detach() {
        card?.let { c -> c.animate().cancel(); (c.parent as? ViewGroup)?.removeView(c) }
        card = null
    }

    /** Gỡ PIP + huỷ ad (gọi khi đóng / rời màn). */
    fun dismiss() {
        dismissed = true
        detach()
        adm?.destroyNativeAd()
        adm = null
    }

    /**
     * Khung card: kéo quá touch slop -> card đi theo tay (con nhận cancel, không tính click ad); thả -> hít về góc gần
     * nhất. [TAP_GUARD_MS] đầu nuốt mọi chạm.
     */
    private inner class PipFrame(context: Context) : FrameLayout(context) {
        private val shownAt = SystemClock.uptimeMillis()
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var lastX = 0f
        private var lastY = 0f
        private var dragging = false

        private fun guarded() = SystemClock.uptimeMillis() - shownAt < TAP_GUARD_MS

        fun cornerTarget(area: IntArray, left: Boolean, top: Boolean): Pair<Float, Float> {
            val m = marginDp * density
            val x = if (left) area[0] + m else area[2] - width - m
            val y = if (top) area[1] + m else area[3] - height - m
            return x to y
        }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; lastX = ev.rawX; lastY = ev.rawY
                    dragging = false
                    animate().cancel()
                    return guarded()
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging && hypot(ev.rawX - downX, ev.rawY - downY) > slop) dragging = true
                    return dragging || guarded()
                }
            }
            return guarded()
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = ev.rawX; downY = ev.rawY; lastX = ev.rawX; lastY = ev.rawY }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging && hypot(ev.rawX - downX, ev.rawY - downY) > slop) dragging = true
                    if (dragging) {
                        x += ev.rawX - lastX
                        y += ev.rawY - lastY
                    }
                    lastX = ev.rawX; lastY = ev.rawY
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) snap()
                    dragging = false
                }
            }
            return true   // nuốt: chạm vào khung (kể cả lúc chặn) không lọt xuống nội dung bên dưới
        }

        /** Thả -> hít về góc gần nhất (theo tâm card). */
        private fun snap() {
            val parent = parent as? View ?: return
            val area = area(parent)
            val cx = x + width / 2f
            val cy = y + height / 2f
            val left = cx < (area[0] + area[2]) / 2f
            val top = cy < (area[1] + area[3]) / 2f
            val (tx, ty) = cornerTarget(area, left, top)
            if (abs(tx - x) < 1f && abs(ty - y) < 1f) return
            animate().x(tx).y(ty).setDuration(250).setInterpolator(DecelerateInterpolator()).start()
        }
    }

    private companion object {
        const val TAG = "AdmNativePip"
        const val PIP_WIDTH_DP = 225
        const val PIP_HEIGHT_DP = 238

        /** Sau khi hiện, chặn chạm ngần này (chống bấm nhầm). */
        const val TAP_GUARD_MS = 1_000L

        /** Nút X khoá ngần này (vòng cam chạy hết mới bấm được). */
        const val CLOSE_LOCK_MS = 3_000L
    }
}
