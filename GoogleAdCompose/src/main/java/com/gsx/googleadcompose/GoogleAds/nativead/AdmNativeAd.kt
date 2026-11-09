package com.gsx.googleadcompose.GoogleAds.nativead

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withStarted
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.VideoOptions
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.gsx.googleadcompose.GoogleAds.AD_FALLBACK_DELAY_MS
import com.gsx.googleadcompose.GoogleAds.AdCore
import com.gsx.googleadcompose.GoogleAds.AdUnitChain
import com.gsx.googleadcompose.GoogleAds.AdUnitCursor
import com.gsx.googleadcompose.GoogleAds.AdmAdDiagnostics
import com.gsx.googleadcompose.GoogleAds.AdmAdEvent
import com.gsx.googleadcompose.GoogleAds.AdmAdEventType
import com.gsx.googleadcompose.GoogleAds.AdmAdFormat
import com.gsx.googleadcompose.GoogleAds.DEFAULT_MAX_FALLBACK_UNITS
import com.gsx.googleadcompose.GoogleAds.adSourceName
import com.gsx.googleadcompose.GoogleAds.allowsFallback
import com.gsx.googleadcompose.data.AdmConfigAdId
import com.gsx.googleadcompose.error.AdmErrorType
import com.gsx.googleadcompose.helper.NetworkHelper
import com.gsx.googleadcompose.utils.PreferencesManager
import kotlinx.coroutines.delay

/**
 * Native ad (GMA Next-Gen SDK) theo pattern Google git `NativeComposeFragment`:
 * `NativeAdLoader.load(...)` -> render asset qua [NativeAdView] wrapper (xem [NativeAdCompose]).
 * Layout dựng sẵn ở [NativeAdLayouts]. Load + show luôn như banner. Log tag `GoogleAds/Native`.
 *
 * Controller giữ guard (premium/network/UMP/units), xoay unit id, gắn callback + paid event + màu
 * layout. Unit ID lấy từ [AdmConfigAdId.listNativeAdUnitID]. Cần init MobileAds (UMP) trước.
 *
 * ```
 * AdmNative(Modifier.fillMaxWidth(), layout = NativeLayout.NORMAL) {
 *     backgroundColor = Color(0xFF222222)
 *     ctaColor = Color(0xFFFF6D00)
 *     onError = { type -> Log.w("ad", "$type") }
 * }
 * ```
 */
class AdmNativeAd internal constructor() : NativeAdEventCallback {

    var enableLogging: Boolean = true
    /** Mute video native khi bắt đầu (mặc định true). */
    var startMuted: Boolean = true

    // ---- Màu layout (đọc bởi layout, vd [NativeAdNormalLayout]) ----
    /** Nền card native. */
    var backgroundColor: Color = Color(0xFF1E1330)
    /** Nền nút install (CTA). */
    var ctaColor: Color = Color(0xFF52DE11)
    /** Chữ trên nút install. */
    var ctaTextColor: Color = Color(0xFF062100)
    /** Màu chữ chính (headline). */
    var textColor: Color = Color.White
    /** Màu chữ phụ (body/store/rating). */
    var subTextColor: Color = Color(0xFFB9AED0)

    /**
     * [NativeLayout.COLLAPSIBLE]: số giây đầu (tính từ lúc ad hiện) mũi tên đóng CHƯA bấm được — vòng
     * tròn quanh nút chạy đủ 1 vòng thì mới ấn được. `0` (mặc định) = bấm được ngay.
     */
    var collapseDelaySeconds: Int = 0

    /**
     * [NativeLayout.COLLAPSIBLE]: ấn mũi tên đóng xong, sau số giây này ad tự HIỆN LẠI (cùng ad đó,
     * không load mới). `0` (mặc định) = ẩn hẳn, không hiện lại.
     */
    var collapseReshowSeconds: Int = 0

    /** [NativeLayout.COLLAPSIBLE]: màu vòng tròn chạy quanh mũi tên trong lúc chờ [collapseDelaySeconds]. */
    var collapseRingColor: Color = Color.White

    /** Số ID dự phòng thử thêm khi ID đang dùng không có ad (chỉ khi xoay vòng, `index = -1`). */
    var maxFallbackUnits: Int = DEFAULT_MAX_FALLBACK_UNITS

