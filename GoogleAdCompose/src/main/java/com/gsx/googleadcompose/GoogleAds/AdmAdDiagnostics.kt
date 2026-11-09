package com.gsx.googleadcompose.GoogleAds

import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo

enum class AdmAdFormat { APP_OPEN, BANNER, INTERSTITIAL, NATIVE, REWARDED, REWARDED_INTERSTITIAL }

enum class AdmAdEventType {
    LOAD_REQUEST,
    LOAD_SUCCESS,
    LOAD_FAIL,
    SHOW_REQUEST,
    SHOW_SUCCESS,
    SHOW_FAIL,
    IMPRESSION,
    CLICK,
    PAID,
    DISMISS,

    /** Lỗi guard của lib (premium/offline/UMP/...): [AdmAdEvent.errorCode] là tên [com.gsx.googleadcompose.error.AdmErrorType]. */
    ERROR,
}

/**
 * 1 sự kiện ad. [errorCode] = mã lỗi gốc của SDK (`NO_FILL`, `NETWORK_ERROR`...), [adSource] = nguồn trả ad,
 * [attempt] = thứ tự ID trong lượt load (0 = ID đầu, >0 = ID dự phòng).
 */
data class AdmAdEvent(
    val format: AdmAdFormat,
    val type: AdmAdEventType,
    val adUnitId: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val latencyMs: Long? = null,
    val adSource: String? = null,
    val attempt: Int? = null,
    val valueMicros: Long? = null,
    val currencyCode: String? = null,
)

/**
 * Kênh dữ liệu cho app đo fill rate / show rate / doanh thu theo từng ID, và lần ra crash do ad nào.
 * Gọi trên main thread.
 *
 * ```
 * AdmAdDiagnostics.listener = { e ->
 *     FirebaseCrashlytics.getInstance().setCustomKey("last_ad", "${e.format} ${e.type} ${e.adSource}")
 *     analytics.logEvent("ad_${e.type.name.lowercase()}", bundleOf("unit" to e.adUnitId, "error" to e.errorCode))
 * }
 * ```
 */
object AdmAdDiagnostics {
    /** Lỗi trong listener bị nuốt: đo đạc không bao giờ được làm crash app. */
    @Volatile
    var listener: ((AdmAdEvent) -> Unit)? = null

    /** Sự kiện gần nhất. */
    @Volatile
    var lastEvent: AdmAdEvent? = null
        private set

    internal fun report(event: AdmAdEvent) {
        lastEvent = event
        val l = listener ?: return
        runCatching { l(event) }
    }
}

internal fun ResponseInfo?.adSourceName(): String? = this?.loadedAdSourceResponseInfo?.name
