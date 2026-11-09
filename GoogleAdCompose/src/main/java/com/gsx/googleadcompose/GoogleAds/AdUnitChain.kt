package com.gsx.googleadcompose.GoogleAds

import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError

/** Nghỉ ngắn trước khi thử ID dự phòng: không request dồn dập ngay trong callback lỗi của SDK. */
internal const val AD_FALLBACK_DELAY_MS = 300L

/** Số ID dự phòng thử thêm mặc định khi ID đang dùng không có ad. */
internal const val DEFAULT_MAX_FALLBACK_UNITS = 2

/** Con trỏ xoay vòng ID của 1 format, dùng chung mọi instance. */
internal class AdUnitCursor {
    private var value = 0

    @Synchronized
    fun next(size: Int): Int {
        val i = value % size
        value = (value + 1) % size
        return i
    }
}

/**
 * Thứ tự ID thử cho 1 lượt load.
 * - `index >= 0`: app chỉ định đúng 1 ID -> không dự phòng.
 * - `index < 0`: bắt đầu từ ID xoay vòng, lỗi thì thử ID kế (bỏ ID trùng), tối đa `1 + maxFallback` ID.
 */
internal class AdUnitChain private constructor(private val attempts: List<Attempt>) {

    data class Attempt(val index: Int, val unitId: String)

    private var position = 0

    val hasNext: Boolean get() = position < attempts.size

    fun next(): Attempt? = attempts.getOrNull(position)?.also { position++ }

    companion object {
        /** Trả null nếu list rỗng hoặc [index] ngoài list. [start] chỉ được gọi khi xoay vòng. */
        fun build(units: List<String>, index: Int, maxFallback: Int, start: () -> Int): AdUnitChain? {
            if (units.isEmpty()) return null
            if (index >= 0) {
                val unit = units.getOrNull(index) ?: return null
                return AdUnitChain(listOf(Attempt(index, unit)))
            }
            val first = start()
            val attempts = units.indices
                .map { offset -> (first + offset) % units.size }
                .map { i -> Attempt(i, units[i]) }
                .distinctBy { it.unitId }
                .take(1 + maxFallback.coerceAtLeast(0))
            return AdUnitChain(attempts)
        }
    }
}

/**
 * Lỗi nào đáng thử ID khác. NETWORK_ERROR cũng thử: ID sai hoặc đã xoá trả về đúng lỗi này (đo trên máy thật),
 * còn mất mạng thật thì hỏng rất nhanh và số lần thử có giới hạn. Timeout thử tiếp chỉ làm user chờ lâu hơn;
 * thiếu App ID hay request bị huỷ thì ID nào cũng vậy.
 */
internal fun LoadAdError.ErrorCode.allowsFallback(): Boolean = when (this) {
    LoadAdError.ErrorCode.NO_FILL,
    LoadAdError.ErrorCode.INTERNAL_ERROR,
    LoadAdError.ErrorCode.INVALID_REQUEST,
    LoadAdError.ErrorCode.NOT_FOUND,
    LoadAdError.ErrorCode.INVALID_AD_RESPONSE,
    LoadAdError.ErrorCode.NETWORK_ERROR -> true
    else -> false
}
