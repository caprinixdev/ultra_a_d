package com.gsx.googleadcompose.GoogleAds

import android.app.Activity
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.gsx.googleadcompose.data.AdmConfigAdId

/**
 * Interstitial ad (GMA Next-Gen SDK) — one-shot, xem [AdmFullScreenAd]. Log tag `GoogleAds/Inter`.
 *
 * ```
 * val inter = AdmInterstitial { onError = { type -> toast(type) } }   // helper -> [AdmInterstitialAd]
 * inter.load()                // load sẵn 1 ad (preload)
 * inter.show(activity)        // sẵn -> show; chưa -> dialog chờ rồi show
 * inter.destroy()             // rời màn
 * ```
 *
 * Unit ID lấy từ [AdmConfigAdId.listInterstitialAdUnitID] (nhiều ID -> xoay vòng + dự phòng).
 */
class AdmInterstitialAd internal constructor() :
    AdmFullScreenAd<InterstitialAd>("GoogleAds/Inter", AdmAdFormat.INTERSTITIAL, CURSOR),
    InterstitialAdEventCallback {

    override fun configuredUnits(): List<String> = AdmConfigAdId.listInterstitialAdUnitID

    override fun requestAd(request: AdRequest, callback: AdLoadCallback<InterstitialAd>) =
        InterstitialAd.load(request, callback)

    override fun bindEvents(ad: InterstitialAd) {
        ad.adEventCallback = this
    }

    override fun presentAd(ad: InterstitialAd, activity: Activity) = ad.show(activity)

    private companion object {
        val CURSOR = AdUnitCursor()   // xoay vòng unit id chung mọi instance
    }
}
