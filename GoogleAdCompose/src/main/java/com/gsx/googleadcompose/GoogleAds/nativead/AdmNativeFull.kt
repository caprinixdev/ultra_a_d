package com.gsx.googleadcompose.GoogleAds.nativead

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import kotlinx.coroutines.delay

/**
 * Native full-screen sau inter (kiểu `DialogInterFull2`): [pages] trang, mỗi trang 1 thanh progress
 * (nằm trong card đáy, dưới CTA) chạy [stepMs]. Nút tròn góc trên phải luôn hiện, mờ + vòng đếm lùi
 * quanh nút khi đang chạy: trang giữa = mũi tên skip, trang cuối = X. Chạy xong -> nút bấm được.
 * Back bị chặn tới khi tắt được.
 *
 * - Truyền [nativeAds] (preload, app sở hữu) -> show, KHÔNG destroy. Ad null -> mượn ad khác trong list.
 * - [nativeAds] == null -> tự load [pages] ad ([index]/[customIds]), tự destroy khi rời màn.
 * - Không có ad nào sau thời gian chờ / premium -> gọi [onClose] luôn.
 *
 * ```
 * val inter = AdmInterstitial { onDismissed = { showStory = true } }
 * if (showStory) AdmNativeFullStory(nativeAds = storyAds, onClose = { showStory = false })
 * ```
 */
@Composable
fun AdmNativeFullStory(
    modifier: Modifier = Modifier,
    nativeAds: List<NativeAd?>? = null,
    pages: Int = 2,
    stepMs: Long = 5_000L,
    index: Int = -1,
    customIds: List<String>? = null,
    onClose: () -> Unit,
    configure: AdmNativeAd.() -> Unit = {},
) {
    @Suppress("NAME_SHADOWING") val pages = pages.coerceAtLeast(1)
    val currentOnClose by rememberUpdatedState(onClose)
    if (AdmNativeAd.isPremiumBlocked()) {                   // premium -> không hiện, đi tiếp luôn
        LaunchedEffect(Unit) { currentOnClose() }
        return
    }
    val isPreview = LocalInspectionMode.current
    val selfLoad = nativeAds == null

    // ---- Self-load: mỗi trang 1 controller (giữ ad + hook), destroy khi rời màn ----
    val controllers = remember { List(pages) { AdmNativeAd() } }
    controllers.forEach { it.apply(configure) }
    val selfAds = remember { mutableStateListOf<NativeAd?>().apply { repeat(pages) { add(null) } } }
    val selfDone = remember { mutableStateListOf<Boolean>().apply { repeat(pages) { add(false) } } }
    LaunchedEffect(Unit) {
        if (isPreview || !selfLoad) return@LaunchedEffect
        controllers.forEachIndexed { i, c ->
            val cb: (NativeAd?) -> Unit = { ad -> selfAds[i] = ad; selfDone[i] = true }
            if (customIds != null) c.loadIds(customIds, index, cb) else c.load(index, cb)
        }
    }
    DisposableEffect(Unit) { onDispose { controllers.forEach { it.destroy() } } }

    fun adAt(i: Int): NativeAd? = if (selfLoad) selfAds.getOrNull(i) else nativeAds?.getOrNull(i)
    val available = (0 until pages).mapNotNull { adAt(it) }

    // ---- Chọn ad cho trang: ad của trang -> ad chưa trang nào dùng -> ad bất kỳ. Chốt 1 lần/trang ----
    var page by remember { mutableIntStateOf(0) }
    val locked = remember { mutableStateMapOf<Int, NativeAd>() }
    val shown = locked[page]
        ?: adAt(page)
        ?: available.firstOrNull { it !in locked.values }
        ?: available.firstOrNull()
    LaunchedEffect(page, shown) { if (shown != null && locked[page] == null) locked[page] = shown }
    LaunchedEffect(shown) { if (!selfLoad) shown?.adEventCallback = controllers[0] }   // hook impression/click/paid

    // ---- Không có ad nào (load xong hết / quá thời gian chờ) -> đóng ----
    var timedOut by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(WAIT_AD_TIMEOUT_MS); timedOut = true }
    val noAd = shown == null && locked.isEmpty() && (timedOut || (selfLoad && selfDone.all { it }))
    LaunchedEffect(noAd) { if (noAd && !isPreview) currentOnClose() }

    // ---- Timer trang: đếm theo frame clock (không bị animator scale = 0 bỏ qua) ----
    val hasAd = shown != null
    var progress by remember(page) { mutableFloatStateOf(0f) }
    var stepDone by remember(page) { mutableStateOf(false) }       // hết thanh -> nút skip / X bấm được
    LaunchedEffect(page, hasAd) {
        if (!hasAd) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (progress < 1f) {
            val now = withFrameMillis { it }
            progress = ((now - start).toFloat() / stepMs).coerceIn(0f, 1f)
        }
        stepDone = true
    }
    val isLast = page >= pages - 1
    val closable = stepDone && isLast

    // Back: chặn tới khi được tắt. Đang chờ ad (chưa hiện gì) -> cho thoát.
    BackHandler { if (!hasAd || closable) currentOnClose() }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (shown != null) {
            key(page) {
                NativeAdFullStoryLayout(
                    ad = shown,
                    modifier = Modifier.fillMaxSize(),
                    ctaColor = controllers[0].ctaColor,
                    ctaTextColor = controllers[0].ctaTextColor,
                )
            }
        } else {
            AdShimmerNative(Modifier.fillMaxSize(), NativeLayout.FULL)
        }

        // ===== Overlay (ngoài NativeAdView -> không tính click ad) =====
        StoryActionButton(
            isClose = isLast,
            remaining = if (stepDone || !hasAd) 0f else 1f - progress,
            enabled = stepDone,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .safeDrawingPadding()
                .padding(16.dp),
            onClick = { if (isLast) currentOnClose() else page += 1 },
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                // Nằm trong card của [NativeAdFullStoryLayout]: margin card 10dp + padding card 16dp.
                .padding(start = 26.dp, end = 26.dp, bottom = 26.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(pages) { i ->
                val fraction = when {
                    i < page -> 1f
                    i == page -> progress
                    else -> 0f
                }
                StoryProgressBar(fraction, Modifier.weight(1f))
            }
        }
    }
}

