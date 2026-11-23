# GS_AD

## Installation
#### repositories
Vào mục setting.gradle.kts. Copy:
```gradle
maven { url 'https://jitpack.io' }
```
Như hình:
![plot](./images/Install.png)


#### dependencies
Cài thư viện
```gradle
implementation("com.github.caprinixdev:ultra_a_d:1.0.28")
```
Thêm các thư viện
```gradle
implementation ("com.google.android.gms:play-services-ads:25.5.0")
implementation ("com.google.android.ump:user-messaging-platform:3.1.0")

implementation ("androidx.multidex:multidex:2.0.1")
implementation ("com.github.eriffanani:ContentLoader:1.2.0")
implementation ("com.facebook.shimmer:shimmer:0.5.0@aar")
implementation ("androidx.lifecycle:lifecycle-process:2.8.7")
```

## AndroidManifest
Copy các thẻ meta vào trong application
```manifest
<uses-permission android: name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android: name="android.permission.INTERNET" />

<meta-data
     android:name="com.google.android.gms.ads.flag.OPTIMIZE_INITIALIZATION"
     android:value="true" />
<meta-data
     android:name="com.google.android.gms.ads.flag.OPTIMIZE_AD_LOADING"
     android:value="true" />
<meta-data
     android:name="com.google.android.gms.ads.AD_MANAGER_APP"
     android:value="true" />
<meta-data
     android:name="com.google.android.gms.ads.APPLICATION_ID"
     android:value="@string/app_ad_id" />
<meta-data
     android:name="com.google.android.gms.ads.flag.NATIVE_AD_DEBUGGER_ENABLED"
     android:value="false" />
```

## Cách sử dụng
Tạo 1 **AppOwner.kt** như example và thêm ***<application android:name=".AppOwner"*** vào ***AndroidManifest.xml*** 

Phải khởi tạo UMP trước khi sử dụng Ad, nên dùng ở trong màn Splash
```kotlin
private lateinit var ump: AdmUMP

ump = AdmUMP(this)

ump.initUMP(gatherConsentFinished = {
    runOnUiThread {
        //TODO: 
    }
})
```
### BannerAd
Gọi Load Ad:
```kotlin
private var bannerAd : AdmBannerAd? = null

bannerAd = AdmBannerAd(id = 0, currentActivity = this) // Nếu id = -1 thì sẽ gọi vòng lặp AdmConfigAdId.listBannerAdUnitID
bannerAd?.loadAd(binding.bannerView)
```

Listener
```kotlin
bannerAd?.onAdFailToLoaded = { admErrorType, errorMessage ->
    runOnUiThread {
        Log.d(TAG, admErrorType.name + "," + errorMessage)
    }
}
```

BannerAd Lifecycle
```kotlin
override fun onResume() {
     super.onResume()
     bannerAd?.resumeBanner()
}

override fun onPause() {
     super.onPause()
     bannerAd?.pauseBanner()
}

override fun onDestroy() {
    bannerAd?.destroyBanner()
    bannerAd = null
    super.onDestroy()
}
```

Có thể sử dụng 1 list ad id riêng nếu như màn đó bị gọi nhiều lần (có thể sử dụng với tất cả các ad):
```kotlin
object GroupBannerAd {
    var listBannerAd: HashMap<String, AdmBannerAd?> = HashMap()
}

val keyAd = this.javaClass.simpleName
if(GroupBannerAd.listBannerAd[keyAd] == null){
    bannerAd = AdmBannerAd(0, this, resources.getStringArray(R.array.banner_ad_main2_unit_id).toList())
    GroupBannerAd.listBannerAd[keyAd] = bannerAd
}else{
    bannerAd = GroupBannerAd.listBannerAd[keyAd]
}
```

### NativeAd
Gọi Load Ad:
```kotlin
private var nativeAd: AdmNativeAd? = null

nativeAd = AdmNativeAd(id = 0, context = applicationContext, isFullScreen = false) // Nếu id = -1 thì sẽ gọi vòng lặp AdmConfigAdId.listNativeAdUnitID
nativeAd?.loadAd(binding.nativeAdContainerView, R.layout.layout_native_ad)
```
Dùng Preload Ad thì tạo 1 object list và dùng populateNativeAdView:
```kotlin
//preload
object GroupNativeAd {
    var listOnBoardNativeAd: MutableList<AdmNativeAd?> = mutableListOf()
}

val admNativeAd = AdmNativeAd(
    id = 0,
    context = applicationContext,
    isFullScreen = false // true
)

admNativeAd.tag = 0 //để nhận biết ad

GroupNativeAd.listOnBoardNativeAd.add(admNativeAd)

//populateNativeAdView
val nativeAd = GroupNativeAd.listOnBoardNativeAd[position]
nativeAd?.populateNativeAdView(
    adContainerView = adContainer,
    layoutNativeAdView = R.layout.layout_native_ad_full
)
```

