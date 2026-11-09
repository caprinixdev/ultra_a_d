package com.gsx.googleadcompose.GoogleAds

import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.common.Ad
import com.google.android.libraries.ads.mobile.sdk.common.AdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo
import com.gsx.googleadcompose.GlobalVariables
import com.gsx.googleadcompose.error.AdmErrorType
import com.gsx.googleadcompose.helper.DialogHelper
import com.gsx.googleadcompose.helper.NetworkHelper
import com.gsx.googleadcompose.utils.PreferencesManager
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Logic chung của ad full-screen **one-shot** (Interstitial / Rewarded / Rewarded Interstitial): load đúng
 * 1 ad, giữ sẵn, show 1 lần rồi bỏ. KHÔNG dùng preloader (preloader tự refill cái kế -> màn 1-shot không
 * bao giờ show cái refill -> phí matched request -> tụt show rate).
 *
 * [load] = load sẵn 1 ad (gọi sớm = preload). ID đang dùng không có ad -> tự thử ID kế trong list
 * ([maxFallbackUnits]). [show]:
 * - Ad đã sẵn -> hiện dialog [showDelayMs] rồi show.
 * - Chưa kịp load -> dialog loading, chờ load xong tự show (quá [loadTimeoutMs] / bấm Back -> hủy + [onError]).
 * - Vừa load hụt trong [failCooldownMs] hoặc [waitWhenNotReady] = false -> báo [onError] ngay, không bắt user chờ.
 *
 * Show xong -> ad bị bỏ, KHÔNG tự load cái kế. Cần show lại -> gọi [load]/[show] lần nữa.
 * Cần init MobileAds (UMP) trước.
 */
