package com.gsx.googleadcompose.GoogleAds

/**
 * Chỗ để dành MỘT ad full-screen đã nạp mà instance nạp nó không dùng tới, cho instance khác cùng loại lấy ra show.
 *
 * App Open dùng nó từ 1.0.27: splash hết giờ chờ rồi ad mới về, hay màn splash đóng khi ad đang về — trước đây ad đó
 * bị huỷ, AdMob đếm là matched request không có impression, gần như mỗi lần mở app chậm. Giờ ad nằm ở đây và instance
 * resume lấy ra show ở lần quay lại app kế tiếp, thay vì xin ad mới.
 *
 * Giữ tối đa 1 ad: có ad mới thì giữ ad nạp sau (còn hạn lâu hơn), huỷ ad kia. Thread-safe.
 */
internal class SpareAdSlot<T : Any>(private val destroyAd: (T) -> Unit) {

    class Entry<T>(val ad: T, val loadedAtMs: Long, val unitId: String?, val index: Int)

    private var entry: Entry<T>? = null

    /** Để dành [e]. Đã có ad thì giữ ad nạp sau, huỷ ad còn lại. */
    @Synchronized
    fun put(e: Entry<T>) {
        val old = entry
        if (old == null || old.ad === e.ad) {
            entry = e
            return
        }
        if (e.loadedAtMs >= old.loadedAtMs) {
            entry = e
            destroyQuietly(old.ad)
        } else {
            destroyQuietly(e.ad)
        }
    }

    /** Lấy ad để dành nếu còn hạn ([ttlMs] tính từ lúc nạp). Hết hạn thì huỷ, trả null. */
    @Synchronized
    fun take(nowMs: Long, ttlMs: Long): Entry<T>? {
        val e = entry ?: return null
        entry = null
        if (nowMs - e.loadedAtMs >= ttlMs) {
            destroyQuietly(e.ad)
            return null
        }
        return e
    }

    /** Có ad để dành còn hạn không (không lấy ra). */
    @Synchronized
    fun has(nowMs: Long, ttlMs: Long): Boolean = entry?.let { nowMs - it.loadedAtMs < ttlMs } == true

    /** Huỷ ad đang để dành (vd đã mua premium). */
    @Synchronized
    fun clear() {
        entry?.let { destroyQuietly(it.ad) }
        entry = null
    }

    private fun destroyQuietly(ad: T) {
        runCatching { destroyAd(ad) }
    }
}
