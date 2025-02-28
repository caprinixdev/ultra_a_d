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
import gs.ad.utils.ads.error.AdmErrorType
import gs.ad.utils.ads.format.AdmBannerAd.Companion.TAG

class AdmBannerModel(
    activity: Activity?,
    keyPosition: String
) : AdListener() {
    var keyPosition: String = ""
    var adContainerView: ConstraintLayout? = null
    var adView: AdView? = null
    var activity: Activity? = null
    val nameActivity : String get() {
        val act = this.activity ?: return ""
        return act::class.java.simpleName
    }

    var onAdImpressionListener: ((keyPosition: String) -> Unit)? = null
    var onAdOpenedListener: ((keyPosition: String) -> Unit)? = null
    var onAdClickedListener: ((keyPosition: String) -> Unit)? = null
    var onAdLoadedListener: ((keyPosition: String) -> Unit)? = null
    var onAdFailToLoadedListener: ((keyPosition: String, errorType: AdmErrorType, errorMessage: String?) -> Unit)? = null

    init {
        this.keyPosition = keyPosition
        this.activity = activity
    }

    // [START get_ad_size]
    // Get the ad size with screen width.
    private val adSize: AdSize
        get() {
            val act = activity ?: return AdSize.BANNER
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

    fun loadBanner(id: String, adContainerView: ConstraintLayout) {
//        destroyView(keyPosition)

        if (activity == null) onAdFailToLoadedListener?.invoke(keyPosition, AdmErrorType.ACTIVITY_IS_NOT_AVAILABLE, null)
        val act = activity ?: return


        // Create a new ad view.
        val adView = AdView(act)
        adView.setAdSize(adSize)
        adView.adUnitId = id
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

    override fun onAdOpened() {
        super.onAdOpened()
        Log.d(TAG, "bannerView onAdOpened")
        onAdOpenedListener?.invoke(keyPosition)
    }

    override fun onAdImpression() {
        super.onAdImpression()
        Log.d(TAG, "bannerView onAdImpression")
        onAdImpressionListener?.invoke(keyPosition)
    }

    override fun onAdLoaded() {
        super.onAdLoaded()
        Log.d(TAG, "bannerView onAdLoaded")
        adContainerView?.visibility = View.VISIBLE
        adView?.visibility = View.VISIBLE
        onAdLoadedListener?.invoke(keyPosition)
    }

    override fun onAdClicked() {
        // Called when a click is recorded for an ad.
        Log.d(TAG, "bannerView onAdClicked")
        onAdClickedListener?.invoke(keyPosition)
    }

    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
        super.onAdFailedToLoad(loadAdError)
        Log.d(TAG, "bannerView " + loadAdError.message)
        onAdFailToLoadedListener?.invoke(keyPosition, AdmErrorType.OTHER, loadAdError.message)
    }
}