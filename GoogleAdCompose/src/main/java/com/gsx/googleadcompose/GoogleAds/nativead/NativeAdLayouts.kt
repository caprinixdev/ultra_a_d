package com.gsx.googleadcompose.GoogleAds.nativead

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.gsx.googleadcompose.GoogleAds.ShimmerBox
import kotlinx.coroutines.delay

/**
 * Layout native dựng sẵn (Compose). Mỗi layout = 1 hàm render asset qua [NativeAdView] wrapper.
 * Mỗi value -> 1 nhánh trong [NativeLayoutRender]. Thêm layout mới: thêm enum + nhánh + hàm.
 */
enum class NativeLayout {
    /** Native cỡ thường, đủ text: icon + headline + store/rating + media + body + nút install. */
    NORMAL,

    /** Native nhỏ: KHÔNG media — icon + headline + store/rating + body + nút install. */
    SMALL,

    /** Native nhỏ: content trái (icon + text), nút install full-height bên phải. */
    SMALL_2,

    /** Media + hàng SMALL_2, mũi tên góc trên trái; ấn -> ẩn CẢ ad luôn (mặc định không mở lại). */
    COLLAPSIBLE,

    /** Full-screen: media nền, Title/Body overlay trên, card trắng (icon + Install) đáy. */
    FULL,
}

/** Map [NativeLayout] -> composable layout. Màu lấy từ controller [c]. */
@Composable
internal fun NativeLayoutRender(
    ad: NativeAd,
    layout: NativeLayout,
    c: AdmNativeAd,
    modifier: Modifier,
) {
    when (layout) {
        NativeLayout.NORMAL -> NativeAdNormalLayout(
            ad = ad,
            modifier = modifier,
            backgroundColor = c.backgroundColor,
            ctaColor = c.ctaColor,
            ctaTextColor = c.ctaTextColor,
            textColor = c.textColor,
            subTextColor = c.subTextColor,
        )

        NativeLayout.SMALL -> NativeAdSmallLayout(
            ad = ad,
            modifier = modifier,
            backgroundColor = c.backgroundColor,
            ctaColor = c.ctaColor,
            ctaTextColor = c.ctaTextColor,
            textColor = c.textColor,
            subTextColor = c.subTextColor,
        )

        NativeLayout.SMALL_2 -> NativeAdSmall2Layout(
            ad = ad,
            modifier = modifier,
            backgroundColor = c.backgroundColor,
            ctaColor = c.ctaColor,
            ctaTextColor = c.ctaTextColor,
            textColor = c.textColor,
            subTextColor = c.subTextColor,
        )

        NativeLayout.COLLAPSIBLE -> NativeAdCollapsibleLayout(
            ad = ad,
            modifier = modifier,
            backgroundColor = c.backgroundColor,
            ctaColor = c.ctaColor,
            ctaTextColor = c.ctaTextColor,
            textColor = c.textColor,
            subTextColor = c.subTextColor,
            collapseDelaySeconds = c.collapseDelaySeconds,
            collapseReshowSeconds = c.collapseReshowSeconds,
            collapseRingColor = c.collapseRingColor,
        )

        NativeLayout.FULL -> NativeAdFullLayout(
            ad = ad,
            modifier = modifier,
            ctaColor = c.ctaColor,
            ctaTextColor = c.ctaTextColor,
        )
    }
}

/**
 * Hình nút CTA đặt trong [NativeAdCallToActionView]. CHỈ là hình vẽ, tuyệt đối không dùng `Button` hay
 * `clickable`: Compose sẽ nuốt lượt chạm nên SDK không nhận được click, ad có impression mà bấm nút không ăn.
 */
@Composable
private fun NativeAdCtaLabel(
    callToAction: String?,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor)
            .defaultMinSize(minWidth = 58.dp, minHeight = 40.dp)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = callToAction ?: "Install",
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.W700,
            maxLines = 1,
        )
    }
}

/**
 * Layout NORMAL — native cỡ thường, đủ text. Card nền [backgroundColor], nút install full-width
 * màu [ctaColor]. Asset đăng ký qua wrapper -> click/impression track đúng policy.
 */
