package com.gsx.googleadcompose.GoogleAds.nativead

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.gsx.googleadcompose.GoogleAds.AdCore
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Góc xuất hiện ban đầu của [AdmNativePip]. */
enum class PipCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

private val PIP_WIDTH = 225.dp
private val PIP_HEIGHT = 238.dp
/** Sau khi hiện, chặn chạm ngần này (chống bấm nhầm). */
private const val TAP_GUARD_MS = 1_000L
/** Nút X khoá ngần này (vòng cam chạy hết mới bấm được). */
private const val CLOSE_LOCK_MS = 3_000L

/**
 * Native nổi kéo thả được (kiểu `AdmobNativePip`): card 225x238dp đặt ở [corner] cách lề [margin],
 * trượt vào từ mép. Kéo -> đi theo tay; thả -> hít về góc gần nhất. 1s đầu chặn chạm; nút X góc trên
 * trái khoá 3s (vòng cam). Bấm ad -> [reloadOnClick] load ad mới. Premium -> không hiện.
 *
 * Đặt làm con CUỐI của Box màn hình (phủ lên nội dung, chỗ trống vẫn chạm xuyên xuống dưới):
 * ```
 * Box(Modifier.fillMaxSize()) {
 *     Content()
 *     AdmNativePip(Modifier.fillMaxSize(), onClose = { })
 * }
 * ```
 */
@Composable
fun AdmNativePip(
    modifier: Modifier = Modifier,
    corner: PipCorner = PipCorner.TOP_RIGHT,
    margin: Dp = 10.dp,
    index: Int = -1,
    customIds: List<String>? = null,
    reloadOnClick: Boolean = true,
    onClose: () -> Unit = {},
    configure: AdmNativeAd.() -> Unit = {},
) {
    if (AdmNativeAd.isPremiumBlocked()) return
    val isPreview = LocalInspectionMode.current
    val controller = remember { AdmNativeAd() }
    controller.apply(configure)
    val currentOnClose by rememberUpdatedState(onClose)

    var generation by remember { mutableIntStateOf(0) }       // tăng -> load ad mới + hiện lại từ đầu
    var ad by remember { mutableStateOf<NativeAd?>(null) }
    var closed by remember { mutableStateOf(false) }

    LaunchedEffect(generation) {
        if (isPreview) return@LaunchedEffect
        ad?.let { old -> ad = null; old.destroy() }
        val cb: (NativeAd?) -> Unit = { loaded ->
            if (loaded != null && reloadOnClick) {
                loaded.adEventCallback = ReloadOnClickCallback(controller) { generation++ }
            }
            ad = loaded
        }
        if (customIds != null) controller.loadIds(customIds, index, cb) else controller.load(index, cb)
    }
    DisposableEffect(Unit) { onDispose { controller.destroy() } }

    val shown = ad
    if (closed || shown == null) return

    BoxWithConstraints(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
        val areaW = constraints.maxWidth.toFloat()
        val areaH = constraints.maxHeight.toFloat()
        key(generation) {
            PipFloating(ad = shown, corner = corner, margin = margin, areaW = areaW, areaH = areaH) {
                closed = true
                currentOnClose()
            }
        }
    }
}

/** Giữ hook của controller (impression/paid/click) + bắn [onClick] để reload. */
private class ReloadOnClickCallback(
    private val base: NativeAdEventCallback,
    private val onClick: () -> Unit,
) : NativeAdEventCallback {
    override fun onAdImpression() = base.onAdImpression()
    override fun onAdPaid(value: AdValue) = base.onAdPaid(value)
    override fun onAdClicked() {
        base.onAdClicked()
        AdCore.onMain(onClick)   // click đến từ thread nền -> đổi Compose state (reload) trên main
    }
}

