package com.gsx.googleadcompose.helper

import android.app.ActivityManager
import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Nhận biết máy yếu để app giảm tải: máy Android Go hoặc RAM dưới 3200 MB (nhóm RAM thấp nhất của Android
 * vitals). Google chấm ANR riêng từng dòng máy (ngưỡng 8%), nên bớt ad nặng trên máy yếu giúp không bị kéo xuống.
 *
 * ```
 * val allowPip = !AdmDevice.isLowEnd && firebase.adsPlacementEnabled(pos)
 * ```
 *
 * Đọc RAM là binder call nên chạy ở thread nền lúc khởi động. Chưa đọc xong thì coi như máy thường.
 */
object AdmDevice {

    private val started = AtomicBoolean(false)

    /** Tổng RAM (MB), 0 nếu chưa đọc xong. */
    @Volatile
    var totalMemoryMb: Long = 0L
        private set

    /** True nếu là máy yếu. Chỉ đọc giá trị đã lưu, gọi từ main thread an toàn. */
    @Volatile
    var isLowEnd: Boolean = false
        private set

    internal fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        thread(name = "gad-device", isDaemon = true) {
            runCatching {
                val am = app.getSystemService(ActivityManager::class.java) ?: return@runCatching
                val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
                totalMemoryMb = info.totalMem / (1024 * 1024)
                isLowEnd = isLowEnd(am.isLowRamDevice, totalMemoryMb)
            }
        }
    }

    internal fun isLowEnd(isLowRamDevice: Boolean, totalMemoryMb: Long): Boolean =
        isLowRamDevice || (totalMemoryMb in 1 until LOW_END_MEMORY_MB)

    private const val LOW_END_MEMORY_MB = 3200L
}
