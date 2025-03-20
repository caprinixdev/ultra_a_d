package gs.ad.utils.ads

import android.app.Activity
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import gs.ad.utils.utils.NetworkUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AdmUMP (
    private val currentActivity: Activity
) {
    private val googleMobileAdsConsentManager: GoogleMobileAdsConsentManager =
        GoogleMobileAdsConsentManager.getInstance(currentActivity)

    private fun showPopupNetworkError(
        isTestUMP: Boolean, hashID: String, gatherConsentFinished: () -> Unit
    ) {
        if (NetworkUtil.isNetworkAvailable(currentActivity)) return
        AlertDialog.Builder(currentActivity).setTitle("Network error")
            .setMessage("The connection to the network is impossible. Please check the status of your connection or try again in a few minutes.")
            .setCancelable(false).setPositiveButton(
                "OK"
            ) { _, _ ->
                clickPopupNetworkErrorButtonOK(
                    isTestUMP, hashID, gatherConsentFinished
                )
            }.create().show()
    }


    private fun clickPopupNetworkErrorButtonOK(
        isTestUMP: Boolean, hashID: String, gatherConsentFinished: () -> Unit
    ) {
        initUMP(isTestUMP, hashID, gatherConsentFinished)
    }

    fun initUMP(
        isTestUMP: Boolean = false,
        hashID: String = "",
        gatherConsentFinished: () -> Unit
    ) {
        if (NetworkUtil.isNetworkAvailable(currentActivity)) {
            googleMobileAdsConsentManager.gatherConsent(
                currentActivity, isTestUMP, hashID
            ) { consentError ->
                if (consentError != null) {
                    // Consent not obtained in current session.
                    Log.d(
                        TAG, String.format("%s: %s", consentError.errorCode, consentError.message)
                    )
                }

                if (googleMobileAdsConsentManager.canRequestAds) {
                    initializeMobileAdsSdk(isTestUMP, hashID, gatherConsentFinished)
                } else {
                    gatherConsentFinished()
                }
            }
        } else {
            showPopupNetworkError(isTestUMP, hashID, gatherConsentFinished)
        }
    }

    private fun initializeMobileAdsSdk(
        isTestUMP: Boolean,
        hashID: String,
        gatherConsentFinished: () -> Unit
    ) {
        if (isTestUMP) {
            // Set your test devices.
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder().setTestDeviceIds(listOf(hashID)).build()
            )
        }

        CoroutineScope(Dispatchers.IO).launch {
            // Initialize the Google Mobile Ads SDK on a background thread.
            //if (MobileAds.getInitializationStatus() == InitializationStatus)
            MobileAds.initialize(currentActivity) {
                gatherConsentFinished()
            }
        }
    }

    companion object {
        const val TAG = "AdmInitUMPActivity"
    }
}