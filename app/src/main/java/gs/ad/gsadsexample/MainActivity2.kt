package gs.ad.gsadsexample

import android.os.Bundle
import android.view.View.GONE
import androidx.appcompat.app.AppCompatActivity
import gs.ad.gsadsexample.ads.GroupBannerAd
import gs.ad.gsadsexample.ads.GroupNativeAd
import gs.ad.gsadsexample.databinding.ActivityMain2Binding
import gs.ad.utils.ads.format.AdmBannerAd
import gs.ad.utils.ads.format.AdmInterstitialAd
import gs.ad.utils.ads.format.AdmNativeAd
import gs.ad.utils.ads.format.AdmRewardAd
import gs.ad.utils.google_iab.BillingClientLifecycle
import gs.ad.utils.google_iab.OnBillingListener
import gs.ad.utils.google_iab.models.PurchaseInfo
import gs.ad.utils.utils.GlobalVariables
import gs.ad.utils.utils.PreferencesManager

class MainActivity2 : AppCompatActivity() {
    private lateinit var binding: ActivityMain2Binding
    private val mBillingClientLifecycle: BillingClientLifecycle?
        get() {
            return (application as AppOwner).mBillingClientLifecycle
        }

    private var nativeAd: AdmNativeAd? = null
    private var bannerAd: AdmBannerAd? = null
    private var rewardedAd: AdmRewardAd? = null
    private var interBackMainActivity: AdmInterstitialAd? = null


    private fun setUpAd() {
        val keyAd = this.javaClass.simpleName
        if (GroupBannerAd.listBannerAd[keyAd] == null) {
            bannerAd = AdmBannerAd(-1, this)
            GroupBannerAd.listBannerAd[keyAd] = bannerAd
        } else {
            bannerAd = GroupBannerAd.listBannerAd[keyAd]
        }
        bannerAd?.setNewActivity(this)


        interBackMainActivity = AdmInterstitialAd(1, this)
        interBackMainActivity?.onAdClosed = {
            runOnUiThread {
                finish()
            }
        }

        nativeAd = AdmNativeAd(3, this, false)
        nativeAd?.setNewActivity(this)
        nativeAd?.onAdFailToLoaded = { admErrorType, errorMessage, tag ->

        }

        nativeAd?.onAdLoaded = {

        }

        rewardedAd = AdmRewardAd(1, this)
        rewardedAd?.onHaveReward = {

        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMain2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        mBillingClientLifecycle?.setListener(this, object : OnBillingListener {
            override fun onPurchasedProductsFetched(purchaseInfos: List<PurchaseInfo>) {
                super.onPurchasedProductsFetched(purchaseInfos)
                runOnUiThread {
                    checkSubToUpdateUI()
                }
            }
        })

        setUpAd()

        binding.button.setOnClickListener {
            interBackMainActivity?.showPopupLoadAds { }
        }

        binding.button1.setOnClickListener {
            rewardedAd?.showPopupLoadAds { }
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    private fun destroyAd() {
        binding.bannerView.visibility = GONE
        binding.nativeAdContainerView.visibility = GONE
        nativeAd?.destroyNativeAd()
    }

    override fun onDestroy() {
        destroyAd()
        nativeAd = null
        bannerAd = null
        rewardedAd = null
        interBackMainActivity = null
        mBillingClientLifecycle?.removeListener(this)
        super.onDestroy()
    }

    private fun checkSubToUpdateUI() {
        if (PreferencesManager.getInstance().isSUB() || PreferencesManager.getInstance()
                .isRemoveAds()
        ) {
            destroyAd()
        } else {
            bannerAd?.loadAd(binding.bannerView, isCollapsible = true)
            nativeAd?.loadAd(binding.nativeAdContainerView, R.layout.layout_native_ad_origin)
        }
    }

    override fun onStart() {
        super.onStart()
        GlobalVariables.canShowOpenAd = true
    }
}