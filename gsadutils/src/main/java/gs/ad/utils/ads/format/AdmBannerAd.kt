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

    private val currentModel: AdmBannerModel?
        get() {
            val act = admMachine.getCurrentActivity()
            val nameActivity = act::class.java.simpleName
            val model =
                mListAdmModel.stream().filter { md -> md.nameActivity == nameActivity }.findFirst()
                    .orElse(null)
            return model ?: currentModel()
        }

    private fun currentModel(): AdmBannerModel? {
        val model = mListAdmModel.stream().filter { md -> md.adView?.isShown == true }.findFirst()
            .orElse(null)
        return model
    }

    private fun currentModelByKeyPosition(keyPosition: String): AdmBannerModel? {
        val model =
            mListAdmModel.stream().filter { md -> md.keyPosition == keyPosition }.findFirst()
                .orElse(null)
        return model
    }

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

        if(currentModelByKeyPosition(keyPosition) != null){
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
        val admBannerModel = AdmBannerModel(act, keyPosition)

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

        val unitAdId = if (id == -1) countTier else id
        if (countTier >= listBannerAdUnitId.size - 1) {
            countTier = 0
        } else {
            countTier++
        }

        admBannerModel.loadBanner(listBannerAdUnitId[unitAdId], adContainerView)

        mListAdmModel.add(admBannerModel)
    }


    fun showAdView() {
        resumeAdView()
        currentModel?.adContainerView?.visibility = View.VISIBLE
        currentModel?.adView?.visibility = View.VISIBLE
    }

    fun hideAdView() {
        pauseAdView()
        currentModel?.adContainerView?.visibility = View.GONE
        currentModel?.adView?.visibility = View.GONE
    }

    fun pauseAdView() {
        currentModel?.adView?.pause()
    }

    fun resumeAdView() {
        currentModel?.adView?.resume()
    }

    fun destroyView(keyPosition: String = "") {
        if (mListAdmModel.isEmpty()) return
        val model =
            if (keyPosition.isEmpty()) currentModel else currentModelByKeyPosition(keyPosition)
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
