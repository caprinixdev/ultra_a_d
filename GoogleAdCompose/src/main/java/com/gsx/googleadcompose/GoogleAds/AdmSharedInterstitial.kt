package com.gsx.googleadcompose.GoogleAds

import android.app.Activity
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo
import com.gsx.googleadcompose.error.AdmErrorType
import com.gsx.googleadcompose.utils.PreferencesManager

/**
 * Luật đếm lượt của `countToShowAds` ở bản 1.0.20: hiện ở lượt thứ [startAds], sau đó cứ [loopAds] lượt hiện một lần
 * (`loopAds <= 0` -> chỉ hiện đúng lượt [startAds]).
 */
internal object AdCounterRule {
    fun isDue(count: Int, startAds: Int, loopAds: Int): Boolean = when {
        count < startAds -> false
        count == startAds -> true
        loopAds <= 0 -> false
        else -> (count - startAds) % loopAds == 0
    }
}

/**
 * Interstitial DÙNG CHUNG cả app — cách khuyên dùng cho inter ở nhiều màn hoặc hiện theo lượt.
 *
 * Kiểu "mỗi màn một `AdmInterstitial { }` + `load()` lúc vào màn" làm tụt show rate: rời màn là instance cùng ad đã nạp
 * bị huỷ, mỗi lần vào màn là thêm một matched request không có lượt hiện (Mesh đo được 9 lần nạp / 3 lần hiện = 33%).
 * Ở đây một instance sống suốt process: ad nạp ở màn này được hiện ở màn khác, cả phiên phí nhiều nhất một ad.
 *
 * Đếm lượt giống `countToShowAds` bản 1.0.20 (bị bỏ ở 1.0.22): [countToShow] đếm và chỉ hiện ở lượt tới hạn;
 * [preloadIfDue] chỉ nạp sẵn khi lượt KẾ TIẾP sẽ hiện — không nạp cho những lượt không hiện.
 *
 * ```
 * val inter = AdmSharedInterstitial.get()            // 1 cái cho cả app
 * // vào màn có nút: nạp sẵn chỉ khi lượt bấm kế tiếp sẽ hiện ad
 * LaunchedEffect(Unit) { inter.preloadIfDue("editor_back", startAds = 1, loopAds = 2) }
 * // bấm nút: đếm lượt, tới hạn thì hiện (chưa nạp kịp -> dialog chờ), xong thì đi tiếp
 * inter.countToShow(keyCounterAd = "editor_back", startAds = 1, loopAds = 2) { shown -> goBack() }
 * // không đếm lượt: inter.load() lúc vào màn, inter.show { shown -> goNext() } lúc bấm
 * ```
 *
 * Luôn đi tiếp trong `onDone` — được gọi ĐÚNG MỘT LẦN mỗi lượt, trên main, kể cả khi không hiện được (premium, chưa có ad,
 * full-screen khác đang hiện, đang có lượt khác). Gọi mọi hàm trên main thread.
 *
 * Mặc định một instance cho cả app ([get] không tham số). Placement nào phải dùng unit riêng thì lấy instance theo key
 * riêng (`get("splash")`) và truyền `index` của unit đó.
 */
class AdmSharedInterstitial private constructor(val key: String) {

    /**
     * Instance SDK bên dưới — chỉnh `loadTimeoutMs`, `showDelayMs`, `waitWhenNotReady`, `loadingText`... ở đây.
     * Đừng gán hook của nó (lớp này đã dùng); dùng hook bên dưới và `onDone` của từng lượt.
     */
    val ad: AdmInterstitialAd = AdmInterstitialAd()

    // ---- Hook cho cả app (analytics, nạp native theo sau...) ----
    var onAvailable: (ResponseInfo) -> Unit = {}
    var onLoadFailed: (AdmErrorType) -> Unit = {}

    /** Ad vừa hiện lên màn — chỗ nạp native full-story theo sau (không nạp lúc inter mới nạp). */
    var onShowed: () -> Unit = {}
    var onImpression: () -> Unit = {}
    var onClicked: () -> Unit = {}
    var onPaid: (AdValue) -> Unit = {}

    /** Lượt đang chạy (dialog chờ / ad đang hiện). Chỉ đụng trên main. */
    private var flow: ((Boolean) -> Unit)? = null

    /** Có ad còn hạn để hiện ngay. */
    val isReady: Boolean get() = ad.isReady

