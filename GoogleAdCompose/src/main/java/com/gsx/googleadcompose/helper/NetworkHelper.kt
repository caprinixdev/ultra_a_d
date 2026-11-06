package com.gsx.googleadcompose.helper

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

object NetworkHelper {

    /** Trạng thái mạng đã cache. null = chưa biết (thread nền chưa đọc xong). */
    private val online = AtomicReference<Boolean?>(null)
    private val started = AtomicBoolean(false)

    /** Default network hiện tại — onLost của network CŨ (đến sau khi đã chuyển mạng) không được đè trạng thái. */
    @Volatile
    private var current: Network? = null

    /**
     * Bắt đầu theo dõi mạng (chỉ chạy 1 lần, gọi sẵn lúc khởi động ở GoogleAdsComposeStartUp).
     * Mọi lệnh hỏi ConnectivityService là binder call — máy bận có thể treo vài giây -> chạy trên thread nền,
     * main thread chỉ đọc cache. (ANR thật trên Play Console: `getActiveNetwork` trong [isOnline] ở main thread.)
     */
    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        thread(name = "gad-network", isDaemon = true) {
            runCatching {
                val cm = app.getSystemService(ConnectivityManager::class.java) ?: return@runCatching
                cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) { current = network }
                    override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                        current = network
                        online.set(caps.isValidatedInternet())
                    }
                    override fun onLost(network: Network) {
                        if (network == current) { current = null; online.set(false) }
                    }
                })
                // Không có default network thì callback không bắn gì -> tự đọc 1 lần (callback về trước thì giữ của nó).
                online.compareAndSet(null, cm.readNow())
            }.onFailure { started.set(false) }   // lỗi (vd vượt giới hạn callback) -> lần gọi sau thử lại
        }
    }

    /**
     * True nếu thiết bị đang có kết nối internet đã xác thực (Wi-Fi, di động, ethernet).
     * UMP consent + request ad cần cái này — offline sẽ fail với INTERNET_ERROR.
     *
     * Chỉ đọc cache -> gọi từ main thread an toàn, không bao giờ block. Chưa biết (vừa khởi động) -> trả true:
     * cho request đi, offline thật thì request tự fail, thay vì đứng chờ hệ thống trên main thread.
     */
    fun isOnline(context: Context): Boolean {
        start(context)
        return online.get() ?: true
    }

    private fun ConnectivityManager.readNow(): Boolean {
        val network = activeNetwork ?: return false
        return getNetworkCapabilities(network)?.isValidatedInternet() == true
    }

    private fun NetworkCapabilities.isValidatedInternet(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
