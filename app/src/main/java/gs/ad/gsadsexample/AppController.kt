package gs.ad.gsadsexample

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import gs.ad.gsadsexample.ads.AdKeyPosition
import gs.ad.gsadsexample.sub.ConsumableProductId
import gs.ad.gsadsexample.sub.SubscriptionProductId
import gs.ad.utils.ads.AdmBuilder
import gs.ad.utils.ads.AdmConfig
import gs.ad.utils.google_iab.BillingClientLifecycle
import gs.ad.utils.utils.GlobalVariables

object AppController {
    @SuppressLint("StaticFieldLeak")
    lateinit var admBuilder: AdmBuilder
    @SuppressLint("StaticFieldLeak")
    var billingClientLifecycle: BillingClientLifecycle? = null

    fun init(context: Context, resources: Resources) {
        billingClientLifecycle = BillingClientLifecycle.build(context){
            licenseKey = context.getString(R.string.license_key)
            consumableIds = enumValues<ConsumableProductId>().map { it.id }
            subscriptionIds = enumValues<SubscriptionProductId>().map { it.id }
        }

        admBuilder = AdmBuilder.build(context) {
            keyShowOpen = AdKeyPosition.AppOpenAd_App_From_Background.name
            config = AdmConfig(
                listBannerAdUnitID = resources.getStringArray(R.array.banner_ad_unit_id).toList(),
                listInterstitialAdUnitID = resources.getStringArray(R.array.interstitial_ad_unit_id).toList(),
                listRewardAdUnitID = resources.getStringArray(R.array.reward_ad_unit_id).toList(),
                listNativeAdUnitID = resources.getStringArray(R.array.native_ad_unit_id).toList(),
                listOpenAdUnitID = resources.getStringArray(R.array.open_ad_unit_id).toList(),
            )
            billingClient = billingClientLifecycle
        }

        enumValues<AdKeyPosition>().forEach {
            GlobalVariables.AdsKeyPositionAllow[it.name] = true
        }
    }

    fun terminateApp(){
        admBuilder.resetCounterAds(AdKeyPosition.InterstitialAd_ScMain.name)
    }
}