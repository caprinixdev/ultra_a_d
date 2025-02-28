package gs.ad.gsadsexample

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import gs.ad.gsadsexample.databinding.ActivitySubscriptionBinding
import gs.ad.gsadsexample.sub.ConsumableProductId
import gs.ad.gsadsexample.sub.MyProductId
import gs.ad.gsadsexample.sub.SubscriptionProductId
import gs.ad.utils.google_iab.BillingClientLifecycle
import gs.ad.utils.google_iab.OnBillingListener
import gs.ad.utils.google_iab.enums.ErrorType
import gs.ad.utils.google_iab.models.ProductInfo
import gs.ad.utils.google_iab.models.PurchaseInfo
import gs.ad.utils.utils.GlobalVariables
import gs.ad.utils.utils.PreferencesManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.util.Date

class SubscriptionActivity : AppCompatActivity() {
    private var binding: ActivitySubscriptionBinding? = null
    private lateinit var myProductId: MyProductId
    var isRestoreSub: Boolean = false
    private val mBillingClientLifecycle: BillingClientLifecycle? get() { return AppController.billingClientLifecycle }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubscriptionBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        mBillingClientLifecycle?.setListener(this, object: OnBillingListener{
            override fun onProductsPurchased(purchaseInfos: List<PurchaseInfo>) {
                super.onProductsPurchased(purchaseInfos)
                showAlertDialogPurchasedSuccess(Date())
            }

            override fun onPurchasedProductsFetched(purchaseInfos: List<PurchaseInfo>) {
                super.onPurchasedProductsFetched(purchaseInfos)
                checkSubSuccessfully()
            }

            override fun onPurchaseAcknowledged(purchaseInfo: PurchaseInfo) {
                super.onPurchaseAcknowledged(purchaseInfo)
            }

            override fun onPurchaseConsumed(purchaseInfo: PurchaseInfo) {
                super.onPurchaseConsumed(purchaseInfo)
            }

            override fun onBillingError(errorType: ErrorType) {
                super.onBillingError(errorType)
            }
        })


        binding?.subWeeklyValue?.text = mBillingClientLifecycle?.getSubscriptionPrice(SubscriptionProductId.Weekly.id)
        binding?.subYearlyValue?.text = mBillingClientLifecycle?.getSubscriptionPrice(SubscriptionProductId.Yearly.id)
        binding?.subLifetimeValue?.text = mBillingClientLifecycle?.getIAPPrice(ConsumableProductId.Lifetime.id)

        clickOps1()
        setListeners()
    }

    private fun setListeners() {
        binding?.subClose?.setOnClickListener { finish() }

        binding?.subYearly?.setOnClickListener { clickOps1() }
        binding?.subWeekly?.setOnClickListener { clickOps2() }
        binding?.subLifetime?.setOnClickListener { clickOps3() }
        binding?.subPurchase?.setOnClickListener { buySubAndIAP() }

        binding?.subRestore?.setOnClickListener {
            isRestoreSub = true
            mBillingClientLifecycle?.fetchSubPurchasedProducts()
        }
        binding?.subTerms?.setOnClickListener {  }
        binding?.subPrivacy?.setOnClickListener {  }
    }

    private fun clickOps1() {
        myProductId = MyProductId.Yearly
        binding?.subYearlyIc?.setImageResource(R.drawable.icon_active)
        binding?.subWeeklyIc?.setImageResource(R.drawable.icon_deactive)
        binding?.subLifetimeIc?.setImageResource(R.drawable.icon_deactive)
    }

    private fun clickOps2() {
        myProductId = MyProductId.Weekly
        binding?.subYearlyIc?.setImageResource(R.drawable.icon_deactive)
        binding?.subWeeklyIc?.setImageResource(R.drawable.icon_active)
        binding?.subLifetimeIc?.setImageResource(R.drawable.icon_deactive)
    }

    private fun clickOps3() {
        myProductId = MyProductId.Lifetime
        binding?.subYearlyIc?.setImageResource(R.drawable.icon_deactive)
        binding?.subWeeklyIc?.setImageResource(R.drawable.icon_deactive)
        binding?.subLifetimeIc?.setImageResource(R.drawable.icon_active)

    }

    private fun buySubAndIAP(){
        val myProductIdValue = myProductId.id
        when (myProductId) {
            MyProductId.Lifetime -> {
                mBillingClientLifecycle?.purchaseLifetime(this@SubscriptionActivity, myProductIdValue)
            }

            MyProductId.Weekly -> {
                mBillingClientLifecycle?.subscribe(this@SubscriptionActivity, myProductIdValue)
            }

            MyProductId.Yearly -> {
                mBillingClientLifecycle?.subscribe(this@SubscriptionActivity, myProductIdValue)
            }
        }
    }

    fun checkSubSuccessfully() {
        if (!isRestoreSub) return
        isRestoreSub = false

        runOnUiThread {

            val alertTitle = if (!PreferencesManager.getInstance().isSUB())
                "Nothing to restore"
            else
                "All purchased is restored"

            val alertMessage = if (!PreferencesManager.getInstance().isSUB())
                "You have never made a payment before, nothing will be restored"
            else
                null

            AlertDialog.Builder(this@SubscriptionActivity)
                .setTitle(alertTitle)
                .setMessage(alertMessage)
                .setCancelable(false)
                .setPositiveButton("OK") { _, _ ->
                    if (PreferencesManager.getInstance().isSUB()) {
                        finish()
                    }
                }
                .create()
                .show()

        }
    }

    private fun showAlertDialogPurchasedSuccess(purchaseTime: Date) {

        AlertDialog.Builder(this)
            .setTitle("Product is purchased")
            .setMessage("Product is valid until $purchaseTime")
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ ->  finish() }
            .create()
            .show()

    }

    override fun onStart() {
        super.onStart()
        GlobalVariables.isShowSub = true
    }

    override fun onDestroy() {
        binding = null
        mBillingClientLifecycle?.removeListener(this)
        super.onDestroy()
    }
}