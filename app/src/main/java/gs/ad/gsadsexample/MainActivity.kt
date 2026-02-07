package gs.ad.gsadsexample

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import gs.ad.gsadsexample.databinding.ActivityMainBinding
import gs.ad.gsadsexample.sub.SubscriptionProductId
import gs.ad.utils.ads.format.AdmBannerAd
import gs.ad.utils.ads.format.AdmInterstitialAd
import gs.ad.utils.ads.format.AdmNativeAd
import gs.ad.utils.ads.format.AdmRewardAd
import gs.ad.utils.google_iab.BillingClientLifecycle
import gs.ad.utils.google_iab.OnBillingListener
import gs.ad.utils.google_iab.models.PurchaseInfo
import gs.ad.utils.utils.GlobalVariables
import gs.ad.utils.utils.PreferencesManager

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val mBillingClientLifecycle: BillingClientLifecycle?
        get() {
            return (application as AppOwner).mBillingClientLifecycle
        }

    private var interShowActivity2: AdmInterstitialAd? = null
    private var interCountAd: AdmInterstitialAd? = null
    private var nativeAd: AdmNativeAd? = null
    private var bannerAd: AdmBannerAd? = null
    private var rewardedAdRemoveAd: AdmRewardAd? = null

    private var mLaunchSubForResult = registerForActivityResult<Intent, ActivityResult>(
        ActivityResultContracts.StartActivityForResult()
    ) {
        GlobalVariables.isShowSub = false
        checkSubToUpdateUI()
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

    private fun setUpAd() {
        bannerAd = AdmBannerAd(-1, this)
        bannerAd?.setNewActivity(this)
        bannerAd?.onAdFailToLoaded = { admErrorType, errorMessage, tag ->
            runOnUiThread {
                Log.d(TAG, "xxxyyy " + admErrorType.name + "," + errorMessage)
            }
        }
        bannerAd?.onAdLoaded = {
            runOnUiThread {
                Log.d(TAG, "xxxyyy " + "Banner Ad Loaded")
            }
        }

        interShowActivity2 = AdmInterstitialAd(0, this)
        interShowActivity2?.onAdClosed = {
            runOnUiThread {
                startActivity(Intent(this@MainActivity, MainActivity2::class.java))
            }
        }
        interShowActivity2?.onAdFailToLoaded = { admErrorType, errorMessage, tag ->
            runOnUiThread {
                Log.d(TAG, "xxxyyy " + admErrorType.name + "," + errorMessage)
            }
        }

        interCountAd = AdmInterstitialAd(1, this)
        interCountAd?.onAdClosed = {
            Log.d(
                TAG,
                "Count Ads onAdClosed : "
            )
        }
        interCountAd?.onAdFailToLoaded = { admErrorType, errorMessage, tag->
            runOnUiThread {
                Log.d(TAG, "xxxyyy " + admErrorType.name + "," + errorMessage)
            }
        }

        nativeAd = AdmNativeAd(1, this, false)
        nativeAd?.onAdFailToLoaded = { admErrorType, errorMessage, tag ->
            runOnUiThread {
                Log.d(TAG, "xxxyyy " + admErrorType.name + "," + errorMessage)
                stopShimmerLoading()
            }
        }

        nativeAd?.onAdLoaded = {
            runOnUiThread {
                stopShimmerLoading()
            }
        }

        rewardedAdRemoveAd = AdmRewardAd(0, this)
        rewardedAdRemoveAd?.onHaveReward = {
            runOnUiThread {
                PreferencesManager.getInstance().removeAds(true)
                checkSubToUpdateUI()
            }
        }
        rewardedAdRemoveAd?.onAdFailToLoaded = { admErrorType, errorMessage, tag ->
            runOnUiThread {
                Log.d(TAG, "xxxyyy " + admErrorType.name + "," + errorMessage)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setUpAd()

        binding.button2.setOnClickListener {
            if (!PreferencesManager.getInstance().isSUB()) {
                val intent = Intent(this, SubscriptionActivity::class.java)
                mLaunchSubForResult.launch(intent)
            } else {
//                openPlayStoreAccount("anime.girlfriend.app", SubscriptionProductId.Weekly.id)
//                Toast.makeText(this@MainActivity, "YOU HAVE SUB", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, SubscriptionActivity::class.java)
                mLaunchSubForResult.launch(intent)
            }
        }

        binding.buttonCount.text = "Count to show ads"
        binding.buttonCount.setOnClickListener {
            binding.buttonCount.text =
                "Count to show ads " +
                        (PreferencesManager.getInstance().getCounterAds(MAIN_COUNTER_AD) + 1)
            interCountAd?.countToShowAds(
                keyCounterAd = MAIN_COUNTER_AD,
                startAds = 3,
                loopAds = 2,
            ){}
        }

        binding.button.setOnClickListener {
            interShowActivity2?.showPopupLoadAds { }
        }

        binding.button1.setOnClickListener {
            rewardedAdRemoveAd?.showPopupLoadAds { }
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

    @SuppressLint("SetTextI18n")
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
        binding.bannerView.visibility = GONE
        binding.nativeAdContainerView.visibility = GONE
        nativeAd?.destroyNativeAd()
    }

    private fun loadNativeAd() {
        startShimmerLoading()
        nativeAd?.loadAd(binding.nativeAdContainerView, R.layout.layout_native_ad)
    }

    private fun loadBannerAd() {
//        bannerAd?.loadAd(binding.bannerView, R.layout.loading_banner)
    }

    private fun startShimmerLoading() {
        binding.layoutNativeAdLoaderContainer.visibility = VISIBLE
    }

    private fun stopShimmerLoading() {
        binding.layoutNativeAdLoaderContainer.visibility = INVISIBLE
    }

    override fun onStart() {
        super.onStart()
        GlobalVariables.canShowOpenAd = true
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        destroyAd()
        interShowActivity2 = null
        interCountAd = null
        nativeAd = null
        rewardedAdRemoveAd = null
        mBillingClientLifecycle?.removeListener(this)
        super.onDestroy()
    }

    companion object {
        const val TAG = "MainActivity"
        const val MAIN_COUNTER_AD = "MAIN_COUNTER_AD"
    }
}