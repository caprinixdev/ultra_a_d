# GSX Google Ads Compose

[![](https://jitpack.io/v/caprinixdev/ultra_a_d.svg)](https://jitpack.io/#caprinixdev/ultra_a_d)

Thư viện gói **Google Mobile Ads (GMA Next-Gen SDK)**, **UMP consent** và **Google Play Billing v9** cho **Jetpack Compose**. Mọi loại ad bọc thành composable / helper: tự load, tự preload, tự destroy khi rời màn, tự chặn khi user premium hoặc offline.

- Module thư viện: `:GoogleAdCompose` (package `com.gsx.googleadcompose`)
- Module app demo: `:app`
- `minSdk = 24`

---

## Mục lục

1. [Cài đặt](#1-cài-đặt)
2. [Init UMP + Mobile Ads](#2-init-ump--mobile-ads) — **làm trước tiên**
3. [App Open ad](#3-app-open-ad)
4. [Native ad](#4-native-ad)
5. [Banner ad](#5-banner-ad)
6. [Interstitial ad](#6-interstitial-ad)
7. [Các ad khác (Reward, Reward Interstitial)](#7-các-ad-khác)
8. [Billing và các cái khác](#8-billing-và-các-cái-khác)
9. [Đo fill rate và lần ra crash](#9-đo-fill-rate-và-lần-ra-crash)

> **Thứ tự khởi tạo bắt buộc:** `init UMP` → ad sẵn sàng → dùng Open / Native / Banner / Inter / Reward. Mọi ad check `AdCore.isMobileAdsReady`; chưa init UMP thì báo lỗi `UMP_IS_NOT_ACTIVE`.

---

## 1. Cài đặt

### 1.1 Thêm dependency (JitPack)

`settings.gradle.kts` — thêm repo JitPack:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

`app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.caprinixdev:ultra_a_d:1.0.24")
}
```

Đổi version sang tag release mới nhất hoặc commit hash. Xem các tag ở [JitPack](https://jitpack.io/#caprinixdev/ultra_a_d).

Lib đã `api(...)` sẵn `ads.mobile.sdk` (GMA Next-Gen) + `billing.ktx`, app không cần khai báo lại.

**Bật R8 cho release** (Play Console › Android vitals › *DEX code optimization*). Lib tự mang keep rule cần thiết (kể cả rule WorkManager của AdMob — thiếu là crash `Failed to create an instance of androidx.work.impl.WorkDatabase` ngay lúc mở app), app không cần thêm rule nào cho lib:

```kotlin
buildTypes {
    release {
        optimization { enable = true }
    }
}
```

Mọi hook (`onLoaded`, `onDismissed`, `onError`, `onPaid`, `onReward`...) chạy trên **main thread** — gọi navigate/toast/đổi state thẳng trong hook được.

### 1.2 AdMob App ID trong manifest

`AndroidManifest.xml` của app (UMP và Mobile Ads đều cần):

```xml
<application android:name=".App">
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="YOUR_APPLICATION_ID" />
</application>
```

> `Prefs` tự init qua một `ContentProvider` của lib **trước** `Application.onCreate` — app **không** cần gọi tay.

### 1.3 Cấp ad unit id

Set ID trong `Application.onCreate` (đọc từ `keys.xml` hay backend tuỳ ý):

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()

        AdmConfigAdId.listBannerAdUnitID             = resources.getStringArray(R.array.banner_ad_units).toList()
        AdmConfigAdId.listInterstitialAdUnitID       = resources.getStringArray(R.array.interstitial_ad_units).toList()
        AdmConfigAdId.listRewardAdUnitID             = resources.getStringArray(R.array.rewarded_ad_units).toList()
        AdmConfigAdId.listRewardInterstitialAdUnitID = resources.getStringArray(R.array.rewarded_interstitial_ad_units).toList()
        AdmConfigAdId.listNativeAdUnitID             = resources.getStringArray(R.array.native_ad_units).toList()
        AdmConfigAdId.listOpenAdUnitID               = resources.getStringArray(R.array.app_open_ad_units).toList()
    }
}
```

Mỗi list nhiều ID → lib **xoay vòng** mỗi lần load. Mọi hàm load/show nhận `index`:
- `index = -1` → xoay vòng (mặc định). ID đang dùng báo không có ad hoặc lỗi → tự thử ID kế trong list, tối đa `maxFallbackUnits` ID dự phòng (mặc định 2, ID trùng bị bỏ qua). Timeout thì không thử ID khác.
- `index >= 0` → chọn cụ thể id thứ `index` (0-based), không dự phòng
- `customIds = listOf(...)` → xoay vòng list id truyền riêng thay cho list trong `AdmConfigAdId`

> **Đừng gọi thẳng `NativeAdLoader.load` / `InterstitialAd.load`... trong app.** SDK ném `IllegalStateException` nếu chưa init. Composable và helper của lib đã tự kiểm tra `AdCore.isMobileAdsReady`.

---

## 2. Init UMP + Mobile Ads

**Bước đầu tiên, làm ở splash.** Xin consent (UMP) trước, init Mobile Ads SDK chỉ khi consent cho phép.

```kotlin
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val ump = AdmUMP()   // tự lấy Activity an toàn từ context Compose

    LaunchedEffect(Unit) {
        ump.initUMP(
            // ads SẴN sàng -> preload native/open sớm (khỏi poll)
            onAdsInitialized = {
                AdmNativeSequenceState.init(listOf(0, 1, 2, 3, 4))
            },
            // xong việc xin consent -> rời splash, vào app
            gatherConsentFinished = { onDone() },
        )
    }
}
```

`initUMP(...)`:
- `testDeviceHashedIds: List<String>` — device test cho consent form.
- `showNetworkErrorDialog: Boolean = true` — offline thì người dùng miễn phí gặp dialog chỉ có Retry, có mạng mới đi tiếp; người đã mua chạy tiếp luôn (xem `OfflineGate` ở mục 8). `false` thì bỏ dialog ở bước này.
- `onAdsInitialized` — bắn khi Mobile Ads init xong (ads sẵn sàng → lúc preload).
- `gatherConsentFinished` — bắn đúng 1 lần khi xong xin consent (form đóng / không cần / lỗi / UMP quá `consentUpdateTimeoutMs`, mặc định 8s) → **lúc rời splash**. Init ad chạy nền song song, không block callback này.

Privacy options (xin lại consent trong Settings):

```kotlin
val ump = AdmUMP()
if (ump.isPrivacyOptionsRequired) {
    Button(onClick = { ump.showPrivacyOptionsForm() }) { Text("Privacy options") }
}
```

> Sau bước này `AdCore.isMobileAdsReady = true`; mọi ad bên dưới mới chạy được.

---

## 3. App Open ad

Dùng `AppOpenAdPreloader`: preload buffer, show bằng `pollAd`, không dialog loading. SDK tự nạp tiếp, tự retry khi lỗi và tự làm mới ad sắp hết hạn. Splash và resume dùng chung 1 unit id thì dùng chung buffer. Hai cách dùng:

### 3.1 Resume — tự show khi app trở lại foreground

Gọi 1 lần trong `Application.onCreate`. Bỏ qua ON_START đầu (cold start để splash lo):

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // ... set ad id, init billing ...

        GlobalVariables.canShowOpenAd = true   // premium -> tự set false
        AdmOpenResume.start()                  // hoặc start { onPaid = { v -> track(v) } }
    }
}
```

Mobile Ads sẵn là preload ngay, nên lần quay lại đầu tiên đã có ad. Mỗi resume kiểm tra `GlobalVariables.canShowOpenAd` + `isShowPopup` (mutex full-screen).

Tuỳ chỉnh: `minResumeIntervalMs` (khoảng cách tối thiểu giữa 2 lần show, mặc định 0) · `skipResumeAfterAdClick` (user rời app do bấm ad thì lúc quay lại không show thêm, mặc định `true`).

### 3.2 Splash — cold start load + show ngay

Instance riêng cho splash, tự destroy khi rời màn:

```kotlin
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val openSplash = AdmOpen()   // helper -> AdmOpenAd, tự destroy
    val openIds = stringArrayResource(R.array.app_open_ad_units_custom).toList()

    // gọi khi giá/consent đã sẵn -> show open rồi vào app
    LaunchedEffect(ready) {
        if (ready) openSplash.showFromSplash(customIds = openIds) { onDone() }
    }
}
```

`showFromSplash(index, customIds, timeoutMs = 8000, onClosed)`: `onClosed` bắn **1 lần** khi ad đóng / lỗi / quá timeout → splash đi tiếp. Mobile Ads chưa init xong thì tự chờ trong `timeoutMs`; offline hoặc consent không cho phép thì `onClosed` ngay. Gọi sau khi consent xong (`gatherConsentFinished`), để không rời splash lúc form consent còn mở.

Hook: `onShowed` / `onDismissed` / `onClicked` / `onImpression` / `onPaid(AdValue)` / `onError(AdmErrorType)`.

---

## 4. Native ad

`NativeAdLoader.load` → render asset qua `NativeAdView` wrapper. Layout dựng sẵn: `NativeLayout.NORMAL`, `SMALL`, `SMALL_2`, `COLLAPSIBLE`, `FULL`.

`COLLAPSIBLE`: media + hàng icon/nút, mũi tên góc trên trái — ấn → ẩn **cả ad** luôn (mặc định không mở lại).

```kotlin
AdmNative(layout = NativeLayout.COLLAPSIBLE, index = -1) {
    collapseDelaySeconds = 3    // 3 giây đầu mũi tên mờ + vòng tròn chạy quanh nút, đủ vòng mới ấn được
    collapseReshowSeconds = 30  // ấn mũi tên xong, 30 giây sau ad tự hiện lại (cùng ad đó)
    collapseRingColor = Color.White   // màu vòng tròn chạy quanh nút (mặc định trắng)
}
```

`collapseDelaySeconds` mặc định `0` (ấn được ngay). Đếm từ lúc ad hiện; dùng được cả với `AdmNativeSequence`.

`collapseReshowSeconds` mặc định `0` (ẩn hẳn). Hiện lại là **cùng ad cũ**, không load mới; `collapseDelaySeconds` đếm lại từ đầu.

### 4.1 Tự load (đơn giản nhất)

```kotlin
AdmNative(Modifier.fillMaxWidth(), layout = NativeLayout.NORMAL, index = -1) {
    backgroundColor = Color(0xFF222222)
    ctaColor = Color(0xFFFF6D00)
    onError = { type -> Log.w("ad", "$type") }
}
```

Chờ load → shimmer; premium → render rỗng.

Slot không bỏ cuộc ngay:
- ID không có ad → thử ID dự phòng (`maxFallbackUnits`, mặc định 2). Khi list xếp floor cao → thấp, đặt `startFromFirstUnit = true` để luôn thử từ đầu.
- SDK chưa init xong → chờ, init xong tự load.
- Vẫn hụt hoặc mất mạng → slot trống, thử lại theo `retryDelaysMs` (mặc định sau 15s và 45s nữa) khi app đang hiện. Hết lượt mới gọi `onError`, nên app đừng ẩn slot trước đó. Đặt `retryDelaysMs = emptyList()` nếu không muốn ad hiện muộn làm dịch nội dung, ví dụ slot nằm giữa danh sách.

> **Layout tự vẽ:** nút CTA trong `NativeAdCallToActionView` chỉ dùng `Box` + `Text`. Đừng dùng `Button` hay `clickable`: Compose nuốt lượt chạm, SDK không nhận click, ad có impression mà bấm nút không ăn.

### 4.2 Preload rồi show (app sở hữu ad)

```kotlin
// preload (vd Application/màn trước)
AdmNativeAd.preload { ad -> ad?.let { cache["home"] = it } }

// show
AdmNative(Modifier.fillMaxWidth(), nativeAd = cache["home"])   // null -> skip
```

> Ad preload do **app sở hữu** → tự gọi `ad.destroy()` khi xong, hoặc truyền `destroyNativeAdOnDispose = true` để ad được huỷ khi rời màn. Mỗi native giữ 1 WebView, quên huỷ sẽ tốn bộ nhớ dần theo số lần vào màn.

### 4.3 Native tuần tự (onboarding nhiều trang)

Preload theo thứ tự, luôn giữ sẵn 1 ad cho lần show kế:

```kotlin
// init 1 lần (Splash/Application), list INDEX vào listNativeAdUnitID
AdmNativeSequenceState.init(listOf(0, 1, 2))

// mỗi trang cần native -> show id kế + preload tiếp
AdmNativeSequence(
    Modifier.fillMaxWidth(),
    active = page == pager.currentPage,   // trong Pager: chỉ consume trang đang hiện
) { ctaColor = Color(0xFF52DE11) }

// rời hẳn onboarding -> dọn buffer
AdmNativeSequenceState.release()
```

Màu layout config được: `backgroundColor`, `ctaColor`, `ctaTextColor`, `textColor`, `subTextColor`, `startMuted`.

### 4.4 Native full sau inter (2 thanh loading)

Media phủ trên, card tối đè đáy (icon + tên + badge Ad, body, nút CTA), 2 thanh nằm trong card dưới CTA, mỗi thanh chạy `stepMs` (5s). Nút tròn góc trên phải mờ + vòng đếm lùi: hết thanh 1 → mũi tên **skip** bấm được; hết thanh 2 → **X** bấm được. Back bị chặn tới lúc tắt được.

```kotlin
val storyAds = remember { mutableStateListOf<NativeAd?>(null, null) }
var withNativeFull by remember { mutableStateOf(false) }   // true -> đóng inter hiện native full
var showStory by remember { mutableStateOf(false) }
val inter = AdmInterstitial { onDismissed = { if (withNativeFull) showStory = true } }

// load inter + preload 2 native cùng lúc
inter.load()
storyAds.indices.forEach { i -> AdmNativeAd.preload { storyAds[i] = it } }

if (showStory) AdmNativeFullStory(nativeAds = storyAds, onClose = { showStory = false /* + destroy ads */ })
```

`nativeAds = null` → tự load `pages` ad và tự destroy. Ad của trang null → mượn ad khác trong list; không có ad nào → báo `onError` đúng 1 lần rồi `onClose`. Vì cả hai đều được gọi, nếu đóng màn trong cả hai thì phải có chốt để chỉ đóng 1 lần.

### 4.5 Native PIP (nổi, kéo thả)

Card 225x238dp nổi ở `corner` (mặc định góc trên phải, cách lề 10dp), trượt vào từ mép. Kéo → đi theo tay, thả → hít về góc gần nhất. 1s đầu chặn chạm; nút X góc trên trái khoá 3s (vòng cam). Bấm ad → load ad mới (`reloadOnClick`).

```kotlin
Box(Modifier.fillMaxSize()) {
    ScreenContent()
    AdmNativePip(Modifier.fillMaxSize(), corner = PipCorner.TOP_RIGHT, onClose = { })   // con CUỐI -> nổi trên cùng
}
```

---

## 5. Banner ad

`BannerAd.load` (suspend) → render trong `AndroidView`. Composable tự load / fill / destroy.

```kotlin
AdmBanner(Modifier.fillMaxWidth())                       // mặc định: ADAPTIVE anchored full-width

AdmBanner(Modifier.fillMaxWidth()) {                     // tuỳ biến
    size = AdmBannerSize.MEDIUM_RECTANGLE
    collapsible = AdmBannerCollapsible.BOTTOM
    onPaid = { v -> track(v) }
    onError = { type -> Log.w("ad", "$type") }
}
```

`size`: `ADAPTIVE` (mặc định, thay smart_banner) · `ADAPTIVE_LARGE` · `BANNER` (320x50) · `FULL_BANNER` · `LARGE_BANNER` · `LEADERBOARD` · `MEDIUM_RECTANGLE` (300x250).

`collapsible`: `NONE` / `TOP` / `BOTTOM` — chỉ chạy với size anchored adaptive (size khác tự ép về `ADAPTIVE`).

Đổi `size`/`collapsible` → tự reload. Đang load → shimmer. Premium/offline → render rỗng.

---

## 6. Interstitial ad

One-shot `InterstitialAd.load`: load đúng 1 ad giữ sẵn, show 1 lần rồi bỏ (không tự refill). `load` gọi sớm; `show` khi ad sẵn thì hiện dialog ngắn rồi show, chưa kịp load thì dialog chờ load xong tự show (quá timeout hoặc bấm Back → `onError`). Cần show lại → gọi `load`/`show` lần nữa.

```kotlin
val inter = AdmInterstitial {        // helper -> AdmInterstitialAd, tự destroy khi rời màn
    onError = { type -> toast("$type") }
    onDismissed = { goNext() }        // ad đóng -> đi tiếp
}

LaunchedEffect(Unit) { inter.load() }    // bật preload sớm

Button(onClick = { inter.show() }) { Text("Show") }   // tự lấy activity foreground
```

Tuỳ chỉnh:

| Thuộc tính | Mặc định | Ý nghĩa |
|---|---|---|
| `loadingText` | `"Loading..."` | Chữ trên dialog |
| `loadTimeoutMs` | 8s | Ad chưa sẵn thì chờ tối đa ngần này |
| `showDelayMs` | 2s | Dialog trước khi show ad đã sẵn |
| `waitWhenNotReady` | `true` | `false` → ad chưa sẵn thì `onError` ngay, không bắt user chờ, ad load tiếp cho lần sau. Nên dùng cho placement không quan trọng |
| `maxFallbackUnits` | 2 | Số ID dự phòng khi không có ad |
| `failCooldownMs` | 15s | Vừa load hụt thì trong ngần này không request lại, `show` báo lỗi ngay |
| `adTtlMs` | 55 phút | Ad giữ quá lâu thì bỏ, load cái mới |

Hook: `onAvailable` / `onShowed` / `onImpression` / `onClicked` / `onPaid` / `onDismissed` / `onComplete` / `onError`.

> Mutex `GlobalVariables.isShowPopup` chặn 2 full-screen đè nhau. Double-tap show → bỏ qua. Ad full-screen khác đang hiện → `onError(AD_IS_EXISTED)`. App xuống nền trước lúc show → giữ ad, `onError(ACTIVITY_IS_NOT_AVAILABLE)`. **Luôn đi tiếp trong cả `onDismissed` lẫn `onError`**, không thì màn sẽ đứng.

### 6.1 Hai kiểu show inter

1. **Inter thường** — đóng inter là đi tiếp.
2. **Inter + native full** — đóng inter → hiện [native full 2 thanh loading](#44-native-full-sau-inter-2-thanh-loading).

```kotlin
Button(onClick = { withNativeFull = false; inter.show() }) { Text("Inter thường") }
Button(onClick = { withNativeFull = true; inter.show() }) { Text("Inter + native full") }
```

Demo đầy đủ: `app/.../interstitial/InterstitialScreen.kt`.

---

## 7. Các ad khác

API y hệt Interstitial, thêm `onReward`.

### 7.1 Rewarded

```kotlin
val reward = AdmReward {
    onReward = { item -> grant(item.amount) }
    onError  = { type -> toast("$type") }
}
LaunchedEffect(Unit) { reward.load() }
Button(onClick = { reward.show() }) { Text("Watch & earn") }
```

### 7.2 Rewarded Interstitial

```kotlin
val ad = AdmRewardInterstitial {
    onReward = { item -> grant(item.amount) }
    onError  = { type -> toast("$type") }
}
ad.load()
ad.show()
```

Unit id lấy từ `listRewardAdUnitID` / `listRewardInterstitialAdUnitID`.

---

## 8. Billing và các cái khác

### 8.1 Init billing (Application)

```kotlin
object MyProducts {
    val subscriptions = listOf("app.weekly", "app.yearly")
    val nonConsumable = listOf("lifetime")
}

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        BillingClient.init(
            context = this,
            nonConsumableIds = MyProducts.nonConsumable,
            subscriptionIds  = MyProducts.subscriptions,
            // consumableIds = ...,            // nếu có
            // autoRefreshOnForeground = true  // mặc định: tự connect + restore mỗi foreground
        )
        BillingClient.startConnection()
    }
}
```

> Mỗi product ID chỉ được xuất hiện **1 lần** giữa các list (route đúng consume/acknowledge). `autoRefreshOnForeground = true` → app **không** cần tự gọi `startConnection`/`restorePurchases`.

**Offline:** người dùng miễn phí cần mạng để có quảng cáo, nên `OfflineGate` hiện dialog Retry không tắt được lúc mở app và mỗi lần quay lại foreground. Người đã mua (sub, lifetime, remove-ads) dùng offline bình thường, restore lỗi thì giữ nguyên quyền đã lưu. Tắt mạng giữa lúc đang dùng thì chưa chặn, để sóng chập chờn không làm hộp thoại nhảy ra giữa chừng.

- Muốn người dùng miễn phí cũng dùng được offline: `OfflineGate.enabled = false`.
- App có billing riêng phải báo cho SDK biết ai đã mua: `PreferencesManager.getInstance().removeAds(isPro)`.

### 8.2 Mua / sub / restore trong Compose

```kotlin
val billing = BillingAction()    // tự lấy Activity, không cần truyền tay

Button(onClick = { billing.subscribe(MyProducts.YEARLY) }) {
    Text("Yearly ${billing.getPrice(MyProducts.YEARLY) ?: "..."}")
}
Button(onClick = { billing.purchase(MyProducts.LIFETIME) }) { Text("Lifetime") }
Button(onClick = { billing.restore() }) { Text("Restore") }
```

`ComposeBilling`: `purchase` · `subscribe` · `changeSubscription` (upgrade/downgrade) · `restore` · `getOffers` · `getPrice` · `hasFreeTrial` · `getFreeTrialPeriod`.

### 8.3 Lắng nghe billing event

```kotlin
BillingEvents(
    onProductsFetched = { products -> /* render giá */ },
    onPurchased       = { ids -> toast("Đã mua $ids") },
    onRestoreFinished = { has -> toast(if (has) "Đã khôi phục" else "Không có gói") },
    onError           = { type, _ -> Log.w("billing", "$type") },
)
```

### 8.4 Trạng thái premium

```kotlin
val premium by rememberPremiumState()   // lifetime || sub || removeAds, reactive
if (premium) { /* ẩn ad, hiện badge */ }

// hoặc đọc thẳng:
PreferencesManager.getInstance().isSUB()        // có sub/lifetime
PreferencesManager.getInstance().isRemoveAds()  // remove-ads
```

Premium → lib tự set `GlobalVariables.isShowSub = false` + `canShowOpenAd = false`, mọi ad tự báo `CLIENT_HAVE_SUB` / `CLIENT_HAVE_BEEN_REMOVED_AD` và **không render**. Remove-ads do app tự cấp: `BillingClient.setRemoveAds(true)`.

---

## 9. Đo fill rate và lần ra crash

`AdmAdDiagnostics.listener` nhận mọi sự kiện của mọi format, kèm mã lỗi gốc của SDK (`NO_FILL`, `NETWORK_ERROR`...), nguồn trả ad, độ trễ và ID dự phòng. Gắn 1 lần trong `Application.onCreate`:

```kotlin
AdmAdDiagnostics.listener = { e ->
    // Crash do ad: biết ngay ad nào đang chạy lúc crash.
    FirebaseCrashlytics.getInstance().setCustomKey("last_ad", "${e.format} ${e.type} ${e.adSource}")

    // Fill rate theo từng ID = LOAD_SUCCESS / LOAD_REQUEST. Show rate = SHOW_SUCCESS / LOAD_SUCCESS.
    analytics.logEvent("ad_${e.type.name.lowercase()}", bundleOf(
        "format" to e.format.name,
        "unit" to e.adUnitId,
        "error" to e.errorCode,
        "source" to e.adSource,
        "latency_ms" to e.latencyMs,
    ))
}
```

Listener chạy trên main thread, lỗi trong listener bị nuốt, không làm crash app. Doanh thu nằm trong sự kiện `PAID` (`valueMicros`, `currencyCode`).

### Máy yếu

`AdmDevice.isLowEnd` = máy Android Go hoặc RAM dưới 3200 MB. Google chấm ANR riêng từng dòng máy (ngưỡng 8%), nên bớt ad nặng trên máy yếu giúp dòng máy đó không vượt ngưỡng:

```kotlin
val allowPip = !AdmDevice.isLowEnd && firebase.adsPlacementEnabled(pos)
```

Trên máy yếu, lib tự dùng shimmer tĩnh. Việc tắt format nào là lựa chọn doanh thu của từng app, lib không tự tắt.

---

## Tham khảo nhanh

### `AdmErrorType`

`LIST_AD_ID_IS_EMPTY` · `AD_ID_IS_NOT_EXIST` · `AD_IS_NOT_AVAILABLE` · `AD_IS_EXISTED` (ad full-screen khác đang hiện) · `NETWORK_IS_NOT_AVAILABLE` · `UMP_IS_NOT_ACTIVE` · `ACTIVITY_IS_NOT_AVAILABLE` · `CLIENT_HAVE_SUB` · `CLIENT_HAVE_BEEN_REMOVED_AD` · `OTHER`

### `GlobalVariables`

| Cờ | Ý nghĩa |
|----|---------|
| `isShowSub` | Còn được hiện màn subscription không (premium → false) |
| `isShowPopup` | Mutex full-screen ad/dialog đang hiện |
| `canShowOpenAd` | Có được show app-open không (premium → false) |

### Log tags

`AdmUMP` · `GoogleAds/Open` · `GoogleAds/Native` · `GoogleAds/Banner` · `GoogleAds/Inter` · `GoogleAds/Reward` · `GoogleAds/RewardInter` · `GoogleAds/Billing`

Bật/tắt log mỗi controller qua `enableLogging` (`BillingClient.enableLogging` mặc định `false`).
