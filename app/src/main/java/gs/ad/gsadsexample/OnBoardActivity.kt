package gs.ad.gsadsexample

import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import gs.ad.gsadsexample.adapter.OnboardPagerAdapter
import gs.ad.gsadsexample.ads.AdKeyPosition
import gs.ad.gsadsexample.databinding.ActivityOnboardBinding
import gs.ad.utils.ads.AdmManager
import gs.ad.utils.ads.OnAdmListener
import gs.ad.utils.ads.TYPE_ADS
import gs.ad.utils.utils.GlobalVariables
import gs.ad.utils.utils.PreferencesManager

class OnBoardActivity : AppCompatActivity(), OnAdmListener{
    private var _binding: ActivityOnboardBinding? = null
    private val binding get() = _binding!!
    private var currentPos = 0
    private val mAdmManager: AdmManager get() { return AppController.admBuilder.getActivity(this)}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityOnboardBinding.inflate(layoutInflater)
        setContentView(binding.getRoot())

        GlobalVariables.canShowOpenAd = false

        mAdmManager.setListener(object: OnAdmListener{
            override fun onAdClicked(typeAds: TYPE_ADS, keyPosition: String) {
                super.onAdClicked(typeAds, keyPosition)
            }

            override fun onAdShowed(typeAds: TYPE_ADS, keyPosition: String) {
                super.onAdShowed(typeAds, keyPosition)
            }
        })

        onBackPressedDispatcher.addCallback {
            onBack()
        }

        setupAdapter()

    }

    fun removeListener(){
        mAdmManager
            .destroyAdByKeyPosition(TYPE_ADS.NativeAd, AdKeyPosition.NativeAd_ScOnBoard_1.name)
            .destroyAdByKeyPosition(TYPE_ADS.NativeAd, AdKeyPosition.NativeAd_ScOnBoard_2.name)
            .destroyAdByKeyPosition(TYPE_ADS.NativeAd, AdKeyPosition.NativeAd_ScOnBoard_3.name)
            .destroyAdByKeyPosition(TYPE_ADS.NativeAd, AdKeyPosition.NativeAd_ScOnBoard_4.name)
            .removeListener()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    private fun onBack() {
        if (currentPos > 0) {
            binding.onboardViewPager.setCurrentItem(currentPos - 1, true)
        }
    }

    private fun setupAdapter() {
        val items = if (PreferencesManager.getInstance().isSUB()) {
            listOf(
                R.drawable.onboard1,
                R.drawable.onboard2,
                R.drawable.onboard3
            )
        } else {
            listOf(
                R.drawable.onboard1,
                R.drawable.onboard2,
                "native_ad",
                R.drawable.onboard3
            )
        }

        val adapter = OnboardPagerAdapter(this, items, admManager = mAdmManager)
        binding.onboardViewPager.adapter = adapter
    }

}