abstract class AdmFullScreenAd<T : Ad> internal constructor(
    private val logTag: String,
    private val format: AdmAdFormat,
    private val cursor: AdUnitCursor,
) : AdEventCallback {

    var enableLogging: Boolean = true
    var loadingText: String = "Loading..."

    /** Show khi ad chưa sẵn -> chờ tối đa ngần này rồi hủy. */
    var loadTimeoutMs: Long = 8_000L

    /** Ad đã sẵn vẫn hiện dialog ngần này trước khi show (cho mượt). */
    var showDelayMs: Long = 2_000L

    /** Ad chưa sẵn lúc show: true -> dialog chờ load; false -> báo [onError] ngay, load tiếp ở nền cho lần sau. */
    var waitWhenNotReady: Boolean = true

    /** Số ID dự phòng thử thêm khi ID đang dùng không có ad (chỉ khi xoay vòng, `index = -1`). */
    var maxFallbackUnits: Int = DEFAULT_MAX_FALLBACK_UNITS

    /** Sau 1 lượt load hụt, trong ngần này: không request lại và show báo lỗi ngay thay vì bắt user chờ. */
    var failCooldownMs: Long = 15_000L

    /** Ad giữ quá lâu thì bỏ, load cái mới (Google: ad hết hạn sau khoảng 1 giờ). */
    var adTtlMs: Long = 55L * 60 * 1000

    /** Kênh báo lỗi duy nhất (xem [AdmErrorType]). */
    var onError: (AdmErrorType) -> Unit = {}

    // ---- Hook sự kiện cho app ----
    /** Ad load xong (sẵn để show) -> trả [ResponseInfo]. */
    var onAvailable: (ResponseInfo) -> Unit = {}
    var onShowed: () -> Unit = {}
    var onImpression: () -> Unit = {}
    var onClicked: () -> Unit = {}
    var onPaid: (AdValue) -> Unit = {}   // doanh thu (paid event)
    var onDismissed: () -> Unit = {}
    var onComplete: () -> Unit = {}

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Ad đã load xong, giữ sẵn để show (null = chưa có). */
    private var loadedAd: T? = null
    private var loadedAtMs = 0L
    private var lastLoadFailedAtMs = 0L
    private var loading: Boolean = false

    /** Tăng mỗi lần destroy -> callback load đến sau khi đã rời màn không đụng state cũ + tự huỷ ad. */
    private var generation = 0

    /** Ad đã lấy ra để show: giữ để destroy sau khi đóng (không destroy thì leak WebView theo số lần show). */
    private var showingAd: T? = null
    private var showWatchdog: Runnable? = null

    private var currentUnit: String? = null
    private var currentIndex: Int = -1
    private fun tag(): String = "[#$currentIndex ${currentUnit ?: "-"}]"

    // ---- Trạng thái "show đang chờ ad load" ----
    private var pendingActivity: Activity? = null
    private var pendingDialog: AlertDialog? = null
    private var pendingDone: AtomicBoolean? = null
    private var pendingTimeout: Runnable? = null
    private var popupOwned = false   // instance này đang giữ mutex isShowPopup

    /** True khi đã có ad còn hạn để show ngay. */
    val isReady: Boolean
        get() = loadedAd != null && !isExpired()

    // ===================== Mỗi format tự cài =====================

    /** List ID mặc định trong [com.gsx.googleadcompose.data.AdmConfigAdId]. */
    internal abstract fun configuredUnits(): List<String>
    internal abstract fun requestAd(request: AdRequest, callback: AdLoadCallback<T>)
    internal abstract fun bindEvents(ad: T)
    internal abstract fun presentAd(ad: T, activity: Activity)

    // ============================ API ============================

    /**
     * Load sẵn 1 ad (gọi sớm = preload). [index] = -1 -> xoay vòng unit id kế tiếp, lỗi thì thử ID dự phòng;
     * >=0 -> chọn cụ thể id thứ [index]. [customIds] != null -> dùng list đó thay cho list mặc định.
     * Đã có ad / đang load / vừa load hụt -> bỏ qua.
     */
    fun load(index: Int = -1, customIds: List<String>? = null) {
        if (bailIfPremium()) return
        loadError(customIds)?.let { fail(it); return }
        dropAdIfExpired()
        if (loadedAd != null || loading) return
        if (isCoolingDown()) { log("vừa load hụt, chờ hết cooldown"); return }

        val units = customIds?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() } ?: units()
        val chain = AdUnitChain.build(units, index, maxFallbackUnits) { cursor.next(units.size) }
            ?: run { fail(AdmErrorType.AD_ID_IS_NOT_EXIST); return }
        loading = true
        requestNext(chain, attempt = 0, gen = generation)
    }

    /**
     * Show dùng activity foreground tự lấy ([AdCore.currentActivity]). Không có -> [onError].
     * [index]: chỉ dùng khi CHƯA load -> load id đó rồi show (xem [show]).
     */
    fun show(index: Int = -1, customIds: List<String>? = null) {
        val act = AdCore.currentActivity ?: run { fail(AdmErrorType.ACTIVITY_IS_NOT_AVAILABLE); return }
        show(act, index, customIds)
    }

    /**
     * Show ad. [index] = -1 xoay vòng, >=0 chọn id cụ thể (CHỈ khi chưa có ad; đã load id khác thì show id
     * đang có). Đang có 1 show chờ / ad đang hiện -> bỏ qua (double-tap). Lỗi -> [onError].
     */
    fun show(activity: Activity, index: Int = -1, customIds: List<String>? = null) {
        if (activity.isFinishing || activity.isDestroyed) { fail(AdmErrorType.ACTIVITY_IS_NOT_AVAILABLE); return }
        premiumBlock()?.let { fail(it); return }
        if (pendingActivity != null || popupOwned) { log("đang show, bỏ qua (double-tap)"); return }
        if (GlobalVariables.isShowPopup) {
            // Báo lỗi thay vì im lặng: app chuyển màn trong onDismissed/onError sẽ không bị đứng.
            log("full-screen/dialog khác đang hiện -> báo lỗi")
            report(AdmAdEventType.ERROR, errorCode = AdmErrorType.AD_IS_EXISTED.name)
            onError(AdmErrorType.AD_IS_EXISTED)
            return
        }
        report(AdmAdEventType.SHOW_REQUEST)
        dropAdIfExpired()
        if (loadedAd != null) { showWithDelay(activity); return }

        loadError(customIds)?.let { fail(it); return }
        if (isCoolingDown()) { log("vừa load hụt -> không bắt user chờ"); fail(AdmErrorType.AD_IS_NOT_AVAILABLE); return }
        if (!loading) load(index, customIds)            // chưa load -> load+show
        if (!loading && loadedAd == null) return        // load lỗi ngay (đã báo onError)
        if (!waitWhenNotReady) { fail(AdmErrorType.AD_IS_NOT_AVAILABLE); return }
        waitForAd(activity)
    }

    /** Bỏ ad đang giữ + hủy show đang chờ (gọi khi rời màn). */
    fun destroy() {
        log("destroy ${tag()}")
        generation++
        clearPending()
        cancelShowWatchdog()
        releasePopup()
        loadedAd?.safeDestroy()
        loadedAd = null
        currentUnit = null
        loading = false
    }

    // ========================== Load =============================

    private fun requestNext(chain: AdUnitChain, attempt: Int, gen: Int) {
        val next = chain.next() ?: run { onChainFailed(); return }
        currentUnit = next.unitId
        currentIndex = next.index
        val startedAt = SystemClock.elapsedRealtime()
        log("load ${tag()}${if (attempt > 0) " [dự phòng $attempt]" else ""}")
        report(AdmAdEventType.LOAD_REQUEST, unit = next.unitId, attempt = attempt)

        val result = runCatching {
            requestAd(AdRequest.Builder(next.unitId).build(), object : AdLoadCallback<T> {
                // Callback đến từ thread nền (GMA Next-Gen) -> về main trước khi đụng state/dialog/hook.
                override fun onAdLoaded(ad: T) = AdCore.onMain {
                    if (gen != generation) { ad.safeDestroy(); return@onMain }
                    onLoaded(ad, next.unitId, attempt, startedAt)
                }

                override fun onAdFailedToLoad(adError: LoadAdError) = AdCore.onMain {
                    if (gen != generation) return@onMain
                    onLoadFailed(adError, chain, next.unitId, attempt, startedAt, gen)
                }
            })
        }
        result.exceptionOrNull()?.let { error ->
            Log.w(logTag, "load exception ${tag()}", error)
            onChainFailed()
        }
    }

    private fun onLoaded(ad: T, unit: String, attempt: Int, startedAt: Long) {
        loading = false
        lastLoadFailedAtMs = 0L
        bindEvents(ad)
        loadedAd = ad
        loadedAtMs = SystemClock.elapsedRealtime()
        log("loaded ${tag()}")
        report(
            AdmAdEventType.LOAD_SUCCESS,
            unit = unit,
            attempt = attempt,
            latencyMs = loadedAtMs - startedAt,
            adSource = ad.getResponseInfo().adSourceName(),
        )
        onAvailable(ad.getResponseInfo())
        showPendingIfAny()
    }

    private fun onLoadFailed(
        error: LoadAdError,
        chain: AdUnitChain,
        unit: String,
        attempt: Int,
        startedAt: Long,
        gen: Int,
    ) {
        log("load FAIL ${tag()}: ${error.code} ${error.message}")
        report(
            AdmAdEventType.LOAD_FAIL,
            unit = unit,
            attempt = attempt,
            errorCode = error.code.name,
            errorMessage = error.message,
            latencyMs = SystemClock.elapsedRealtime() - startedAt,
        )
        if (error.code.allowsFallback() && chain.hasNext) {
            AdCore.postMain(AD_FALLBACK_DELAY_MS) {
                if (gen == generation) requestNext(chain, attempt + 1, gen)
            }
            return
        }
        onChainFailed()
    }

    private fun onChainFailed() {
        loading = false
        currentUnit = null
        lastLoadFailedAtMs = SystemClock.elapsedRealtime()
        failPending(AdmErrorType.AD_IS_NOT_AVAILABLE)
    }

    private fun isCoolingDown(): Boolean =
        lastLoadFailedAtMs != 0L && SystemClock.elapsedRealtime() - lastLoadFailedAtMs < failCooldownMs

    private fun isExpired(): Boolean = SystemClock.elapsedRealtime() - loadedAtMs >= adTtlMs

    private fun dropAdIfExpired() {
        if (loadedAd != null && isExpired()) {
            log("ad giữ quá ${adTtlMs}ms -> bỏ, load cái mới")
            loadedAd?.safeDestroy()
            loadedAd = null
        }
    }

    // ===================== Show chờ ad load ======================

    /** Ad đã sẵn: hiện dialog [showDelayMs] rồi show. */
    private fun showWithDelay(activity: Activity) {
        log("ad sẵn, dialog ${showDelayMs}ms rồi show ${tag()}")
        markPopup()
        pendingActivity = activity
        pendingDialog = DialogHelper.showLoading(activity, loadingText)
        val done = AtomicBoolean(false)
        pendingDone = done
        val run = Runnable {
            if (done.compareAndSet(false, true)) {
                cleanupPending()
                presentLoadedAd(activity)
            }
        }
        pendingTimeout = run
        mainHandler.postDelayed(run, showDelayMs)
    }

    private fun waitForAd(activity: Activity) {
        log("chờ ad... ${tag()}")
        markPopup()
        pendingActivity = activity
        val done = AtomicBoolean(false)
        pendingDone = done
        pendingDialog = DialogHelper.showLoading(activity, loadingText) {
            // Bấm Back lúc đang chờ -> hủy chờ, ad load xong vẫn giữ cho lần sau.
            log("user hủy chờ ad")
            failPending(AdmErrorType.AD_IS_NOT_AVAILABLE)
        }
        val timeout = Runnable {
            log("timeout chờ ad ${tag()}")
            failPending(AdmErrorType.AD_IS_NOT_AVAILABLE)
        }
        pendingTimeout = timeout
        mainHandler.postDelayed(timeout, loadTimeoutMs)
    }

    private fun showPendingIfAny() {
        val act = pendingActivity ?: return
        if (loadedAd == null) return
        val done = pendingDone ?: return
        if (!done.compareAndSet(false, true)) return
        cleanupPending()
        presentLoadedAd(act)
    }

    private fun failPending(type: AdmErrorType) {
        val done = pendingDone ?: return
        if (!done.compareAndSet(false, true)) return
        cleanupPending()
        fail(type)
    }

    private fun cleanupPending() {
        pendingTimeout?.let { mainHandler.removeCallbacks(it) }
        safeDismiss(pendingDialog)
        pendingActivity = null
        pendingDialog = null
        pendingTimeout = null
        pendingDone = null
    }

    private fun clearPending() {
        pendingDone?.set(true)
        cleanupPending()
    }

    // ========================== Show ============================

    private fun presentLoadedAd(activity: Activity) {
        val ad = loadedAd ?: run { log("ad null ${tag()}"); fail(AdmErrorType.AD_IS_NOT_AVAILABLE); return }
        // App xuống nền trong lúc dialog/chờ load: giữ ad cho lần sau. Show lúc này SDK báo APP_NOT_FOREGROUND
        // và ad đã lấy ra bị mất.
        if (!AdCore.canPresentFrom(activity)) {
            log("app không ở foreground -> giữ ad, hủy show")
            fail(AdmErrorType.ACTIVITY_IS_NOT_AVAILABLE)
            return
        }
        log("show ${tag()}")
        loadedAd = null   // consume -> KHÔNG refill (one-shot)
        showingAd = ad
        armShowWatchdog()
        runCatching { presentAd(ad, activity) }.exceptionOrNull()?.let { error ->
            Log.w(logTag, "show exception ${tag()}", error)
            onShowFailed(error.javaClass.simpleName, error.message)
        }
    }

    /** Lưới an toàn: gọi show mà SDK không báo gì -> nhả mutex, báo lỗi để app đi tiếp. */
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
        showingAd?.safeDestroy()
        showingAd = null
        fail(AdmErrorType.AD_IS_NOT_AVAILABLE)
    }

    // ===================== Event callback của SDK =================

    // Event callback cũng đến từ thread nền -> về main (hook app hay navigate/đổi Compose state).
    override fun onAdShowedFullScreenContent() = AdCore.onMain {
        cancelShowWatchdog()
        markPopup()   // giữ mutex suốt lúc ad hiện, kể cả khi watchdog đã nhả vì máy quá chậm
        log("showed ${tag()}")
        report(AdmAdEventType.SHOW_SUCCESS)
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
        val shown = showingAd
        showingAd = null
        AdCore.postMain { shown?.safeDestroy() }   // lượt sau: để callback thưởng (nếu đến sát) chạy xong
    }

    override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) = AdCore.onMain {
        log("show FAIL ${tag()}: $fullScreenContentError")
        onShowFailed(fullScreenContentError.code.name, fullScreenContentError.message)
    }

    // ======================== Helpers ===========================

    internal fun log(msg: String) {
        if (enableLogging) Log.d(logTag, msg)
    }

    internal fun logCurrent(msg: String) = log("$msg ${tag()}")

    private fun safeDismiss(dialog: AlertDialog?) {
        dialog ?: return
        runCatching { if (dialog.isShowing) dialog.dismiss() }
    }

    private fun T.safeDestroy() {
        runCatching { destroy() }.exceptionOrNull()?.let { Log.w(logTag, "destroy ad lỗi", it) }
    }

    private fun premiumBlock(): AdmErrorType? {
        val pm = PreferencesManager.getInstance()
        return when {
            pm.isRemoveAds() || pm.isLifetime() -> AdmErrorType.CLIENT_HAVE_BEEN_REMOVED_AD
            pm.isSUB() -> AdmErrorType.CLIENT_HAVE_SUB
            else -> null
        }
    }

    /** Premium -> bỏ ad đang giữ + báo [onError], trả true (caller return). */
    private fun bailIfPremium(): Boolean {
        val err = premiumBlock() ?: return false
        loadedAd?.safeDestroy()
        loadedAd = null
        currentUnit = null
        fail(err)
        return true
    }

    private fun loadError(customIds: List<String>?): AdmErrorType? {
        premiumBlock()?.let { return it }
        if (!AdCore.isMobileAdsReady) return AdmErrorType.UMP_IS_NOT_ACTIVE
        val ctx = AdCore.appContext
        if (ctx != null && !NetworkHelper.isOnline(ctx)) return AdmErrorType.NETWORK_IS_NOT_AVAILABLE
        // customIds có ID dùng được thì list mặc định rỗng cũng không sao.
        val hasUnits = customIds?.any { it.isNotBlank() } == true || units().isNotEmpty()
        if (!hasUnits) return AdmErrorType.LIST_AD_ID_IS_EMPTY
        return null
    }

    private fun units(): List<String> = configuredUnits().filter { it.isNotBlank() }

    private fun fail(type: AdmErrorType) {
        log("ERROR: $type")
        report(AdmAdEventType.ERROR, errorCode = type.name)
        cancelShowWatchdog()
        releasePopup()
        onError(type)
    }

    private fun report(
        type: AdmAdEventType,
        unit: String? = currentUnit,
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
                format = format,
                type = type,
                adUnitId = unit,
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

    /** Chiếm mutex full-screen (chặn ad/dialog khác đè). */
    private fun markPopup() {
        GlobalVariables.isShowPopup = true
        popupOwned = true
    }

    /** Nhả mutex nếu instance này đang giữ (không đụng cờ của instance khác). */
    private fun releasePopup() {
        if (popupOwned) {
            GlobalVariables.isShowPopup = false
            popupOwned = false
        }
    }

    private companion object {
        const val SHOW_WATCHDOG_MS = 8_000L
    }
}
