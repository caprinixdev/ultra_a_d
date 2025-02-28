package gs.ad.utils.ads

import gs.ad.utils.ads.error.AdmErrorType

interface OnAdmListener {

    fun onAdHaveReward(typeAds: TYPE_ADS, keyPosition: String) {}
    fun onAdNotHaveReward(typeAds: TYPE_ADS, keyPosition: String) {}

    fun onAdShowed(typeAds: TYPE_ADS, keyPosition: String) {}
    fun onAdImpression(typeAds: TYPE_ADS, keyPosition: String) {}
    fun onAdOpened(typeAds: TYPE_ADS, keyPosition: String) {}
    fun onAdClicked(typeAds: TYPE_ADS, keyPosition: String) {}
    fun onAdLoaded(typeAds: TYPE_ADS, keyPosition: String) {}
    fun onAdClosed(typeAds: TYPE_ADS, keyPosition: String) {}
    fun onAdFailToLoaded(
        typeAds: TYPE_ADS,
        keyPosition: String,
        errorType: AdmErrorType,
        errorMessage: String?
    ) {
    }
}