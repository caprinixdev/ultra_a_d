package gs.ad.gsadsexample

import android.os.Bundle
import android.util.Log
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import gs.ad.gsadsexample.adapter.OnboardPagerAdapter
import gs.ad.gsadsexample.ads.GroupNativeAd
import gs.ad.gsadsexample.databinding.ActivityOnboardBinding
import gs.ad.utils.utils.GlobalVariables
import gs.ad.utils.utils.PreferencesManager

class OnBoardActivity : AppCompatActivity(){
    private var _binding: ActivityOnboardBinding? = null
    private val binding get() = _binding!!
    private var currentPos = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityOnboardBinding.inflate(layoutInflater)
        setContentView(binding.getRoot())

        GlobalVariables.canShowOpenAd = false

        onBackPressedDispatcher.addCallback {
            onBack()
        }

        setupAdapter()
    }

    override fun onDestroy() {
        val list = GroupNativeAd.listOnBoardNativeAd

        list.forEach { (index, admNativeAd) ->
            admNativeAd?.destroyNativeAd()
            list[index] = null
        }

        list.clear()

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

        val adapter = OnboardPagerAdapter(this, items)
        binding.onboardViewPager.adapter = adapter
    }

}