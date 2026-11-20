package com.gsx.googleadcompose.GoogleAds

import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo
import com.gsx.googleadcompose.GlobalVariables
import com.gsx.googleadcompose.data.AdmConfigAdId
import com.gsx.googleadcompose.error.AdmErrorType
import com.gsx.googleadcompose.helper.DialogHelper
import com.gsx.googleadcompose.helper.NetworkHelper
import com.gsx.googleadcompose.utils.PreferencesManager

/**
 * App Open ad (GMA Next-Gen SDK) — nạp TỪNG AD MỘT bằng `AppOpenAd.load`, giữ tối đa 1 ad, show 1 lần rồi bỏ.
 * Log tag `GoogleAds/Open`. KHÔNG dialog loading.
 *
 * Bản 1.0.22–1.0.25 dùng `AppOpenAdPreloader`: preloader tự nạp lại ngay sau mỗi lần show và tự làm mới ad sắp
 * hết hạn, nên app nạp những ad không bao giờ hiện — AdMob đếm là matched request không có impression:
 * - splash show xong, preloader nạp tiếp 1 ad, rồi màn splash huỷ instance -> ad đó bị vứt, MỖI lần mở app;
 * - resume vừa bật là nạp ngay, show xong lại nạp bù -> người dùng không rời app thêm lần nào thì vứt.
 * Show rate App Open tụt còn khoảng 20–27%. Giờ:
 * - [resumeAd]: nạp lúc app XUỐNG NỀN (ON_STOP), người dùng quay lại (ON_START) là có ad để show; chưa nạp kịp thì
 *   bỏ lần này, không show trễ (ad về sau vẫn giữ cho lần quay lại kế). Show xong KHÔNG nạp bù.
 * - [showFromSplash]: nạp + show cho cold start; show xong KHÔNG nạp bù.
 * - Từ 1.0.27, ad đã nạp mà instance không dùng tới (splash hết giờ chờ rồi ad mới về, màn splash huỷ instance lúc ad
 *   đang về/đang giữ) KHÔNG bị huỷ nữa: để dành ([keepUnusedAd]) cho instance khác — thường là resume — show ở lần
 *   quay lại app kế tiếp thay vì xin ad mới. Trước đây mỗi lần như vậy là một matched request không có impression.
 * - [loadOnReturn] (từ 1.0.27, như SDK 1.0.21 bản XML): resume KHÔNG nạp sẵn lúc xuống nền mà nạp lúc người dùng quay
 *   lại, dialog chờ tối đa [returnWaitMs] rồi show. Không còn ad nạp cho lần quay lại cuối phiên (người dùng thoát
 *   hẳn) — phần phí lớn nhất của cách nạp sẵn — nên show rate App Open cao hơn nhiều; đổi lại người dùng chờ ad.
 *
 * `load`/`show` private (nội bộ). Cả hai cách dùng nhận [index]: -1 xoay vòng (lỗi thì thử ID dự phòng),
 * >=0 chọn id cụ thể trong [AdmConfigAdId.listOpenAdUnitID]. Mọi sự kiện trả qua hook ([onShowed]/[onDismissed]/
 * [onError]/[onPaid]/...).
 */
class AdmOpenAd internal constructor() : AppOpenAdEventCallback {

    var enableLogging: Boolean = true

    /** Không còn tác dụng: App Open giữ tối đa 1 ad. Giữ lại để code cũ vẫn biên dịch. */
    @Deprecated("App Open nạp từng ad một (không còn preloader); giá trị này bị bỏ qua.")
    var bufferSize: Int = 1

    /** Số ID dự phòng thử thêm khi ID đang dùng không có ad (chỉ khi xoay vòng, `index = -1`). */
    var maxFallbackUnits: Int = DEFAULT_MAX_FALLBACK_UNITS

    /** Resume: khoảng cách tối thiểu giữa 2 lần show. `0` (mặc định) = không giới hạn. */
    var minResumeIntervalMs: Long = 0L

    /** Resume: user rời app do bấm ad thì lúc quay lại không show thêm app-open. */
    var skipResumeAfterAdClick: Boolean = true