Listener
```kotlin
nativeAd?.onAdFailToLoaded = { admErrorType, errorMessage ->
    runOnUiThread {
        Log.d(TAG, admErrorType.name + "," + errorMessage)
    }
}
```

Destroy Ad
```kotlin
override fun onDestroy() {
    //destroy list
    val list = GroupNativeAd.listOnBoardNativeAd

    list.forEachIndexed { index, admNativeAd ->
        admNativeAd?.destroyNativeAd()
        list[index] = null
    }

    list.clear()
    
    //destroy nativeAd
    nativeAd?.destroyNativeAd()
    nativeAd = null
    super.onDestroy()
}
```

### Native collapsible (1.0.28)
Media + hàng icon/chữ/nút cài đặt, mũi tên gập góc trên trái. Bấm mũi tên -> ẩn cả ad (mũi tên không tính click ad).
```kotlin
private var collapsible: AdmNativeCollapsible? = null

collapsible = AdmNativeCollapsible(-1, this).apply {
    collapseDelaySeconds = 3     // 3 giây đầu mũi tên mờ + vòng chạy quanh nút, chưa bấm được
    collapseReshowSeconds = 30   // gập xong 30 giây ad tự hiện lại (cùng ad). 0 = ẩn hẳn
    onCollapsed = { tag -> }     // người dùng vừa bấm mũi tên
    loadAd(binding.containerNativeCollapsible)   // ConstraintLayout, cao wrap_content
}

override fun onDestroy() {
    collapsible?.destroy()
    super.onDestroy()
}
```
Layout mặc định `layout_native_ad_collapsible`. Tự làm layout: giữ các id `ad_*` + 1 `gs.ad.utils.ads.view.AdmCountdownButton` id `ad_collapse`, rồi `loadAd(container, R.layout.my_layout)`.

### Native full sau inter — 2 thanh loading (1.0.28)
Toàn màn hình, 2 trang, mỗi trang 1 thanh chạy 5 giây. Nút góc trên phải mờ + vòng đếm lùi: trang 1 = mũi tên sang trang, trang cuối = X; chạy xong mới bấm được. Back bị chặn tới khi được tắt. 10 giây không có ad -> tự đóng.
```kotlin
// Nạp sẵn 2 native (app sở hữu)
val storyAds = List(2) { AdmNativeAd(-1, this, true).also { it.preloadAd() } }

inter.onAdClosed = {
    runOnUiThread {
        AdmNativeFullStory(this).apply {
            onClose = { goNext() }
        }.show(storyAds)          // không truyền list -> story tự load (adId, adsToLoad)
    }
}
// onDestroy: storyAds.forEach { it.destroyNativeAd() }
```
Tuỳ chỉnh: `pages` (mặc định 2), `stepMs` (5000), `adsToLoad`, `layoutNativeAdView` (mặc định `layout_native_ad_full_story`).

### Native PIP — nổi, kéo thả (1.0.28)
Thẻ 225x238dp trượt vào từ mép, kéo đi theo tay, thả ra hít về góc gần nhất. 1 giây đầu chặn chạm; nút X góc trên trái khoá 3 giây (vòng cam). Bấm ad -> nạp ad mới.
```kotlin
private var pip: AdmNativePip? = null

pip = AdmNativePip(this).apply {
    corner = AdmNativePip.Corner.TOP_RIGHT   // góc xuất hiện
    reloadOnClick = true
    onClose = { }
    show()
}

override fun onDestroy() {
    pip?.dismiss()
    super.onDestroy()
}
```

### InterstitialAd
Show Ad
```kotlin
private var interShowActivity2: AdmInterstitialAd? = null

interShowActivity2 = AdmInterstitialAd(id = 0, currentActivity = this) // Nếu id = -1 thì sẽ gọi vòng lặp AdmConfigAdId.listInterstitialAdUnitID
interShowActivity2?.showPopupLoadAds { }
```

