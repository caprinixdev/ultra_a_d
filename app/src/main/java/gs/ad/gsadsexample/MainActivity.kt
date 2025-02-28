package gs.ad.gsadsexample

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import gs.ad.gsadsexample.ads.AdKeyPosition
import gs.ad.gsadsexample.databinding.ActivityMainBinding
import gs.ad.gsadsexample.sub.SubscriptionProductId
import gs.ad.utils.ads.AdmManager
import gs.ad.utils.ads.OnAdmListener
import gs.ad.utils.ads.TYPE_ADS
import gs.ad.utils.ads.error.AdmErrorType
import gs.ad.utils.google_iab.BillingClientLifecycle
import gs.ad.utils.google_iab.OnBillingListener
import gs.ad.utils.google_iab.models.PurchaseInfo
import gs.ad.utils.utils.GlobalVariables
import gs.ad.utils.utils.PreferencesManager

class MainActivity : AppCompatActivity(), OnAdmListener {
    private lateinit var binding: ActivityMainBinding
    private val mAdmManager: AdmManager
        get() {
            return AppController.admBuilder.isMainActivity(this)
        }

    private val mBillingClientLifecycle: BillingClientLifecycle?
        get() {
            return AppController.billingClientLifecycle
        }

    private var mLaunchSubForResult = registerForActivityResult<Intent, ActivityResult>(
        ActivityResultContracts.StartActivityForResult()
    ) {
        GlobalVariables.isShowSub = false
        checkSubToUpdateUI()
    }

    override fun onAdClosed(typeAds: TYPE_ADS, keyPosition: String) {
        super.onAdClosed(typeAds, keyPosition)
        when (typeAds) {
            TYPE_ADS.BannerAd -> {}
            TYPE_ADS.NativeAd -> {}
            TYPE_ADS.OpenAd -> {}
            TYPE_ADS.InterstitialAd -> {
                if (keyPosition == AdKeyPosition.InterstitialAd_ScMain.name) {
                    startActivity(Intent(this@MainActivity, MainActivity2::class.java))
                } else if (keyPosition == AdKeyPosition.InterstitialAd_ScMain_CountShowAd.name) {
                    Log.d(
                        TAG,
                        "Count Ads onAdClosed : " + mAdmManager.getCounterAds(AdKeyPosition.InterstitialAd_ScMain_CountShowAd.name)
                    )
                }
            }

            TYPE_ADS.RewardAd -> {
                if (keyPosition == AdKeyPosition.RewardAd_ScMain_RemoveAd.name) {
                    PreferencesManager.getInstance().removeAds(true)
                    runOnUiThread {
                        checkSubToUpdateUI()
                    }
                }
            }
        }
    }

    override fun onAdLoaded(typeAds: TYPE_ADS, keyPosition: String) {
        super.onAdLoaded(typeAds, keyPosition)
        when (typeAds) {
            TYPE_ADS.BannerAd -> {}
            TYPE_ADS.NativeAd -> stopShimmerLoading()
            TYPE_ADS.OpenAd -> {}
            TYPE_ADS.InterstitialAd -> {}
            TYPE_ADS.RewardAd -> {}
        }
    }

    override fun onAdFailToLoaded(
        typeAds: TYPE_ADS,
        keyPosition: String,
        errorType: AdmErrorType,
        errorMessage: String?
    ) {
        super.onAdFailToLoaded(typeAds, keyPosition, errorType, errorMessage)
        when (typeAds) {
            TYPE_ADS.BannerAd -> {}
            TYPE_ADS.NativeAd -> stopShimmerLoading()
            TYPE_ADS.OpenAd -> {}
            TYPE_ADS.InterstitialAd -> {}
            TYPE_ADS.RewardAd -> {}
        }
    }

