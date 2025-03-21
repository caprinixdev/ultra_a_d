package gs.ad.utils.ads.format

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.Window
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import gs.ad.utils.ads.AdmConfigAdId
import gs.ad.utils.ads.GoogleMobileAdsConsentManager
import gs.ad.utils.ads.error.AdmErrorType
import gs.ad.utils.databinding.PopupLoadAdsBinding
import gs.ad.utils.utils.GlobalVariables
import gs.ad.utils.utils.NetworkUtil
import gs.ad.utils.utils.PreferencesManager
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.schedule
import kotlin.concurrent.timerTask

class AdmInterstitialAd(
    private var id: Int,
    private var currentActivity: Activity
) : FullScreenContentCallback() {
    var tag = 0

    private val googleMobileAdsConsentManager: GoogleMobileAdsConsentManager =
        GoogleMobileAdsConsentManager.getInstance(currentActivity.applicationContext)
    private var mInterstitialAd: InterstitialAd? = null
    private var dialogLoadAds: Dialog? = null

    var onAdFailToLoaded: ((AdmErrorType, String?) -> Unit?)? = null
    var onAdLoaded: (() -> Unit)? = null
    var onAdClosed: (() -> Unit)? = null
    var onAdClicked: (() -> Unit)? = null
    var onAdShow: (() -> Unit)? = null
    private var isLoadingAd = false

    private var timer : Timer? = Timer()
    private var timerTask : TimerTask? = null
    private fun createTimerTask() = object : TimerTask() {
        override fun run() {
            showAds()
        }
    }
    private var isShowPopup: Boolean = false
    private var isCountAd: Boolean = false

    fun setNewId(newValue: Int) {
        id = newValue
    }

    fun setNewActivity(newValue: Activity){
        currentActivity = newValue
    }

    private fun loadAds() {
        if (AdmConfigAdId.listInterstitialAdUnitID.isEmpty()){
            onAdFailToLoaded?.invoke(AdmErrorType.LIST_AD_ID_IS_EMPTY, null)
            return
        }

        if (id >= AdmConfigAdId.listInterstitialAdUnitID.count()){
            onAdFailToLoaded?.invoke(AdmErrorType.AD_ID_IS_NOT_EXIST, null)
            return
        }

        if (mInterstitialAd != null) {
            onAdFailToLoaded?.invoke(AdmErrorType.AD_IS_EXISTED, null)
            return
        }
        if (!NetworkUtil.isNetworkAvailable(currentActivity.applicationContext)) {
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

        if (isLoadingAd) {
            onAdFailToLoaded?.invoke(AdmErrorType.AD_IS_LOADING, null)
            return
        }
        isLoadingAd = true

        val adUnitId = AdmConfigAdId.getInterstitialAdUnitID(id)
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            currentActivity, adUnitId, adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    // The mInterstitialAd reference will be null until
                    // an ad is loaded.
                    Log.d(TAG, "InterstitialAd onAdLoaded")
                    isLoadingAd = false
                    mInterstitialAd = interstitialAd
                    mInterstitialAd?.fullScreenContentCallback = this@AdmInterstitialAd
                    onAdLoaded?.invoke()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    // Handle the error
                    Log.d(TAG, "InterstitialAd $loadAdError")
                    isLoadingAd = false
                    mInterstitialAd = null
                    onAdFailToLoaded?.invoke(AdmErrorType.OTHER, loadAdError.message)
                    closeAds()
                }
            })
    }

    private fun closeAds() {
        GlobalVariables.isShowPopup = false
        resetTimer()
        onAdClosed?.invoke()
    }

    private fun resetTimer(){
        isCountAd = false
        isShowPopup = false
        timerTask?.cancel()
        timerTask = null
        timer?.purge()
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
        mInterstitialAd = null
        closeAds()
//        loadAds(currentID)
    }

    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
        // Called when ad fails to show.
        Log.e(TAG, "Ad failed to show fullscreen content.")
        mInterstitialAd = null
        onAdFailToLoaded?.invoke(AdmErrorType.OTHER, adError.message)
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

    private fun showAds() {
        if (PreferencesManager.getInstance().isSUB() || PreferencesManager.getInstance().isRemoveAds()) {
            closeAds()
            return
        }

        if (canShowAds()) {
            resetTimer()
            currentActivity.runOnUiThread {
                delEventDialogLoadAds()
                mInterstitialAd?.show(currentActivity)
            }
        } else {
            Log.d(TAG, "The interstitial ad wasn't ready yet.")
            //adsManager.activity.closeAds(TYPE_ADS.InterstitialAd);
            //loadAds();
        }
    }

    private fun canShowAds(): Boolean {
        return mInterstitialAd != null
    }

    fun countToShowAds(
        keyCounterAd: String,
        startAds: Int,
        loopAds: Int,
        onLoadingAd: (() -> Unit)?
    ) {
        if (PreferencesManager.getInstance().isSUB() || PreferencesManager.getInstance().isRemoveAds()) {
            closeAds()
            return
        }

        if (isCountAd) {
            onAdFailToLoaded?.invoke(AdmErrorType.AD_IS_LOADING, null)
            return
        }
        isCountAd = true

        var countAds = PreferencesManager.getInstance().getCounterAds(keyCounterAd)
        countAds += 1
        PreferencesManager.getInstance().saveCounterAds(keyCounterAd, countAds)
        val isShowAds = if (countAds < startAds) {
            false
        } else if (countAds == startAds) {
            true
        } else {
            (countAds - startAds) % loopAds == 0
        }

        if (isShowAds) {
            showPopupLoadAds(onLoadingAd)
        } else {
            closeAds()
        }
    }

    fun showPopupLoadAds(onLoadingAd: (() -> Unit)?) {
        if (!googleMobileAdsConsentManager.canRequestAds
            || PreferencesManager.getInstance().isRemoveAds()
            || PreferencesManager.getInstance().isSUB()
        ) {
            closeAds()
            return
        }

        if (isShowPopup) {
            onAdFailToLoaded?.invoke(AdmErrorType.AD_IS_LOADING, null)
            return
        }
        isShowPopup = true

        onLoadingAd?.invoke()
        GlobalVariables.isShowPopup = true

        dialogLoadAds = Dialog(currentActivity)
        dialogLoadAds?.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialogLoadAds?.setCancelable(false)
        dialogLoadAds?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val binding = PopupLoadAdsBinding.inflate(dialogLoadAds!!.layoutInflater)
        dialogLoadAds?.setContentView(binding.getRoot())

//        binding.animationView.spin();

        loadAds()
        timerTask = createTimerTask()
        timer?.schedule(timerTask, 0L, 1000L)

        dialogLoadAds?.show()
    }

    private fun delEventDialogLoadAds() {
        val dl= dialogLoadAds ?: return
        if (dl.isShowing) {
            dl.dismiss()
            dialogLoadAds = null
        }
    }

//    private object Holder { val INSTANCE = AdmInterstitialAd() }

    companion object {
        const val TAG = "AdmInterstitialAd"
//        @JvmStatic
//        fun getInstance(): AdmInterstitialAd{
//            return Holder.INSTANCE
//        }
    }
}