/** Chờ tối đa ngần này mà không có ad nào -> tự đóng. */
private const val WAIT_AD_TIMEOUT_MS = 10_000L

/**
 * Layout native full sau inter (giống app tham khảo): media phủ phần trên (nền trắng), card tối đè đáy
 * gồm icon + tên + badge "Ad" xanh, body 2 dòng, nút CTA pill to. Card chừa [barsSpace] dưới CTA cho
 * 2 thanh progress (vẽ NGOÀI NativeAdView để progress chạy không recompose asset ad).
 */
@Composable
fun NativeAdFullStoryLayout(
    ad: NativeAd,
    modifier: Modifier = Modifier,
    ctaColor: Color = Color(0xFF5B8DEF),
    ctaTextColor: Color = Color.White,
    cardColor: Color = Color(0xF22B2B2B),
    barsSpace: Dp = 40.dp,
) {
    NativeAdView(ad, modifier) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            // ----- Media phủ trên, chừa đáy cho card đè một phần -----
            NativeAdMediaView(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(bottom = 110.dp)
                    .background(Color.White),
            )

            // ----- Card tối đè đáy -----
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(10.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(cardColor)
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = barsSpace),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    rememberAdIcon(ad)?.let { bmp ->
                        NativeAdIconView(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))) {
                            Image(bitmap = bmp, contentDescription = "icon")
                        }
                    }
                    Box(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        ad.headline?.let {
                            NativeAdHeadlineView {
                                Text(
                                    it,
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.W700,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Text(
                        text = "Ad",
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .align(Alignment.Top)
                            .padding(start = 6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF5B8DEF))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
                ad.body?.let {
                    NativeAdBodyView(Modifier.padding(top = 10.dp)) {
                        Text(it, color = Color.White, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(14.dp))
                NativeAdCallToActionView(Modifier.fillMaxWidth().height(56.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(50))
                            .background(ctaColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = (ad.callToAction ?: "Install").uppercase(),
                            color = ctaTextColor,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.W700,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Thanh progress 8dp bo tròn: nền xám, phần chạy trắng (nằm trong card tối). */
@Composable
private fun StoryProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(8.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF6B6B6B)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .clip(RoundedCornerShape(50))
                .background(Color.White)
        )
    }
}

/**
 * Nút tròn xám (skip -> hoặc X) + vòng đếm lùi trắng quanh nút ([remaining] 1..0).
 * Chưa [enabled] -> icon mờ 50%, không bấm được (giống `btn_skip_full_2` / `btn_close_full_2`).
 */
@Composable
private fun StoryActionButton(
    isClose: Boolean,
    remaining: Float,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxSize().alpha(if (enabled) 1f else 0.5f)) {
            val s = size.width / 32f                         // toạ độ theo viewport 32 của vector gốc
            val sw = 2f * s
            drawCircle(Color(0xFF585858).copy(alpha = 0.7f))
            if (isClose) {
                drawLine(Color.White, Offset(11.05f * s, 11.05f * s), Offset(20.95f * s, 20.95f * s), sw, StrokeCap.Round)
                drawLine(Color.White, Offset(11.05f * s, 20.95f * s), Offset(20.95f * s, 11.05f * s), sw, StrokeCap.Round)
            } else {
                drawLine(Color.White, Offset(10f * s, 16f * s), Offset(22.35f * s, 16f * s), sw, StrokeCap.Round)
                drawLine(Color.White, Offset(17.34f * s, 11f * s), Offset(22.5f * s, 16f * s), sw, StrokeCap.Round)
                drawLine(Color.White, Offset(22.5f * s, 16f * s), Offset(17.34f * s, 21f * s), sw, StrokeCap.Round)
            }
        }
        if (remaining > 0f) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 2.dp.toPx()
                drawArc(
                    color = Color.White,
                    startAngle = -90f,
                    sweepAngle = 360f * remaining,
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke),
                )
            }
        }
    }
}