Listener
```kotlin
interShowActivity2?.onAdClosed = {
    runOnUiThread {
        startActivity(Intent(this@MainActivity, MainActivity2::class.java))
        }
    }

interShowActivity2?.onAdFailToLoaded = { admErrorType, errorMessage ->
    runOnUiThread {
        Log.d(TAG, admErrorType.name + "," + errorMessage)
    }
}
```
Sử dụng ***countToShowAds*** với trường hợp đếm tương tác nhiêu nút bấm trên 1 màn hình, với firstShowAd là số lần phải tương tác các nút bấm trong lần đầu tiên để show ad, loopShowAd là số lần khi tương tác lại các nút bấm đó để show lại ad :
```kotlin
 interShowActivity2?.countToShowAds(
    keyCounterAd = MAIN_COUNTER_AD,
    startAds = 3,
    loopAds = 2,
){}
```
Muốn reset lại số lần thì sử dụng ***resetCounterAds*** ở ***AppOwner.kt*** :
```kotlin
resetCounterAds(MainActivity.MAIN_COUNTER_AD)
```

Destroy Ad
```kotlin
override fun onDestroy() {
    interShowActivity2 = null
    super.onDestroy()
}
```

### RewardAd
```kotlin
private var rewardedAdRemoveAd: AdmRewardAd? = null

rewardedAdRemoveAd = AdmRewardAd(id = 0, currentActivity = this) // Nếu id = -1 thì sẽ gọi vòng lặp AdmConfigAdId.listRewardAdUnitID
```

Listener
```kotlin
rewardedAdRemoveAd?.onHaveReward = {
    runOnUiThread {
        PreferencesManager.getInstance().removeAds(true)
        checkSubToUpdateUI()
    }
}

rewardedAdRemoveAd?.onAdFailToLoaded = { admErrorType, errorMessage ->
    runOnUiThread {
        Log.d(TAG, admErrorType.name + "," + errorMessage)
    }
}
```

Destroy Ad
```kotlin
override fun onDestroy() {
    rewardedAdRemoveAd = null
    super.onDestroy()
}
```

### OpenAd
Để xuất hiện OpenAd thì sử dụng ***GlobalVariables.canShowOpenAd = true***. ví dụ:
```kotlin
override fun onStart() {
     super.onStart()
     GlobalVariables.canShowOpenAd = true
}
```

OpenAd ở các màn **Splash, Subscription, OnBoard** ko nên hiện thì để ***GlobalVariables.canShowOpenAd = false***

### Lưu ý
Xem Reward Remove Ad sử dụng như sau, bỏ remove ad thì set ***false*** :
```kotlin
PreferencesManager.getInstance().removeAds(true) // false
```

Sử dụng isRemoveAds() để check remove ad:
```kotlin
PreferencesManager.getInstance().isRemoveAds()
```

# Sub
## Cách sử dụng
Tạo các 4 file Product Id như hình:
![plot](./images/SubProductId.png)

Tạo 1 **AppOwner.kt** như hình:
![plot](./images/SubAppOwner.png)

Khởi tạo ***BillingClientLifecycle*** trong các activity
```kotlin
private val mBillingClientLifecycle: BillingClientLifecycle? get() { return  (application as AppOwner).mBillingClientLifecycle ?: null}
```

Gọi ***setListener*** trong onCreate ở các activity như sau:
```kotlin
mBillingClientLifecycle?.setListener(this, object : OnBillingListener {
            override fun onPurchasedProductsFetched(purchaseInfos: List<PurchaseInfo>) {
                super.onPurchasedProductsFetched(purchaseInfos)
            }
        })
```
Dùng ***removeListener*** trước finish 1 activity như sau:
```kotlin
     mBillingClientLifecycle?.removeListener(this)
     finish()
```
Đối với activity main như sau:
```kotlin
override fun onDestroy() {
     mBillingClientLifecycle?.removeListener(this)
     super.onDestroy()
}
```

Lấy giá item Sub và iap như sau:
```kotlin
mBillingClientLifecycle.getSubscriptionPrice(SubscriptionProductId.Weekly.id)
```
```kotlin
mBillingClientLifecycle.getIAPPrice(ConsumableProductId.Lifetime.id)
```

Mua gói Sub và Lifetime sử dụng như sau:
```kotlin
mBillingClientLifecycle.subscribe(this@SubscriptionActivity, ConsumableProductId.Lifetime.id)
```
```kotlin
mBillingClientLifecycle.purchaseLifetime(this@SubscriptionActivity, SubscriptionProductId.Weekly.id)
```

Sử dụng ***fetchSubPurchasedProducts*** để check Sub có tồn tại ko
```kotlin
mBillingClientLifecycle?.fetchSubPurchasedProducts()
```

Sử dụng isSUB() để check sub:
```kotlin
PreferencesManager.getInstance().isSUB()
```

