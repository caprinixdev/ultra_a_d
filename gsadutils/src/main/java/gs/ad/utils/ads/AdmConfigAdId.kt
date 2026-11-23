package gs.ad.utils.ads

object AdmConfigAdId {
    var listBannerAdUnitID: List<String> = listOf()
    var listInterstitialAdUnitID: List<String> = listOf()
    var listRewardAdUnitID: List<String> = listOf()
    var listNativeAdUnitID: List<String> = listOf()
    var listOpenAdUnitID: List<String> = listOf()

    fun getBannerAdUnitID(id : Int) : String{
        return listBannerAdUnitID[id]
    }

    fun getInterstitialAdUnitID(id : Int) : String{
        return listInterstitialAdUnitID[id]
    }

    fun getRewardAdUnitID(id : Int) : String{
        return listRewardAdUnitID[id]
    }

    fun getNativeAdUnitID(id : Int) : String{
        return listNativeAdUnitID[id]
    }

    fun getOpenAdUnitID(id : Int) : String{
        return listOpenAdUnitID[id]
    }
}