    /** Đang có một lượt hiện chạy. */
    val isShowing: Boolean get() = flow != null

    init {
        ad.onAvailable = { onAvailable(it) }
        ad.onLoadFailed = { onLoadFailed(it) }
        ad.onShowed = { onShowed() }
        ad.onImpression = { onImpression() }
        ad.onClicked = { onClicked() }
        ad.onPaid = { onPaid(it) }
        ad.onComplete = { finish(true) }
        // ngoài lượt hiện, onError chỉ là lỗi nạp (đã báo qua onLoadFailed) -> finish bỏ qua vì không có lượt nào
        ad.onError = { finish(false) }
    }

    /** Nạp sẵn nếu chưa có (đã có / đang nạp / vừa hụt / đang hiện -> bỏ qua). [index] -1 xoay vòng. */
    fun load(index: Int = -1) {
        if (flow != null) return
        ad.load(index)
    }

    /**
     * Nạp sẵn CHỈ KHI lượt [countToShow] kế tiếp với [keyCounterAd] sẽ hiện ad. Gọi lúc vào màn có chỗ đặt đó — màn
     * vào nhiều lần mà lượt chưa tới hạn thì không tốn request nào.
     */
    fun preloadIfDue(keyCounterAd: String, startAds: Int, loopAds: Int, index: Int = -1) {
        if (isPremium()) return
        val next = PreferencesManager.getInstance().getCounterAds(keyCounterAd) + 1
        if (AdCounterRule.isDue(next, startAds, loopAds)) load(index)
    }

    /**
     * Hiện ad rồi gọi [onDone] (true = đã hiện rồi đóng, false = không hiện được). Chưa có ad thì SDK nạp và chờ tối đa
     * `ad.loadTimeoutMs` với dialog "Loading…" (`ad.waitWhenNotReady = false` -> báo false ngay). [activity] null ->
     * activity đang foreground.
     */
    fun show(activity: Activity? = null, index: Int = -1, onDone: (shown: Boolean) -> Unit) {
        if (flow != null) { onDone(false); return }
        val act = activity ?: AdCore.currentActivity ?: run { onDone(false); return }
        flow = onDone
        runCatching { ad.show(act, index) }.onFailure { finish(false) }
    }

    /**
     * Đếm lượt [keyCounterAd] (lưu qua các lần mở app, như bản 1.0.20) rồi hiện nếu tới hạn — xem [AdCounterRule].
     * Không tới hạn, premium, hoặc đang có lượt khác -> [onDone] (false) ngay; premium và bấm chồng không tính lượt.
     */
    fun countToShow(
        activity: Activity? = null,
        keyCounterAd: String,
        startAds: Int,
        loopAds: Int,
        index: Int = -1,
        onDone: (shown: Boolean) -> Unit,
    ) {
        if (isPremium() || flow != null) { onDone(false); return }
        val pm = PreferencesManager.getInstance()
        val count = pm.getCounterAds(keyCounterAd) + 1
        pm.saveCounterAds(keyCounterAd, count)
        if (AdCounterRule.isDue(count, startAds, loopAds)) show(activity, index, onDone) else onDone(false)
    }

    /** Bỏ ad đang giữ (vd user vừa mua premium). Lượt đang chạy kết thúc với false. Dùng tiếp được sau đó. */
    fun clear() {
        finish(false)
        ad.destroy()
    }

    private fun finish(shown: Boolean) {
        val done = flow ?: return
        flow = null
        AdCore.onMain { done(shown) }
    }

    private fun isPremium(): Boolean = PreferencesManager.getInstance().let { it.isSUB() || it.isRemoveAds() || it.isLifetime() }

    companion object {
        const val DEFAULT_KEY = "default"

        private val instances = HashMap<String, AdmSharedInterstitial>()

        /** Instance dùng chung theo [key] — mặc định một cái cho cả app. Tạo lần đầu gọi, sống suốt process. */
        @JvmStatic
        fun get(key: String = DEFAULT_KEY): AdmSharedInterstitial =
            synchronized(instances) { instances.getOrPut(key) { AdmSharedInterstitial(key) } }

        /** [clear] mọi instance. */
        @JvmStatic
        fun clearAll() {
            synchronized(instances) { instances.values.toList() }.forEach { it.clear() }
        }
    }
}
