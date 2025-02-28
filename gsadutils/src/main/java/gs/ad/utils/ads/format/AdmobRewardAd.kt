package gs.ad.utils.ads.format

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import gs.ad.utils.ads.AdmMachine
import gs.ad.utils.ads.TYPE_ADS
import gs.ad.utils.ads.error.AdmErrorType
import gs.ad.utils.utils.NetworkUtil
import gs.ad.utils.utils.PreferencesManager

internal class AdmobRewardAd(
    private val context: Context,
    private val admMachine: AdmMachine,
    listRewardAdUnitID: List<String>
) : FullScreenContentCallback() {
    private var mListAd: HashMap<Int, RewardedAd?> = HashMap()
    private var currentID: Int = 0

    private var keyPosition: String = ""
    private var isReward = false
    private var countTier = 0

    private val listRewardAdUnitId: List<String> = listRewardAdUnitID

    fun loadAds(id: Int) {
        currentID = id
        isReward = false

        if (listRewardAdUnitId.isEmpty()) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.RewardAd,
                keyPosition,
                AdmErrorType.LIST_AD_ID_IS_EMPTY,
                null
            )
            return
        }

        if (id >= listRewardAdUnitId.count()) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.RewardAd,
                keyPosition,
                AdmErrorType.AD_ID_IS_NOT_EXIST,
                null
            )
            return
        }

        if (!NetworkUtil.isNetworkAvailable(context)) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.RewardAd,
                keyPosition,
                AdmErrorType.NETWORK_IS_NOT_AVAILABLE,
                null
            )
            return
        }

        if (admMachine.getCurrentActivity().isFinishing) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.RewardAd,
                keyPosition,
                AdmErrorType.ACTIVITY_IS_FINISHING,
                null
            )
            return
        }

        if (admMachine.getCurrentActivity().isDestroyed) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.RewardAd,
                keyPosition,
                AdmErrorType.ACTIVITY_IS_DESTROYED,
                null
            )
            return
        }

        if (mListAd[id] != null) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.RewardAd,
                keyPosition,
                AdmErrorType.AD_IS_EXISTED,
                null
            )
            return
        }

//        if(PreferencesManager.getInstance().isSUB()){
//            admMachine.onAdFailToLoaded(TYPE_ADS.RewardAd, keyPosition, AdmErrorType.CLIENT_HAVE_SUB, null)
//            return
//        }

        if (PreferencesManager.getInstance().isRemoveAds()) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.RewardAd,
                keyPosition,
                AdmErrorType.CLIENT_HAVE_BEEN_REMOVED_AD,
                null
            )
            return
        }

        val act = admMachine.getCurrentActivity()
        val adRequest = AdRequest.Builder().build()

        val unitAdId = if (id == -1) countTier else id
        RewardedAd.load(
            act, listRewardAdUnitId[unitAdId],
            adRequest, object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    // Handle the error.
                    Log.d(TAG, loadAdError.toString())
                    admMachine.onAdFailToLoaded(
                        TYPE_ADS.RewardAd,
                        keyPosition,
                        AdmErrorType.OTHER,
                        loadAdError.message
                    )
                    mListAd[id] = null
                    closeAds()
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    mListAd[id] = ad
                    mListAd[id]?.fullScreenContentCallback = this@AdmobRewardAd
                    admMachine.onAdLoaded(TYPE_ADS.RewardAd, keyPosition)
                    Log.d(TAG, "Ad was loaded.")
                }
            })

        if (countTier >= listRewardAdUnitId.size - 1) {
            countTier = 0
        } else {
            countTier++
        }
    }

    private fun closeAds() {
        admMachine.getCurrentActivity().runOnUiThread {
            admMachine.closeAds(
                TYPE_ADS.RewardAd,
                keyPosition
            )
        }
        keyPosition = ""
    }

    override fun onAdClicked() {
        // Called when a click is recorded for an ad.
        Log.d(TAG, "Ad was clicked.")
        admMachine.onAdClicked(TYPE_ADS.RewardAd, keyPosition)
    }

    override fun onAdDismissedFullScreenContent() {
        // Called when ad is dismissed.
        // Set the ad reference to null so you don't show the ad a second time.
        Log.d(TAG, "Ad dismissed fullscreen content.")
        mListAd[currentID] = null
        admMachine.getCurrentActivity().runOnUiThread {
            if (isReward) admMachine.haveReward(TYPE_ADS.RewardAd, keyPosition)
            else admMachine.notHaveReward(TYPE_ADS.RewardAd, keyPosition)
            admMachine.closeAds(TYPE_ADS.RewardAd, keyPosition)
        }

//        loadAds(currentID)
    }

    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
        // Called when ad fails to show.
        Log.e(TAG, "Ad failed to show fullscreen content.")
        mListAd[currentID] = null
        admMachine.onAdFailToLoaded(
            TYPE_ADS.RewardAd,
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
    }

    fun showAds(id: Int, keyPos: String) {
        currentID = id
        val act = admMachine.getCurrentActivity()
        keyPosition = keyPos
        admMachine.showAds()
        //        if(PreferencesManager.getInstance().isSUB() || PreferencesManager.getInstance().isRemoveAds() )return;
        if (canShowAds()) {
            mListAd[currentID]?.show(
                act
            ) { isReward = true }
        } else {
            Log.d("TAG", "The reward ad wasn't ready yet.")
            //            adsManager.activity.closeAds(TYPE_ADS.RewardAd);
//            loadAds();
        }
    }

    fun canShowAds(): Boolean {
        return mListAd[currentID] != null
    }

    companion object {
        const val TAG = "AdmRewardAd"
    }
}