@Composable
private fun PipFloating(
    ad: NativeAd,
    corner: PipCorner,
    margin: Dp,
    areaW: Float,
    areaH: Float,
    onClose: () -> Unit,
) {
    val density = LocalDensity.current
    val cardW = with(density) { PIP_WIDTH.toPx() }
    val cardH = with(density) { PIP_HEIGHT.toPx() }
    val m = with(density) { margin.toPx() }
    fun cornerTarget(left: Boolean, top: Boolean) =
        Offset(if (left) m else areaW - cardW - m, if (top) m else areaH - cardH - m)

    val startLeft = corner == PipCorner.TOP_LEFT || corner == PipCorner.BOTTOM_LEFT
    val startTop = corner == PipCorner.TOP_LEFT || corner == PipCorner.TOP_RIGHT
    val start = cornerTarget(startLeft, startTop)

    val scope = rememberCoroutineScope()
    val x = remember { Animatable(if (startLeft) -cardW else areaW) }   // bắt đầu ngoài mép -> trượt vào
    val y = remember { Animatable(start.y) }
    val tapGuardUntil = remember { SystemClock.uptimeMillis() + TAP_GUARD_MS }
    LaunchedEffect(Unit) { x.animateTo(start.x, tween(300, easing = LinearOutSlowInEasing)) }

    Box(
        modifier = Modifier
            .offset { IntOffset(x.value.roundToInt(), y.value.roundToInt()) }
            .size(PIP_WIDTH, PIP_HEIGHT)
            .pointerInput(Unit) {
                awaitEachGesture {
                    // Pass Initial: xét trước con (NativeAdView). Kéo quá slop -> consume -> con nhận cancel.
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val guarded = SystemClock.uptimeMillis() < tapGuardUntil
                    if (guarded) down.consume()
                    var dragging = false
                    var total = Offset.Zero
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (dragging || guarded) change.consume()
                            break
                        }
                        val delta = change.positionChange()
                        if (!dragging) {
                            total += delta
                            if (total.getDistance() > viewConfiguration.touchSlop) dragging = true
                        }
                        if (dragging) {
                            change.consume()
                            scope.launch {
                                x.snapTo(x.value + delta.x)
                                y.snapTo(y.value + delta.y)
                            }
                        } else if (guarded) {
                            change.consume()
                        }
                    }
                    if (dragging) {
                        // Thả -> hít về góc gần nhất (theo tâm card).
                        val target = cornerTarget(x.value + cardW / 2 < areaW / 2, y.value + cardH / 2 < areaH / 2)
                        scope.launch { x.animateTo(target.x, tween(250, easing = LinearOutSlowInEasing)) }
                        scope.launch { y.animateTo(target.y, tween(250, easing = LinearOutSlowInEasing)) }
                    }
                }
            },
    ) {
        NativeAdPipLayout(ad = ad, modifier = Modifier.fillMaxSize())
        PipCloseButton(Modifier.align(Alignment.TopStart).padding(8.dp), onClick = onClose)
    }
}

/**
 * Layout PIP (giống `layout_native_pip_pro`): card kem bo 4dp viền nhạt, media trên, icon + badge "Ad"
 * cam + headline/body, nút CTA gradient cam full-width.
 */
@Composable
fun NativeAdPipLayout(
    ad: NativeAd,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFFFFF5E5),
    textColor: Color = Color(0xFF242424),
    ctaGradient: List<Color> = listOf(Color(0xFFF8B213), Color(0xFFFB7311)),
    ctaTextColor: Color = Color.White,
) {
    NativeAdView(ad, modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(4.dp))
                .background(backgroundColor)
                .border(1.dp, Color(0xFFEDEDED), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            NativeAdMediaView(Modifier.fillMaxWidth().weight(1f))
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                rememberAdIcon(ad)?.let { bmp ->
                    NativeAdIconView(Modifier.size(32.dp).clip(RoundedCornerShape(6.dp))) {
                        Image(bitmap = bmp, contentDescription = "icon")
                    }
                }
                Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Ad",
                            color = Color.White,
                            fontSize = 8.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFFB7311))
                                .padding(horizontal = 2.dp, vertical = 1.dp),
                        )
                        ad.headline?.let {
                            NativeAdHeadlineView(Modifier.weight(1f, fill = false).padding(start = 4.dp)) {
                                Text(it, color = textColor, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    ad.body?.let {
                        NativeAdBodyView {
                            Text(
                                it,
                                color = textColor.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            NativeAdCallToActionView(Modifier.fillMaxWidth().height(40.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.verticalGradient(ctaGradient)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = (ad.callToAction ?: "Open now").uppercase(),
                        color = ctaTextColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W700,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Nút X 24dp (giống view `WT0`): tròn xám, X trắng; 3s đầu mờ + vòng cam chạy lùi, chưa bấm được.
 * Đếm theo frame clock (animator scale = 0 vẫn khoá đủ 3s).
 */
@Composable
private fun PipCloseButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (progress < 1f) {
            val now = withFrameMillis { it }
            progress = ((now - start).toFloat() / CLOSE_LOCK_MS).coerceIn(0f, 1f)
        }
    }
    val enabled = progress >= 1f

    Box(modifier = modifier.size(24.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick)) {
        Canvas(Modifier.fillMaxSize()) {
            val ring = 1.2.dp.toPx()
            val inset = ring / 2
            drawCircle(Color(0xFF585858).copy(alpha = if (enabled) 0.7f else 0.3f))
            if (!enabled) {
                drawArc(
                    color = Color(0xFFFB7311),
                    startAngle = 270f,
                    sweepAngle = -(360f - progress * 360f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - ring, size.height - ring),
                    style = Stroke(ring, cap = StrokeCap.Round),
                )
            }
            val pad = (size.minDimension / 2 - ring) * 2f / 3f
            val xColor = Color.White.copy(alpha = if (enabled) 1f else 0.5f)
            val sw = 1.4.dp.toPx()
            drawLine(xColor, Offset(inset + pad, inset + pad), Offset(size.width - inset - pad, size.height - inset - pad), sw, StrokeCap.Round)
            drawLine(xColor, Offset(inset + pad, size.height - inset - pad), Offset(size.width - inset - pad, inset + pad), sw, StrokeCap.Round)
        }
    }
}
