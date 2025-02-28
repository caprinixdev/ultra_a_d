package gs.ad.utils.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import gs.ad.utils.google_iab.BillingClientLifecycle
import gs.ad.utils.utils.GlobalVariables
import gs.ad.utils.utils.PreferencesManager
import java.util.concurrent.atomic.AtomicBoolean

class AdmBuilder(
    val context: Context,
    private val keyShowOpenAd: String?,
    private val config: AdmConfig,
    private val billingClient: BillingClientLifecycle?
): DefaultLifecycleObserver {
    private lateinit var mAdmMachine: AdmMachine
    private lateinit var mAdmManager: AdmManager
    private var mBillingClientLifecycle: BillingClientLifecycle? = null
    private constructor(builder: Builder) : this(builder.context, builder.keyShowOpen, builder.config, builder.billingClient){
        mAdmMachine = AdmMachine.build(builder.context){ config = builder.config}
        mAdmManager = AdmManager.build(mAdmMachine) {}
        mBillingClientLifecycle = builder.billingClient
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    private val canLoadOpenAd = AtomicBoolean(false)

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        Log.d(TAG, "owner onStart")

        if (!canLoadOpenAd.get()) {
            canLoadOpenAd.set(true)
            return
        }

        Log.d(
            TAG,
            "ON_RESUME App in foreground " + GlobalVariables.isShowSub + "," + GlobalVariables.isShowPopup
        )

        if (GlobalVariables.isShowSub) return
        if (GlobalVariables.isShowPopup) return
        if (!GlobalVariables.canShowOpenAd) return

        Log.d(TAG, "show openAd")
        val keyShowOpenAd = keyShowOpenAd ?: return
        mAdmManager.showDefaultOpenAd(keyShowOpenAd)
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        Log.d(TAG, "owner onCreate")
        mBillingClientLifecycle?.connectBillingConnector()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        mBillingClientLifecycle?.destroyBillingConnector()
        super.onDestroy(owner)
        Log.d(TAG, "owner onDestroy")
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        Log.d(TAG, "owner onStop")
        mAdmManager.resetInitUMP()
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        Log.d(TAG, "owner onPause")
    }

    fun isMainActivity(activity: Activity): AdmManager{
        mAdmMachine.isMainActivity(activity)
        return mAdmManager
    }

    fun getActivity(activity: Activity): AdmManager{
        mAdmMachine.setActivity(activity)
        return mAdmManager
    }

    fun resetCounterAds(keyCount: String){
        PreferencesManager.getInstance().resetCounterAds(keyCount)
    }

    companion object {
        const val TAG = "AdmBuilder"
        inline fun build(context: Context, block: Builder.() -> Unit) = Builder(context).apply(block).build()
    }

    class Builder(
        val context: Context
    ) {
        var billingClient: BillingClientLifecycle? = null
        var keyShowOpen: String? = null
        lateinit var config: AdmConfig
        fun build() = AdmBuilder(this)
    }
}