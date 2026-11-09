package com.gsx.googleadcompose.GoogleAds

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdPreloader
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo
import com.gsx.googleadcompose.GlobalVariables
import com.gsx.googleadcompose.data.AdmConfigAdId
import com.gsx.googleadcompose.error.AdmErrorType
import com.gsx.googleadcompose.helper.NetworkHelper
import com.gsx.googleadcompose.utils.PreferencesManager

/**
 * Buffer preload của SDK là toàn cục theo preload ID (= unit id), mà nhiều [AdmOpenAd] có thể cùng dùng 1 unit
 * (splash + resume + màn test). Hub này đếm người dùng: instance này rời màn không được huỷ buffer của
 * instance khác, và mọi instance đều nhận callback. Chỉ đụng trên main.
 */
private object AppOpenPreloadHub {
    private val listeners = HashMap<String, LinkedHashSet<PreloadCallback>>()

    private val sdkCallback = object : PreloadCallback {
        // SDK cấm gọi pollAd/start/destroy trong callback preload -> luôn post sang lượt sau của main.
        override fun onAdPreloaded(preloadId: String, responseInfo: ResponseInfo) = dispatch(preloadId) {
            it.onAdPreloaded(preloadId, responseInfo)
        }

        override fun onAdsExhausted(preloadId: String) = dispatch(preloadId) {
            it.onAdsExhausted(preloadId)
        }

        override fun onAdFailedToPreload(preloadId: String, adError: LoadAdError) = dispatch(preloadId) {
            it.onAdFailedToPreload(preloadId, adError)
        }
    }

    private fun dispatch(preloadId: String, block: (PreloadCallback) -> Unit) = AdCore.postMain {
        listeners[preloadId]?.toList()?.forEach(block)
    }

    /** Trả false nếu SDK không nhận preload. */
    fun acquire(unit: String, config: PreloadConfiguration, listener: PreloadCallback): Boolean {
        val existing = listeners[unit]
        if (existing != null) {
            existing += listener
            return true
        }
        listeners[unit] = linkedSetOf(listener)
        val started = runCatching { AppOpenAdPreloader.start(unit, config, sdkCallback) }
            .onFailure { Log.w("GoogleAds/Open", "preload start lỗi", it) }
            .getOrDefault(false)
        if (!started) listeners.remove(unit)
        return started
    }

    fun release(unit: String, listener: PreloadCallback) {
        val set = listeners[unit] ?: return
        set -= listener
        if (set.isEmpty()) {
            listeners.remove(unit)
            runCatching { AppOpenAdPreloader.destroy(unit) }
        }
    }
}

/**
 * App Open ad (GMA Next-Gen SDK) — **[AppOpenAdPreloader]**: preload buffer, show bằng `pollAd`, SDK tự
 * nạp tiếp, tự retry khi lỗi, tự làm mới ad sắp hết hạn. Log tag `GoogleAds/Open`. KHÔNG dialog loading.
 *
 * `load`/`show` private (nội bộ). 2 cách dùng công khai:
 * - [resumeAd] — tự show khi app trở lại foreground (gọi 1 lần, vd Application qua [AdmOpenResume]).
 * - [showFromSplash] — load + show ngay cho cold-start trên splash.
 *
 * Cả hai nhận [index]: -1 xoay vòng, >=0 chọn id cụ thể trong [AdmConfigAdId.listOpenAdUnitID].
 * Mọi sự kiện vẫn trả qua hook ([onShowed]/[onDismissed]/[onError]/[onPaid]/...).
 */
class AdmOpenAd internal constructor() : AppOpenAdEventCallback {

    var enableLogging: Boolean = true
    /** Số ad giữ sẵn trong buffer (mặc định SDK = 1). */
    var bufferSize: Int = 1

    /** Splash: số ID dự phòng thử thêm khi ID đang dùng không có ad (chỉ khi xoay vòng, `index = -1`). */
    var maxFallbackUnits: Int = DEFAULT_MAX_FALLBACK_UNITS

