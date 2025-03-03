package gs.ad.utils.ads.format

import android.content.Context
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import gs.ad.utils.ads.AdmMachine
import gs.ad.utils.ads.TYPE_ADS
import gs.ad.utils.ads.error.AdmErrorType
import gs.ad.utils.utils.NetworkUtil
import gs.ad.utils.utils.PreferencesManager


internal class AdmBannerAd(
    private val context: Context,
    private val admMachine: AdmMachine,
    listBannerAdUnitID: List<String>
) {

    private var mListAdmModel: MutableList<AdmBannerModel> = ArrayList()





    private val listBannerAdUnitId: List<String> = listBannerAdUnitID
    private var countTier: Int = 0


    fun loadBanner(id: Int = -1, keyPosition: String, adContainerView: ConstraintLayout) {
//        destroyView(keyPosition)
        if(listBannerAdUnitId.isEmpty()){
            admMachine.onAdFailToLoaded(TYPE_ADS.BannerAd, keyPosition, AdmErrorType.LIST_AD_ID_IS_EMPTY, null)
            return
        }

        if(id >= listBannerAdUnitId.count()){
            admMachine.onAdFailToLoaded(TYPE_ADS.BannerAd, keyPosition, AdmErrorType.AD_ID_IS_NOT_EXIST, null)
            return
        }

        if(!NetworkUtil.isNetworkAvailable(context)){
            admMachine.onAdFailToLoaded(TYPE_ADS.BannerAd, keyPosition, AdmErrorType.NETWORK_IS_NOT_AVAILABLE, null)
            return
        }

        if(admMachine.getCurrentActivity().isFinishing){
            admMachine.onAdFailToLoaded(TYPE_ADS.BannerAd, keyPosition, AdmErrorType.ACTIVITY_IS_FINISHING, null)
            return
        }

        if(admMachine.getCurrentActivity().isDestroyed){
            admMachine.onAdFailToLoaded(TYPE_ADS.BannerAd, keyPosition, AdmErrorType.ACTIVITY_IS_DESTROYED, null)
            return
        }

        if(getAdByKeyPosition(keyPosition) != null){
            admMachine.onAdFailToLoaded(TYPE_ADS.BannerAd, keyPosition, AdmErrorType.AD_IS_EXISTED, null)
            return
        }

        if(PreferencesManager.getInstance().isSUB()){
            admMachine.onAdFailToLoaded(TYPE_ADS.BannerAd, keyPosition, AdmErrorType.CLIENT_HAVE_SUB, null)
            return
        }

        if(PreferencesManager.getInstance().isRemoveAds()){
            admMachine.onAdFailToLoaded(TYPE_ADS.BannerAd, keyPosition, AdmErrorType.CLIENT_HAVE_BEEN_REMOVED_AD, null)
            return
        }


        val act = admMachine.getCurrentActivity()

        val unitAdId = if (id == -1) countTier else id
        if (countTier >= listBannerAdUnitId.size - 1) {
            countTier = 0
        } else {
            countTier++
        }

        val admBannerModel = AdmBannerModel(act, keyPosition, unitAdId)

        admBannerModel.onAdOpenedListener = { keyPos ->
            admMachine.onAdOpened(TYPE_ADS.BannerAd, keyPos)
        }

        admBannerModel.onAdImpressionListener = { keyPos ->
            admMachine.onAdImpression(TYPE_ADS.BannerAd, keyPos)
        }

        admBannerModel.onAdFailToLoadedListener = { keyPos, errorType, errorMessage ->
            admMachine.onAdFailToLoaded(TYPE_ADS.BannerAd, keyPos, errorType, errorMessage)
        }

        admBannerModel.onAdLoadedListener = { keyPos ->
            admMachine.onAdLoaded(TYPE_ADS.BannerAd, keyPos)
        }

        admBannerModel.onAdClickedListener = { keyPos ->
            admMachine.onAdClicked(TYPE_ADS.BannerAd, keyPos)
        }

        admBannerModel.loadBanner(listBannerAdUnitId[unitAdId], adContainerView)

        mListAdmModel.add(admBannerModel)
    }

    fun getAdByAdId(adId: Int): AdmBannerModel?{
        val model =
            mListAdmModel.stream().filter { md -> md.currentId == adId }.findFirst()
                .orElse(null)
        return model
    }

    fun getAdByKeyPosition(keyPosition: String?): AdmBannerModel? {
        val model =
            mListAdmModel.stream().filter { md -> md.keyPosition == keyPosition }.findFirst()
                .orElse(null)
        return model
    }

    fun showAdView(keyPosition: String? = null) {
        if(keyPosition == null){
            resumeAdView()
            mListAdmModel.forEach {
                it.adContainerView?.visibility = View.VISIBLE
                it.adView?.visibility = View.VISIBLE
            }
        }else{
            resumeAdView(keyPosition)
            val model = getAdByKeyPosition(keyPosition)
            model?.adContainerView?.visibility = View.VISIBLE
            model?.adView?.visibility = View.VISIBLE
        }
    }

    fun hideAdView(keyPosition: String? = null) {
        if(keyPosition == null) {
            pauseAdView()
            mListAdmModel.forEach {
                it.adContainerView?.visibility = View.GONE
                it.adView?.visibility = View.GONE
            }
        }else{
            pauseAdView(keyPosition)
            val model = getAdByKeyPosition(keyPosition)
            model?.adContainerView?.visibility = View.GONE
            model?.adView?.visibility = View.GONE
        }
    }

    fun pauseAdView(keyPosition: String? = null) {
        if(keyPosition == null) {
            mListAdmModel.forEach {
                it.adView?.pause()
            }
        }else{
            getAdByKeyPosition(keyPosition)?.adView?.pause()
        }
    }

    fun resumeAdView(keyPosition: String? = null) {
        if(keyPosition == null) {
            mListAdmModel.forEach {
                it.adView?.resume()
            }
        }else{
            getAdByKeyPosition(keyPosition)?.adView?.resume()
        }
    }

    fun destroyView(keyPosition: String? = null) {
        if(mListAdmModel.isEmpty())return
        val model = getAdByKeyPosition(keyPosition)
        model?.adView?.destroy()
        model?.adView = null
//        model?.textView = null
        model?.adContainerView?.removeAllViews()
        model?.adContainerView?.visibility = View.GONE
        mListAdmModel.remove(model)
    }

    companion object {
        const val TAG = "AdmBannerAd"
    }
}
