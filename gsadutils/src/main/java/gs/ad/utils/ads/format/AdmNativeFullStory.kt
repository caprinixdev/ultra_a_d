package gs.ad.utils.ads.format

import android.app.Activity
import android.app.Dialog
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import gs.ad.utils.R
import gs.ad.utils.ads.view.AdmCountdownButton
import gs.ad.utils.ads.view.AdmStoryProgressBar
import gs.ad.utils.utils.PreferencesManager

/**
 * Native full-screen sau inter, 2 thanh loading (bản XML, cùng logic bản Compose `AdmNativeFullStory`):
 * [pages] trang, mỗi trang 1 thanh progress (trong card đáy, dưới nút cài đặt) chạy [stepMs]. Nút tròn góc trên phải
 * luôn hiện, mờ + vòng đếm lùi khi đang chạy: trang giữa = mũi tên skip, trang cuối = X. Chạy xong mới bấm được.
 * Back bị chặn tới khi tắt được.
 *
 * - Truyền `nativeAds` (đã preloadAd, app sở hữu) -> show, KHÔNG huỷ. Trang thiếu ad -> mượn ad khác trong list.
 * - Không truyền -> tự load [adsToLoad] ad (mặc định [pages]) theo [adId], tự huỷ khi đóng.
 * - Không có ad nào sau 10 giây / premium -> đóng luôn ([onClose]).
 *
 * ```
 * inter.onAdClosed = {
 *     AdmNativeFullStory(this).apply { onClose = { goNext() } }.show(listOf(storyAd1, storyAd2))
 * }
 * ```
 */
class AdmNativeFullStory(private val activity: Activity) {

    var pages: Int = 2
    var stepMs: Long = 5_000L

    /** Tự load: id native ([AdmNativeAd] id, -1 xoay vòng) + list xoay vòng riêng nếu có. */
    var adId: Int = -1
    var listAdCircularArray: List<String>? = null

    /** Tự load: số ad nạp (1..pages). 1 = cả story 1 ad, trang sau dùng lại (đỡ phí ad về muộn). */
    var adsToLoad: Int = pages

    /** Layout từng trang (giữ id ad_* như [R.layout.layout_native_ad_full_story]). */
    var layoutNativeAdView: Int = R.layout.layout_native_ad_full_story

    /** Story đóng (bấm X / Back lúc được phép / không có ad / premium). */
    var onClose: (() -> Unit)? = null

