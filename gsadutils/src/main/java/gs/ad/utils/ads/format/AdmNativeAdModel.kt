package gs.ad.utils.ads.format

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
import gs.ad.utils.ads.error.AdmErrorType
import gs.ad.utils.ads.format.AdmNativeAd.Companion.TAG

class AdmNativeAdModel(
    val context: Context,
    keyPosition: String,
    adId: Int
) : AdListener() {
    var keyPosition: String = ""
    var adContainerView: ConstraintLayout? = null
    var nativeAd: NativeAd? = null
    var nativeAdView: NativeAdView? = null
    var currentAdId: Int = 0

    var onAdImpressionListener: ((keyPosition: String) -> Unit)? = null
    var onAdOpenedListener: ((keyPosition: String) -> Unit)? = null
    var onAdClickedListener: ((keyPosition: String) -> Unit)? = null
    var onAdLoadedListener: ((keyPosition: String) -> Unit)? = null
    var onAdFailToLoadedListener: ((keyPosition: String, errorType: AdmErrorType, errorMessage: String?) -> Unit)? =
        null

    init {
        this.currentAdId = adId
        this.keyPosition = keyPosition
    }

    fun preloadAd(
        id: String,
        isFullScreen: Boolean,
        isVideoOption: Boolean,
        isMutedVideo: Boolean = true,
        mediaAspectRatio: Int = MediaAspectRatio.PORTRAIT,
        nativeAdOptions: Int = NativeAdOptions.ADCHOICES_TOP_LEFT

    ) {
        val builder = AdLoader.Builder(context, id)

        builder.forNativeAd { nativeAd ->
            this.nativeAd = nativeAd
        }

        val adOptions = NativeAdOptions.Builder()
            .setAdChoicesPlacement(nativeAdOptions)

        if (isVideoOption) {
            val videoOptions = VideoOptions.Builder().setStartMuted(isMutedVideo).build()
            adOptions.setVideoOptions(videoOptions)
        }

        if (isFullScreen) {
            adOptions.setMediaAspectRatio(mediaAspectRatio)
        }

        val adOptionsBuild = adOptions.build()
        builder.withNativeAdOptions(adOptionsBuild)
        val adLoader = builder.withAdListener(this).build()
        adLoader.loadAd(AdRequest.Builder().build())
    }

    fun loadAd(
        id: String,
        adContainerView: ConstraintLayout,
        layoutNativeAdView: Int,
        isFullScreen: Boolean,
        isVideoOption: Boolean,
        isMutedVideo: Boolean = true,
        mediaAspectRatio: Int = MediaAspectRatio.PORTRAIT,
        nativeAdOptions: Int = NativeAdOptions.ADCHOICES_TOP_LEFT
    ) {
//        destroyView(keyPosition)

        val nativeAdView =
            LayoutInflater.from(context).inflate(layoutNativeAdView, null) as NativeAdView

        adContainerView.removeAllViews()
        adContainerView.addView(nativeAdView)

        nativeAdView.updateLayoutParams<ConstraintLayout.LayoutParams> {
            height = ConstraintLayout.LayoutParams.MATCH_PARENT
            width = ConstraintLayout.LayoutParams.MATCH_PARENT
        }

        val builder = AdLoader.Builder(context, id)

        builder.forNativeAd { nativeAd ->
            // You must call destroy on old ads when you are done with them,
            // otherwise you will have a memory leak.
            this.nativeAd = nativeAd
            this.nativeAdView = nativeAdView

            populateNativeAdView(nativeAd, nativeAdView)
        }

        val adOptions = NativeAdOptions.Builder()
            .setAdChoicesPlacement(nativeAdOptions)

        if (isVideoOption) {
            val videoOptions = VideoOptions.Builder().setStartMuted(isMutedVideo).build()
            adOptions.setVideoOptions(videoOptions)
        }

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
                ContextCompat.getDrawable(context, R.drawable.round_corner)
            it.text = ""
        }

        // These assets aren't guaranteed to be in every UnifiedNativeAd, so it's important to
        // check before trying to display them.
        nativeAdView.bodyView?.let {
            (it as TextView).background =
                ContextCompat.getDrawable(context, R.drawable.round_corner)
            it.text = ""
        }

        nativeAdView.callToActionView?.let {
            (it as Button).background = ContextCompat.getDrawable(context, R.drawable.round_corner)
            it.text = ""
        }

        nativeAdView.iconView?.let {
            (it as ImageView).background =
                ContextCompat.getDrawable(context, R.drawable.round_corner)
        }

        nativeAdView.priceView?.let {
            (it as TextView).background =
                ContextCompat.getDrawable(context, R.drawable.round_corner)
            it.text = ""
        }

        nativeAdView.storeView?.let {
            (it as TextView).background =
                ContextCompat.getDrawable(context, R.drawable.round_corner)
            it.text = ""
        }

        nativeAdView.starRatingView?.let {
            (it as RatingBar).background =
                ContextCompat.getDrawable(context, R.drawable.round_corner)
        }

        nativeAdView.advertiserView?.let {
            (it as TextView).background =
                ContextCompat.getDrawable(context, R.drawable.round_corner)
            it.text = ""
        }
    }

    fun populateNativeAdView(nativeAd: NativeAd, nativeAdView: NativeAdView) {
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

    override fun onAdOpened() {
        super.onAdOpened()
        Log.d(TAG, "native ads onAdOpened")
        onAdOpenedListener?.invoke(keyPosition)
    }

    override fun onAdImpression() {
        super.onAdImpression()
        Log.d(TAG, "native ads onAdImpression")
        onAdImpressionListener?.invoke(keyPosition)
    }

    override fun onAdClicked() {
        super.onAdClicked()
        Log.d(TAG, "native ads onAdLoaded")
        onAdClickedListener?.invoke(keyPosition)
    }

    override fun onAdLoaded() {
        super.onAdLoaded()
        Log.d(TAG, "native ads onAdLoaded")
        onAdLoadedListener?.invoke(keyPosition)
    }

    override fun onAdFailedToLoad(p0: LoadAdError) {
        super.onAdFailedToLoad(p0)
        Log.d(TAG, "native ads onAdFailedToLoad " + p0.message)
        onAdFailToLoadedListener?.invoke(keyPosition, AdmErrorType.OTHER, p0.message)
    }
}