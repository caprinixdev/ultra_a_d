package com.gsx.googleadcompose.GoogleAds

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.gsx.googleadcompose.helper.AdmDevice

/**
 * Shimmer kiểu Facebook — gradient sáng quét ngang lặp vô hạn, đắp lên placeholder lúc ad đang load.
 * Dùng chung cho [AdmBanner] + [AdmNative].
 */
@Composable
private fun Modifier.shimmer(
    shape: Shape = RoundedCornerShape(6.dp),
    base: Color = Color(0xFFBDBDBD),
    highlight: Color = Color(0xFFE8E8E8),
): Modifier {
    // Máy yếu: màu tĩnh, không chạy animation vẽ lại từng frame đúng lúc ad đang load.
    if (AdmDevice.isLowEnd) return this.clip(shape).drawBehind { drawRect(base) }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x = transition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "x",
    )
    val colors = listOf(base, highlight, base)
    // Đọc x trong pha VẼ: mỗi frame chỉ vẽ lại, không recompose. 1 native có ~8 ô shimmer, recompose từng
    // frame trên main ở máy yếu đúng lúc ad đang load là dễ giật / ANR.
    return this
        .clip(shape)
        .drawBehind {
            val w = size.width.coerceAtLeast(1f)
            val p = x.value
            drawRect(Brush.linearGradient(colors, start = Offset(p * w - w, 0f), end = Offset(p * w, 0f)))
        }
}

/** Ô shimmer 1 mảng (placeholder block). */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(6.dp)) {
    Box(modifier.shimmer(shape))
}

/** Skeleton banner: 1 mảng chữ nhật cao [heightDp]. */
@Composable
fun AdShimmerBanner(modifier: Modifier = Modifier, heightDp: Int) {
    ShimmerBox(
        modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .padding(horizontal = 8.dp),
        shape = RoundedCornerShape(8.dp),
    )
}