    var onAdShow: ((Int) -> Unit)? = null
    var onAdClicked: ((Int) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private var dialog: Dialog? = null
    private var ads: List<AdmNativeAd?> = emptyList()
    private var selfLoad = false
    private val locked = HashMap<Int, AdmNativeAd>()

    /** Ad tự load đã có kết quả (có ad hoặc lỗi). */
    private val settled = HashSet<AdmNativeAd>()
    private var page = 0
    private var pageStartedAt = 0L
    private var stepDone = false
    private var shownAd: AdmNativeAd? = null
    private var closed = false

    private lateinit var adContainer: ConstraintLayout
    private lateinit var loading: View
    private lateinit var action: AdmCountdownButton
    private val bars = ArrayList<AdmStoryProgressBar>()

    private val isLast: Boolean get() = page >= pages - 1

    /** Hiện story. [nativeAds] = ad đã [AdmNativeAd.preloadAd] (app sở hữu); null = tự load. */
    fun show(nativeAds: List<AdmNativeAd?>? = null) {
        pages = pages.coerceAtLeast(1)
        val pm = PreferencesManager.getInstance()
        if (pm.isSUB() || pm.isRemoveAds() || activity.isFinishing || activity.isDestroyed) {
            onClose?.invoke()
            return
        }
        selfLoad = nativeAds == null
        ads = nativeAds ?: List(adsToLoad.coerceIn(1, pages)) {
            AdmNativeAd(adId, activity, true, listAdCircularArray).also { adm ->
                adm.onAdLoaded = { activity.runOnUiThread { settled += adm; render() } }
                adm.onAdFailToLoaded = { _, _, _ -> activity.runOnUiThread { settled += adm; render() } }
            }
        }
        ads.forEach { adm ->
            adm?.onAdShow = { onAdShow?.invoke(it) }
            adm?.onAdClicked = { onAdClicked?.invoke(it) }
        }

        val d = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        d.setContentView(R.layout.layout_native_full_story)
        d.setCancelable(false)
        d.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        // Back: chặn tới khi được tắt. Đang chờ ad (chưa hiện gì) -> cho thoát.
        d.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                if (shownAd == null || (stepDone && isLast)) close()
                true
            } else keyCode == KeyEvent.KEYCODE_BACK
        }
        adContainer = d.findViewById(R.id.story_ad_container)
        loading = d.findViewById(R.id.story_loading)
        action = d.findViewById(R.id.story_action)
        val barRow = d.findViewById<LinearLayout>(R.id.story_bars)
        val gap = (4 * activity.resources.displayMetrics.density).toInt()
        repeat(pages) { i ->
            val bar = AdmStoryProgressBar(activity)
            barRow.addView(bar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                if (i > 0) marginStart = gap
            })
            bars += bar
        }
        action.onUnlockedClick = { if (isLast) close() else { page += 1; startPage() } }
        dialog = d
        d.show()

        if (selfLoad) ads.forEach { it?.preloadAd() }
        // Không có ad nào sau thời gian chờ -> đóng.
        handler.postDelayed({ if (shownAd == null && locked.isEmpty()) close() }, WAIT_AD_TIMEOUT_MS)
        startPage()
    }

    private fun adAt(i: Int): AdmNativeAd? = ads.getOrNull(i)?.takeIf { it.nativeAd != null }

    /** Ad cho trang hiện tại: ad của trang -> ad chưa trang nào dùng -> ad bất kỳ. Chốt 1 lần/trang. */
    private fun pickAd(): AdmNativeAd? {
        locked[page]?.let { return it }
        val available = ads.filterNotNull().filter { it.nativeAd != null }
        return adAt(page)
            ?: available.firstOrNull { it !in locked.values }
            ?: available.firstOrNull()
    }

    private fun startPage() {
        shownAd = null
        stepDone = false
        render()
    }

    /** Vẽ trang hiện tại (gọi lại khi có ad về). Có ad -> hiện + bắt đầu đếm [stepMs]. */
    private fun render() {
        if (closed || dialog == null) return
        if (shownAd != null) return
        val adm = pickAd()
        if (adm == null) {
            loading.visibility = View.VISIBLE
            action.icon = if (isLast) AdmCountdownButton.Icon.CLOSE else AdmCountdownButton.Icon.ARROW_RIGHT
            action.unlockNow()
            action.visibility = View.INVISIBLE
            if (selfLoad && ads.all { it?.isLoaded() == true } && locked.isEmpty()) close()   // load xong hết mà không có ad
            return
        }
        locked[page] = adm
        shownAd = adm
        loading.visibility = View.GONE
        adm.populateNativeAdView(adContainer, layoutNativeAdView)
        action.visibility = View.VISIBLE
        action.icon = if (isLast) AdmCountdownButton.Icon.CLOSE else AdmCountdownButton.Icon.ARROW_RIGHT
        action.startLock(stepMs)
        pageStartedAt = SystemClock.uptimeMillis()
        tick()
    }

    /** Chạy thanh progress của trang theo đồng hồ thật (tắt animation hệ thống cũng không bỏ qua). */
    private fun tick() {
        if (closed) return
        val p = ((SystemClock.uptimeMillis() - pageStartedAt).toFloat() / stepMs).coerceIn(0f, 1f)
        bars.forEachIndexed { i, bar -> bar.fraction = if (i < page) 1f else if (i == page) p else 0f }
        if (p >= 1f) {
            stepDone = true
            action.unlockNow()
            return
        }
        adContainer.postOnAnimation { tick() }
    }

    /** Đóng story: huỷ ad tự load (ad app truyền vào thì không đụng), gọi [onClose]. */
    fun close() {
        if (closed) return
        closed = true
        handler.removeCallbacksAndMessages(null)
        runCatching { dialog?.dismiss() }
        dialog = null
        if (selfLoad) ads.forEach { it?.destroyNativeAd() }
        onClose?.invoke()
    }

    /** Tự load: ad này đã có kết quả (có ad / lỗi). */
    private fun AdmNativeAd.isLoaded(): Boolean = nativeAd != null || this in settled

    companion object {
        /** Chờ tối đa ngần này mà không có ad nào -> tự đóng. */
        private const val WAIT_AD_TIMEOUT_MS = 10_000L
    }
}
