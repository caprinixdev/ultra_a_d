package gs.ad.utils.ads.format

import android.content.Context
import android.util.Log
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import com.google.android.gms.ads.MediaAspectRatio
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import gs.ad.utils.ads.AdmMachine
import gs.ad.utils.ads.TYPE_ADS
import gs.ad.utils.ads.error.AdmErrorType
import gs.ad.utils.utils.NetworkUtil
import gs.ad.utils.utils.PreferencesManager


internal class AdmNativeAd(
    private val context: Context,
    private val admMachine: AdmMachine,
    listNativeAdUnitID: List<String>
) {
    private var mListAdmModel: MutableList<AdmNativeAdModel> = ArrayList()
    private val listNativeAdUnitId: List<String> = listNativeAdUnitID
    private var countTier: Int = 0

    fun preloadAd(
        id: Int = -1,
        keyPosition: String,
        isFullScreen: Boolean,
        isVideoOption: Boolean,
        isMutedVideo: Boolean = true,
        mediaAspectRatio: Int = MediaAspectRatio.PORTRAIT,
        nativeAdOptions: Int = NativeAdOptions.ADCHOICES_TOP_LEFT

    ) {
        if (listNativeAdUnitId.isEmpty()) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.NativeAd,
                keyPosition,
                AdmErrorType.LIST_AD_ID_IS_EMPTY,
                null
            )
            return
        }

        if (id >= listNativeAdUnitId.count()) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.NativeAd,
                keyPosition,
                AdmErrorType.AD_ID_IS_NOT_EXIST,
                null
            )
            return
        }

        if (!NetworkUtil.isNetworkAvailable(context)) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.NativeAd,
                keyPosition,
                AdmErrorType.NETWORK_IS_NOT_AVAILABLE,
                null
            )
            return
        }

        if (admMachine.getCurrentActivity().isFinishing) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.NativeAd,
                keyPosition,
                AdmErrorType.ACTIVITY_IS_FINISHING,
                null
            )
            return
        }

        if (admMachine.getCurrentActivity().isDestroyed) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.NativeAd,
                keyPosition,
                AdmErrorType.ACTIVITY_IS_DESTROYED,
                null
            )
            return
        }

        if (getAdByKeyPosition(keyPosition) != null || getAdByKeyPosition(keyPosition)?.nativeAd != null) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.NativeAd,
                keyPosition,
                AdmErrorType.AD_IS_EXISTED,
                null
            )
            return
        }

        if (PreferencesManager.getInstance().isSUB()) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.NativeAd,
                keyPosition,
                AdmErrorType.CLIENT_HAVE_SUB,
                null
            )
            return
        }

        if (PreferencesManager.getInstance().isRemoveAds()) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.NativeAd,
                keyPosition,
                AdmErrorType.CLIENT_HAVE_BEEN_REMOVED_AD,
                null
            )
            return
        }

        val unitAdId = if (id == -1) countTier else id

        if (countTier >= listNativeAdUnitId.size - 1) {
            countTier = 0
        } else {
            countTier++
        }

        val admNativeAdModel = AdmNativeAdModel(context, keyPosition, unitAdId)

        admNativeAdModel.onAdOpenedListener = { keyPos ->
            admMachine.onAdOpened(TYPE_ADS.NativeAd, keyPos)
        }

        admNativeAdModel.onAdImpressionListener = { keyPos ->
            admMachine.onAdImpression(TYPE_ADS.NativeAd, keyPos)
        }

        admNativeAdModel.onAdFailToLoadedListener = { keyPos, errorType, errorMessage ->
            destroyView(keyPos)
            admMachine.onAdFailToLoaded(TYPE_ADS.NativeAd, keyPos, errorType, errorMessage)
        }

        admNativeAdModel.onAdLoadedListener = { keyPos ->
            admMachine.onAdLoaded(TYPE_ADS.NativeAd, keyPos)
        }

        admNativeAdModel.onAdClickedListener = { keyPos ->
            admMachine.onAdClicked(TYPE_ADS.NativeAd, keyPos)
        }

        admNativeAdModel.preloadAd(
            listNativeAdUnitId[unitAdId],
            isFullScreen,
            isVideoOption,
            isMutedVideo,
            mediaAspectRatio,
            nativeAdOptions
        )

        mListAdmModel.add(admNativeAdModel)
    }

    fun applyNativeAdView(
        keyPosition: String,
        adContainerView: ConstraintLayout,
        nativeAdView: NativeAdView
    ) {
        val model = getAdByKeyPosition(keyPosition) ?: return
        if (model.nativeAd == null) {
            destroyView(keyPosition)
            return
        }
        val nativeAd = model.nativeAd ?: return

        model.adContainerView = adContainerView
        model.nativeAdView = nativeAdView

        model.populateNativeAdView(nativeAd, nativeAdView)
        adContainerView.removeAllViews()
        adContainerView.addView(nativeAdView)
        nativeAdView.updateLayoutParams<ConstraintLayout.LayoutParams> {
            height = ConstraintLayout.LayoutParams.MATCH_PARENT
            width = ConstraintLayout.LayoutParams.MATCH_PARENT
        }
    }

    /**
     * Creates a request for a new native ad based on the boolean parameters and calls the
     * corresponding "populate" method when one is successfully returned.
     */
    fun loadAd(
        id: Int = -1,
        keyPosition: String,
        adContainerView: ConstraintLayout,
        layoutNativeAdViewId: Int,
        isFullScreen: Boolean,
        isVideoOption: Boolean,
        isMutedVideo: Boolean = true,
        mediaAspectRatio: Int = MediaAspectRatio.PORTRAIT,
        nativeAdOptions: Int = NativeAdOptions.ADCHOICES_TOP_LEFT
    ) {
        if (listNativeAdUnitId.isEmpty()) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.NativeAd,
                keyPosition,
                AdmErrorType.LIST_AD_ID_IS_EMPTY,
                null
            )
            return
        }

        if (id >= listNativeAdUnitId.count()) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.NativeAd,
                keyPosition,
                AdmErrorType.AD_ID_IS_NOT_EXIST,
                null
            )
            return
        }

        if (!NetworkUtil.isNetworkAvailable(context)) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.NativeAd,
                keyPosition,
                AdmErrorType.NETWORK_IS_NOT_AVAILABLE,
                null
            )
            return
        }

        if (admMachine.getCurrentActivity().isFinishing) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.NativeAd,
                keyPosition,
                AdmErrorType.ACTIVITY_IS_FINISHING,
                null
            )
            return
        }

        if (admMachine.getCurrentActivity().isDestroyed) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.NativeAd,
                keyPosition,
                AdmErrorType.ACTIVITY_IS_DESTROYED,
                null
            )
            return
        }

        if (getAdByKeyPosition(keyPosition) != null || getAdByKeyPosition(keyPosition)?.nativeAd != null) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.NativeAd,
                keyPosition,
                AdmErrorType.AD_IS_EXISTED,
                null
            )
            return
        }

        if (PreferencesManager.getInstance().isSUB()) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.NativeAd,
                keyPosition,
                AdmErrorType.CLIENT_HAVE_SUB,
                null
            )
            return
        }

        if (PreferencesManager.getInstance().isRemoveAds()) {
            admMachine.onAdFailToLoaded(
                TYPE_ADS.NativeAd,
                keyPosition,
                AdmErrorType.CLIENT_HAVE_BEEN_REMOVED_AD,
                null
            )
            return
        }

        val unitAdId = if (id == -1) countTier else id

        if (countTier >= listNativeAdUnitId.size - 1) {
            countTier = 0
        } else {
            countTier++
        }

        val admNativeAdModel = AdmNativeAdModel(context, keyPosition, unitAdId)

        admNativeAdModel.onAdOpenedListener = { keyPos ->
            admMachine.onAdOpened(TYPE_ADS.NativeAd, keyPos)
        }

        admNativeAdModel.onAdImpressionListener = { keyPos ->
            admMachine.onAdImpression(TYPE_ADS.NativeAd, keyPos)
        }

        admNativeAdModel.onAdFailToLoadedListener = { keyPos, errorType, errorMessage ->
            destroyView(keyPos)
            admMachine.onAdFailToLoaded(TYPE_ADS.NativeAd, keyPos, errorType, errorMessage)
        }

        admNativeAdModel.onAdLoadedListener = { keyPos ->
            getAdByKeyPosition(keyPos)?.adContainerView?.visibility = VISIBLE
            admMachine.onAdLoaded(TYPE_ADS.NativeAd, keyPos)
        }

        admNativeAdModel.onAdClickedListener = { keyPos ->
            admMachine.onAdClicked(TYPE_ADS.NativeAd, keyPos)
        }

        admNativeAdModel.loadAd(
            listNativeAdUnitId[unitAdId],
            adContainerView,
            layoutNativeAdViewId,
            isFullScreen,
            isVideoOption,
            isMutedVideo,
            mediaAspectRatio,
            nativeAdOptions
        )

        mListAdmModel.add(admNativeAdModel)
    }

    fun getAdByKeyPosition(keyPosition: String?): AdmNativeAdModel? {
        val model =
            mListAdmModel.stream().filter { md -> md.keyPosition == keyPosition }.findFirst()
                .orElse(null)
        return model
    }

    fun getAdById(adId: Int): AdmNativeAdModel? {
        val model =
            mListAdmModel.stream().filter { md -> md.currentAdId == adId }.findFirst()
                .orElse(null)
        return model
    }

    fun showAdView(keyPosition: String? = null) {
        if(keyPosition == null){
            mListAdmModel.forEach {
                it.adContainerView?.visibility = VISIBLE
                it.nativeAdView?.visibility = VISIBLE
            }
        }else{
            val model = getAdByKeyPosition(keyPosition)
            model?.adContainerView?.visibility = VISIBLE
            model?.nativeAdView?.visibility = VISIBLE
        }
    }

    fun hideAdView(keyPosition: String? = null) {
        if(keyPosition == null){
            mListAdmModel.forEach {
                it.adContainerView?.visibility = GONE
                it.nativeAdView?.visibility = GONE
            }
        }else{
            val model = getAdByKeyPosition(keyPosition)
            model?.adContainerView?.visibility = GONE
            model?.nativeAdView?.visibility = GONE
        }
    }

    fun destroyView(keyPosition: String = "") {
        if (mListAdmModel.isEmpty()) return
        val model = getAdByKeyPosition(keyPosition)

        removeModel(model)
    }

    private fun removeModel(model: AdmNativeAdModel?) {
        Log.d(TAG, "removeModel: " + model?.keyPosition)

        model?.nativeAd?.destroy()
        model?.nativeAd = null
        model?.nativeAdView?.destroy()
        model?.nativeAdView = null
        model?.adContainerView?.removeAllViews()
        model?.adContainerView?.visibility = View.GONE

        mListAdmModel.remove(model)
    }

    companion object {
        const val TAG = "AdmNativeAd"
    }
}