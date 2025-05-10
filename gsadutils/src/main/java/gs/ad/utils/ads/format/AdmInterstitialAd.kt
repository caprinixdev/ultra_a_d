package gs.ad.utils.ads.format

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.Window
import androidx.collection.CircularArray
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

class AdmInterstitialAd(
    private var id: Int,
    private var currentActivity: Activity,
    private var listAdCircularArray: List<String>? = null
) : FullScreenContentCallback() {
    var tag = 0

    private val googleMobileAdsConsentManager: GoogleMobileAdsConsentManager =
        GoogleMobileAdsConsentManager.getInstance(currentActivity.applicationContext)
    private var mInterstitialAd: InterstitialAd? = null
    private var dialogLoadAds: Dialog? = null

    var onAdFailToLoaded: ((AdmErrorType, String?, Int) -> Unit?)? = null
    var onAdLoaded: ((Int) -> Unit)? = null
    var onAdClosed: ((Int) -> Unit)? = null
    var onAdClicked: ((Int) -> Unit)? = null
    var onAdShow: ((Int) -> Unit)? = null
    private var isLoadingAd = false

    private var timer: Timer? = Timer()
    private var timerTask: TimerTask? = null
    private fun createTimerTask() = object : TimerTask() {
        override fun run() {
            showAds()
        }
    }

    private var isShowPopup: Boolean = false
    private var isCountAd: Boolean = false
    private var countTier: Int = 0

    fun setNewId(newValue: Int) {
        id = newValue
    }

    fun setNewActivity(newValue: Activity) {
        currentActivity = newValue
    }

    private fun getAdUnitId() : String{
        var adUnitId = ""
        if (id == -1){
            val listCount = listAdCircularArray ?: AdmConfigAdId.listInterstitialAdUnitID
            adUnitId = listCount[countTier]
            countTier = ++countTier % listCount.count()
        }else{
            adUnitId = AdmConfigAdId.getInterstitialAdUnitID(id)
        }
        return adUnitId
    }

    private fun loadAds() {
        if (AdmConfigAdId.listInterstitialAdUnitID.isEmpty()) {
            onAdFailToLoaded?.invoke(AdmErrorType.LIST_AD_ID_IS_EMPTY, null, tag)
            return
        }

        if (id >= AdmConfigAdId.listInterstitialAdUnitID.count()) {
            onAdFailToLoaded?.invoke(AdmErrorType.AD_ID_IS_NOT_EXIST, null, tag)
            return
        }

        if (mInterstitialAd != null) {
            onAdFailToLoaded?.invoke(AdmErrorType.AD_IS_EXISTED, null, tag)
            return
        }
        if (!NetworkUtil.isNetworkAvailable(currentActivity.applicationContext)) {
            onAdFailToLoaded?.invoke(AdmErrorType.NETWORK_IS_NOT_AVAILABLE, null, tag)
            return
        }

        if (PreferencesManager.getInstance().isSUB()) {
            onAdFailToLoaded?.invoke(AdmErrorType.CLIENT_HAVE_SUB, null, tag)
            return
        }

        if (PreferencesManager.getInstance().isRemoveAds()) {
            onAdFailToLoaded?.invoke(AdmErrorType.CLIENT_HAVE_BEEN_REMOVED_AD, null, tag)
            return
        }

        if (isLoadingAd) {
            onAdFailToLoaded?.invoke(AdmErrorType.AD_IS_LOADING, null, tag)
            return
        }
        isLoadingAd = true

        val adUnitId = getAdUnitId()

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
                    onAdLoaded?.invoke(tag)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    // Handle the error
                    Log.d(TAG, "InterstitialAd $loadAdError")
                    isLoadingAd = false
                    mInterstitialAd = null
                    onAdFailToLoaded?.invoke(AdmErrorType.OTHER, loadAdError.message, tag)
                    closeAds()
                }
            })
    }

    private fun closeAds() {
        delEventDialogLoadAds()
        GlobalVariables.isShowPopup = false
        resetTimer()
        onAdClosed?.invoke(tag)
    }

    private fun resetTimer() {
        isCountAd = false
        isShowPopup = false
        timerTask?.cancel()
        timerTask = null
        timer?.purge()
    }

    override fun onAdClicked() {
        // Called when a click is recorded for an ad.
        Log.d(TAG, "Ad was clicked.")
        onAdClicked?.invoke(tag)
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
        onAdFailToLoaded?.invoke(AdmErrorType.OTHER, adError.message, tag)
        closeAds()
    }

    override fun onAdImpression() {
        // Called when an impression is recorded for an ad.
        Log.d(TAG, "Ad recorded an impression.")
    }

    override fun onAdShowedFullScreenContent() {
        // Called when ad is shown.
        Log.d(TAG, "Ad showed fullscreen content.")
        onAdShow?.invoke(tag)
    }

    private fun showAds() {
        if (PreferencesManager.getInstance().isSUB() || PreferencesManager.getInstance()
                .isRemoveAds()
        ) {
            closeAds()
            return
        }

        if (canShowAds()) {
            currentActivity.runOnUiThread {
                resetTimer()
                delEventDialogLoadAds()
                mInterstitialAd?.show(currentActivity)
            }
        } else {
            Log.d(TAG, "The interstitial ad wasn't ready yet.")

            if (!NetworkUtil.isNetworkAvailable(currentActivity.applicationContext)) {
                onAdFailToLoaded?.invoke(AdmErrorType.NETWORK_IS_NOT_AVAILABLE, null, tag)
                closeAds()
            }

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
        if (PreferencesManager.getInstance().isSUB() || PreferencesManager.getInstance()
                .isRemoveAds()
        ) {
            closeAds()
            return
        }

        if (isCountAd) {
            onAdFailToLoaded?.invoke(AdmErrorType.AD_IS_LOADING, null, tag)
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
            onAdFailToLoaded?.invoke(AdmErrorType.AD_IS_LOADING, null, tag)
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
        currentActivity.runOnUiThread {
            val dl = dialogLoadAds ?: return@runOnUiThread
            if (dl.isShowing) {
                dl.dismiss()
                dialogLoadAds = null
            }
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