    /** Resume: khoảng cách tối thiểu giữa 2 lần show. `0` (mặc định) = không giới hạn. */
    var minResumeIntervalMs: Long = 0L

    /** Resume: user rời app do bấm ad thì lúc quay lại không show thêm app-open. */
    var skipResumeAfterAdClick: Boolean = true

    /** Kênh báo lỗi duy nhất (xem [AdmErrorType]). */
    var onError: (AdmErrorType) -> Unit = {}

    // ---- Hook sự kiện cho app ----
    /** Buffer có ad sẵn (preload xong) -> trả [ResponseInfo] (ad object poll lúc show). */
    var onAvailable: (ResponseInfo) -> Unit = {}
    var onExhausted: () -> Unit = {}
    var onShowed: () -> Unit = {}
    var onImpression: () -> Unit = {}
    var onClicked: () -> Unit = {}
    var onPaid: (AdValue) -> Unit = {}
    var onDismissed: () -> Unit = {}
    var onComplete: () -> Unit = {}

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Unit id đang preload. */
    private var preloadUnitId: String? = null
    private var currentIndex: Int = -1
    private var chain: AdUnitChain? = null
    private var chainAttempt = 0
    private var requestStartedAtMs = 0L
    private var preloadFailNotified = false
    private fun tag(): String = "[#$currentIndex ${preloadUnitId ?: "-"}]"

    private var popupOwned = false   // instance này đang giữ mutex isShowPopup

    /** Ad đã poll để show: giữ để destroy sau khi đóng. */
    private var showingAd: AppOpenAd? = null
    private var showWatchdog: Runnable? = null
    private var lastShowedAtMs = 0L

    // ---- Chờ preload để show (load+show) ----
    private var wantShow = false
    private var wantIndex = -1
    private var wantCustomIds: List<String>? = null

    // ---- Splash: callback đóng/lỗi/timeout (1-shot) ----
    private var splashClosed: (() -> Unit)? = null
    private var splashTimeout: Runnable? = null

    // ---- Auto-show theo foreground (resume) ----
    private var resumeObserver: LifecycleEventObserver? = null
    private var skipNextStart = false
    private var leftAppByAdClick = false

    private val preloadListener = object : PreloadCallback {
        override fun onAdPreloaded(preloadId: String, responseInfo: ResponseInfo) {
            if (preloadId != preloadUnitId) return
            preloadFailNotified = false
            log("preloaded ${tag()}")
            report(
                AdmAdEventType.LOAD_SUCCESS,
                attempt = chainAttempt,
                latencyMs = SystemClock.elapsedRealtime() - requestStartedAtMs,
                adSource = responseInfo.adSourceName(),
            )
            onAvailable(responseInfo)
            if (wantShow) {
                wantShow = false
                show(wantIndex, customIds = wantCustomIds)
            }
        }

        override fun onAdsExhausted(preloadId: String) {
            if (preloadId == preloadUnitId) onExhausted()
        }

        override fun onAdFailedToPreload(preloadId: String, adError: LoadAdError) {
            if (preloadId != preloadUnitId) return
            log("preload FAIL ${tag()}: ${adError.code} ${adError.message}")
            report(
                AdmAdEventType.LOAD_FAIL,
                attempt = chainAttempt,
                errorCode = adError.code.name,
                errorMessage = adError.message,
                latencyMs = SystemClock.elapsedRealtime() - requestStartedAtMs,
            )
            // Splash đang chờ -> đổi sang ID dự phòng. Còn lại SDK tự retry với backoff, không cần làm gì.
            val next = chain?.takeIf { wantShow && adError.code.allowsFallback() && it.hasNext }?.next()
            if (next != null) {
                AppOpenPreloadHub.release(preloadId, this)
                preloadUnitId = null
                chainAttempt++
                AdCore.postMain(AD_FALLBACK_DELAY_MS) { if (wantShow) startPreload(next) }
                return
            }
            // SDK retry nhiều lần khi mạng hỏng: chỉ báo app 1 lần mỗi đợt lỗi, trừ khi có người đang chờ ad.
            val someoneWaiting = wantShow || splashClosed != null
            wantShow = false
            if (someoneWaiting || !preloadFailNotified) {
                preloadFailNotified = true
                fail(AdmErrorType.AD_IS_NOT_AVAILABLE)
            }
        }
    }

