package gs.ad.utils.ads.format

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback
import gs.ad.utils.ads.AdmMachine
import gs.ad.utils.ads.TYPE_ADS
import gs.ad.utils.ads.error.AdmErrorType
import gs.ad.utils.utils.GlobalVariables
import gs.ad.utils.utils.NetworkUtil
import gs.ad.utils.utils.PreferencesManager
import java.util.Date

internal class AdmOpenAd(
    var context: Context,
    private val admMachine: AdmMachine,
    listOpenAdUnitID: List<String>
) : FullScreenContentCallback() {
    private var mListAd: HashMap<Int, AppOpenAd?> = HashMap()
    private var currentID: Int = 0
    private var keyPosition: String = ""

    val hasUsing4Hours: Boolean
        get() = GlobalVariables.hasUsing4Hours

    private var countTier = 0
    private var loadTime: Long = 0

    private val listOpenAdId: List<String> = listOpenAdUnitID

    fun loadAds(id: Int) {
        currentID = id

        if (listOpenAdId.isEmpty()) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.OpenAd,
                keyPosition,
                AdmErrorType.LIST_AD_ID_IS_EMPTY,
                null
            )
            return
        }

        if (id >= listOpenAdId.count()) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.OpenAd,
                keyPosition,
                AdmErrorType.AD_ID_IS_NOT_EXIST,
                null
            )
            return
        }

        if (!NetworkUtil.isNetworkAvailable(context)) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.OpenAd,
                keyPosition,
                AdmErrorType.NETWORK_IS_NOT_AVAILABLE,
                null
            )
            return
        }

        if (admMachine.getCurrentActivity().isFinishing) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.OpenAd,
                keyPosition,
                AdmErrorType.ACTIVITY_IS_FINISHING,
                null
            )
            return
        }

        if (admMachine.getCurrentActivity().isDestroyed) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.OpenAd,
                keyPosition,
                AdmErrorType.ACTIVITY_IS_DESTROYED,
                null
            )
            return
        }

        if (mListAd[id] != null) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.OpenAd,
                keyPosition,
                AdmErrorType.AD_IS_EXISTED,
                null
            )
            return
        }

        if (PreferencesManager.getInstance().isSUB()) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.OpenAd,
                keyPosition,
                AdmErrorType.CLIENT_HAVE_SUB,
                null
            )
            return
        }

        if (PreferencesManager.getInstance().isRemoveAds()) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.OpenAd,
                keyPosition,
                AdmErrorType.CLIENT_HAVE_BEEN_REMOVED_AD,
                null
            )
            return
        }

        val act = admMachine.getCurrentActivity()

        val adRequest = AdRequest.Builder().build()

        val unitAdId = if (id == -1) countTier else id
        AppOpenAd.load(
            act, listOpenAdId[unitAdId], adRequest,
            object : AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    // Called when an app open ad has loaded.
                    Log.d(TAG, "AppOpenAd Ad was loaded.")
                    mListAd[id] = ad
                    mListAd[id]?.fullScreenContentCallback = this@AdmOpenAd
                    if (hasUsing4Hours) {
                        loadTime = Date().time
                    }

                    admMachine.onAdLoaded(TYPE_ADS.OpenAd, keyPosition)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    // Called when an app open ad has failed to load.
                    Log.d(TAG, loadAdError.message)
                    resetAd()
                    admMachine.onAdFailToLoaded(
                        TYPE_ADS.OpenAd,
                        keyPosition,
                        AdmErrorType.OTHER,
                        loadAdError.message
                    )
                    //closeAds();
                }
            })

        if (countTier >= 2) {
            countTier = 0
        } else {
            countTier++
        }
    }

    private fun closeAds() {
        admMachine.getCurrentActivity().runOnUiThread {
            admMachine.closeAds(TYPE_ADS.OpenAd, keyPosition)
        }
        keyPosition = ""
    }

    override fun onAdClicked() {
        // Called when a click is recorded for an ad.
        Log.d(TAG, "Ad was clicked.")
        admMachine.onAdClicked(TYPE_ADS.OpenAd, keyPosition)
    }

    override fun onAdDismissedFullScreenContent() {
        // Called when ad is dismissed.
        // Set the ad reference to null so you don't show the ad a second time.
        Log.d(TAG, "Ad dismissed fullscreen content.")

        resetAd()
        closeAds()
    }

    fun resetAd() {
        if (currentID == -1) {
            // Dung 4 tieng command mAppOpenAd = null; va loadAds();
            if (!hasUsing4Hours) {
                mListAd[currentID] = null
            }
        } else {
            mListAd[currentID] = null
        }
    }

    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
        // Called when ad fails to show.
        Log.e(TAG, "Ad failed to show fullscreen content.")
        // Dung 4 tieng command mAppOpenAd = null;
        resetAd()
        admMachine.onAdFailToLoaded(
            TYPE_ADS.OpenAd,
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
        admMachine.onAdShow(TYPE_ADS.OpenAd, keyPosition)
    }

    fun showDefaultAds(id: Int, keyPosition: String) {
        currentID = id
        val act = admMachine.getCurrentActivity() ?: return
        if (PreferencesManager.getInstance().isSUB() || PreferencesManager.getInstance()
                .isRemoveAds()
        ) return

        this.keyPosition = keyPosition
        if (isAdAvailable()) {
            mListAd[currentID]?.show(act)
        } else {
            Log.d(TAG, "The open ad wasn't ready yet.")
            closeAds()
            loadAds(currentID)
        }
    }

    fun showAds(id: Int, keyPosition: String) {
        currentID = id
        val act = admMachine.getCurrentActivity() ?: return
        if (PreferencesManager.getInstance().isSUB() || PreferencesManager.getInstance()
                .isRemoveAds()
        ) return

        this.keyPosition = keyPosition
        if (mListAd[currentID] != null) {
            mListAd[currentID]?.show(act)
        } else {
            Log.d(TAG, "The open ad wasn't ready yet.")
//            closeAds()
//            loadAds(currentID)
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
            mListAd[currentID] != null
        } else {
            mListAd[currentID] != null && wasLoadTimeLessThanNHoursAgo(4)
        }
    }

    companion object {
        const val TAG = "AdmOpenAd"
    }
}