    /** Ad giữ quá ngần này thì bỏ (Google: App Open hết hạn sau 4 giờ). */
    var adTtlMs: Long = 4L * 60 * 60 * 1000 - 5L * 60 * 1000

    /**
     * Ad instance này nạp xong mà không ai dùng (splash đã đi tiếp, instance bị [destroy]) thì để dành cho instance
     * App Open khác show — thường là resume ở lần quay lại app kế tiếp — thay vì huỷ. Instance nào cũng lấy ad để
     * dành trước khi xin ad mới. Ad để dành có thể của ad unit khác (vd ad unit splash show ở chỗ resume); app cần
     * tách bạch ad unit theo chỗ đặt thì đặt `false`.
     */
    var keepUnusedAd: Boolean = true

    /**
     * Resume kiểu SDK 1.0.21 (bản XML — app gọi `loadAds(isShowAd = true)` mỗi lần quay lại): `true` -> KHÔNG nạp sẵn
     * lúc app xuống nền; người dùng quay lại mới nạp, nạp xong show luôn (chờ tối đa [returnWaitMs]). Ad nào cũng nạp
     * đúng lúc sắp show nên gần như không ad nào bị phí -> show rate App Open cao. Đổi lại mỗi lần quay lại mà chưa có
     * ad để dành, người dùng chờ ad 1–3 giây. `false` (mặc định) = nạp sẵn lúc xuống nền, quay lại show ngay nhưng ad
     * nạp cho lần quay lại cuối phiên (người dùng không quay lại nữa) bị phí. Đọc lúc mỗi lần quay lại, đổi lúc nào
     * cũng được (vd theo Remote Config).
     */
    var loadOnReturn: Boolean = false

    /** [loadOnReturn]: chờ ad tối đa ngần này; quá thì bỏ lượt này, ad về sau để dành cho lần quay lại kế. */
    var returnWaitMs: Long = 5_000L

    /**
     * [loadOnReturn]: hiện dialog [loadingText] trong lúc chờ (khuyên bật — ad không bất ngờ bật lên khi người dùng
     * đang bấm; bấm Back là thôi chờ). `false` = như 1.0.21: không dialog, ad tự bật lên khi nạp xong.
     */
    var returnShowsLoading: Boolean = true

    /** Chữ trên dialog chờ ad ([returnShowsLoading]). */
    var loadingText: String = "Loading..."

    /** Kênh báo lỗi duy nhất (xem [AdmErrorType]). */
    var onError: (AdmErrorType) -> Unit = {}

    // ---- Hook sự kiện cho app ----
    /** Ad nạp xong, sẵn để show -> trả [ResponseInfo]. */
    var onAvailable: (ResponseInfo) -> Unit = {}

    /** Không còn được gọi (chỉ preloader cũ mới báo hết buffer). Giữ lại để code cũ vẫn biên dịch. */
    @Deprecated("Không còn preloader nên không còn sự kiện hết buffer.")
    var onExhausted: () -> Unit = {}
    var onShowed: () -> Unit = {}
    var onImpression: () -> Unit = {}
    var onClicked: () -> Unit = {}
    var onPaid: (AdValue) -> Unit = {}
    var onDismissed: () -> Unit = {}
    var onComplete: () -> Unit = {}

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Ad đã nạp, chờ show (null = chưa có). */
    private var loadedAd: AppOpenAd? = null
    private var loadedAtMs = 0L
    private var loading = false

    /** Tăng mỗi lần destroy -> callback nạp đến muộn không đụng state cũ + tự huỷ ad. */
    private var generation = 0

    private var currentUnit: String? = null
    private var currentIndex: Int = -1
    private var loadFailNotified = false
    private fun tag(): String = "[#$currentIndex ${currentUnit ?: "-"}]"

    private var popupOwned = false   // instance này đang giữ mutex isShowPopup

    /** Ad đã lấy ra để show: giữ để destroy sau khi đóng. */
    private var showingAd: AppOpenAd? = null
    private var showWatchdog: Runnable? = null
    private var lastShowedAtMs = 0L

