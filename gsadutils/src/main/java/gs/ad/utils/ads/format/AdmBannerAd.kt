package gs.ad.utils.ads.format

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowMetrics
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import gs.ad.utils.R
import gs.ad.utils.ads.AdmConfigAdId
import gs.ad.utils.ads.GoogleMobileAdsConsentManager
import gs.ad.utils.ads.error.AdmErrorType
import gs.ad.utils.utils.NetworkUtil
import gs.ad.utils.utils.PreferencesManager

class AdmBannerAd(
    private var id: Int,
    private var currentActivity: Activity,
    private var listAdCircularArray: List<String>? = null,
    private val customSize: AdSize? = null
) : AdListener() {
    var tag = 0

    private val googleMobileAdsConsentManager: GoogleMobileAdsConsentManager =
        GoogleMobileAdsConsentManager.getInstance(currentActivity.applicationContext)

    var adContainerView: ConstraintLayout? = null
    var adView: AdView? = null

    var onAdFailToLoaded: ((AdmErrorType, String?, Int) -> Unit?)? = null
    var onAdLoaded: ((Int) -> Unit)? = null
    var onAdClosed: ((Int) -> Unit)? = null
    var onAdClicked: ((Int) -> Unit)? = null
    var onAdShow: ((Int) -> Unit)? = null
    private var isLoadingAd = false
    private var countTier: Int = 0

    fun setNewId(newValue: Int) {
        id = newValue
    }

    fun setNewActivity(newValue: Activity) {
        currentActivity = newValue
        adView?.destroy()
        adView = null
    }

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

    private fun getAdUnitId() : String{
        var adUnitId = ""
        if (id == -1){
            val listCount = listAdCircularArray ?: AdmConfigAdId.listBannerAdUnitID
            adUnitId = listCount[countTier]
            countTier = ++countTier % listCount.count()
        }else{
            adUnitId = AdmConfigAdId.getBannerAdUnitID(id)
        }
        return adUnitId
    }

    fun loadAd(adContainerView: ConstraintLayout, loadingLayout: Int? = null,isCollapsible: Boolean = true) {
        if (AdmConfigAdId.listBannerAdUnitID.isEmpty()) {
            onAdFailToLoaded?.invoke(AdmErrorType.LIST_AD_ID_IS_EMPTY, null, tag)
            return
        }

        if (id >= AdmConfigAdId.listBannerAdUnitID.count()) {
            onAdFailToLoaded?.invoke(AdmErrorType.AD_ID_IS_NOT_EXIST, null, tag)
            return
        }

        if (adView != null) {
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

        if (!googleMobileAdsConsentManager.canRequestAds) {
            onAdFailToLoaded?.invoke(AdmErrorType.UMP_IS_NOT_ACTIVE, null, tag)
            return
        }

        if (isLoadingAd) {
            onAdFailToLoaded?.invoke(AdmErrorType.AD_IS_LOADING, null, tag)
            return
        }
        isLoadingAd = true

        val adUnitId = getAdUnitId()

        // Create a new ad view.
        val adView = AdView(currentActivity)
        val size =if (isCollapsible) adSize else customSize ?: adSize
        adView.adUnitId = adUnitId
        adView.adListener = this

//        val textView = TextView(act)
//        textView.id = View.generateViewId()
//        textView.textSize = (adSize.height / 4.0).toFloat()
//        textView.text = act.resources.getString(R.string.loading_ads)
//        textView.gravity = Gravity.CENTER

        // Create a new ad loader.
        // Update banner
        val contentLoader = if (loadingLayout == null) {
            LayoutInflater.from(currentActivity)
                .inflate(R.layout.loading_banner, adContainerView, false)
        } else {
            LayoutInflater.from(currentActivity)
                .inflate(loadingLayout, adContainerView, false)
        }

        val layoutParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            size.getHeightInPixels(currentActivity)
        )
        contentLoader.layoutParams = layoutParams
        // Replace ad container with new ad view.
        adContainerView.removeAllViews()
        adContainerView.addView(contentLoader)
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
        adView.setAdSize(size)
        val adBuilder = AdRequest.Builder()
        val adRequest = adBuilder.apply {
            if (isCollapsible) {
                val extras = Bundle().apply { putString("collapsible", "bottom") }
                addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
            }
        }.build()
        adView.loadAd(adRequest)
        adView.visibility = View.VISIBLE
        adContainerView.visibility = View.VISIBLE
//        textView.visibility = View.VISIBLE
        this.adContainerView = adContainerView
        this.adView = adView
    }

    fun resumeBanner() {
        adView?.resume()
    }

    fun pauseBanner() {
        adView?.pause()
    }

    fun destroyBanner() {
        adView?.destroy()
        // Update banner
        adContainerView?.visibility = View.GONE

        adView = null
    }

    override fun onAdOpened() {
        super.onAdOpened()
        Log.d(TAG, "bannerView onAdOpened")
    }

    override fun onAdImpression() {
        super.onAdImpression()
        Log.d(TAG, "bannerView onAdShow")
        onAdShow?.invoke(tag)
    }

    override fun onAdLoaded() {
        super.onAdLoaded()
        Log.d(TAG, "bannerView onAdLoaded")
        isLoadingAd = false
        // Update banner
        adContainerView?.removeAllViews()
        adContainerView?.addView(adView)

        adContainerView?.visibility = View.VISIBLE
        adView?.visibility = View.VISIBLE
        onAdLoaded?.invoke(tag)
    }

    override fun onAdClicked() {
        // Called when a click is recorded for an ad.
        Log.d(TAG, "bannerView onAdClicked")
        onAdClicked?.invoke(tag)
    }

    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
        super.onAdFailedToLoad(loadAdError)
        Log.d(TAG, "bannerView " + loadAdError.message)
        isLoadingAd = false
        // Update banner
        adContainerView?.removeAllViews()
        adContainerView?.visibility = View.GONE

        onAdFailToLoaded?.invoke(AdmErrorType.OTHER, loadAdError.message, tag)
    }

    companion object {
        const val TAG = "AdmBannerAd"
    }
}