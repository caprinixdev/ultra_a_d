package com.gsx.googleadcompose.GoogleAds

import android.app.Activity
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardItem
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
import com.gsx.googleadcompose.data.AdmConfigAdId

/**
 * Rewarded ad (GMA Next-Gen SDK) — one-shot, xem [AdmFullScreenAd]. Log tag `GoogleAds/Reward`.
 *
 * ```
 * val reward = AdmReward {           // composable helper -> trả [AdmRewardAd], tự destroy
 *     onReward = { item -> grant(item.amount) }
 *     onError  = { type -> toast(type) }
 * }
 * reward.load()              // load sẵn 1 ad (vd onCreate)
 * reward.show(activity)      // sẵn -> show; chưa -> dialog chờ rồi show
 * reward.destroy()           // rời màn
 * ```
 *
 * Unit ID lấy từ [AdmConfigAdId.listRewardAdUnitID] (nhiều ID -> xoay vòng + dự phòng).
 */
class AdmRewardAd internal constructor() :
    AdmFullScreenAd<RewardedAd>("GoogleAds/Reward", AdmAdFormat.REWARDED, CURSOR),
    RewardedAdEventCallback {

    var onReward: (RewardItem) -> Unit = {}

    override fun configuredUnits(): List<String> = AdmConfigAdId.listRewardAdUnitID

    override fun requestAd(request: AdRequest, callback: AdLoadCallback<RewardedAd>) =
        RewardedAd.load(request, callback)

    override fun bindEvents(ad: RewardedAd) {
        ad.adEventCallback = this
    }

    override fun presentAd(ad: RewardedAd, activity: Activity) {
        ad.show(activity) { rewardItem ->
            logCurrent("reward: ${rewardItem.amount} ${rewardItem.type}")
            AdCore.onMain { onReward(rewardItem) }
        }
    }

    private companion object {
        val CURSOR = AdUnitCursor()   // xoay vòng unit id chung mọi instance
    }
}