    /** true: mỗi lượt đều thử từ ID đầu list (khi list xếp floor cao -> thấp). false: xoay vòng. */
    var startFromFirstUnit: Boolean = false

    /**
     * [AdmNative] tự load mà hụt: slot thử lại sau lần lượt các khoảng này, chỉ khi app đang hiện trên màn.
     * Rỗng = không thử lại. Hết lượt mới báo [onError].
     */
    var retryDelaysMs: List<Long> = listOf(15_000L, 45_000L)

    /** Còn lượt thử lại -> chưa báo lỗi cho app, app chưa ẩn slot. */
    internal var holdRetryableErrors = false

    /** Lỗi gần nhất bị giữ lại do [holdRetryableErrors]. */
    internal var heldError: AdmErrorType? = null
        private set

    /**
     * Áp cấu hình của composable, gọi mỗi lần recompose để nhận lambda mới. Gỡ hook cũ trước: app hay bọc hook
     * (`val old = onPaid; onPaid = { old(it); ... }`), không gỡ thì mỗi lần recompose lại bọc thêm 1 lớp và
     * 1 sự kiện bị xử lý nhiều lần.
     */
    internal fun applyConfig(configure: AdmNativeAd.() -> Unit) {
        onError = {}
        onLoaded = {}
        onImpression = {}
        onClicked = {}
        onPaid = {}
        configure()
    }

    /** Kênh báo lỗi (xem [AdmErrorType]). */
    var onError: (AdmErrorType) -> Unit = {}

    // ---- Hook sự kiện ----
    /** Ad load xong -> trả [NativeAd] (lấy `.responseInfo` nếu cần). */
    var onLoaded: (NativeAd) -> Unit = {}
    var onImpression: () -> Unit = {}
    var onClicked: () -> Unit = {}
    var onPaid: (AdValue) -> Unit = {}

    /** Tăng mỗi lần destroy -> callback load đến sau khi đã rời màn tự huỷ ad, không đụng state cũ. */
    private var generation = 0
    private var currentIndex: Int = -1
    private var unitId: String? = null
    private fun tag(): String = "[#$currentIndex ${unitId ?: "-"}]"

    /** Ad đang giữ. */
    @Volatile
    var nativeAd: NativeAd? = null
        private set

    /** True khi premium -> không nên hiện. */
    fun isBlockedByPremium(): Boolean = premiumBlock() != null

    /** Loại lỗi premium (removeAds/lifetime/sub) chặn native, null nếu không bị chặn. */
    fun premiumBlockError(): AdmErrorType? = premiumBlock()

    // ============================ Load ============================

    /**
     * Load 1 native. Kết quả trả qua [onResult] (NativeAd hoặc null) + hook [onLoaded]/[onError].
     * [index] = -1 xoay vòng, >=0 chọn id cụ thể trong [AdmConfigAdId.listNativeAdUnitID].
     */
    fun load(index: Int = -1, onResult: (NativeAd?) -> Unit = {}) =
        loadIds(units(), index, onResult)

    /** Load xoay vòng từ list id [ids] cho trước (vd customIds của [AdmNative]). [index]=-1 xoay vòng + dự phòng. */
    internal fun loadIds(ids: List<String>, index: Int = -1, onResult: (NativeAd?) -> Unit = {}) {
        premiumBlock()?.let { fail(it); onResult(null); return }
        val u = ids.filter { it.isNotBlank() }
        if (u.isEmpty()) { fail(AdmErrorType.LIST_AD_ID_IS_EMPTY); onResult(null); return }
        val chain = AdUnitChain.build(u, index, maxFallbackUnits) { if (startFromFirstUnit) 0 else CURSOR.next(u.size) }
        val first = chain?.next()
        if (chain == null || first == null) { fail(AdmErrorType.AD_ID_IS_NOT_EXIST); onResult(null); return }
        currentIndex = first.index
        request(first.unitId, chain, attempt = 0, gen = generation, onResult)
    }

    /** Load native theo unit id cụ thể (không qua [AdmConfigAdId]). Dùng cho [AdmNativeSequence]. */
    fun loadUnit(unitId: String, onResult: (NativeAd?) -> Unit = {}) {
        request(unitId, chain = null, attempt = 0, gen = generation, onResult)
    }

