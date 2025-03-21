package gs.ad.utils.ads.format

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback
import com.google.android.gms.ads.interstitial.InterstitialAd
import gs.ad.utils.ads.AdmConfigAdId
import gs.ad.utils.ads.GoogleMobileAdsConsentManager
import gs.ad.utils.ads.error.AdmErrorType
import gs.ad.utils.utils.GlobalVariables
import gs.ad.utils.utils.NetworkUtil
import gs.ad.utils.utils.PreferencesManager
import java.util.Date
import java.util.Timer

class AdmOpenAd(
    private var id: Int,
    private val context: Context,
    private var currentActivity: Activity? = null
) : FullScreenContentCallback() {
    var tag = 0


    private val googleMobileAdsConsentManager: GoogleMobileAdsConsentManager =
        GoogleMobileAdsConsentManager.getInstance(context)
    private var mOpenAd: AppOpenAd? = null

    var onAdFailToLoaded: ((AdmErrorType, String?) -> Unit?)? = null
    var onAdLoaded: (() -> Unit)? = null
    var onAdClosed: (() -> Unit)? = null
    var onAdClicked: (() -> Unit)? = null
    var onAdShow: (() -> Unit)? = null

    val hasUsing4Hours: Boolean get() = GlobalVariables.hasUsing4Hours

    private var loadTime: Long = 0
    private var isLoadingAd = false
    private var isShowingAd = false

    fun setNewId(newValue: Int) {
        id = newValue
    }

    fun setNewActivity(newValue: Activity){
        currentActivity = newValue
    }

    private fun loadAds() {
        if (AdmConfigAdId.listOpenAdUnitID.isEmpty()){
            onAdFailToLoaded?.invoke(AdmErrorType.LIST_AD_ID_IS_EMPTY, null)
            return
        }

        if (id >= AdmConfigAdId.listOpenAdUnitID.count()){
            onAdFailToLoaded?.invoke(AdmErrorType.AD_ID_IS_NOT_EXIST, null)
            return
        }

        if (mOpenAd != null) {
            onAdFailToLoaded?.invoke(AdmErrorType.AD_IS_EXISTED, null)
            return
        }
        if (!NetworkUtil.isNetworkAvailable(context)) {
            onAdFailToLoaded?.invoke(AdmErrorType.NETWORK_IS_NOT_AVAILABLE, null)
            return
        }

        if (PreferencesManager.getInstance().isSUB()) {
            onAdFailToLoaded?.invoke(AdmErrorType.CLIENT_HAVE_SUB, null)
            return
        }

        if (PreferencesManager.getInstance().isRemoveAds()) {
            onAdFailToLoaded?.invoke(AdmErrorType.CLIENT_HAVE_BEEN_REMOVED_AD, null)
            return
        }

        if (currentActivity == null){
            onAdFailToLoaded?.invoke(AdmErrorType.ACTIVITY_IS_NOT_AVAILABLE, null)
            return
        }

        if (isLoadingAd) {
            onAdFailToLoaded?.invoke(AdmErrorType.AD_IS_LOADING, null)
            return
        }
        isLoadingAd = true

        val adUnitId = AdmConfigAdId.getOpenAdUnitID(id)
        val act = currentActivity ?: return

        val adRequest = AdRequest.Builder().build()

        AppOpenAd.load(act, adUnitId, adRequest,
            object : AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    // Called when an app open ad has loaded.
                    Log.d(TAG, "AppOpenAd Ad was loaded.")
                    isLoadingAd = false
                    mOpenAd = ad
                    mOpenAd?.fullScreenContentCallback = this@AdmOpenAd
                    if (hasUsing4Hours) {
                        loadTime = Date().time
                    }

                    onAdLoaded?.invoke()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    // Called when an app open ad has failed to load.
                    Log.d(TAG, loadAdError.message)
                    isLoadingAd = false
                    resetAd()
                    onAdFailToLoaded?.invoke(
                        AdmErrorType.OTHER,
                        loadAdError.message
                    )
                    closeAds()
                }
            })
    }

    private fun closeAds() {
        isShowingAd = false
        onAdClosed?.invoke()
    }

    override fun onAdClicked() {
        // Called when a click is recorded for an ad.
        Log.d(TAG, "Ad was clicked.")
        onAdClicked?.invoke()
    }

    override fun onAdDismissedFullScreenContent() {
        // Called when ad is dismissed.
        // Set the ad reference to null so you don't show the ad a second time.
        Log.d(TAG, "Ad dismissed fullscreen content.")

        resetAd()
        closeAds()
    }

    fun resetAd() {
        if (!hasUsing4Hours) {
            mOpenAd = null
        }
    }

    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
        // Called when ad fails to show.
        Log.e(TAG, "Ad failed to show fullscreen content.")
        // Dung 4 tieng command mAppOpenAd = null;
        resetAd()
        onAdFailToLoaded?.invoke(
            AdmErrorType.OTHER,
            adError.message
        )
    }

    override fun onAdImpression() {
        // Called when an impression is recorded for an ad.
        Log.d(TAG, "Ad recorded an impression.")
    }

    override fun onAdShowedFullScreenContent() {
        // Called when ad is shown.
        Log.d(TAG, "Ad showed fullscreen content.")
        onAdShow?.invoke()
    }

    fun showAds() {
        if (PreferencesManager.getInstance().isSUB() || PreferencesManager.getInstance().isRemoveAds()
            || !googleMobileAdsConsentManager.canRequestAds || currentActivity == null) {
            closeAds()
            return
        }

        if (isShowingAd) {
            onAdFailToLoaded?.invoke(AdmErrorType.AD_IS_SHOWING, null)
            return
        }
        isShowingAd = true

        val act = currentActivity ?: return
        if (isAdAvailable()) {
            act.runOnUiThread {
                mOpenAd?.show(act)
            }
        } else {
            Log.d(TAG, "The open ad wasn't ready yet.")
            closeAds()
            loadAds()
        }
    }

    private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
        val dateDifference = Date().time - loadTime
        val numMilliSecondsPerHour = (60 * 60 * 1000).toLong()
        Log.d(
            TAG,
            "wasLoadTimeLessThanNHoursAgo : " + dateDifference + ", " + (numMilliSecondsPerHour * numHours)
        )
        return (dateDifference < (numMilliSecondsPerHour * numHours))
    }

    private fun isAdAvailable(): Boolean {
        return if (!hasUsing4Hours) {
            mOpenAd != null
        } else {
            mOpenAd != null && wasLoadTimeLessThanNHoursAgo(4)
        }
    }

    companion object {
        const val TAG = "AdmOpenAd"
    }
}