    // ---- Chờ nạp để show (splash) ----
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

    // ---- Resume [loadOnReturn]: đang chờ ad lúc quay lại ----
    private var returnWait: Runnable? = null
    private var returnDialog: AlertDialog? = null

    /** True khi có một ad còn hạn để show ngay — của instance này hoặc ad để dành ([keepUnusedAd]). */
    val isReady: Boolean
        get() = (loadedAd != null && !isExpired()) ||
            (keepUnusedAd && SPARE.has(SystemClock.elapsedRealtime(), adTtlMs))

    // ========================= Công khai ========================

    /**
     * Bật auto-show app-open khi app trở lại foreground (ProcessLifecycle ON_START). Ad được nạp lúc app xuống nền
     * (ON_STOP) — KHÔNG nạp ngay lúc bật — nên ad chỉ nạp khi người dùng thật sự rời app. Mỗi resume kiểm tra
     * [GlobalVariables.canShowOpenAd] + [GlobalVariables.isShowPopup]; lúc xuống nền `canShowOpenAd = false` thì
     * cũng không nạp. [loadOnReturn] = true: nạp lúc quay lại thay vì lúc xuống nền (kiểu 1.0.21).
     *
     * @param index -1 xoay vòng / >=0 id cụ thể.
     * @param skipFirstStart bỏ qua ON_START đầu (cold start / splash đang lo).
     */
    fun resumeAd(index: Int = -1, skipFirstStart: Boolean = true) {
        if (resumeObserver != null) { log("resumeAd đã bật"); return }
        if (bailIfPremium()) { log("resumeAd: premium -> không bật"); return }
        skipNextStart = skipFirstStart
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> onBackground(index)
                Lifecycle.Event.ON_START -> onForeground(index)
                else -> Unit
            }
        }
        resumeObserver = obs
        ProcessLifecycleOwner.get().lifecycle.addObserver(obs)
        log(if (loadOnReturn) "resumeAd bật — nạp lúc quay lại rồi show" else "resumeAd bật — nạp khi app xuống nền, show khi quay lại")
    }

    private fun onBackground(index: Int) {
        if (skipResumeAfterAdClick && AdCore.adClickedWithin(AD_CLICK_WINDOW_MS)) leftAppByAdClick = true
        if (returnWait != null) {
            log("rời app lúc đang chờ ad -> thôi chờ, ad về sau để dành")
            endReturnWait(release = true)
        }
        if (loadOnReturn) return   // nạp lúc quay lại, không nạp sẵn
        if (premiumBlock() != null) return
        // Không có mạng / Mobile Ads chưa sẵn: lặng lẽ bỏ, lần xuống nền sau thử lại (không báo onError mỗi lần).
        preloadError(null)?.let { log("xuống nền, chưa nạp được: $it"); return }
        load(index)   // đã có ad / đang nạp / canShowOpenAd=false -> load tự bỏ qua
    }

    private fun onForeground(index: Int) {
        // Mua premium runtime -> bỏ ad + gỡ observer (ngừng hẳn).
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
        if (loadOnReturn) {
            // post: ProcessLifecycle báo ON_START trước khi AdCore ghi nhận Activity vừa start
            mainHandler.post { showOnReturn(index) }
            return
        }
        show(index, waitForLoad = false)   // chưa sẵn -> bỏ lần này, KHÔNG show trễ
    }

    /**
     * [loadOnReturn]: có ad (của mình / để dành) -> show ngay; chưa có -> nạp, dialog chờ tối đa [returnWaitMs], nạp
     * xong thì đóng dialog rồi show. Quá giờ / Back / rời app -> thôi chờ, ad về sau để dành cho lần quay lại kế.
     */
    private fun showOnReturn(index: Int) {
        val act = AdCore.currentActivity
        if (act == null || !AdCore.canPresentFrom(act)) { log("quay lại: chưa có Activity -> bỏ"); return }
        if (premiumBlock() != null || returnWait != null) return
        if (GlobalVariables.isShowPopup) { log("full-screen/dialog khác đang hiện, bỏ qua"); return }
        dropAdIfExpired()
        if (loadedAd == null) adoptSpare()
        if (loadedAd != null) { show(index, waitForLoad = false); return }
        preloadError(null)?.let { log("quay lại, không nạp được: $it"); return }

        if (returnShowsLoading) {
            markPopup()   // giữ mutex suốt lúc chờ: không ad full-screen nào khác chen vào
            returnDialog = DialogHelper.showLoading(act, loadingText) {
                log("bấm Back lúc chờ ad -> thôi chờ")
                endReturnWait(release = true)
            }
        }
        val timeout = Runnable {
            log("chờ ad quá ${returnWaitMs}ms -> bỏ lượt này, ad về sau để dành")
            endReturnWait(release = true)
        }
        returnWait = timeout
        mainHandler.postDelayed(timeout, returnWaitMs)
        wantShow = true
        wantIndex = index
        wantCustomIds = null
        log("quay lại: nạp rồi show")
        if (!loading) load(index)
        if (!loading && loadedAd == null) endReturnWait(release = true)   // load bỏ ngang (đã báo onError)
    }

    /** Thôi chờ ad lúc quay lại: đóng dialog + huỷ hẹn giờ. [release] = bỏ luôn lượt show (nhả mutex). */
    private fun endReturnWait(release: Boolean) {
        val wait = returnWait
        val dialog = returnDialog
        if (wait == null && dialog == null) return
        wait?.let { mainHandler.removeCallbacks(it) }
        returnWait = null
        returnDialog = null
        dialog?.let { d -> runCatching { if (d.isShowing) d.dismiss() } }
        if (release) {
            wantShow = false
            releasePopup()
        }
    }

    /**
     * Cold-start splash: load + show. [onClosed] fire 1 lần khi **ad đóng / lỗi / quá [timeoutMs]**
     * (nạp chưa kịp) -> splash gọi tiếp vào main. [index] chọn id. Mobile Ads chưa init xong thì tự chờ
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
        mainHandler.post { cb() }   // callback có thể đến từ thread ad -> ép về main
    }

    /** Bỏ ad đang giữ + gỡ observer resume (gọi khi rời màn / tắt). */
    fun destroy() {
        log("destroy ${tag()}")
        generation++
        cancelShowWatchdog()
        endReturnWait(release = true)
        releasePopup()
        wantShow = false
        splashTimeout?.let { mainHandler.removeCallbacks(it) }
        splashTimeout = null
        splashClosed = null
        resumeObserver?.let { ProcessLifecycleOwner.get().lifecycle.removeObserver(it) }
        resumeObserver = null
        loadedAd?.let { ad ->
            if (isExpired()) ad.safeDestroy() else keepForLater(ad, loadedAtMs, currentUnit, currentIndex)
        }
        loadedAd = null
        loading = false
        currentUnit = null
    }

    // ========================= Nội bộ ===========================

    /** Nạp 1 ad theo [index] (-1 xoay vòng + dự phòng). canShowOpenAd=false / đã có ad / đang nạp -> bỏ. */
    private fun load(index: Int = -1, customIds: List<String>? = null, force: Boolean = false) {
        if (!force && !GlobalVariables.canShowOpenAd) { log("canShowOpenAd=false -> không load"); return }
        if (bailIfPremium()) return
        preloadError(customIds)?.let { fail(it); return }    // còn lại: UMP/network/empty
        dropAdIfExpired()
        if (loadedAd == null && !loading) adoptSpare()       // có ad để dành -> khỏi xin ad mới
        if (loadedAd != null || loading) return

        // customIds != null -> xoay vòng list đó thay cho AdmConfigAdId.listOpenAdUnitID.
        val u = customIds?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() } ?: units()
        val chain = AdUnitChain.build(u, index, maxFallbackUnits) { CURSOR.next(u.size) }
            ?: run { fail(AdmErrorType.AD_ID_IS_NOT_EXIST); return }
        loading = true
        requestNext(chain, attempt = 0, gen = generation)
    }

    private fun requestNext(chain: AdUnitChain, attempt: Int, gen: Int) {
        val next = chain.next() ?: run { onChainFailed(); return }
        currentUnit = next.unitId
        currentIndex = next.index
        val startedAt = SystemClock.elapsedRealtime()
        log("load ${tag()}${if (attempt > 0) " [dự phòng $attempt]" else ""}")
        report(AdmAdEventType.LOAD_REQUEST, attempt = attempt)

        val result = runCatching {
            AppOpenAd.load(AdRequest.Builder(next.unitId).build(), object : AdLoadCallback<AppOpenAd> {
                // Callback đến từ thread nền (GMA Next-Gen) -> về main trước khi đụng state/hook.
                override fun onAdLoaded(ad: AppOpenAd) = AdCore.onMain {
                    if (gen != generation) {
                        // instance đã destroy (màn splash đóng) khi ad đang về -> để dành, không huỷ
                        report(
                            AdmAdEventType.LOAD_SUCCESS,
                            attempt = attempt,
                            latencyMs = SystemClock.elapsedRealtime() - startedAt,
                            adSource = ad.getResponseInfo().adSourceName(),
                            unitId = next.unitId,
                        )
                        keepForLater(ad, SystemClock.elapsedRealtime(), next.unitId, next.index)
                        return@onMain
                    }
                    onLoaded(ad, attempt, startedAt, next.unitId, next.index)
                }

                override fun onAdFailedToLoad(adError: LoadAdError) = AdCore.onMain {
                    if (gen != generation) return@onMain
                    onLoadFailed(adError, chain, attempt, startedAt, gen)
                }
            })
        }
        result.exceptionOrNull()?.let { error ->
            Log.w(TAG, "load exception ${tag()}", error)
            onChainFailed()
        }
    }

    private fun onLoaded(ad: AppOpenAd, attempt: Int, startedAt: Long, unitId: String, index: Int) {
        loading = false
        loadFailNotified = false
        val now = SystemClock.elapsedRealtime()
        log("loaded ${tag()}")
        report(
            AdmAdEventType.LOAD_SUCCESS,
            attempt = attempt,
            latencyMs = now - startedAt,
            adSource = ad.getResponseInfo().adSourceName(),
        )
        // Đã lấy ad để dành trong lúc ad này đang về -> ad này đi để dành, không đè mất ad kia.
        loadedAd?.let { keepForLater(ad, now, unitId, index); return }
        loadedAd = ad
        loadedAtMs = now
        onAvailable(ad.getResponseInfo())
        when {
            wantShow -> {
                wantShow = false
                endReturnWait(release = false)   // quay lại: đóng dialog chờ trước khi ad lên, giữ mutex
                show(wantIndex, customIds = wantCustomIds)
            }
            // Splash đã đi tiếp (hết giờ chờ) và instance không lo resume, hoặc resume [loadOnReturn] đã thôi chờ:
            // không ai show ad này ngay -> để dành (instance nào cũng lấy được, kể cả splash), không đợi destroy.
            splashClosed == null && (resumeObserver == null || loadOnReturn) && keepUnusedAd -> {
                loadedAd = null
                keepForLater(ad, now, unitId, index)
            }
        }
    }

    /** Ad nạp xong mà không dùng tới: để dành cho instance khác ([keepUnusedAd]), không thì huỷ. */
    private fun keepForLater(ad: AppOpenAd, loadedAt: Long, unitId: String?, index: Int) {
        if (!keepUnusedAd || premiumBlock() != null) { ad.safeDestroy(); return }
        log("để dành ad [#$index ${unitId ?: "-"}] cho lần show sau")
        SPARE.put(SpareAdSlot.Entry(ad, loadedAt, unitId, index))
    }

    /** Lấy ad để dành (nếu còn hạn) làm ad của instance này. Chỉ gọi khi [loadedAd] == null. */
    private fun adoptSpare() {
        if (!keepUnusedAd) return
        val e = SPARE.take(SystemClock.elapsedRealtime(), adTtlMs) ?: return
        loadedAd = e.ad
        loadedAtMs = e.loadedAtMs
        currentUnit = e.unitId
        currentIndex = e.index
        log("dùng ad để dành ${tag()}")
    }

    private fun onLoadFailed(error: LoadAdError, chain: AdUnitChain, attempt: Int, startedAt: Long, gen: Int) {
        log("load FAIL ${tag()}: ${error.code} ${error.message}")
        report(
            AdmAdEventType.LOAD_FAIL,
            attempt = attempt,
            errorCode = error.code.name,
            errorMessage = error.message,
            latencyMs = SystemClock.elapsedRealtime() - startedAt,
        )
        if (error.code.allowsFallback() && chain.hasNext) {
            AdCore.postMain(AD_FALLBACK_DELAY_MS) { if (gen == generation) requestNext(chain, attempt + 1, gen) }
            return
        }
        onChainFailed()
    }

    private fun onChainFailed() {
        loading = false
        currentUnit = null
        // Nạp nền hỏng liên tục khi mạng chập chờn: chỉ báo app 1 lần mỗi đợt lỗi, trừ khi có người đang chờ ad.
        val someoneWaiting = wantShow || splashClosed != null
        wantShow = false
        if (someoneWaiting || !loadFailNotified) {
            loadFailNotified = true
            fail(AdmErrorType.AD_IS_NOT_AVAILABLE)
        }
    }

    private fun isExpired(): Boolean = SystemClock.elapsedRealtime() - loadedAtMs >= adTtlMs

    private fun dropAdIfExpired() {
        if (loadedAd != null && isExpired()) {
            log("ad giữ quá ${adTtlMs}ms -> bỏ")
            loadedAd.safeDestroy()
            loadedAd = null
        }
    }

    /**
     * Có ad -> show NGAY. Chưa có: [waitForLoad] true -> load([index]) rồi show khi xong (splash); false -> không
     * show, không nạp (resume: ad cho lần quay lại kế được nạp lúc xuống nền). Premium / popup khác đang hiện / app
     * không ở foreground -> bỏ.
     */
    private fun show(index: Int = -1, waitForLoad: Boolean = true, customIds: List<String>? = null, force: Boolean = false) {
        val act = AdCore.currentActivity ?: run { fail(AdmErrorType.ACTIVITY_IS_NOT_AVAILABLE); return }
        if (!AdCore.canPresentFrom(act)) { fail(AdmErrorType.ACTIVITY_IS_NOT_AVAILABLE); return }
        premiumBlock()?.let { fail(it); return }
        // popupOwned: chính instance này giữ mutex (dialog chờ ad lúc quay lại) -> vẫn show
        if (GlobalVariables.isShowPopup && !popupOwned) { log("full-screen/dialog khác đang hiện, bỏ qua"); return }

        dropAdIfExpired()
        if (loadedAd == null) adoptSpare()
        val ad = loadedAd
        if (ad != null) {
            markPopup()
            present(act, ad)
            return
        }
        if (!waitForLoad) {
            log("chưa có ad (${if (loading) "đang nạp" else "chưa nạp"}) -> bỏ lần này ${tag()}")
            return
        }
        if (!loading) load(index, customIds, force)
        if (!loading) return   // load lỗi ngay (đã báo onError)
        wantShow = true
        wantIndex = index
        wantCustomIds = customIds
        log("chưa sẵn -> load rồi show ${tag()}")
    }

    private fun present(activity: Activity, ad: AppOpenAd) {
        loadedAd = null   // one-shot: show xong KHÔNG nạp bù
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

    /** Premium -> bỏ ad đang giữ + báo lỗi, trả true (caller phải return). */
    private fun bailIfPremium(): Boolean {
        val err = premiumBlock() ?: return false
        loadedAd.safeDestroy()
        loadedAd = null
        SPARE.clear()
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
        endReturnWait(release = true)
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
        unitId: String? = currentUnit,
    ) {
        AdmAdDiagnostics.report(
            AdmAdEvent(
                format = AdmAdFormat.APP_OPEN,
                type = type,
                adUnitId = unitId,
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

        /** Ad App Open để dành, dùng chung mọi instance (xem [keepUnusedAd]). */
        val SPARE = SpareAdSlot<AppOpenAd> { it.destroy() }
    }
}