    private fun request(unit: String, chain: AdUnitChain?, attempt: Int, gen: Int, onResult: (NativeAd?) -> Unit) {
        envBlock()?.let { fail(it); onResult(null); return }
        if (unit.isBlank()) { fail(AdmErrorType.AD_ID_IS_NOT_EXIST); onResult(null); return }
        unitId = unit
        val startedAt = SystemClock.elapsedRealtime()
        log("loadUnit ${tag()}${if (attempt > 0) " [dự phòng $attempt]" else ""}")
        report(AdmAdEventType.LOAD_REQUEST, attempt = attempt)

        val videoOptions = VideoOptions.Builder().setStartMuted(startMuted).build()
        val request = NativeAdRequest.Builder(unit, listOf(NativeAd.NativeAdType.NATIVE))
            .setVideoOptions(videoOptions)
            .build()

        val result = runCatching {
            NativeAdLoader.load(request, object : NativeAdLoaderCallback {
                // Callback đến từ thread nền -> về main: onResult đổi Compose state / AdmNativeSequenceState.
                override fun onNativeAdLoaded(ad: NativeAd) = AdCore.onMain {
                    if (gen != generation) {                 // đã rời màn trong lúc load -> không ai nhận ad này
                        runCatching { ad.destroy() }
                        return@onMain
                    }
                    log("loaded ${tag()}")
                    report(
                        AdmAdEventType.LOAD_SUCCESS,
                        attempt = attempt,
                        latencyMs = SystemClock.elapsedRealtime() - startedAt,
                        adSource = ad.getResponseInfo().adSourceName(),
                    )
                    ad.adEventCallback = this@AdmNativeAd
                    val previous = nativeAd
                    nativeAd = ad
                    if (previous !== ad) runCatching { previous?.destroy() }   // load lại: ad cũ không được bỏ rơi
                    onLoaded(ad)
                    onResult(ad)
                }

                override fun onAdFailedToLoad(adError: LoadAdError) = AdCore.onMain {
                    if (gen != generation) return@onMain
                    log("load FAIL ${tag()}: ${adError.code} ${adError.message}")
                    report(
                        AdmAdEventType.LOAD_FAIL,
                        attempt = attempt,
                        errorCode = adError.code.name,
                        errorMessage = adError.message,
                        latencyMs = SystemClock.elapsedRealtime() - startedAt,
                    )
                    val next = chain?.takeIf { adError.code.allowsFallback() }?.next()
                    if (next != null) {
                        AdCore.postMain(AD_FALLBACK_DELAY_MS) {
                            if (gen == generation) {
                                currentIndex = next.index
                                request(next.unitId, chain, attempt + 1, gen, onResult)
                            }
                        }
                        return@onMain
                    }
                    fail(AdmErrorType.AD_IS_NOT_AVAILABLE)
                    onResult(null)
                }
            })
        }
        result.exceptionOrNull()?.let { error ->
            Log.w(TAG, "load exception ${tag()}", error)
            fail(AdmErrorType.AD_IS_NOT_AVAILABLE)
            onResult(null)
        }
    }

    /** Nhận event của ad load từ nơi khác (preload/tuần tự): gắn đúng unit id để đo doanh thu theo từng ID. */
    internal fun bindEvents(ad: NativeAd) {
        unitId = runCatching { ad.adUnitId }.getOrNull() ?: unitId
        ad.adEventCallback = this
    }

    /** Hủy ad (gọi khi rời màn). Load đang chạy về sau sẽ tự huỷ ad, không gọi onResult. */
    fun destroy() {
        log("destroy ${tag()}")
        generation++
        runCatching { nativeAd?.destroy() }
        nativeAd = null
    }

    // ============ NativeAdEventCallback (kế thừa AdEventCallback) ============

    // Event callback đến từ thread nền -> về main trước khi gọi hook app.
    override fun onAdImpression() = AdCore.onMain {
        log("impression ${tag()}")
        report(AdmAdEventType.IMPRESSION)
        onImpression()
    }

    override fun onAdClicked() = AdCore.onMain {
        log("clicked ${tag()}")
        AdCore.noteAdClicked()
        report(AdmAdEventType.CLICK)
        onClicked()
    }