    /** True khi buffer có ad sẵn để show ngay. */
    val isReady: Boolean
        get() = preloadUnitId?.let { AppOpenAdPreloader.isAdAvailable(it) } == true

    // ========================= Công khai ========================

    /**
     * Bật auto-show app-open khi app trở lại foreground (ProcessLifecycle ON_START). Mobile Ads sẵn là tự
     * preload ngay -> lần quay lại đầu tiên đã có ad. Mỗi resume kiểm tra [GlobalVariables.canShowOpenAd]
     * + [GlobalVariables.isShowPopup].
     *
     * @param index -1 xoay vòng / >=0 id cụ thể.
     * @param skipFirstStart bỏ qua ON_START đầu (cold start / splash đang lo).
     */
    fun resumeAd(index: Int = -1, skipFirstStart: Boolean = true) {
        if (resumeObserver != null) { log("resumeAd đã bật"); return }
        if (bailIfPremium()) { log("resumeAd: premium -> không bật"); return }  // premium: không preload/observer
        skipNextStart = skipFirstStart
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP ->
                    if (skipResumeAfterAdClick && AdCore.adClickedWithin(AD_CLICK_WINDOW_MS)) leftAppByAdClick = true
                Lifecycle.Event.ON_START -> onForeground(index)
                else -> Unit
            }
        }
        resumeObserver = obs
        ProcessLifecycleOwner.get().lifecycle.addObserver(obs)
        log("resumeAd bật")
        AdCore.whenAdsReady { if (resumeObserver === obs) load(index) }
    }

    private fun onForeground(index: Int) {
        // Mua premium runtime -> dọn preloader + gỡ observer (ngừng hẳn).
        if (bailIfPremium()) {
            resumeObserver?.let { ProcessLifecycleOwner.get().lifecycle.removeObserver(it) }
            resumeObserver = null
            return
        }
        if (skipNextStart) { skipNextStart = false; log("ON_START đầu -> bỏ qua"); return }
        if (!GlobalVariables.canShowOpenAd) { log("canShowOpenAd=false -> bỏ"); return }
        if (leftAppByAdClick) {
            leftAppByAdClick = false
            log("quay lại sau khi bấm ad -> không show thêm")
            return
        }
        if (minResumeIntervalMs > 0 && lastShowedAtMs != 0L &&
            SystemClock.elapsedRealtime() - lastShowedAtMs < minResumeIntervalMs
        ) {
            log("chưa đủ ${minResumeIntervalMs}ms từ lần show trước -> bỏ")
            return
        }
        show(index, waitForLoad = false)   // chưa sẵn -> chỉ preload, KHÔNG show trễ
    }

    /**
     * Cold-start splash: load + show. [onClosed] fire 1 lần khi **ad đóng / lỗi / quá [timeoutMs]**
     * (preload chưa kịp) -> splash gọi tiếp vào main. [index] chọn id. Mobile Ads chưa init xong thì tự chờ
     * trong [timeoutMs]; consent không cho request ad -> [onClosed] ngay.
     */
    fun showFromSplash(
        index: Int = -1,
        customIds: List<String>? = null,
        timeoutMs: Long = 8_000L,
        onClosed: () -> Unit,
    ) {
        // User chủ động gọi splash open -> bỏ qua canShowOpenAd (đã đồng ý show). Resume vẫn check.
        splashClosed = onClosed
        val t = Runnable { log("splash open timeout ${timeoutMs}ms"); consumeSplash() }
        splashTimeout = t
        mainHandler.postDelayed(t, timeoutMs)

        premiumBlock()?.let { fail(it); return }
        val ctx = AdCore.appContext
        if (ctx != null && !NetworkHelper.isOnline(ctx)) { fail(AdmErrorType.NETWORK_IS_NOT_AVAILABLE); return }
        AdCore.whenAdsReady(onUnavailable = { fail(AdmErrorType.UMP_IS_NOT_ACTIVE) }) {
            if (splashClosed != null) show(index, customIds = customIds, force = true)
        }
    }

    /** Fire callback splash 1-shot (đóng/lỗi/timeout) + dọn. Đảm bảo chạy trên main (navigate an toàn). */
    private fun consumeSplash() {
        splashTimeout?.let { mainHandler.removeCallbacks(it) }
        splashTimeout = null
        wantShow = false
        val cb = splashClosed ?: return
        splashClosed = null
        mainHandler.post { cb() }   // callback có thể đến từ thread ad/preload -> ép về main
    }

    /** Dừng preload + gỡ observer resume (gọi khi rời màn / tắt). */
    fun destroy() {
        log("destroy ${tag()}")
        cancelShowWatchdog()
        releasePopup()
        wantShow = false
        splashTimeout?.let { mainHandler.removeCallbacks(it) }
        splashTimeout = null
        splashClosed = null
        resumeObserver?.let { ProcessLifecycleOwner.get().lifecycle.removeObserver(it) }
        resumeObserver = null
        preloadUnitId?.let { AppOpenPreloadHub.release(it, preloadListener) }
        preloadUnitId = null
    }

    // ========================= Nội bộ ===========================

    /** Preload buffer cho id theo [index] (-1 xoay vòng). canShowOpenAd=false / đang preload -> bỏ. */
    private fun load(index: Int = -1, customIds: List<String>? = null, force: Boolean = false) {
        if (!force && !GlobalVariables.canShowOpenAd) { log("canShowOpenAd=false -> không load"); return }
        if (bailIfPremium()) return                 // premium -> dọn buffer + return
        preloadError(customIds)?.let { fail(it); return }    // còn lại: UMP/network/empty
        if (preloadUnitId != null) return                          // đang preload -> bỏ qua

        // customIds != null -> xoay vòng list đó thay cho AdmConfigAdId.listOpenAdUnitID.
        val u = customIds?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() } ?: units()
        val newChain = AdUnitChain.build(u, index, maxFallbackUnits) { CURSOR.next(u.size) }
        val first = newChain?.next() ?: run { fail(AdmErrorType.AD_ID_IS_NOT_EXIST); return }
        chain = newChain
        chainAttempt = 0
        startPreload(first)
    }

    private fun startPreload(attempt: AdUnitChain.Attempt) {
        preloadUnitId = attempt.unitId
        currentIndex = attempt.index
        requestStartedAtMs = SystemClock.elapsedRealtime()
        log("load (preload) ${tag()}")
        report(AdmAdEventType.LOAD_REQUEST, attempt = chainAttempt)

        val config = PreloadConfiguration(AdRequest.Builder(attempt.unitId).build(), bufferSize)
        if (!AppOpenPreloadHub.acquire(attempt.unitId, config, preloadListener)) {
            preloadUnitId = null
            wantShow = false
            fail(AdmErrorType.AD_IS_NOT_AVAILABLE)
        }
    }

    /**
     * Sẵn -> show NGAY (SDK đã tự làm mới ad sắp hết hạn trong buffer). Chưa sẵn: [waitForLoad] true ->
     * load([index]) rồi show khi xong (splash); false -> chỉ preload, KHÔNG show (resume: bỏ lần này,
     * foreground sau mới show). Premium / popup khác đang hiện / app không ở foreground -> bỏ.
     */
    private fun show(index: Int = -1, waitForLoad: Boolean = true, customIds: List<String>? = null, force: Boolean = false) {
        val act = AdCore.currentActivity ?: run { fail(AdmErrorType.ACTIVITY_IS_NOT_AVAILABLE); return }
        if (!AdCore.canPresentFrom(act)) { fail(AdmErrorType.ACTIVITY_IS_NOT_AVAILABLE); return }
        premiumBlock()?.let { fail(it); return }
        if (GlobalVariables.isShowPopup) { log("full-screen/dialog khác đang hiện, bỏ qua"); return }

        val id = preloadUnitId
        if (id != null && AppOpenAdPreloader.isAdAvailable(id)) {
            markPopup(); pollAndShow(act, id); return
        }
        if (preloadUnitId == null) load(index, customIds, force)
        if (waitForLoad) {
            if (preloadUnitId == null) return   // load lỗi ngay (đã báo onError)
            wantShow = true
            wantIndex = index
            wantCustomIds = customIds
            log("chưa sẵn -> load rồi show ${tag()}")
        } else {
            log("chưa sẵn -> chỉ preload, bỏ show lần này ${tag()}")
        }
    }

    private fun pollAndShow(activity: Activity, id: String) {
        val ad = runCatching { AppOpenAdPreloader.pollAd(id) }.getOrNull()
        if (ad == null) { log("pollAd null ${tag()}"); fail(AdmErrorType.AD_IS_NOT_AVAILABLE); return }
        log("show ${tag()}")
        report(AdmAdEventType.SHOW_REQUEST)
        ad.adEventCallback = this
        showingAd = ad
        armShowWatchdog()
        runCatching { ad.show(activity) }.exceptionOrNull()?.let { error ->
            Log.w(TAG, "show exception ${tag()}", error)
            onShowFailed(error.javaClass.simpleName, error.message)
        }
    }

    /** Lưới an toàn: gọi show mà SDK không báo gì -> nhả mutex, báo lỗi để splash/app đi tiếp. */
    private fun armShowWatchdog() {
        cancelShowWatchdog()
        val watchdog = Runnable {
            showWatchdog = null
            log("show không có phản hồi sau ${SHOW_WATCHDOG_MS}ms ${tag()}")
            report(AdmAdEventType.SHOW_FAIL, errorCode = "SHOW_WATCHDOG")
            fail(AdmErrorType.AD_IS_NOT_AVAILABLE)
        }
        showWatchdog = watchdog
        mainHandler.postDelayed(watchdog, SHOW_WATCHDOG_MS)
    }

    private fun cancelShowWatchdog() {
        showWatchdog?.let { mainHandler.removeCallbacks(it) }
        showWatchdog = null
    }

    private fun onShowFailed(code: String, message: String?) {
        cancelShowWatchdog()
        report(AdmAdEventType.SHOW_FAIL, errorCode = code, errorMessage = message)
        showingAd.safeDestroy()
        showingAd = null
        fail(AdmErrorType.AD_IS_NOT_AVAILABLE)
    }

    private fun AppOpenAd?.safeDestroy() {
        this ?: return
        runCatching { destroy() }.exceptionOrNull()?.let { Log.w(TAG, "destroy ad lỗi", it) }
    }

    // ================ AppOpenAdEventCallback ====================

    // Event callback đến từ thread nền -> về main.
    override fun onAdShowedFullScreenContent() = AdCore.onMain {
        cancelShowWatchdog()
        markPopup()   // giữ mutex suốt lúc ad hiện, kể cả khi watchdog đã nhả vì máy quá chậm
        lastShowedAtMs = SystemClock.elapsedRealtime()
        log("showed ${tag()}")
        report(AdmAdEventType.SHOW_SUCCESS)
        splashTimeout?.let { mainHandler.removeCallbacks(it) }   // đã show -> không để timeout skip
        splashTimeout = null
        onShowed()
    }

    override fun onAdImpression() = AdCore.onMain {
        report(AdmAdEventType.IMPRESSION)
        onImpression()
    }

    override fun onAdClicked() = AdCore.onMain {
        AdCore.noteAdClicked()
        report(AdmAdEventType.CLICK)
        onClicked()
    }

    override fun onAdPaid(value: AdValue) = AdCore.onMain {
        report(AdmAdEventType.PAID, valueMicros = value.valueMicros, currencyCode = value.currencyCode)
        onPaid(value)
    }

    override fun onAdDismissedFullScreenContent() = AdCore.onMain {
        cancelShowWatchdog()
        log("dismissed ${tag()}")
        report(AdmAdEventType.DISMISS)
        releasePopup()
        onDismissed()
        onComplete()
        consumeSplash()       // ad đóng -> splash vào main
        val shown = showingAd
        showingAd = null
        AdCore.postMain { shown.safeDestroy() }
    }

    override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) = AdCore.onMain {
        log("show FAIL ${tag()}: $fullScreenContentError")
        onShowFailed(fullScreenContentError.code.name, fullScreenContentError.message)
    }

    // ======================== Helpers ===========================

    private fun premiumBlock(): AdmErrorType? {
        val pm = PreferencesManager.getInstance()
        return when {
            pm.isRemoveAds() || pm.isLifetime() -> AdmErrorType.CLIENT_HAVE_BEEN_REMOVED_AD
            pm.isSUB() -> AdmErrorType.CLIENT_HAVE_SUB
            else -> null
        }
    }

    /**
     * Premium -> DỌN preloader buffer đang chạy + báo lỗi, trả true (caller phải return).
     * Khác guard thường: preloader tự giữ buffer, chỉ `return` không đủ -> phải destroy.
     */
    private fun bailIfPremium(): Boolean {
        val err = premiumBlock() ?: return false
        preloadUnitId?.let { AppOpenPreloadHub.release(it, preloadListener) }
        preloadUnitId = null
        fail(err)
        return true
    }

    private fun preloadError(customIds: List<String>?): AdmErrorType? {
        premiumBlock()?.let { return it }
        if (!AdCore.isMobileAdsReady) return AdmErrorType.UMP_IS_NOT_ACTIVE
        val ctx = AdCore.appContext
        if (ctx != null && !NetworkHelper.isOnline(ctx)) return AdmErrorType.NETWORK_IS_NOT_AVAILABLE
        // customIds có ID dùng được thì list mặc định rỗng cũng không sao.
        val hasUnits = customIds?.any { it.isNotBlank() } == true || units().isNotEmpty()
        if (!hasUnits) return AdmErrorType.LIST_AD_ID_IS_EMPTY
        return null
    }

    private fun units(): List<String> = AdmConfigAdId.listOpenAdUnitID.filter { it.isNotBlank() }

    private fun fail(type: AdmErrorType) {
        log("ERROR: $type")
        report(AdmAdEventType.ERROR, errorCode = type.name)
        cancelShowWatchdog()
        releasePopup()
        onError(type)
        consumeSplash()   // lỗi (load/show/no-activity/premium) -> splash vào main
    }

    private fun report(
        type: AdmAdEventType,
        attempt: Int? = null,
        errorCode: String? = null,
        errorMessage: String? = null,
        latencyMs: Long? = null,
        adSource: String? = null,
        valueMicros: Long? = null,
        currencyCode: String? = null,
    ) {
        AdmAdDiagnostics.report(
            AdmAdEvent(
                format = AdmAdFormat.APP_OPEN,
                type = type,
                adUnitId = preloadUnitId,
                errorCode = errorCode,
                errorMessage = errorMessage,
                latencyMs = latencyMs,
                adSource = adSource,
                attempt = attempt,
                valueMicros = valueMicros,
                currencyCode = currencyCode,
            )
        )
    }

    private fun markPopup() {
        GlobalVariables.isShowPopup = true
        popupOwned = true
    }

    private fun releasePopup() {
        if (popupOwned) {
            GlobalVariables.isShowPopup = false
            popupOwned = false
        }
    }

    private fun log(msg: String) {
        if (enableLogging) Log.d(TAG, msg)
    }

    private companion object {
        const val TAG = "GoogleAds/Open"
        const val SHOW_WATCHDOG_MS = 8_000L

        /** Click trước khi app xuống nền trong khoảng này -> coi là rời app do bấm ad. */
        const val AD_CLICK_WINDOW_MS = 10_000L
        val CURSOR = AdUnitCursor()
    }
}
