package gs.ad.utils.ads.format

import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.gms.ads.nativead.NativeAd
import gs.ad.utils.R
import gs.ad.utils.ads.error.AdmErrorType
import gs.ad.utils.ads.view.AdmCountdownButton

/**
 * Native COLLAPSIBLE (bản XML, cùng logic bản Compose): media + hàng icon/chữ/nút cài đặt, mũi tên gập góc trên trái
 * media. Bấm mũi tên -> ẩn CẢ ad (container GONE).
 *
 * - [collapseDelaySeconds]: N giây đầu (tính từ lúc ad hiện) mũi tên mờ + vòng chạy quanh nút, chưa bấm được.
 * - [collapseReshowSeconds]: gập xong N giây thì ad tự HIỆN LẠI (cùng ad đó, không load mới). `0` = ẩn hẳn.
 * - [onCollapsed]: báo đúng lúc người dùng bấm mũi tên.
 *
 * Load / hiện / huỷ dùng nguyên [AdmNativeAd] (`loadAd(container, layout)`), chỉ thêm phần mũi tên.
 *
 * ```
 * val collapsible = AdmNativeCollapsible(-1, this).apply {
 *     collapseDelaySeconds = 3
 *     collapseReshowSeconds = 30
 *     onCollapsed = { tag -> }
 * }
 * collapsible.loadAd(binding.containerNativeCollapsible)   // ConstraintLayout, cao wrap_content
 * // onDestroy: collapsible.destroy()
 * ```
 */
class AdmNativeCollapsible(
    id: Int,
    private val activity: Activity,
    listAdCircularArray: List<String>? = null,
) {
    var tag: Int = 0

    /** Số giây đầu mũi tên chưa bấm được (vòng chạy quanh nút). `0` = bấm được ngay. */
    var collapseDelaySeconds: Int = 0

    /** Gập xong sau số giây này ad tự hiện lại (cùng ad). `0` = ẩn hẳn. */
    var collapseReshowSeconds: Int = 0

    /** Màu vòng chạy quanh mũi tên lúc chờ [collapseDelaySeconds]. */
    var collapseRingColor: Int = Color.WHITE

    /** Người dùng vừa bấm mũi tên gập ad. */
    var onCollapsed: ((Int) -> Unit)? = null

    var onAdLoaded: ((NativeAd?) -> Unit)? = null
    var onAdFailToLoaded: ((AdmErrorType, String?, Int) -> Unit?)? = null
    var onAdShow: ((Int) -> Unit)? = null
    var onAdClicked: ((Int) -> Unit)? = null

    /** Ad native bên dưới (logic load/hiện của SDK cũ). */
    val admNativeAd = AdmNativeAd(id, activity, false, listAdCircularArray)

    val nativeAd: NativeAd? get() = admNativeAd.nativeAd

    private var container: ConstraintLayout? = null
    private val handler = Handler(Looper.getMainLooper())

    /** Load + hiện vào [container] (ConstraintLayout). [layoutNativeAdView] phải có AdmCountdownButton id `ad_collapse`. */
    fun loadAd(container: ConstraintLayout, layoutNativeAdView: Int = R.layout.layout_native_ad_collapsible) {
        this.container = container
        admNativeAd.tag = tag
        admNativeAd.onAdLoaded = { ad ->
            activity.runOnUiThread { armCollapse() }
            onAdLoaded?.invoke(ad)
        }
        admNativeAd.onAdFailToLoaded = { type, message, t -> onAdFailToLoaded?.invoke(type, message, t) }
        admNativeAd.onAdShow = { onAdShow?.invoke(it) }
        admNativeAd.onAdClicked = { onAdClicked?.invoke(it) }
        admNativeAd.loadAd(container, layoutNativeAdView)
    }

    /** Ad đã hiện (vừa load / hiện lại): mũi tên khoá [collapseDelaySeconds] rồi mới bấm được. */
    private fun armCollapse() {
        val c = container ?: return
        val button = c.findViewById<AdmCountdownButton>(R.id.ad_collapse) ?: return
        button.icon = AdmCountdownButton.Icon.CHEVRON_DOWN
        button.ringColor = collapseRingColor
        button.ringGrows = true
        button.startLock(collapseDelaySeconds * 1000L)
        button.onUnlockedClick = { collapse() }
    }

    private fun collapse() {
        val c = container ?: return
        c.visibility = View.GONE
        onCollapsed?.invoke(tag)
        if (collapseReshowSeconds > 0) {
            handler.postDelayed({
                val again = container ?: return@postDelayed
                if (admNativeAd.nativeAd == null) return@postDelayed
                again.visibility = View.VISIBLE
                armCollapse()   // hiện lại: mũi tên đếm lại từ đầu
            }, collapseReshowSeconds * 1000L)
        }
    }

    /** Huỷ ad (gọi ở onDestroy của màn). */
    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        admNativeAd.destroyNativeAd()
        container = null
    }
}
