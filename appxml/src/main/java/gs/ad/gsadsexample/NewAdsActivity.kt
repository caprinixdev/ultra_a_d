package gs.ad.gsadsexample

import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import gs.ad.utils.ads.format.AdmInterstitialAd
import gs.ad.utils.ads.format.AdmNativeAd
import gs.ad.utils.ads.format.AdmNativeCollapsible
import gs.ad.utils.ads.format.AdmNativeFullStory
import gs.ad.utils.ads.format.AdmNativePip

/** Demo 3 loại ad mới của 1.0.28: native collapsible, inter + native full story (2 thanh), native PIP. */
class NewAdsActivity : AppCompatActivity() {

    private var collapsible: AdmNativeCollapsible? = null
    private var pip: AdmNativePip? = null
    private val storyAds = ArrayList<AdmNativeAd>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_ads)

        // Native collapsible ở đáy: mũi tên khoá 3s, gập xong 10s hiện lại.
        collapsible = AdmNativeCollapsible(-1, this).apply {
            collapseDelaySeconds = 3
            collapseReshowSeconds = 10
            onCollapsed = { Log.d(TAG, "collapsible: user gập ad") }
            loadAd(findViewById<ConstraintLayout>(R.id.container_native_collapsible))
        }

        // Nạp sẵn 2 native cho story sau inter.
        preloadStoryAds()

        findViewById<Button>(R.id.btn_inter_story).setOnClickListener {
            val inter = AdmInterstitialAd(-1, this)
            inter.onAdLoaded = { runOnUiThread { inter.showAds() } }
            inter.onAdFailToLoaded = { _, _, _ -> runOnUiThread { showStory() } }
            inter.onAdClosed = { runOnUiThread { showStory() } }
            inter.loadAds()
        }

        findViewById<Button>(R.id.btn_pip).setOnClickListener {
            pip?.dismiss()
            pip = AdmNativePip(this).apply {
                corner = AdmNativePip.Corner.TOP_RIGHT
                onClose = { Log.d(TAG, "pip: user đóng") }
                show()
            }
        }
    }

    private fun preloadStoryAds() {
        storyAds.forEach { it.destroyNativeAd() }
        storyAds.clear()
        repeat(2) { storyAds += AdmNativeAd(-1, this, true).also { it.preloadAd() } }
    }

    private fun showStory() {
        AdmNativeFullStory(this).apply {
            onClose = {
                Log.d(TAG, "story: đóng")
                preloadStoryAds()   // ad đã hiện thì nạp mới cho lần sau
            }
        }.show(storyAds.toList())
    }

    override fun onDestroy() {
        collapsible?.destroy()
        pip?.dismiss()
        storyAds.forEach { it.destroyNativeAd() }
        super.onDestroy()
    }

    private companion object {
        const val TAG = "NewAdsDemo"
    }
}