    override fun onAdPaid(value: AdValue) = AdCore.onMain {
        log("paid ${tag()}: ${value.valueMicros} ${value.currencyCode}")
        report(AdmAdEventType.PAID, valueMicros = value.valueMicros, currencyCode = value.currencyCode)
        onPaid(value)
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

    /** Guard môi trường (premium/UMP/network) — KHÔNG gồm check list rỗng. */
    private fun envBlock(): AdmErrorType? {
        premiumBlock()?.let { return it }
        if (!AdCore.isMobileAdsReady) return AdmErrorType.UMP_IS_NOT_ACTIVE
        val ctx = AdCore.appContext
        if (ctx != null && !NetworkHelper.isOnline(ctx)) return AdmErrorType.NETWORK_IS_NOT_AVAILABLE
        return null
    }

    private fun units(): List<String> = AdmConfigAdId.listNativeAdUnitID.filter { it.isNotBlank() }

    private fun fail(type: AdmErrorType) {
        log("ERROR: $type")
        report(AdmAdEventType.ERROR, errorCode = type.name)
        if (holdRetryableErrors && type in RETRYABLE_ERRORS) {
            heldError = type
            return
        }
        onError(type)
    }

    /** SDK chưa sẵn sàng quá lâu / consent không cho phép: báo app, slot vẫn tự load nếu SDK sẵn sàng sau đó. */
    internal fun notifyAdsNotReady() {
        holdRetryableErrors = false
        fail(AdmErrorType.UMP_IS_NOT_ACTIVE)
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
                format = AdmAdFormat.NATIVE,
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

    private fun log(msg: String) {
        if (enableLogging) Log.d(TAG, msg)
    }

    companion object {
        private const val TAG = "GoogleAds/Native"
        private val CURSOR = AdUnitCursor()

        /** Lỗi có thể hết sau một lúc: đáng để slot thử lại. Premium / sai ID thì không. */
        private val RETRYABLE_ERRORS = setOf(
            AdmErrorType.AD_IS_NOT_AVAILABLE,
            AdmErrorType.NETWORK_IS_NOT_AVAILABLE,
            AdmErrorType.UMP_IS_NOT_ACTIVE,
        )

        /** True nếu lúc này gửi được request (không premium, SDK đã init, có mạng). */
        internal fun canRequestNow(): Boolean {
            if (isPremiumBlocked() || !AdCore.isMobileAdsReady) return false
            val ctx = AdCore.appContext
            return ctx == null || NetworkHelper.isOnline(ctx)
        }

        /** True khi premium (removeAds/lifetime/sub) -> không hiện native (kể cả shimmer). */
        fun isPremiumBlocked(): Boolean {
            val pm = PreferencesManager.getInstance()
            return pm.isRemoveAds() || pm.isLifetime() || pm.isSUB()
        }

        /**
         * Preload native — load rồi trả [NativeAd] qua [onResult] để app tự lưu (map/list) + truyền
         * vào [AdmNative]. App SỞ HỮU ad: tự `destroy()` khi xong. Lỗi/premium -> onResult(null).
         *
         * ```
         * AdmNativeAd.preload { ad -> ad?.let { cache["home"] = it } }
         * ```
         */
        fun preload(
            index: Int = -1,
            configure: AdmNativeAd.() -> Unit = {},
            onResult: (NativeAd?) -> Unit,
        ) {
            AdmNativeAd().apply(configure).load(index, onResult = onResult)
        }

        /** Preload native theo unit id cụ thể (cho [AdmNativeSequence]). */
        fun preloadUnit(
            unitId: String,
            configure: AdmNativeAd.() -> Unit = {},
            onResult: (NativeAd?) -> Unit,
        ) {
            AdmNativeAd().apply(configure).loadUnit(unitId, onResult)
        }
    }
}

/**
 * Native tuần tự — preload list id theo thứ tự, luôn giữ sẵn 1 ad cho lần show kế.
 * Show id[n] -> tự preload id[n+1]. Tới id CUỐI thì dừng (KHÔNG xoay vòng). Init list 1 lần.
 *
 * ```
 * AdmNativeSequenceState.init(listOf(0, 1, 2))   // index vào listNativeAdUnitID, 1 lần (Application/Splash)
 * AdmNativeSequence(Modifier.fillMaxWidth())     // mỗi chỗ cần -> show id kế + preload tiếp
 * ```
 */
object AdmNativeSequenceState {
    private const val TAG = "GoogleAds/NativeSeq"

    /** Chờ tối đa preload ad kế (ms) rồi cho đi (isNextReady=true). Đổi được. */
    var timeoutMs: Long = 1000L

    private var ids: List<String> = emptyList()
    private var pos: Int = 0
    private var loading: Boolean = false
    private val handler = Handler(Looper.getMainLooper())

    /** Tăng khi init/release -> ad load xong muộn sau đó tự huỷ, không rơi vào buffer không ai dọn. */
    private var generation = 0

    /** Ad sẵn cho lần [consume] kế (state -> Compose observe được). */
    var ready by mutableStateOf<NativeAd?>(null)
        private set

    /**
     * Slot hiện tại ĐÃ LOAD XONG THẬT (ad sẵn HOẶC fail). View consume theo cờ này -> chờ ad thật,
     * KHÔNG bị timeout ép skip. Reset `false` mỗi lần bắt đầu preload id kế.
     */
    var resolved by mutableStateOf(false)
        private set

    /**
     * Cờ NAV (cho nút "Tiếp" onboarding): slot xong THẬT [resolved] HOẶC đã quá [timeoutMs].
     * Timeout chỉ mở nút đi tiếp — KHÔNG ảnh hưởng việc view chờ/hiện ad thật.
     */
    var isNextReady by mutableStateOf(false)
        private set

    /** Hết id (đã tới cuối list, không còn ad sẵn/đang load). */
    val isExhausted: Boolean
        get() = pos >= ids.size && ready == null && !loading

    /**
     * Init bằng list INDEX (vào [AdmConfigAdId.listNativeAdUnitID]) theo thứ tự muốn show, rồi
     * preload id đầu. Index sai/blank bị bỏ. Gọi 1 lần (đổi thứ tự -> gọi lại).
     */
    fun init(indices: List<Int>, timeoutMs: Long = this.timeoutMs) {
        this.timeoutMs = timeoutMs
        val all = AdmConfigAdId.listNativeAdUnitID
        ids = indices.mapNotNull { all.getOrNull(it)?.takeIf { s -> s.isNotBlank() } }
        pos = 0
        generation++
        handler.removeCallbacksAndMessages(null)
        ready?.destroy()
        ready = null
        loading = false
        resolved = false
        isNextReady = false
        preloadNext()
    }

    private fun preloadNext() {
        handler.removeCallbacksAndMessages(null)
        if (loading) return                                    // đang load -> để callback xử lý
        if (pos >= ids.size) {                                 // hết id -> settled, no ad
            resolved = true
            isNextReady = true
            return
        }
        loading = true
        resolved = false
        isNextReady = false
        // Timeout: CHỈ mở nút đi tiếp (nav). View vẫn chờ ad thật (resolved) -> load xong là hiện.
        handler.postDelayed({ isNextReady = true }, timeoutMs)
        val id = ids[pos]
        val gen = generation
        Log.d(TAG, "preload pos=$pos $id")
        AdmNativeAd.preloadUnit(id) { ad ->
            if (gen != generation) {                           // đã init lại / release trong lúc load
                runCatching { ad?.destroy() }
                return@preloadUnit
            }
            loading = false
            handler.removeCallbacksAndMessages(null)
            // ID này không có ad nhưng vẫn request được -> thử ID kế cho slot hiện tại, trang không bị trống.
            if (ad == null && pos + 1 < ids.size && AdmNativeAd.canRequestNow()) {
                Log.w(TAG, "preload FAIL pos=$pos -> thử id kế")
                pos += 1
                preloadNext()
                return@preloadUnit
            }
            ready = ad                                         // null nếu fail -> slot rỗng
            resolved = true                                    // load XONG thật (sẵn/fail) -> view consume
            isNextReady = true
            if (ad == null) Log.w(TAG, "preload FAIL pos=$pos (slot rỗng, consume sẽ skip)")
        }
    }

    /**
     * Lấy ad slot hiện tại để show + advance pos + preload id kế. Trả ad (hoặc null nếu fail -> view
     * skip). Chỉ gọi khi [resolved] (đã load xong THẬT). Chưa xong -> null, không advance (view chờ).
     */
    fun consume(): NativeAd? {
        if (!resolved) return null            // slot chưa load xong thật -> view tiếp tục chờ (shimmer)
        val ad = ready                        // null nếu fail -> view skip
        ready = null
        resolved = false
        isNextReady = false
        pos += 1                              // tiến tới id kế; pos == ids.size -> hết
        preloadNext()
        return ad
    }

    /**
     * Dọn buffer khi RỜI HẲN sequence (vd onboarding xong): destroy ad đang giữ chưa consume + dừng
     * preload. Tránh leak ad không view nào lấy. Ad đã consume (view đang show) do view tự destroy.
     */
    fun release() {
        generation++
        handler.removeCallbacksAndMessages(null)
        ready?.destroy()
        ready = null
        resolved = false
        isNextReady = false
        loading = false
        pos = 0
        ids = emptyList()
    }
}

/**
 * Compose native tuần tự — show ad đang sẵn của [AdmNativeSequenceState], tự kích preload id kế.
 * Cần [AdmNativeSequenceState.init] trước. Chưa sẵn -> shimmer. Ad consumed thuộc view này -> tự destroy.
 *
 * [active]: CHỈ consume khi view thật sự đang hiển thị. Trong Pager (compose sẵn neighbor) -> truyền
 * `active = (page == pager.currentPage)` để KHÔNG consume sớm cho trang off-screen (load tuần tự
 * đúng theo lúc lướt tới, tránh cascade).
 *
 * ```
 * AdmNativeSequence(Modifier.fillMaxWidth(), active = page == pager.currentPage) { ctaColor = ... }
 * ```
 */
@Composable
fun AdmNativeSequence(
    modifier: Modifier = Modifier,
    layout: NativeLayout = NativeLayout.NORMAL,
    active: Boolean = true,
    configure: AdmNativeAd.() -> Unit = {},
) {
    if (AdmNativeAd.isPremiumBlocked()) return            // premium -> render rỗng ngay, KHÔNG shimmer (tránh nháy)

    val resolved = AdmNativeSequenceState.resolved       // observe: slot load XONG thật (sẵn hoặc fail)
    var consumed by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf<NativeAd?>(null) }

    // Chỉ consume khi: view đang hiển thị ([active]) + slot load XONG thật ([resolved]).
    LaunchedEffect(resolved, active) {
        if (!consumed && active && resolved) {
            consumed = true
            shown = AdmNativeSequenceState.consume()
        }
    }
    DisposableEffect(Unit) { onDispose { shown?.destroy() } }   // view sở hữu ad đã consume

    when {
        shown != null -> AdmNative(modifier = modifier, layout = layout, nativeAd = shown, configure = configure)
        consumed -> Unit                                    // đã consume nhưng fail -> skip (rỗng)
        else -> AdShimmerNative(modifier, layout)           // chờ ad thật -> shimmer
    }
}

/** Chờ SDK init tối đa ngần này thì thôi shimmer + báo app. SDK sẵn sàng sau đó, slot vẫn tự load. */
private const val ADS_READY_WAIT_MS = 12_000L

/**
 * Compose native — 2 chế độ tách bằng [index]:
 * - Truyền [nativeAd] (preload) -> show ngay, KHÔNG tự load. App sở hữu ad: tự destroy, hoặc truyền
 *   [destroyNativeAdOnDispose] = true để ad được huỷ khi rời màn (mỗi ad giữ 1 WebView, quên huỷ là tốn bộ nhớ).
 * - [index] != null (và không có [nativeAd]) -> TỰ load slot đó (-1 xoay vòng, >=0 cụ thể), shimmer
 *   lúc chờ, tự destroy khi rời màn.
 * - [index] == null + [nativeAd] == null -> SKIP (render rỗng). Dùng cho preload đang chờ.
 *
 * Slot tự load không bỏ cuộc ngay: SDK chưa init xong thì chờ; hết ad / mất mạng thì thử lại theo
 * [AdmNativeAd.retryDelaysMs] khi app đang hiện. Hết lượt mới báo onError. Premium -> render rỗng.
 *
 * ```
 * AdmNative(Modifier.fillMaxWidth(), nativeAd = cache["home"])      // preload -> show, null -> skip
 * AdmNative(Modifier.fillMaxWidth(), index = -1) { ctaColor = ... } // tự load (xoay vòng)
 * ```
 */
@Composable
fun AdmNative(
    modifier: Modifier = Modifier,
    layout: NativeLayout = NativeLayout.NORMAL,
    nativeAd: NativeAd? = null,
    index: Int? = null,
    customIds: List<String>? = null,
    destroyNativeAdOnDispose: Boolean = false,
    configure: AdmNativeAd.() -> Unit = {},
) {
    val controller = remember { AdmNativeAd() }
    controller.applyConfig(configure)
    val blocked = remember { controller.isBlockedByPremium() }
    val isPreview = LocalInspectionMode.current

    var selfAd by remember { mutableStateOf<NativeAd?>(null) }
    var selfAdSlot by remember { mutableStateOf<Any?>(null) }
    var failed by remember { mutableStateOf(false) }
    val slot = remember(index, customIds) { Any() }
    var attempt by remember(slot) { mutableIntStateOf(0) }
    val wantSelf = index != null || customIds != null   // customIds truyền -> xoay vòng list đó
    val adsReady = isPreview || AdCore.isMobileAdsReady   // snapshot state: SDK init xong -> effect tự chạy lại
    val adsUnavailable = AdCore.adsUnavailable
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // Tự load CHỈ khi: không có ad preload + có yêu cầu self-load ([index] != null).
    LaunchedEffect(nativeAd, slot, blocked, adsReady, adsUnavailable, attempt) {
        if (isPreview) return@LaunchedEffect
        if (blocked) {
            // Premium/removeAds -> báo error (không im lặng), rồi render rỗng.
            controller.onError(controller.premiumBlockError() ?: AdmErrorType.CLIENT_HAVE_BEEN_REMOVED_AD)
            return@LaunchedEffect
        }
        if (nativeAd != null || !wantSelf || (selfAd != null && selfAdSlot === slot)) return@LaunchedEffect
        if (!adsReady) {
            // SDK chưa init xong: chờ thay vì coi là hỏng, adsReady đổi thì effect này chạy lại và load.
            if (!adsUnavailable) delay(ADS_READY_WAIT_MS)
            if (!failed) {
                failed = true
                controller.notifyAdsNotReady()
            }
            return@LaunchedEffect
        }
        controller.holdRetryableErrors = attempt < controller.retryDelaysMs.size
        val cb: (NativeAd?) -> Unit = { result ->
            selfAd = result
            selfAdSlot = slot
            failed = result == null
        }
        if (customIds != null) controller.loadIds(customIds, index ?: -1, cb)   // xoay vòng list truyền
        else controller.load(index ?: -1, cb)
    }
    // Slot đang trống trên màn: thử lại theo lịch. App ở nền thì chờ quay lại, không request khi user không thấy.
    LaunchedEffect(failed, attempt, adsReady) {
        if (!failed || !adsReady || isPreview || blocked || !wantSelf || nativeAd != null) return@LaunchedEffect
        val wait = controller.retryDelaysMs.getOrNull(attempt) ?: return@LaunchedEffect
        delay(wait)
        lifecycle.withStarted { }
        attempt += 1
    }
    // Ad TỰ load luôn được destroy; ad preload do app sở hữu -> chỉ destroy khi app yêu cầu.
    val currentNativeAd by rememberUpdatedState(nativeAd)
    val currentDestroyOnDispose by rememberUpdatedState(destroyNativeAdOnDispose)
    DisposableEffect(Unit) {
        onDispose {
            controller.destroy()
            if (currentDestroyOnDispose) runCatching { currentNativeAd?.destroy() }
        }
    }

    val shown = nativeAd ?: selfAd
    LaunchedEffect(shown) { shown?.let(controller::bindEvents) }   // gắn impression/click/paid

    when {
        blocked -> Unit
        shown != null -> NativeLayoutRender(shown, layout, controller, modifier)
        failed -> Unit                                              // lỗi -> rỗng (đã báo onError)
        wantSelf -> AdShimmerNative(modifier, layout, controller.backgroundColor)  // self-load chờ
        else -> Unit                                                // preload chờ/skip -> rỗng
    }
}
