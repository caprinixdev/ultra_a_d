package com.gsx.googleadcompose.GoogleAds

import android.app.Activity
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardItem
import com.google.android.libraries.ads.mobile.sdk.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.libraries.ads.mobile.sdk.rewardedinterstitial.RewardedInterstitialAdEventCallback
import com.gsx.googleadcompose.data.AdmConfigAdId

/**
 * Rewarded Interstitial ad (GMA Next-Gen SDK) — one-shot, xem [AdmFullScreenAd]. Log tag `GoogleAds/RewardInter`.
 *
 * ```
 * val ad = AdmRewardInterstitial {     // helper -> [AdmRewardInterstitialAd], tự destroy
 *     onReward = { item -> grant(item.amount) }
 *     onError  = { type -> toast(type) }
 * }
 * ad.load()
 * ad.show()
 * ```
 *
 * Unit ID lấy từ [AdmConfigAdId.listRewardInterstitialAdUnitID] (nhiều ID -> xoay vòng + dự phòng).
 */
class AdmRewardInterstitialAd internal constructor() :
    AdmFullScreenAd<RewardedInterstitialAd>("GoogleAds/RewardInter", AdmAdFormat.REWARDED_INTERSTITIAL, CURSOR),
    RewardedInterstitialAdEventCallback {

    var onReward: (RewardItem) -> Unit = {}

    override fun configuredUnits(): List<String> = AdmConfigAdId.listRewardInterstitialAdUnitID

    override fun requestAd(request: AdRequest, callback: AdLoadCallback<RewardedInterstitialAd>) =
        RewardedInterstitialAd.load(request, callback)

    override fun bindEvents(ad: RewardedInterstitialAd) {
        ad.adEventCallback = this
    }

    override fun presentAd(ad: RewardedInterstitialAd, activity: Activity) {
        ad.show(activity) { rewardItem ->
            logCurrent("reward: ${rewardItem.amount} ${rewardItem.type}")
            AdCore.onMain { onReward(rewardItem) }
        }
    }

    private companion object {
        val CURSOR = AdUnitCursor()   // xoay vòng unit id chung mọi instance
    }
}
