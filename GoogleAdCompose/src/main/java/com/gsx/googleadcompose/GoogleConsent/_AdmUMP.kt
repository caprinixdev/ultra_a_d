package com.gsx.googleadcompose.GoogleConsent

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebSettings
import com.gsx.googleadcompose.GoogleAds.AdCore
import com.gsx.googleadcompose.helper.DialogHelper
import com.gsx.googleadcompose.helper.NetworkHelper
import com.gsx.googleadcompose.helper.OfflineGate
import com.google.android.libraries.ads.mobile.sdk.MobileAds.Companion.initialize
import com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Điều phối UMP (User Messaging Platform) consent + init Mobile Ads cho GMA Next-Gen SDK.
 *
 * Thứ tự bắt buộc: xin consent trước, chỉ init ad khi consent cho phép. AdMob App ID đọc từ
 * manifest meta-data `com.google.android.gms.ads.APPLICATION_ID` (UMP cũng cần cái này).
 *
 * Trong Compose, gọi composable [AdmUMP] để lấy instance (tự lấy Activity an toàn):
 * ```
 * val ump = AdmUMP()
 * LaunchedEffect(Unit) {
 *     ump.initUMP(gatherConsentFinished = { /* ready -> vào app */ })
 * }
 * ```
 *
 * @param activity Activity dùng để hiện consent form.
 */
class _AdmUMP(private val activity: Activity) {

    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(activity)

    /**
     * UMP không phản hồi update trong ngần này -> đi tiếp với consent đang có, splash không bị treo.
     * Form không hiện muộn đè lên màn sau; lần mở app sau xin lại.
     */
    var consentUpdateTimeoutMs: Long = 8_000L

    /** True khi trạng thái consent cho phép request ad. */
    val canRequestAds: Boolean
        get() = consentInformation.canRequestAds()

    /** True nếu cần cung cấp lối vào privacy options (xin lại consent) trong app. */
    val isPrivacyOptionsRequired: Boolean
        get() = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /**
     * Xin consent, sau đó init Mobile Ads SDK nếu được phép.
     *
     * [gatherConsentFinished] gọi đúng 1 lần khi xong việc xin consent (form đóng, không cần, update lỗi hoặc
     * quá [consentUpdateTimeoutMs]) — đây là lúc rời splash. Init ad chạy nền song song, không block callback này.
     *
     * Nếu offline thì UMP update sẽ fail INTERNET_ERROR. Người dùng miễn phí gặp dialog chỉ có Retry, có mạng mới
     * đi tiếp ([OfflineGate]); người đã mua chạy tiếp ngay với consent đã cache.
     *
     * @param testDeviceHashedIds các device hashed ID coi là test device cho consent form.
     * @param showNetworkErrorDialog false: không hiện dialog ở bước này, chạy tiếp với consent đã cache.
     * @param onAdsInitialized gọi trên main khi Mobile Ads sẵn sàng (đã sẵn -> gọi ngay).
     * @param gatherConsentFinished gọi khi xong việc xin consent.
     */
    fun initUMP(
        testDeviceHashedIds: List<String> = emptyList(),
        showNetworkErrorDialog: Boolean = true,
        onAdsInitialized: () -> Unit = {},
        gatherConsentFinished: () -> Unit,
    ) {
        AdCore.markAdsPending()
        OfflineGate.start()

        val readyNotified = AtomicBoolean(false)
        val notifyReady = { if (readyNotified.compareAndSet(false, true)) onAdsInitialized() }

        val finished = AtomicBoolean(false)
        val finish = {
            if (finished.compareAndSet(false, true)) {
                if (canRequestAds) initializeAds(testDeviceHashedIds, notifyReady) else AdCore.markAdsUnavailable()
                gatherConsentFinished()
            }
        }

        // Offline -> UMP không fetch form được.
        if (!NetworkHelper.isOnline(activity)) {
            when {
                // không cần hỏi: người đã mua không có quảng cáo để xem, hoặc app tắt dialog -> dùng consent đã cache
                !showNetworkErrorDialog || OfflineGate.isPremium() -> finish()
                // người dùng miễn phí phải có mạng: chỉ có Retry, có mạng mới đi tiếp ([OfflineGate])
                OfflineGate.enabled -> DialogHelper.requireNetwork(activity) {
                    initUMP(testDeviceHashedIds, showNetworkErrorDialog, onAdsInitialized, gatherConsentFinished)
                }
                else -> DialogHelper.showNoNetworkDialog(
                    activity = activity,
                    onRetry = { initUMP(testDeviceHashedIds, showNetworkErrorDialog, onAdsInitialized, gatherConsentFinished) },
                    onCancel = { finish() },
                )
            }
            return
        }

        val paramsBuilder = ConsentRequestParameters.Builder()
        if (testDeviceHashedIds.isNotEmpty()) {
            val debug = ConsentDebugSettings.Builder(activity)
            testDeviceHashedIds.forEach { debug.addTestDeviceHashedId(it) }
            paramsBuilder.setConsentDebugSettings(debug.build())
        }

        // Thử init ad bằng consent của phiên trước, trước khi form xử lý xong.
        if (canRequestAds) initializeAds(testDeviceHashedIds, notifyReady)

        val handler = Handler(Looper.getMainLooper())
        val timeout = Runnable {
            Log.w(TAG, "Consent update quá ${consentUpdateTimeoutMs}ms -> đi tiếp với consent hiện có")
            finish()
        }
        handler.postDelayed(timeout, consentUpdateTimeoutMs)

        runCatching {
            consentInformation.requestConsentInfoUpdate(
                activity,
                paramsBuilder.build(),
                {
                    handler.removeCallbacks(timeout)
                    if (!finished.get()) showConsentFormIfRequired(finish)
                },
                { requestError: FormError ->
                    // Update lỗi — chạy tiếp với consent hiện đang có.
                    handler.removeCallbacks(timeout)
                    Log.w(TAG, "Consent update failed ${requestError.errorCode}: ${requestError.message}")
                    finish()
                },
            )
        }.onFailure { error ->
            handler.removeCallbacks(timeout)
            Log.w(TAG, "Consent update exception", error)
            finish()
        }
    }

