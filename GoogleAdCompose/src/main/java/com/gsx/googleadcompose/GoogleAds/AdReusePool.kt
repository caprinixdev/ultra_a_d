package com.gsx.googleadcompose.GoogleAds

/**
 * Giữ ad đã nạp mà chỗ nạp nó không kịp hiện (màn đóng khi ad đang về, onboarding bị bỏ dở, banner rời màn...), cho
 * chỗ cùng loại kế tiếp lấy ra hiện thay vì xin ad mới. AdMob đếm mỗi ad nạp mà không hiện là matched request không
 * có impression -> show rate tụt; dùng lại ad đó vừa cứu show rate vừa bớt request.
 *
 * [key] tách nhóm ad dùng thay được cho nhau (vd banner cùng cỡ); `null` lúc lấy = nhóm nào cũng được.
 * Giữ tối đa [maxSize] ad, ad cũ nhất bị huỷ trước; ad quá [ttlMs] kể từ lúc nạp bị huỷ. Thread-safe.
 */
internal class AdReusePool<T : Any>(
    private val maxSize: Int,
    private val ttlMs: Long,
    private val destroyAd: (T) -> Unit,
) {
    class Entry<T>(val key: String, val ad: T, val loadedAtMs: Long)

    private val entries = ArrayDeque<Entry<T>>()

    @Synchronized
    fun put(key: String, ad: T, loadedAtMs: Long) {
        entries.removeAll { it.ad === ad }
        entries.addLast(Entry(key, ad, loadedAtMs))
        while (entries.size > maxSize) destroyQuietly(entries.removeFirst().ad)
    }

    /** Lấy ad còn hạn mới nhất của nhóm [key] (null = nhóm nào cũng được). Ad hết hạn gặp trên đường thì huỷ. */
    @Synchronized
    fun take(key: String?, nowMs: Long): Entry<T>? {
        entries.removeAll { (nowMs - it.loadedAtMs >= ttlMs).also { old -> if (old) destroyQuietly(it.ad) } }
        val e = entries.lastOrNull { key == null || it.key == key } ?: return null
        entries.remove(e)
        return e
    }

    @Synchronized
    fun clear() {
        entries.forEach { destroyQuietly(it.ad) }
        entries.clear()
    }

    private fun destroyQuietly(ad: T) {
        runCatching { destroyAd(ad) }
    }
}
