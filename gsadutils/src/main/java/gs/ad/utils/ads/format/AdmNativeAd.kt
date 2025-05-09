package gs.ad.utils.ads.format

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MediaAspectRatio
import com.google.android.gms.ads.VideoController
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import gs.ad.utils.R
import gs.ad.utils.ads.AdmConfigAdId
import gs.ad.utils.ads.GoogleMobileAdsConsentManager
import gs.ad.utils.ads.error.AdmErrorType
import gs.ad.utils.utils.NetworkUtil
import gs.ad.utils.utils.PreferencesManager

class AdmNativeAd(
    private var id: Int,
    private var currentActivity: Activity,
    private var isFullScreen: Boolean,
    private var listAdCircularArray: List<String>? = null,
    private val isMutedVideo: Boolean = true,
    private val mediaAspectRatio: Int = MediaAspectRatio.PORTRAIT,
    private val nativeAdOptions: Int = NativeAdOptions.ADCHOICES_TOP_LEFT
) : AdListener() {
    var tag: Int = 0

    private val googleMobileAdsConsentManager: GoogleMobileAdsConsentManager =
        GoogleMobileAdsConsentManager.getInstance(currentActivity)
    private val nameActivity: String = currentActivity.javaClass.simpleName

    var nativeAd: NativeAd? = null

    var onAdFailToLoaded: ((AdmErrorType, String?, Int) -> Unit?)? = null
    var onAdLoaded: ((Int) -> Unit)? = null
    var onAdClosed: ((Int) -> Unit)? = null
    var onAdClicked: ((Int) -> Unit)? = null
    var onAdShow: ((Int) -> Unit)? = null

    private var isLoadingAd = false
    private var isDestroyed = false
    private var countTier: Int = 0

    fun setNewId(newValue: Int) {
        id = newValue
    }

    fun setNewActivity(newValue: Activity){
        currentActivity = newValue
    }

    fun setNewIsFullScreen(newValue: Boolean){
        isFullScreen = newValue
    }

    private fun getAdUnitId() : String{
        var adUnitId = ""
        if (id == -1){
            val listCount = listAdCircularArray ?: AdmConfigAdId.listNativeAdUnitID
            adUnitId = listCount[countTier]
            countTier = ++countTier % listCount.count()
        }else{
            adUnitId = AdmConfigAdId.getNativeAdUnitID(id)
        }
        return adUnitId
    }

    fun preloadAd() {
        if (AdmConfigAdId.listNativeAdUnitID.isEmpty()) {
            onAdFailToLoaded?.invoke(AdmErrorType.LIST_AD_ID_IS_EMPTY, null, tag)
            return
        }

        if (id >= AdmConfigAdId.listNativeAdUnitID.count()) {
            onAdFailToLoaded?.invoke(AdmErrorType.AD_ID_IS_NOT_EXIST, null, tag)
            return
        }

        if (nativeAd != null) {
            onAdFailToLoaded?.invoke(AdmErrorType.AD_IS_EXISTED, null, tag)
            return
        }
        if (!NetworkUtil.isNetworkAvailable(currentActivity)) {
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
        Log.d(TAG, "id: $id")
        val adUnitId = getAdUnitId()

        val builder = AdLoader.Builder(currentActivity, adUnitId)

        builder.forNativeAd { nativeAd ->
            if (isDestroyed) {
                Log.d(TAG, "Native Ad destroy : $nameActivity")
                nativeAd.destroy()
                return@forNativeAd
            }
            this.nativeAd = nativeAd
        }

        val adOptions = NativeAdOptions.Builder()
            .setAdChoicesPlacement(nativeAdOptions)

        val videoOptions = VideoOptions.Builder().setStartMuted(isMutedVideo).build()
        adOptions.setVideoOptions(videoOptions)

        if (isFullScreen) {
            adOptions.setMediaAspectRatio(mediaAspectRatio)
        }

        val adOptionsBuild = adOptions.build()
        builder.withNativeAdOptions(adOptionsBuild)
        val adLoader = builder.withAdListener(this).build()
        adLoader.loadAd(AdRequest.Builder().build())
    }

    fun loadAd(adContainerView: ConstraintLayout, layoutNativeAdView: Int) {
        if (AdmConfigAdId.listNativeAdUnitID.isEmpty()) {
            onAdFailToLoaded?.invoke(AdmErrorType.LIST_AD_ID_IS_EMPTY, null, tag)
            return
        }

        if (id >= AdmConfigAdId.listNativeAdUnitID.count()) {
            onAdFailToLoaded?.invoke(AdmErrorType.AD_ID_IS_NOT_EXIST, null, tag)
            return
        }

        if (nativeAd != null) {
            onAdFailToLoaded?.invoke(AdmErrorType.AD_IS_EXISTED, null, tag)
            return
        }
        if (!NetworkUtil.isNetworkAvailable(currentActivity)) {
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
        Log.d(TAG, "id: $id")
        val adUnitId = getAdUnitId()

        val builder = AdLoader.Builder(currentActivity, adUnitId)

        builder.forNativeAd { nativeAd ->
            // You must call destroy on old ads when you are done with them,
            // otherwise you will have a memory leak.
            if (isDestroyed) {
                Log.d(TAG, "Native Ad destroy : $nameActivity")
                nativeAd.destroy()
                return@forNativeAd
            }
            this.nativeAd = nativeAd
            populateNativeAdView(adContainerView, layoutNativeAdView)
        }

        val adOptions = NativeAdOptions.Builder()
            .setAdChoicesPlacement(nativeAdOptions)

        val videoOptions = VideoOptions.Builder().setStartMuted(isMutedVideo).build()
        adOptions.setVideoOptions(videoOptions)

        if (isFullScreen) {
            adOptions.setMediaAspectRatio(mediaAspectRatio)
        }

        val adOptionsBuild = adOptions.build()
        builder.withNativeAdOptions(adOptionsBuild)
        val adLoader = builder.withAdListener(this).build()
        adLoader.loadAd(AdRequest.Builder().build())
    }

    private fun loadingNativeAdView(nativeAdView: NativeAdView) {
        // Set the media view.
        nativeAdView.mediaView = nativeAdView.findViewById(R.id.ad_media)

        // Set other ad assets.
        nativeAdView.headlineView = nativeAdView.findViewById(R.id.ad_headline)
        nativeAdView.bodyView = nativeAdView.findViewById(R.id.ad_body)
        nativeAdView.callToActionView = nativeAdView.findViewById(R.id.ad_call_to_action)
        nativeAdView.iconView = nativeAdView.findViewById(R.id.ad_app_icon)
        nativeAdView.priceView = nativeAdView.findViewById(R.id.ad_price)
        nativeAdView.starRatingView = nativeAdView.findViewById(R.id.ad_stars)
        nativeAdView.storeView = nativeAdView.findViewById(R.id.ad_store)
        nativeAdView.advertiserView = nativeAdView.findViewById(R.id.ad_advertiser)

        // The headline and media content are guaranteed to be in every UnifiedNativeAd.
        nativeAdView.headlineView?.let {
            (it as TextView).background =
                ContextCompat.getDrawable(currentActivity, R.drawable.round_corner)
            it.text = ""
        }

        // These assets aren't guaranteed to be in every UnifiedNativeAd, so it's important to
        // check before trying to display them.
        nativeAdView.bodyView?.let {
            (it as TextView).background =
                ContextCompat.getDrawable(currentActivity, R.drawable.round_corner)
            it.text = ""
        }

        nativeAdView.callToActionView?.let {
            (it as Button).background = ContextCompat.getDrawable(currentActivity, R.drawable.round_corner)
            it.text = ""
        }

        nativeAdView.iconView?.let {
            (it as ImageView).background =
                ContextCompat.getDrawable(currentActivity, R.drawable.round_corner)
        }

        nativeAdView.priceView?.let {
            (it as TextView).background =
                ContextCompat.getDrawable(currentActivity, R.drawable.round_corner)
            it.text = ""
        }

        nativeAdView.storeView?.let {
            (it as TextView).background =
                ContextCompat.getDrawable(currentActivity, R.drawable.round_corner)
            it.text = ""
        }

        nativeAdView.starRatingView?.let {
            (it as RatingBar).background =
                ContextCompat.getDrawable(currentActivity, R.drawable.round_corner)
        }

        nativeAdView.advertiserView?.let {
            (it as TextView).background =
                ContextCompat.getDrawable(currentActivity, R.drawable.round_corner)
            it.text = ""
        }
    }

    fun populateNativeAdView(adContainerView: ConstraintLayout, layoutNativeAdView: Int) {
        if (nativeAd == null) {
            onAdFailToLoaded?.invoke(AdmErrorType.AD_IS_NOT_AVAILABLE, null, tag)
            return
        }

        val nativeAdView =
            LayoutInflater.from(currentActivity).inflate(layoutNativeAdView, null) as NativeAdView

        adContainerView.removeAllViews()
        adContainerView.addView(nativeAdView)

        nativeAdView.updateLayoutParams<ConstraintLayout.LayoutParams> {
            height = ConstraintLayout.LayoutParams.MATCH_PARENT
            width = ConstraintLayout.LayoutParams.MATCH_PARENT
        }

        val nativeAd = nativeAd ?: return

        // Set the media view.
        nativeAdView.mediaView = nativeAdView.findViewById(R.id.ad_media)

        // Set other ad assets.
        nativeAdView.headlineView = nativeAdView.findViewById(R.id.ad_headline)
        nativeAdView.bodyView = nativeAdView.findViewById(R.id.ad_body)
        nativeAdView.callToActionView = nativeAdView.findViewById(R.id.ad_call_to_action)
        nativeAdView.iconView = nativeAdView.findViewById(R.id.ad_app_icon)
        nativeAdView.priceView = nativeAdView.findViewById(R.id.ad_price)
        nativeAdView.starRatingView = nativeAdView.findViewById(R.id.ad_stars)
        nativeAdView.storeView = nativeAdView.findViewById(R.id.ad_store)
        nativeAdView.advertiserView = nativeAdView.findViewById(R.id.ad_advertiser)

        // The headline and media content are guaranteed to be in every UnifiedNativeAd.
        nativeAdView.headlineView?.let {
            (it as TextView).text = nativeAd.headline
        }
        nativeAd.mediaContent?.let { mc ->
            nativeAdView.mediaView?.let {
                it.mediaContent = mc
            }
        }

        // These assets aren't guaranteed to be in every UnifiedNativeAd, so it's important to
        // check before trying to display them.
        nativeAdView.bodyView?.let {
            if (nativeAd.body == null) {
                (it as TextView).visibility = View.INVISIBLE
            } else {
                (it as TextView).visibility = View.VISIBLE
                (it as TextView).text = nativeAd.body
            }
        }

        nativeAdView.callToActionView?.let {
            if (nativeAd.callToAction == null) {
                (it as Button).visibility = View.INVISIBLE
            } else {
                (it as Button).visibility = View.VISIBLE
                (it as Button).text = nativeAd.callToAction
            }
        }

        nativeAdView.iconView?.let {
            if (nativeAd.icon == null) {
                (it as ImageView).visibility = View.GONE
            } else {
                (it as ImageView).setImageDrawable(nativeAd.icon?.drawable)
                (it as ImageView).visibility = View.VISIBLE
            }
        }

        nativeAdView.priceView?.let {
            if (nativeAd.price == null) {
                (it as TextView).visibility = View.INVISIBLE
            } else {
                (it as TextView).visibility = View.VISIBLE
                (it as TextView).text = nativeAd.price
            }
        }

        nativeAdView.storeView?.let {
            if (nativeAd.store == null) {
                (it as TextView).visibility = View.INVISIBLE
            } else {
                (it as TextView).visibility = View.VISIBLE
                (it as TextView).text = nativeAd.store
            }
        }

        nativeAdView.starRatingView?.let {
            if (nativeAd.starRating == null) {
                (it as RatingBar).visibility = View.INVISIBLE
            } else {
                (it as RatingBar).rating = nativeAd.starRating!!.toFloat()
                (it as RatingBar).visibility = View.VISIBLE
            }
        }

        nativeAdView.advertiserView?.let {
            if (nativeAd.advertiser == null) {
                (it as TextView).visibility = View.INVISIBLE
            } else {
                (it as TextView).text = nativeAd.advertiser
                (it as TextView).visibility = View.VISIBLE
            }
        }


        // This method tells the Google Mobile Ads SDK that you have finished populating your
        // native ad view with this native ad.
        nativeAdView.setNativeAd(nativeAd)

        // Get the video controller for the ad. One will always be provided, even if the ad doesn't
        // have a video asset.
        val mediaContent = nativeAd.mediaContent
        val vc = mediaContent?.videoController

        // Updates the UI to say whether or not this ad has a video asset.
        if (vc != null && mediaContent.hasVideoContent()) {
            // Create a new VideoLifecycleCallbacks object and pass it to the VideoController. The
            // VideoController will call methods on this object when events occur in the video
            // lifecycle.
            vc.videoLifecycleCallbacks =
                object : VideoController.VideoLifecycleCallbacks() {
                    override fun onVideoEnd() {
                        // Publishers should allow native ads to complete video playback before
                        // refreshing or replacing them with another ad in the same UI location.
                        super.onVideoEnd()
                    }
                }
        } else {

        }
    }

    fun destroyNativeAd() {
        isDestroyed = true
        nativeAd?.destroy()
        nativeAd = null
    }

    override fun onAdOpened() {
        super.onAdOpened()
        Log.d(TAG, "native ads onAdOpened: $nameActivity")
    }

    override fun onAdImpression() {
        super.onAdImpression()
        Log.d(TAG, "native ads onAdShow: $nameActivity")
        onAdShow?.invoke(tag)
    }

    override fun onAdClicked() {
        super.onAdClicked()
        Log.d(TAG, "native ads onAdLoaded: $nameActivity")
        onAdClicked?.invoke(tag)
    }

    override fun onAdLoaded() {
        super.onAdLoaded()
        Log.d(TAG, "native ads onAdLoaded: $nameActivity")
        isLoadingAd = false
        onAdLoaded?.invoke(tag)
    }

    override fun onAdFailedToLoad(p0: LoadAdError) {
        super.onAdFailedToLoad(p0)
        Log.d(TAG, "native ads onAdFailedToLoad: $nameActivity " + p0.message)
        isLoadingAd = false
        onAdFailToLoaded?.invoke(AdmErrorType.OTHER, p0.message, tag)
    }

    companion object {
        const val TAG = "AdmNativeAd"
    }
}