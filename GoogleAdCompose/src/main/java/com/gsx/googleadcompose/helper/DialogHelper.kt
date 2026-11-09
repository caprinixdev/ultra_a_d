package com.gsx.googleadcompose.helper

import android.app.Activity
import android.app.AlertDialog
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.lang.ref.WeakReference

object DialogHelper {

    private const val TAG = "GoogleAds/Dialog"

    /** Việc đang chờ có mạng + dialog đang chặn: nhiều lời gọi chung 1 dialog, không xếp chồng. */
    private val waitingForNetwork = ArrayList<() -> Unit>()
    private var blockingDialog: WeakReference<AlertDialog>? = null

    /**
     * Dialog loading: spinner xoay tròn + chữ. [onCancel] null -> không tắt được; khác null -> bấm Back/ra ngoài
     * để hủy chờ. Trả dialog để [AlertDialog.dismiss], null nếu không hiện được.
     */
    fun showLoading(
        activity: Activity,
        message: String = "Loading...",
        onCancel: (() -> Unit)? = null,
    ): AlertDialog? {
        if (activity.isFinishing || activity.isDestroyed) return null
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        val gap = (16 * activity.resources.displayMetrics.density).toInt()
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(ProgressBar(activity))
            addView(TextView(activity).apply {
                text = message
                setPadding(gap, 0, 0, 0)
            })
        }
        val builder = AlertDialog.Builder(activity)
            .setView(row)
            .setCancelable(onCancel != null)
        onCancel?.let { cancel -> builder.setOnCancelListener { cancel() } }
        return runCatching { builder.create().also { it.show() } }
            .onFailure { Log.w(TAG, "Không hiện được dialog loading", it) }   // Activity đang đóng: BadTokenException
            .getOrNull()
    }

    /**
     * Chặn 1 hành động sau khi kiểm tra mạng. Có mạng: chạy [onAvailable] ngay. Mất mạng: hiện
     * dialog no-network; bấm Retry sẽ check lại — có mạng thì dialog đóng và chạy [onAvailable],
     * không thì hiện lại. Gọi nhiều lần khi dialog đang hiện -> gộp, không xếp chồng dialog.
     * (Text dialog để tiếng Anh.)
     */
    fun requireNetwork(
        activity: Activity,
        title: String = "No internet connection",
        message: String = "An internet connection is required to continue. Please check your network.",
        onAvailable: () -> Unit,
    ) {
        if (NetworkHelper.isOnline(activity)) {
            onAvailable()
            return
        }
        waitingForNetwork += onAvailable
        if (blockingDialog?.get()?.isShowing == true) return
        blockingDialog = showNoNetworkDialog(
            activity = activity,
            title = title,
            message = message,
            onRetry = {
                val waiting = waitingForNetwork.toList()
                waitingForNetwork.clear()
                waiting.forEach { requireNetwork(activity, title, message, it) }
            },
        )?.let { WeakReference(it) }
    }

    /**
     * Hiện dialog "no internet" không hủy được. (Text trên dialog giữ tiếng Anh.)
     *
     * @param onRetry chạy khi user bấm Retry (đã dismiss sẵn).
     * @param onCancel khác null thì hiện nút Cancel; null thì chỉ có Retry (ép user kết nối lại —
     *   dialog cứ hiện lại tới khi có mạng).
     * @return dialog đã hiện; null nếu không hiện được (đã gọi [onCancel] nếu có) hoặc gọi từ thread nền
     *   (dialog sẽ hiện ở lượt sau trên main).
     */
    fun showNoNetworkDialog(
        activity: Activity,
        title: String = "No internet connection",
        message: String = "An internet connection is required to continue. Please check your network.",
        onRetry: () -> Unit,
        onCancel: (() -> Unit)? = null,
    ): AlertDialog? {
        if (activity.isFinishing || activity.isDestroyed) {
            onCancel?.invoke()
            return null
        }
        if (Looper.myLooper() !== Looper.getMainLooper()) {
            activity.runOnUiThread { showNoNetworkDialog(activity, title, message, onRetry, onCancel) }
            return null
        }
        val builder = AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Retry") { dialog, _ ->
                dialog.dismiss()
                onRetry()
            }
        if (onCancel != null) {
            builder.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                onCancel()
            }
        }
        return runCatching { builder.show() }
            .onFailure { error ->
                Log.w(TAG, "Không hiện được dialog no-network", error)
                onCancel?.invoke()
            }
            .getOrNull()
    }
}
