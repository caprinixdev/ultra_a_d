package gs.ad.utils.ads

import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.gms.ads.MediaAspectRatio
import com.google.android.gms.ads.nativead.NativeAdOptions
import gs.ad.utils.ads.format.AdmBannerModel
import gs.ad.utils.ads.format.AdmNativeAdModel
import gs.ad.utils.utils.PreferencesManager

class AdmManager(private val mAdmMachine: AdmMachine) {
    private constructor(builder: Builder) : this(builder.admMachine)

    fun resetCounterAds(keyCount: String){
        PreferencesManager.getInstance().resetCounterAds(keyCount)
    }

    fun getCounterAds(keyCount: String): Int{
        return PreferencesManager.getInstance().getCounterAds(keyCount)
    }

    fun initUMP(
        isTestUMP: Boolean = false,
        hashID: String = "",
        gatherConsentFinished: () -> Unit
    ) {
        mAdmMachine.initUMP(isTestUMP, hashID, gatherConsentFinished)
    }

    fun resetInitUMP() {
        mAdmMachine.resetInitUMP()
    }

    fun setListener(event: OnAdmListener){
        mAdmMachine.setListener(event)
    }

    fun removeListener():AdmManager {
        mAdmMachine.removeListener()
        return this
    }

    fun removeMainActivity():AdmManager{
        mAdmMachine.removeMainActivity()
        return this
    }

    fun preloadInterstitialAd(id: Int){
        mAdmMachine.preloadInterstitialAd(id)
    }

    fun preloadRewardAd(id: Int) {
        mAdmMachine.preloadRewardAd(id)
    }

    fun preloadOpenAd(id: Int) {
        mAdmMachine.preloadOpenAd(id)
    }

    fun countToShowInterstitialAd(id: Int, keyPosition: String, firstShowAd: Int, loopShowAd: Int) {
        mAdmMachine.countToShowAds(id, TYPE_ADS.InterstitialAd, keyPosition, firstShowAd, loopShowAd)
    }

    fun countToShowRewardAd(id: Int, keyPosition: String, firstShowAd: Int, loopShowAd: Int) {
        mAdmMachine.countToShowAds(id, TYPE_ADS.RewardAd, keyPosition, firstShowAd, loopShowAd)
    }

    fun showOpenAd(id: Int, keyPosition: String) {
        mAdmMachine.showOpenAd(id, keyPosition)
    }

    fun showDefaultOpenAd(keyPosition: String) {
        mAdmMachine.showDefaultOpenAd(-1, keyPosition)
    }

    fun showInterstitialAd(id: Int, keyPosition: String) {
        mAdmMachine.showInterstitialAd(id, keyPosition)
    }

    fun showRewardAd(id: Int, keyPosition: String) {
        mAdmMachine.showRewardAd(id, keyPosition)
    }

    fun preloadNativeAd(id: Int, keyPosition: String, isFullScreen: Boolean,
                        isVideoOption: Boolean = false,
                        isMutedVideo: Boolean = true,
                        mediaAspectRatio: Int = MediaAspectRatio.PORTRAIT,
                        nativeAdOptions: Int = NativeAdOptions.ADCHOICES_TOP_LEFT) {
        mAdmMachine.preloadNativeAd(id, keyPosition, isFullScreen, isVideoOption, isMutedVideo, mediaAspectRatio, nativeAdOptions)
    }

    fun applyNativeAdView(
        keyPosition: String,
        container: ConstraintLayout,
        layoutId: Int
    ) {
        mAdmMachine.applyNativeAdView(keyPosition, container, layoutId)
    }

    fun loadNativeAd(
        id: Int,
        keyPosition: String,
        container: ConstraintLayout,
        layoutId: Int,
        isFullScreen: Boolean,
        isVideoOption: Boolean = false,
        isMutedVideo: Boolean = true,
        mediaAspectRatio: Int = MediaAspectRatio.PORTRAIT,
        nativeAdOptions: Int = NativeAdOptions.ADCHOICES_TOP_LEFT
    ) {
        mAdmMachine.loadNativeAd(id, keyPosition, container, layoutId, isFullScreen, isVideoOption, isMutedVideo, mediaAspectRatio, nativeAdOptions)
    }

    fun getNativeAdByAdId(adId: Int): AdmNativeAdModel?{
        return mAdmMachine.getNativeAdByAdId(adId)
    }

    fun getNativeAdByKeyPosition(keyPosition: String): AdmNativeAdModel?{
        return mAdmMachine.getNativeAdByKeyPosition(keyPosition)
    }

    fun hideNativeAdView(keyPosition: String? = null) {
        mAdmMachine.hideNativeAdView(keyPosition)
    }

    fun showNativeAdView(keyPosition: String? = null) {
        mAdmMachine.showNativeAdView(keyPosition)
    }

    fun loadBannerAd(id: Int, keyPosition: String, container: ConstraintLayout) {
        mAdmMachine.loadBannerAd(id, keyPosition, container)
    }

    fun destroyAdByKeyPosition(typeAds: TYPE_ADS, keyPosition: String): AdmManager {
        when (typeAds) {
            TYPE_ADS.BannerAd -> mAdmMachine.destroyBannerAdByKeyPosition(keyPosition)
            TYPE_ADS.NativeAd -> mAdmMachine.destroyNativeAdByKeyPosition(keyPosition)
            TYPE_ADS.OpenAd -> TODO()
            TYPE_ADS.InterstitialAd -> TODO()
            TYPE_ADS.RewardAd -> TODO()
        }
        return this
    }

    fun hideBannerAdView(keyPosition: String? = null) {
        mAdmMachine.hideBannerAdView(keyPosition)
    }

    fun showBannerAdView(keyPosition: String? = null) {
        mAdmMachine.showBannerAdView(keyPosition)
    }

    fun pauseBannerAdView(keyPosition: String? = null) {
        mAdmMachine.pauseBannerAdView(keyPosition)
    }

    fun resumeBannerAdView(keyPosition: String? = null) {
        mAdmMachine.resumeBannerAdView(keyPosition)
    }

    fun getBannerAdByAdId(adId: Int): AdmBannerModel?{
        return mAdmMachine.getBannerAdByAdId(adId)
    }

    fun getBannerAdByKeyPosition(keyPosition: String): AdmBannerModel?{
        return mAdmMachine.getBannerAdByKeyPosition(keyPosition)
    }

    companion object {
        const val TAG = "AdmManager"
        inline fun build(admMachine: AdmMachine, block: Builder.() -> Unit) =
            Builder(admMachine).apply(block).build()
    }

    class Builder(
        val admMachine: AdmMachine
    ) {
        fun build() = AdmManager(this)
    }
}