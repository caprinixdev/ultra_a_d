package gs.ad.gsadsexample

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.multidex.MultiDexApplication
import gs.ad.gsadsexample.sub.ConsumableProductId
import gs.ad.gsadsexample.sub.SubscriptionProductId
import gs.ad.utils.ads.AdmConfigAdId
import gs.ad.utils.ads.format.AdmOpenAd
import gs.ad.utils.google_iab.BillingClientLifecycle
import gs.ad.utils.utils.GlobalVariables
import gs.ad.utils.utils.PreferencesManager
import gs.ad.utils.utils.Prefs

class AppOwner: MultiDexApplication(), Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {
    private lateinit var admOpenAd: AdmOpenAd
    var mBillingClientLifecycle: BillingClientLifecycle? = null
    override fun onCreate() {
        super<MultiDexApplication>.onCreate()
        Prefs.init(applicationContext)

        registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        AdmConfigAdId.listBannerAdUnitID = resources.getStringArray(R.array.banner_ad_unit_id).toList()
        AdmConfigAdId.listInterstitialAdUnitID = resources.getStringArray(R.array.interstitial_ad_unit_id).toList()
        AdmConfigAdId.listRewardAdUnitID = resources.getStringArray(R.array.reward_ad_unit_id).toList()
        AdmConfigAdId.listNativeAdUnitID = resources.getStringArray(R.array.native_ad_unit_id).toList()
        AdmConfigAdId.listOpenAdUnitID = resources.getStringArray(R.array.open_ad_unit_id).toList()

        admOpenAd = AdmOpenAd(0 ,applicationContext)

        mBillingClientLifecycle = BillingClientLifecycle.build(applicationContext){
            licenseKey = context.getString(R.string.license_key)
            consumableIds = enumValues<ConsumableProductId>().map { it.id }
            subscriptionIds = enumValues<SubscriptionProductId>().map { it.id }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        Log.d(TAG, "owner onStart"  + GlobalVariables.isShowSub + "," + GlobalVariables.isShowPopup)
        mBillingClientLifecycle?.connectBillingConnector()
        if (GlobalVariables.isShowSub) return
        if (GlobalVariables.isShowPopup) return
        if (!GlobalVariables.canShowOpenAd) return

        admOpenAd.showAds()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        Log.d(TAG, "owner onDestroy")
        mBillingClientLifecycle?.destroyBillingConnector()
        super.onDestroy(owner)
    }

    private fun resetCounterAds(keyCount: String){
        PreferencesManager.getInstance().resetCounterAds(keyCount)
    }

    override fun onActivityCreated(p0: Activity, p1: Bundle?) {
    }

    override fun onActivityStarted(p0: Activity) {
        admOpenAd.currentActivity = p0
    }

    override fun onActivityResumed(p0: Activity) {
    }

    override fun onActivityPaused(p0: Activity) {
    }

    override fun onActivityStopped(p0: Activity) {
    }

    override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {
    }

    override fun onActivityDestroyed(p0: Activity) {
        if (p0 is SplashActivity){
            resetCounterAds(MainActivity.MAIN_COUNTER_AD)
        }
    }

    companion object{
        const val TAG = "MyAppOwner"
    }
}