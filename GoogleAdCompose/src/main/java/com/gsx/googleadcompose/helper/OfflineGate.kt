package com.gsx.googleadcompose.helper

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.gsx.googleadcompose.GoogleAds.AdCore
import com.gsx.googleadcompose.utils.PreferencesManager
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Người dùng miễn phí đổi lại bằng việc xem quảng cáo, mà quảng cáo cần mạng: tắt mạng không được là cách dùng
 * app như đã mua. Người đã mua gói bỏ quảng cáo thì dùng offline bình thường.
 *
 * Kiểm tra lúc xin consent ([com.gsx.googleadcompose.GoogleConsent._AdmUMP.initUMP]) và mỗi lần app quay lại
 * foreground. Không kiểm tra giữa lúc đang dùng: sóng chập chờn không được làm hộp thoại nhảy ra giữa chừng.
 *
 * Biết ai đã mua qua [PreferencesManager]. App có billing riêng thì phải tự đặt `removeAds` theo trạng thái mua.
 */
object OfflineGate {

    /** false: người dùng miễn phí cũng dùng được offline, hộp thoại lúc xin consent có thêm nút Cancel. */
    @Volatile
    var enabled: Boolean = true

    private val started = AtomicBoolean(false)

    internal fun isPremium(): Boolean = PreferencesManager.getInstance().let {
        it.isSUB() || it.isRemoveAds() || it.isLifetime()
    }

    internal fun mustBeOnline(enabled: Boolean, premium: Boolean): Boolean = enabled && !premium

    /** Bắt đầu kiểm tra mỗi lần quay lại foreground. Gọi trên main; gọi nhiều lần cũng chỉ chạy 1 lần. */
    internal fun start() {
        if (!started.compareAndSet(false, true)) return
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        // addObserver phát lại onStart nếu app đang ở foreground: lượt đó initUMP đang tự kiểm tra rồi
        var skipReplay = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        val main = Handler(Looper.getMainLooper())
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (skipReplay) {
                    skipReplay = false
                    return
                }
                // sau khi activity đã lên hẳn, để hộp thoại gắn đúng vào activity đang hiện
                main.post { check() }
            }
        })
    }

    private fun check() {
        if (!mustBeOnline(enabled, isPremium())) return
        val activity = AdCore.currentActivity ?: return
        DialogHelper.requireNetwork(activity) {}
    }
}