    private fun openPlayStoreAccount(packageName: String = "", sku: String = "") {
        try {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/account/subscriptions?package=$packageName")
                )
            )
        } catch (e: ActivityNotFoundException) {
            e.printStackTrace()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mAdmManager.setListener(this)

        binding.button2.setOnClickListener {
            if (!PreferencesManager.getInstance().isSUB()) {
                val intent = Intent(this, SubscriptionActivity::class.java)
                mLaunchSubForResult.launch(intent)
            } else {
                openPlayStoreAccount("anime.girlfriend.app", SubscriptionProductId.Weekly.id)
                Toast.makeText(this@MainActivity, "YOU HAVE SUB", Toast.LENGTH_SHORT).show()
            }
        }

//        mAdmManager.showOpenAd(3, AdKeyPosition.AppOpenAd_ScMain.name)

        binding.buttonCount.text = "Count to show ads"
        mAdmManager.resetCounterAds(AdKeyPosition.InterstitialAd_ScMain_CountShowAd.name)
        binding.buttonCount.setOnClickListener {
            binding.buttonCount.text =
                "Count to show ads " + (mAdmManager.getCounterAds(AdKeyPosition.InterstitialAd_ScMain_CountShowAd.name) + 1)
            mAdmManager.countToShowInterstitialAd(
                1,
                AdKeyPosition.InterstitialAd_ScMain_CountShowAd.name,
                firstShowAd = 3,
                loopShowAd = 2
            )
        }

        binding.button.setOnClickListener {
            mAdmManager.showInterstitialAd(0, AdKeyPosition.InterstitialAd_ScMain.name)
        }

        binding.button1.setOnClickListener {
            mAdmManager.showRewardAd(0, AdKeyPosition.RewardAd_ScMain_RemoveAd.name)
        }

        mBillingClientLifecycle?.setListener(this, object : OnBillingListener {
            override fun onPurchasedProductsFetched(purchaseInfos: List<PurchaseInfo>) {
                super.onPurchasedProductsFetched(purchaseInfos)
                Log.d(TAG, "onPurchasedProductsFetched: ")
                runOnUiThread {
                    checkSubToUpdateUI()
                }
            }
        })

    }

    private fun checkSubToUpdateUI() {
        if (PreferencesManager.getInstance().isSUB()) {
            stopShimmerLoading()
            binding.button2.text = "Have Sub"
            destroyAd()
            binding.button1.visibility = INVISIBLE

        } else if (PreferencesManager.getInstance().isRemoveAds()) {
            stopShimmerLoading()
            binding.button2.text = "Buy Sub"
            destroyAd()
            binding.button1.visibility = INVISIBLE
        } else {
            binding.button2.text = "Buy Sub"
            loadNativeAd()
            loadBannerAd()
            binding.button1.visibility = VISIBLE
        }
    }

    private fun destroyAd() {
        mAdmManager.destroyAdByKeyPosition(
            TYPE_ADS.BannerAd,
            AdKeyPosition.BannerAd_ScMain.name
        )
        mAdmManager.destroyAdByKeyPosition(
            TYPE_ADS.NativeAd,
            AdKeyPosition.NativeAd_ScMain.name
        )
    }

    private fun loadNativeAd() {
        startShimmerLoading()
        mAdmManager
            .loadNativeAd(
                4,
                AdKeyPosition.NativeAd_ScMain.name,
                binding.nativeAdContainerView,
                R.layout.layout_native_ad,
                isFullScreen = false
            )
    }

    private fun loadBannerAd() {
        mAdmManager.loadBannerAd(0, AdKeyPosition.BannerAd_ScMain.name, binding.bannerView)
    }

    private fun startShimmerLoading() {
        binding.layoutNativeAdLoaderContainer.visibility = VISIBLE
    }

    private fun stopShimmerLoading() {
        binding.layoutNativeAdLoaderContainer.visibility = INVISIBLE
    }

    override fun onStart() {
        super.onStart()
        mBillingClientLifecycle?.fetchSubPurchasedProducts()
        GlobalVariables.canShowOpenAd = true
    }

    override fun onResume() {
        super.onResume()
        mAdmManager.resumeBannerAdView()
    }

    override fun onPause() {
        super.onPause()
        mAdmManager.pauseBannerAdView()
    }

    override fun onDestroy() {
        mAdmManager
            .destroyAdByKeyPosition(TYPE_ADS.BannerAd, AdKeyPosition.BannerAd_ScMain.name)
            .destroyAdByKeyPosition(TYPE_ADS.NativeAd, AdKeyPosition.NativeAd_ScMain.name)
            .removeListener()
            .removeMainActivity()
        mBillingClientLifecycle?.removeListener(this)
        AppController.terminateApp()
        super.onDestroy()
    }

    companion object {
        val TAG = "MainActivity"
    }
}