@Composable
fun NativeAdNormalLayout(
    ad: NativeAd,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF1E1330),
    ctaColor: Color = Color(0xFF52DE11),
    ctaTextColor: Color = Color(0xFF062100),
    textColor: Color = Color.White,
    subTextColor: Color = Color(0xFFB9AED0),
) {
    NativeAdView(ad, modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .background(backgroundColor)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ----- Hàng đầu: icon + headline + store/rating, badge "Ad" góc phải -----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rememberAdIcon(ad)?.let { bmp ->
                    NativeAdIconView(Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))) {
                        Image(bitmap = bmp, contentDescription = "icon")
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    ad.headline?.let {
                        NativeAdHeadlineView {
                            Text(
                                text = it,
                                color = textColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.W700,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ad.starRating?.let {
                            NativeAdStarRatingView {
                                Text("★ ${"%.1f".format(it)}", color = ctaColor, fontSize = 13.sp)
                            }
                        }
                        ad.store?.let {
                            NativeAdStoreView(Modifier.padding(start = 6.dp)) {
                                Text(it, color = subTextColor, fontSize = 13.sp, maxLines = 1)
                            }
                        }
                    }
                }
                NativeAdAttribution(text = "Ad")
            }

            // ----- Media (video: cao theo aspectRatio nhưng kẹp 120..320dp; ảnh: 120..150dp) -----
            if (ad.mediaContent.hasVideoContent) {
                val ratio = ad.mediaContent.aspectRatio.let { if (it > 0) it else 16f / 9f }
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val mediaHeight = (maxWidth / ratio).coerceIn(120.dp, 320.dp)
                    NativeAdMediaView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(mediaHeight)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
            } else {
                NativeAdMediaView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 150.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }

            // ----- Body (đủ text) -----
            ad.body?.let {
                NativeAdBodyView {
                    Text(
                        text = it,
                        color = subTextColor,
                        fontSize = 14.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // ----- Nút install full-width màu ctaColor -----
            NativeAdCallToActionView(Modifier.fillMaxWidth()) {
                NativeAdCtaLabel(ad.callToAction, ctaColor, ctaTextColor, Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * Layout SMALL — native nhỏ, KHÔNG media: hàng icon + (headline/rating/store/body), nút install
 * full-width BÊN DƯỚI. Card nền [backgroundColor], nút màu [ctaColor].
 */
@Composable
fun NativeAdSmallLayout(
    ad: NativeAd,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF1E1330),
    ctaColor: Color = Color(0xFF52DE11),
    ctaTextColor: Color = Color(0xFF062100),
    textColor: Color = Color.White,
    subTextColor: Color = Color(0xFFB9AED0),
) {
    NativeAdView(ad, modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .background(backgroundColor)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ----- Hàng icon + content -----
            Row(verticalAlignment = Alignment.CenterVertically) {
                rememberAdIcon(ad)?.let { bmp ->
                    NativeAdIconView(Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))) {
                        Image(bitmap = bmp, contentDescription = "icon")
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ad.headline?.let {
                            NativeAdHeadlineView(Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = it,
                                    color = textColor,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.W700,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        NativeAdAttribution(modifier = Modifier.padding(start = 6.dp), text = "Ad")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ad.starRating?.let {
                            NativeAdStarRatingView {
                                Text("★ ${"%.1f".format(it)}", color = ctaColor, fontSize = 12.sp)
                            }
                        }
                        ad.store?.let {
                            NativeAdStoreView(Modifier.padding(start = 6.dp)) {
                                Text(it, color = subTextColor, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                    ad.body?.let {
                        NativeAdBodyView {
                            Text(
                                text = it,
                                color = subTextColor,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            // ----- Nút install full-width bên dưới -----
            NativeAdCallToActionView(Modifier.fillMaxWidth()) {
                NativeAdCtaLabel(ad.callToAction, ctaColor, ctaTextColor, Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * Layout SMALL_2 — content trái (icon + headline/rating/body), nút install full-height bên phải.
 * KHÔNG media. Card nền [backgroundColor], nút màu [ctaColor].
 */
@Composable
fun NativeAdSmall2Layout(
    ad: NativeAd,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF1E1330),
    ctaColor: Color = Color(0xFF52DE11),
    ctaTextColor: Color = Color(0xFF062100),
    textColor: Color = Color.White,
    subTextColor: Color = Color(0xFFB9AED0),
) {
    NativeAdView(ad, modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .background(backgroundColor)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ----- Content trái -----
            rememberAdIcon(ad)?.let { bmp ->
                NativeAdIconView(Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))) {
                    Image(bitmap = bmp, contentDescription = "icon")
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ad.headline?.let {
                        NativeAdHeadlineView(Modifier.weight(1f, fill = false)) {
                            Text(
                                text = it,
                                color = textColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.W700,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    NativeAdAttribution(modifier = Modifier.padding(start = 6.dp), text = "Ad")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ad.starRating?.let {
                        NativeAdStarRatingView {
                            Text("★ ${"%.1f".format(it)}", color = ctaColor, fontSize = 12.sp)
                        }
                    }
                    ad.store?.let {
                        NativeAdStoreView(Modifier.padding(start = 6.dp)) {
                            Text(it, color = subTextColor, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
                ad.body?.let {
                    NativeAdBodyView {
                        Text(
                            text = it,
                            color = subTextColor,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // ----- Nút install full-height bên phải -----
            NativeAdCallToActionView(Modifier.fillMaxHeight()) {
                NativeAdCtaLabel(ad.callToAction, ctaColor, ctaTextColor, Modifier.fillMaxHeight())
            }
        }
    }
}

/**
 * Layout COLLAPSIBLE — media + hàng [NativeAdSmall2Layout]. Mũi tên xuống góc trên trái đè media;
 * ấn -> ẩn CẢ ad luôn (render rỗng, mặc định không mở lại). Mũi tên KHÔNG bọc wrapper -> không tính click ad.
 *
 * [collapseDelaySeconds] > 0: trong N giây đầu mũi tên mờ + vòng tròn chạy quanh nút, đủ vòng mới ấn được.
 * [collapseReshowSeconds] > 0: ấn mũi tên xong, sau N giây ad tự hiện lại (0 = ẩn hẳn).
 * [collapseRingColor]: màu vòng tròn chạy quanh nút lúc chờ.
 */
@Composable
fun NativeAdCollapsibleLayout(
    ad: NativeAd,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF1E1330),
    ctaColor: Color = Color(0xFF52DE11),
    ctaTextColor: Color = Color(0xFF062100),
    textColor: Color = Color.White,
    subTextColor: Color = Color(0xFFB9AED0),
    collapseDelaySeconds: Int = 0,
    collapseReshowSeconds: Int = 0,
    collapseRingColor: Color = Color.White,
) {
    var closed by remember { mutableStateOf(false) }
    if (closed) {                                       // đã ấn mũi tên -> ẩn cả ad
        // Hẹn hiện lại CÙNG ad đó sau N giây; hiện lại thì [collapseDelaySeconds] đếm lại từ đầu.
        if (collapseReshowSeconds > 0) {
            LaunchedEffect(collapseReshowSeconds) {
                delay(collapseReshowSeconds * 1000L)
                closed = false
            }
        }
        return
    }

    // Đếm 0f -> 1f trong [collapseDelaySeconds] giây kể từ lúc ad hiện. Tính theo frame time thật
    // (không dùng tween) -> tắt animation trong Developer options cũng KHÔNG bỏ qua được delay.
    var closeProgress by remember { mutableFloatStateOf(if (collapseDelaySeconds > 0) 0f else 1f) }
    LaunchedEffect(collapseDelaySeconds) {
        val totalNanos = collapseDelaySeconds * 1_000_000_000L
        if (totalNanos <= 0L) { closeProgress = 1f; return@LaunchedEffect }
        closeProgress = 0f
        val start = withFrameNanos { it }
        while (closeProgress < 1f) {
            closeProgress = withFrameNanos { ((it - start).toFloat() / totalNanos).coerceAtMost(1f) }
        }
    }

    NativeAdView(ad, modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .background(backgroundColor)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ----- Media + mũi tên đóng đè góc trên trái -----
            Box(modifier = Modifier.fillMaxWidth()) {
                if (ad.mediaContent.hasVideoContent) {
                    val ratio = ad.mediaContent.aspectRatio.let { if (it > 0) it else 16f / 9f }
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val mediaHeight = (maxWidth / ratio).coerceIn(120.dp, 320.dp)
                        NativeAdMediaView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(mediaHeight)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    }
                } else {
                    NativeAdMediaView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 150.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
                ChevronToggle(
                    expanded = false,
                    tint = Color.White,
                    background = Color(0xFF585858).copy(alpha = 0.7f),   // nền tối như nút X của FULL/PiP -> vòng sáng nhìn rõ
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                    ringColor = collapseRingColor,
                    progress = { closeProgress },
                ) { closed = true }
            }

            // ----- Hàng content + nút install -----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rememberAdIcon(ad)?.let { bmp ->
                    NativeAdIconView(Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))) {
                        Image(bitmap = bmp, contentDescription = "icon")
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ad.headline?.let {
                            NativeAdHeadlineView(Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = it,
                                    color = textColor,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.W700,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        NativeAdAttribution(modifier = Modifier.padding(start = 6.dp), text = "Ad")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ad.starRating?.let {
                            NativeAdStarRatingView {
                                Text("★ ${"%.1f".format(it)}", color = ctaColor, fontSize = 12.sp)
                            }
                        }
                        ad.store?.let {
                            NativeAdStoreView(Modifier.padding(start = 6.dp)) {
                                Text(it, color = subTextColor, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                    ad.body?.let {
                        NativeAdBodyView {
                            Text(
                                text = it,
                                color = subTextColor,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                NativeAdCallToActionView(Modifier.fillMaxHeight()) {
                    NativeAdCtaLabel(ad.callToAction, ctaColor, ctaTextColor, Modifier.fillMaxHeight())
                }
            }
        }
    }
}

/**
 * Mũi tên chevron (kiểu nút collapse của banner): vẽ Canvas, xoay 180° khi mở.
 *
 * [progress] 0f..1f = tiến độ chờ: < 1f thì mũi tên mờ + vòng cung chạy quanh nút, CHƯA gọi [onClick];
 * đủ 1f mới ấn được. Đọc trong draw/click (lambda) -> chạy mỗi frame mà không recompose.
 */
@Composable
private fun ChevronToggle(
    expanded: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    background: Color = Color.White.copy(alpha = 0.12f),
    ringColor: Color = tint,
    progress: () -> Float = { 1f },
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(background)
            .drawBehind {
                val p = progress()
                if (p < 1f) {
                    val sw = 2.dp.toPx()
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * p,
                        useCenter = false,
                        topLeft = Offset(sw / 2, sw / 2),
                        size = Size(size.width - sw, size.height - sw),
                        style = Stroke(sw, cap = StrokeCap.Round),
                    )
                }
            }
            // Luôn clickable (kể cả lúc chờ) để NUỐT tap -> không lọt xuống media bên dưới thành click ad.
            .clickable { if (progress() >= 1f) onClick() }
            .padding(7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier
            .fillMaxSize()
            .rotate(if (expanded) 180f else 0f)) {
            val w = size.width
            val h = size.height
            val sw = 1.8.dp.toPx()
            val color = if (progress() < 1f) tint.copy(alpha = 0.35f) else tint
            // chữ "v": 2 nét từ 2 mép trên xuống giữa dưới
            drawLine(
                color,
                Offset(w * 0.18f, h * 0.35f),
                Offset(w * 0.5f, h * 0.68f),
                sw,
                StrokeCap.Round
            )
            drawLine(
                color,
                Offset(w * 0.82f, h * 0.35f),
                Offset(w * 0.5f, h * 0.68f),
                sw,
                StrokeCap.Round
            )
        }
    }
}

/**
 * Layout FULL — native full-screen: media nền (9:16), Title/Body overlay trên + menu "⋮", dải
 * "Install now"/chevron/"Ad" dưới media, card trắng (icon + headline + nút Install) đáy.
 */
@Composable
fun NativeAdFullLayout(
    ad: NativeAd,
    modifier: Modifier = Modifier,
    ctaColor: Color = Color(0xFF1E8E3E),
    ctaTextColor: Color = Color.White,
) {
    NativeAdView(ad, modifier) {
        Box(modifier = Modifier

            .fillMaxSize()
            .background(Color(0xFF000000)
            )) {
            // ===== Media phủ toàn màn =====
            NativeAdMediaView(modifier = Modifier.matchParentSize())

            // ===== Overlay trên: Title + Body (trái) + menu "⋮" (phải) =====
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    ad.headline?.let {
                        Text(
                            it,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.W700,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    ad.body?.let {
                        NativeAdBodyView {
                            Text(
                                it,
                                color = Color.White,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // ===== Nhóm đáy đè lên media: dải + card + swipe =====
            Column(modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .safeDrawingPadding()) {
                // ----- Dải "Install now" | chevron ^ | "Ad" -----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.weight(1f))
                    NativeAdAttribution(text = "Ad")
                }

                // ----- Card trắng: cách mép + bo 4 góc, đè lên media -----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    rememberAdIcon(ad)?.let { bmp ->
                        NativeAdIconView(Modifier
                            .size(67.dp)
                            .clip(RoundedCornerShape(12.dp))) {
                            Image(bitmap = bmp, contentDescription = "icon")
                        }
                    }
                    Column(modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)) {
                        ad.headline?.let {
                            NativeAdHeadlineView {
                                Text(
                                    it,
                                    color = Color(0xFF1A1A1A),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.W700,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        ad.store?.let {
                            Text(it, color = Color(0xFF707070), fontSize = 12.sp, maxLines = 1)
                        }
                    }
                    NativeAdCallToActionView {
                        NativeAdCtaLabel(ad.callToAction, ctaColor, ctaTextColor)
                    }
                }
            }
        }
    }
}

/**
 * Skeleton shimmer cho native lúc đang load. NORMAL + COLLAPSIBLE có khối media (collapsible tự mở).
 * Nền card [backgroundColor] khớp layout -> load xong không bị nhảy.
 */
@Composable
fun AdShimmerNative(
    modifier: Modifier = Modifier,
    layout: NativeLayout = NativeLayout.NORMAL,
    backgroundColor: Color = Color(0xFF1E1330),
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
        .background(backgroundColor)
        .padding(12.dp)

    when (layout) {
        // NORMAL: dọc — icon+2 dòng, media, body, nút full-width.
        NativeLayout.NORMAL -> Column(
            cardModifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ShimmerHeaderRow()
            ShimmerBox(Modifier
                .fillMaxWidth()
                .height(150.dp), RoundedCornerShape(10.dp))
            ShimmerBox(Modifier
                .fillMaxWidth()
                .height(12.dp))
            ShimmerBox(Modifier
                .fillMaxWidth(0.7f)
                .height(12.dp))
            ShimmerBox(Modifier
                .fillMaxWidth()
                .height(40.dp), RoundedCornerShape(10.dp))
        }
        // SMALL: hàng icon+2 dòng, nút full-width bên dưới.
        NativeLayout.SMALL -> Column(
            cardModifier,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ShimmerHeaderRow()
            ShimmerBox(Modifier
                .fillMaxWidth()
                .height(40.dp), RoundedCornerShape(10.dp))
        }
        // SMALL_2: 1 hàng — icon + 2 dòng + nút phải.
        NativeLayout.SMALL_2 -> Row(cardModifier, verticalAlignment = Alignment.CenterVertically) {
            ShimmerHeaderRow(Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            ShimmerBox(Modifier.size(width = 78.dp, height = 40.dp), RoundedCornerShape(10.dp))
        }
        // COLLAPSIBLE: media TRÊN (tự mở) + hàng SMALL_2.
        NativeLayout.COLLAPSIBLE -> Column(
            cardModifier,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ShimmerBox(Modifier
                .fillMaxWidth()
                .height(150.dp), RoundedCornerShape(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShimmerHeaderRow(Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                ShimmerBox(Modifier.size(width = 78.dp, height = 40.dp), RoundedCornerShape(10.dp))
            }
        }
        // FULL: media phủ toàn màn + card trắng đè đáy.
        NativeLayout.FULL -> Box(modifier
            .fillMaxSize()
            .background(Color(0xFF000000))) {
            ShimmerBox(Modifier.matchParentSize())
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShimmerHeaderRow(Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                ShimmerBox(Modifier.size(width = 78.dp, height = 40.dp), RoundedCornerShape(10.dp))
            }
        }
    }
}

/** Header skeleton dùng chung: icon vuông + 2 dòng chữ. */
@Composable
private fun ShimmerHeaderRow(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        ShimmerBox(Modifier.size(48.dp), RoundedCornerShape(8.dp))
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ShimmerBox(Modifier
                .fillMaxWidth(0.6f)
                .height(14.dp))
            ShimmerBox(Modifier
                .fillMaxWidth(0.4f)
                .height(12.dp))
        }
    }
}
