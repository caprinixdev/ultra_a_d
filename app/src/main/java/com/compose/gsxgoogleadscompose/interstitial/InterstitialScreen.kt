package com.compose.gsxgoogleadscompose.interstitial

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.compose.gsxgoogleadscompose.common.PremiumStatusText
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.gsx.googleadcompose.GoogleAds.AdmSharedInterstitial
import com.gsx.googleadcompose.GoogleAds.nativead.AdmNativeAd
import com.gsx.googleadcompose.GoogleAds.nativead.AdmNativeFullStory

/**
 * Màn test interstitial — dùng [AdmSharedInterstitial] (1 inter cho cả app, không chết theo màn):
 * 1. Inter thường: đóng inter là xong.
 * 2. Inter + native full: đóng inter -> native full 2 trang (mỗi trang 5s -> skip, trang cuối 5s -> X). 2 native nạp
 *    lúc inter vừa hiện (onShowed), không nạp cùng lúc inter.
 * 3. Đếm lượt: hiện ở lượt 1, 3, 5… như `countToShowAds` bản 1.0.20; chỉ nạp sẵn khi lượt kế sẽ hiện.
 */
@Composable
fun InterstitialScreen() {
    var status by remember { mutableStateOf("Chưa load") }
    var withNativeFull by remember { mutableStateOf(false) }   // kiểu của lần show đang chạy
    var showStory by remember { mutableStateOf(false) }

    // 2 native cho story: nạp lúc inter vừa hiện (onShowed) — inter không hiện thì không phí native nào
    val storyAds = remember { mutableStateListOf<NativeAd?>(null, null) }
    DisposableEffect(Unit) { onDispose { storyAds.forEach { it?.destroy() } } }

    fun preloadStoryAds() {
        storyAds.indices.forEach { i ->
            if (storyAds[i] == null) AdmNativeAd.preload { storyAds[i] = it }
        }
    }

    val inter = remember { AdmSharedInterstitial.get() }
    DisposableEffect(inter) {
        inter.onAvailable = { status = "Ad sẵn sàng ✅" }
        inter.onLoadFailed = { type -> status = "Nạp lỗi: $type" }
        inter.onShowed = { if (withNativeFull) preloadStoryAds() }
        inter.onPaid = { v -> status = "Paid:${v.valueMicros} ${v.currencyCode}" }
        // instance sống tiếp sau màn này: gỡ hook của màn khi rời đi
        onDispose { inter.onAvailable = {}; inter.onLoadFailed = {}; inter.onShowed = {}; inter.onPaid = {} }
    }
    // vào màn: nạp sẵn chỉ khi lượt bấm "3." kế tiếp sẽ hiện ad
    LaunchedEffect(Unit) { inter.preloadIfDue(COUNTER_KEY, startAds = 1, loopAds = 2) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF120726))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Interstitial", color = Color(0xFF52DE11), fontSize = 32.sp, fontWeight = FontWeight.W700)
            PremiumStatusText()
            Text(
                text = status,
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 16.dp, bottom = 32.dp),
            )

            Button(
                onClick = {
                    status = "Đang load..."
                    inter.load(1)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Load") }

            OutlinedButton(
                onClick = {
                    withNativeFull = false
                    inter.show { shown -> status = if (shown) "Inter đã đóng" else "Không hiện được" }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text("1. Show inter thường") }

            OutlinedButton(
                onClick = {
                    withNativeFull = true
                    inter.show { shown -> if (shown) showStory = true else status = "Không hiện được" }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text("2. Show inter + native full") }

            OutlinedButton(
                onClick = {
                    withNativeFull = false
                    inter.countToShow(keyCounterAd = COUNTER_KEY, startAds = 1, loopAds = 2) { shown ->
                        status = if (shown) "Tới lượt: đã hiện" else "Chưa tới lượt (hiện ở lượt 1, 3, 5…)"
                        inter.preloadIfDue(COUNTER_KEY, startAds = 1, loopAds = 2)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text("3. Đếm lượt: hiện lượt 1, 3, 5…") }
        }

        if (showStory) {
            AdmNativeFullStory(
                modifier = Modifier.fillMaxSize(),
                nativeAds = storyAds,
                onClose = {
                    showStory = false
                    storyAds.indices.forEach { i -> storyAds[i]?.destroy(); storyAds[i] = null }
                },
            ) {
                ctaColor = Color(0xFF5B8DEF)
                ctaTextColor = Color.White
            }
        }
    }
}

private const val COUNTER_KEY = "sample_inter_counter"
