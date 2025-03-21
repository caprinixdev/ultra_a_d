package gs.ad.utils.ads.format

import android.app.Activity
import android.os.Build
import android.util.Log
import android.view.View
import android.view.WindowMetrics
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import gs.ad.utils.ads.AdmConfigAdId
import gs.ad.utils.ads.GoogleMobileAdsConsentManager
import gs.ad.utils.ads.error.AdmErrorType
import gs.ad.utils.utils.NetworkUtil
import gs.ad.utils.utils.PreferencesManager

class AdmBannerAd(
    private val id: Int,
    private val currentActivity: Activity,
    private val customSize: AdSize? = null
) : AdListener() {
    var tag = 0

    private val googleMobileAdsConsentManager: GoogleMobileAdsConsentManager =
        GoogleMobileAdsConsentManager.getInstance(currentActivity.applicationContext)

    var adContainerView: ConstraintLayout? = null
    var adView: AdView? = null

    var onAdFailToLoaded: ((AdmErrorType, String?) -> Unit?)? = null
    var onAdLoaded: (() -> Unit)? = null
    var onAdClosed: (() -> Unit)? = null
    var onAdClicked: (() -> Unit)? = null
    var onAdShow: (() -> Unit)? = null
    private var isLoadingAd = false

    // [START get_ad_size]
    // Get the ad size with screen width.
    private val adSize: AdSize
        get() {
            val act = currentActivity
            val displayMetrics = act.resources.displayMetrics
            val adWidthPixels =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val windowMetrics: WindowMetrics = act.windowManager.currentWindowMetrics
                    windowMetrics.bounds.width()
                } else {
                    displayMetrics.widthPixels
                }
            val density = displayMetrics.density
            val adWidth = (adWidthPixels / density).toInt()
            return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(act, adWidth)
        }

    fun loadAd(adContainerView: ConstraintLayout) {
        if (AdmConfigAdId.listBannerAdUnitID.isEmpty()){
            onAdFailToLoaded?.invoke(AdmErrorType.LIST_AD_ID_IS_EMPTY, null)
            return
        }

        if (id >= AdmConfigAdId.listBannerAdUnitID.count()){
            onAdFailToLoaded?.invoke(AdmErrorType.AD_ID_IS_NOT_EXIST, null)
            return
        }

        if (adView != null) {
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

        if (!googleMobileAdsConsentManager.canRequestAds){
            onAdFailToLoaded?.invoke(AdmErrorType.UMP_IS_NOT_ACTIVE, null)
            return
        }

        if (isLoadingAd) {
            onAdFailToLoaded?.invoke(AdmErrorType.AD_IS_LOADING, null)
            return
        }
        isLoadingAd = true


        val adUnitId = AdmConfigAdId.getBannerAdUnitID(id)
        // Create a new ad view.
        val adView = AdView(currentActivity)
        val size = customSize ?: adSize
        adView.setAdSize(size)
        adView.adUnitId = adUnitId
        adView.adListener = this

//        val textView = TextView(act)
//        textView.id = View.generateViewId()
//        textView.textSize = (adSize.height / 4.0).toFloat()
//        textView.text = act.resources.getString(R.string.loading_ads)
//        textView.gravity = Gravity.CENTER

        // Replace ad container with new ad view.
        adContainerView.removeAllViews()
        adContainerView.addView(adView)
//        adContainerView.parent?.let {
//            (it as ViewGroup).addView(textView)
//        }

//        textView.updateLayoutParams<ConstraintLayout.LayoutParams> {
//            height = 50
//            width = 0
//            startToStart = adContainerView.id
//            endToEnd = adContainerView.id
//            topToTop = adContainerView.id
//            bottomToBottom = adContainerView.id
//        }
        // Start loading the ad in the background.
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
        adView.visibility = View.VISIBLE
        adContainerView.visibility = View.VISIBLE
//        textView.visibility = View.VISIBLE
        this.adContainerView = adContainerView
        this.adView = adView
    }

    fun resumeBanner(){
        adView?.resume()
    }

    fun pauseBanner(){
        adView?.pause()
    }

    fun destroyBanner(){
        adView?.destroy()
        adView = null
    }

    override fun onAdOpened() {
        super.onAdOpened()
        Log.d(TAG, "bannerView onAdOpened")
    }

    override fun onAdImpression() {
        super.onAdImpression()
        Log.d(TAG, "bannerView onAdShow")
        onAdShow?.invoke()
    }

    override fun onAdLoaded() {
        super.onAdLoaded()
        Log.d(TAG, "bannerView onAdLoaded")
        isLoadingAd = false
        adContainerView?.visibility = View.VISIBLE
        adView?.visibility = View.VISIBLE
        onAdLoaded?.invoke()
    }

    override fun onAdClicked() {
        // Called when a click is recorded for an ad.
        Log.d(TAG, "bannerView onAdClicked")
        onAdClicked?.invoke()
    }

    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
        super.onAdFailedToLoad(loadAdError)
        Log.d(TAG, "bannerView " + loadAdError.message)
        isLoadingAd = false
        onAdFailToLoaded?.invoke(AdmErrorType.OTHER, loadAdError.message)
    }

    companion object {
        const val TAG = "AdmBannerAd"
    }
}