    private fun showConsentFormIfRequired(finish: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) { finish(); return }
        runCatching {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError: FormError? ->
                if (formError != null) {
                    Log.w(TAG, "Consent form error ${formError.errorCode}: ${formError.message}")
                }
                finish()
            }
        }.onFailure { error ->
            Log.w(TAG, "Consent form exception", error)
            finish()
        }
    }

    /**
     * Hiện privacy options form (gọi từ lối vào trong settings khi [isPrivacyOptionsRequired] = true).
     */
    fun showPrivacyOptionsForm(onDismissed: (FormError?) -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error -> onDismissed(error) }
    }

    /** Reset consent đã lưu. Chỉ dùng debug. */
    fun resetConsent() = consentInformation.reset()

    private fun initializeAds(testDeviceHashedIds: List<String>, onAdsInitialized: () -> Unit) {
        AdCore.whenAdsReady(onReady = onAdsInitialized)
        if (!ADS_INIT_STARTED.compareAndSet(false, true)) return   // 1 lần mỗi process, kể cả Activity tạo lại

        val appContext = activity.applicationContext
        // GMA Next-Gen SDK phải init ngoài main thread để tránh ANR. Đọc App ID (PackageManager = binder call)
        // cũng làm luôn ở đây.
        INIT_SCOPE.launch {
            try {
                val appId = readAppId(appContext)
                    ?: throw IllegalStateException("Missing manifest meta-data com.google.android.gms.ads.APPLICATION_ID")

                // Nạp WebView ở thread nền: phần nặng nhất của init, để dành cho main dễ ANR ở máy yếu. Cũng là
                // phép thử: máy đang cập nhật / thiếu WebView thì ném lỗi ở đây, không làm app crash về sau.
                WebSettings.getDefaultUserAgent(appContext)

                val config = InitializationConfig.Builder(appId)
                if (testDeviceHashedIds.isNotEmpty()) {
                    config.setRequestConfiguration(
                        RequestConfiguration.Builder().setTestDeviceIds(testDeviceHashedIds).build()
                    )
                }
                initialize(appContext, config.build()) {
                    Log.d(TAG, "Mobile Ads SDK initialized.")
                    AdCore.markAdsReady()
                }
            } catch (error: Throwable) {
                // Lỗi trong coroutine không bắt sẽ crash app ngay lúc mở -> phiên này chịu không có ad.
                Log.e(TAG, "Init Mobile Ads lỗi", error)
                ADS_INIT_STARTED.set(false)
                AdCore.markAdsUnavailable()
            }
        }
    }

    private fun readAppId(context: Context): String? = runCatching {
        val info = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        info.metaData?.getString("com.google.android.gms.ads.APPLICATION_ID")
    }.getOrNull()

    private companion object {
        const val TAG = "AdmUMP"
        val ADS_INIT_STARTED = AtomicBoolean(false)
        val INIT_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
