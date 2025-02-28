package gs.ad.utils.ads.format

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import gs.ad.utils.ads.AdmMachine
import gs.ad.utils.ads.TYPE_ADS
import gs.ad.utils.ads.error.AdmErrorType
import gs.ad.utils.utils.NetworkUtil
import gs.ad.utils.utils.PreferencesManager

internal class AdmInterstitialAd(
    private val context: Context,
    private val admMachine: AdmMachine,
    listInterstitialAdUnitID: List<String>
) : FullScreenContentCallback() {
    private var mListAd: HashMap<Int, InterstitialAd?> = HashMap()
    private var currentID: Int = 0

    private val listInterstitialAdUnitId: List<String> = listInterstitialAdUnitID
    private var keyPosition: String = ""

    private var countTier = 0

    fun loadAds(id: Int) {
        currentID = id

        if (listInterstitialAdUnitId.isEmpty()) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.InterstitialAd,
                keyPosition,
                AdmErrorType.LIST_AD_ID_IS_EMPTY,
                null
            )
            return
        }

        if (id >= listInterstitialAdUnitId.count()) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.InterstitialAd,
                keyPosition,
                AdmErrorType.AD_ID_IS_NOT_EXIST,
                null
            )
            return
        }

        if (!NetworkUtil.isNetworkAvailable(context)) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.InterstitialAd,
                keyPosition,
                AdmErrorType.NETWORK_IS_NOT_AVAILABLE,
                null
            )
            return
        }

        if (admMachine.getCurrentActivity().isFinishing) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.InterstitialAd,
                keyPosition,
                AdmErrorType.ACTIVITY_IS_FINISHING,
                null
            )
            return
        }

        if (admMachine.getCurrentActivity().isDestroyed) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.InterstitialAd,
                keyPosition,
                AdmErrorType.ACTIVITY_IS_DESTROYED,
                null
            )
            return
        }

        if (mListAd[id] != null) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.InterstitialAd,
                keyPosition,
                AdmErrorType.AD_IS_EXISTED,
                null
            )
            return
        }

        if (PreferencesManager.getInstance().isSUB()) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.InterstitialAd,
                keyPosition,
                AdmErrorType.CLIENT_HAVE_SUB,
                null
            )
            return
        }

        if (PreferencesManager.getInstance().isRemoveAds()) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.InterstitialAd,
                keyPosition,
                AdmErrorType.CLIENT_HAVE_BEEN_REMOVED_AD,
                null
            )
            return
        }

        val act = admMachine.getCurrentActivity()
        val adRequest = AdRequest.Builder().build()

        val unitAdId = if (id == -1) countTier else id
        InterstitialAd.load(
            act, listInterstitialAdUnitId[unitAdId], adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    // The mInterstitialAd reference will be null until
                    // an ad is loaded.
                    mListAd[id] = interstitialAd
                    mListAd[id]?.fullScreenContentCallback = this@AdmInterstitialAd
                    Log.d(TAG, "InterstitialAd onAdLoaded")
                    admMachine.onAdLoaded(TYPE_ADS.InterstitialAd, keyPosition)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    // Handle the error
                    Log.d(TAG, "InterstitialAd $loadAdError")
                    mListAd[id] = null
                    admMachine.onAdFailToLoaded(
                        TYPE_ADS.InterstitialAd,
                        keyPosition,
                        AdmErrorType.OTHER,
                        loadAdError.message
                    )
                    //closeAds();
                }
            })

        if (countTier >= listInterstitialAdUnitId.size - 1) {
            countTier = 0
        } else {
            countTier++
        }
    }

    private fun closeAds() {
        admMachine.getCurrentActivity().runOnUiThread {
            admMachine.closeAds(
                TYPE_ADS.InterstitialAd,
                keyPosition
            )
        }
        keyPosition = ""
    }

    override fun onAdClicked() {
        // Called when a click is recorded for an ad.
        Log.d(TAG, "Ad was clicked.")
        admMachine.onAdClicked(TYPE_ADS.InterstitialAd, keyPosition)
    }

    override fun onAdDismissedFullScreenContent() {
        // Called when ad is dismissed.
        // Set the ad reference to null so you don't show the ad a second time.
        Log.d(TAG, "Ad dismissed fullscreen content.")
        mListAd[currentID] = null
        closeAds()
//        loadAds(currentID)
    }

    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
        // Called when ad fails to show.
        Log.e(TAG, "Ad failed to show fullscreen content.")
        mListAd[currentID] = null
        admMachine.onAdFailToLoaded(
            TYPE_ADS.InterstitialAd,
            keyPosition,
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
        admMachine.onAdShow(TYPE_ADS.InterstitialAd, keyPosition)
    }

    fun showAds(id: Int, keyPos: String) {
        currentID = id
        val act = admMachine.getCurrentActivity() ?: return
        if (PreferencesManager.getInstance().isSUB() || PreferencesManager.getInstance()
                .isRemoveAds()
        ) {
            closeAds()
            return
        }

        admMachine.showAds()
        keyPosition = keyPos
        if (canShowAds()) {
            mListAd[currentID]?.show(act)
        } else {
            Log.d(TAG, "The interstitial ad wasn't ready yet.")
//          adsManager.activity.closeAds(TYPE_ADS.InterstitialAd);
//          loadAds();
        }
    }

    fun canShowAds(): Boolean {
        return mListAd[currentID] != null
    }

    companion object {
        const val TAG = "AdmInterstitialAd"
    }
}
