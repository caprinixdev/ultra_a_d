package com.gsx.googleadcompose.GoogleAds

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import java.lang.ref.WeakReference

/** Trạng thái chung cho ads: app context + cờ MobileAds + activity đang foreground. */
object AdCore {
    /** App context, set sẵn bởi ContentProvider lúc khởi động. */
    @Volatile
    var appContext: Context? = null

    /** Snapshot state: composable đọc cờ này sẽ tự chạy lại đúng lúc SDK init xong. */
    private var adsReadyFlag by mutableStateOf(false)

    /**
     * True khi Mobile Ads đã init. SDK NÉM `IllegalStateException` nếu load ad trước lúc này -> mọi đường vào
     * ad phải hỏi cờ này trước. Hỏi cả SDK: app tự gọi `MobileAds.initialize` (lối thoát khi UMP quá hạn) thì
     * lib vẫn biết, không báo nhầm UMP_IS_NOT_ACTIVE cả phiên.
     */
    var isMobileAdsReady: Boolean
        get() = adsReadyFlag || runCatching { MobileAds.isInitialized }.getOrDefault(false)
        set(value) {
            adsReadyFlag = value
        }

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Chạy [block] trên main thread (đang ở main -> chạy luôn, không thì post).
     * GMA Next-Gen SDK gọi callback load/event trên thread NỀN -> mọi callback ad phải qua đây trước khi
     * đụng state SDK, dialog, Compose state hay hook của app (navigate/toast chỉ an toàn trên main).
     */
    internal fun onMain(block: () -> Unit) {
        if (Looper.myLooper() === Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    /** Luôn post sang lượt sau của main (thoát khỏi stack callback của SDK). */
    internal fun postMain(delayMs: Long = 0L, block: () -> Unit) {
        mainHandler.postDelayed(block, delayMs)
    }

    // ===================== Chờ Mobile Ads sẵn sàng =====================

    private class ReadyWaiter(val onReady: () -> Unit, val onUnavailable: (() -> Unit)?)

    private val readyWaiters = ArrayList<ReadyWaiter>()   // chỉ đụng trên main

    /** Biết chắc phiên này chưa có ad (consent không cho phép / init lỗi). Snapshot state cho composable. */
    internal var adsUnavailable by mutableStateOf(false)
        private set

    /**
     * Gọi [onReady] khi Mobile Ads sẵn sàng (sẵn rồi -> gọi luôn). [onUnavailable] (nếu có) gọi khi biết chắc
     * phiên này chưa có ad: consent không cho phép hoặc init lỗi -> caller khỏi phải chờ hết timeout.
     */
    internal fun whenAdsReady(onUnavailable: (() -> Unit)? = null, onReady: () -> Unit) = onMain {
        when {
            isMobileAdsReady -> onReady()
            adsUnavailable && onUnavailable != null -> onUnavailable()
            else -> readyWaiters += ReadyWaiter(onReady, onUnavailable)
        }
    }

    internal fun markAdsReady() {
        isMobileAdsReady = true
        onMain {
            adsUnavailable = false
            val waiters = readyWaiters.toList()
            readyWaiters.clear()
            waiters.forEach { it.onReady() }
        }
    }

    /** Consent không cho request ad / init lỗi: báo ngay cho ai đang chờ có [ReadyWaiter.onUnavailable]. */
    internal fun markAdsUnavailable() = onMain {
        if (isMobileAdsReady) return@onMain
        adsUnavailable = true
        val givingUp = readyWaiters.filter { it.onUnavailable != null }
        readyWaiters.removeAll(givingUp)
        givingUp.forEach { it.onUnavailable?.invoke() }
    }

    /** Bắt đầu xin consent lại -> chưa kết luận được là không có ad. */
    internal fun markAdsPending() = onMain { adsUnavailable = false }

    // ====================== Theo dõi click ad ==========================

    @Volatile
    private var lastAdClickAtMs = 0L

    internal fun noteAdClicked() {
        lastAdClickAtMs = SystemClock.elapsedRealtime()
    }

    /** True nếu vừa có click ad trong [windowMs] (user rời app do bấm ad, không phải tự thoát). */
    internal fun adClickedWithin(windowMs: Long): Boolean =
        lastAdClickAtMs != 0L && SystemClock.elapsedRealtime() - lastAdClickAtMs <= windowMs

    // ====================== Theo dõi Activity ==========================

    /** Activity gần nhất lên foreground. WeakReference -> không giữ leak. */
    private var activityRef: WeakReference<Activity>? = null

    /** Activity đang visible (giữa onStart..onStop). */
    private var startedRef: WeakReference<Activity>? = null

    private var trackingActive = false

    /** Activity foreground còn sống, hoặc null. Dùng cho [com.gsx.googleadcompose.GoogleAds] show() không tham số. */
    val currentActivity: Activity?
        get() = activityRef?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }

    /**
     * True nếu mở ad full-screen từ [activity] được: còn sống + đang visible. App đã xuống nền mà vẫn gọi
     * show thì SDK báo lỗi `APP_NOT_FOREGROUND` và ad đã lấy ra bị mất, phải load lại từ đầu.
     */
    internal fun canPresentFrom(activity: Activity): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false
        return !trackingActive || startedRef?.get() === activity
    }

    /** Đăng ký theo dõi activity. Gọi 1 lần lúc khởi động (ContentProvider). */
    fun registerActivityTracking(app: Application) {
        if (trackingActive) return
        trackingActive = true
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            // Bắt từ onStart (không đợi onResume): ProcessLifecycle ON_START bắn trước onResume, Activity vừa
            // được tạo lại (máy yếu hay bị kill khi ở nền) mà chưa có ref thì app-open resume sẽ bị bỏ lỡ.
            override fun onActivityStarted(activity: Activity) {
                activityRef = WeakReference(activity)
                startedRef = WeakReference(activity)
            }

            override fun onActivityResumed(activity: Activity) {
                activityRef = WeakReference(activity)
                startedRef = WeakReference(activity)
            }

            override fun onActivityStopped(activity: Activity) {
                if (startedRef?.get() === activity) startedRef = null
            }

            override fun onActivityDestroyed(activity: Activity) {
                if (activityRef?.get() === activity) activityRef = null
                if (startedRef?.get() === activity) startedRef = null
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        })
    }
}
