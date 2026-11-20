package com.gsx.googleadcompose.GoogleAds

/**
 * App-open resume cấp app — gọi 1 lần trong `Application.onCreate`. Giữ 1 [AdmOpenAd] sống cả phiên,
 * nạp ad lúc app xuống nền và show khi app trở lại foreground (check `canShowOpenAd` + `isShowPopup`).
 *
 * ```
 * class App : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         AdmOpenResume.start()      // hoặc start { onPaid = { ... } }
 *     }
 * }
 * ```
 * UMP/MobileAds init ở splash -> ON_START đầu (cold) bị bỏ qua; mỗi lần xuống nền nạp 1 ad, lần quay lại sau show.
 * Show rate cao hơn (kiểu SDK 1.0.21): `start { loadOnReturn = true }` — nạp lúc quay lại, dialog chờ rồi show.
 */
object AdmOpenResume {

    private var ad: AdmOpenAd? = null

    /** Instance app-open đang chạy (null nếu chưa [start]). */
    val instance: AdmOpenAd? get() = ad

    /** Bật resume (idempotent). [index] -1 xoay vòng / >=0 id cụ thể. [configure] gắn hook 1 lần. */
    fun start(index: Int = -1, configure: AdmOpenAd.() -> Unit = {}) {
        if (ad != null) return
        ad = AdmOpenAd().apply(configure).also { it.resumeAd(index) }
    }

    /** Tắt + dọn (hiếm dùng). */
    fun stop() {
        ad?.destroy()
        ad = null
    }
}
