package gs.ad.utils.google_iab

import android.app.Activity
import android.content.Context
import android.util.Log
import gs.ad.utils.google_iab.enums.ErrorType
import gs.ad.utils.google_iab.enums.ProductType
import gs.ad.utils.google_iab.models.BillingResponse
import gs.ad.utils.google_iab.models.ProductInfo
import gs.ad.utils.google_iab.models.PurchaseInfo
import gs.ad.utils.google_iab.models.SubscriptionOfferDetails
import gs.ad.utils.utils.PreferencesManager

class BillingClientLifecycle(
    private val context: Context,
    private val licenseKey: String,
    private val consumableIds: List<String>,
    private val nonConsumableIds: List<String>,
    private val subscriptionIds: List<String>,
): BillingEventListener {
    private var _currentProductId: String = ""
    private var _isBuyAgain: Boolean = false
    private var _isLifetime: Boolean = false
    private var _isSub: Boolean = false

    private var mHandleEvent: HashMap<String, OnBillingListener> = HashMap()
    private var mProductDetails: HashMap<String, ProductInfo> = HashMap()

    private constructor(builder: Builder) : this(
        builder.context,
        builder.licenseKey,
        builder.consumableIds,
        builder.nonConsumableIds,
        builder.subscriptionIds
    ){
        billingConnector.setBillingEventListener(this)
    }

    private var billingConnector: BillingConnector = BillingConnector(context, licenseKey)
        .setConsumableIds(consumableIds)
        .setNonConsumableIds(nonConsumableIds)
        .setSubscriptionIds(subscriptionIds)
        .autoAcknowledge()
        .autoConsume()
        .enableLogging()
        .connect()

    private fun isReadyBillingConnector(): Boolean {
        return billingConnector.isReady
    }

    internal fun connectBillingConnector() {
        if (isReadyBillingConnector()) return
        billingConnector.connect()
    }

    internal fun destroyBillingConnector() {
        billingConnector.release()
    }

    fun setListener(activity: Activity, eventListener: OnBillingListener){
        val keyEvent = activity::class.java.simpleName
        this.mHandleEvent[keyEvent] = eventListener
    }

    fun removeListener(activity: Activity){
        val keyEvent = activity::class.java.simpleName
        this.mHandleEvent.remove(keyEvent)
    }

    fun fetchSubPurchasedProducts() {
        connectBillingConnector()
        billingConnector.fetchSubPurchasedProducts()
    }

    fun purchaseLifetime(activity: Activity, productId: String) {
        connectBillingConnector()
        _currentProductId = productId
        _isLifetime = true
        billingConnector.purchase(activity, productId)
    }

    fun purchase(activity: Activity, productId: String, isBuyAgain: Boolean) {
        connectBillingConnector()
        _currentProductId = productId
        _isBuyAgain = isBuyAgain
        billingConnector.purchase(activity, productId)
    }

    fun subscribe(activity: Activity, productId: String, selectedOfferIndex: Int) {
        connectBillingConnector()
        _isSub = true
        billingConnector.subscribe(activity, productId, selectedOfferIndex)
    }

    fun subscribe(activity: Activity, productId: String) {
        connectBillingConnector()
        _isSub = true
        billingConnector.subscribe(activity, productId)
    }

    fun unsubscribe(activity: Activity, productId: String) {
        connectBillingConnector()
        billingConnector.unsubscribe(activity, productId)
    }

    private fun getProductDetails(id: String): ProductInfo?{
        val re = Regex("[^A-Za-z0-9 ]")
        val key = id.replace(re, "")
        return mProductDetails[key]
    }

    fun getIAPDetails(id: String): ProductInfo?{
        return getProductDetails(id)
    }

    fun getIAPPrice(id: String): String?{
        return getProductDetails(id)?.oneTimePurchaseOfferFormattedPrice
    }

    fun getSubscriptionDetails(packageId: String, productId: String): SubscriptionOfferDetails? {
        return getProductDetails(packageId)?.subscriptionOfferDetails?.stream()?.filter{ s-> s.basePlanId == productId }?.findFirst()?.orElse(null)
    }

    fun getSubscriptionIndex(packageId: String, productId: String): Int? {
        return getProductDetails(packageId)?.subscriptionOfferDetails?.indexOfFirst { s-> s.basePlanId == productId }
    }

    fun getSubscriptionPrice(packageId: String): String? {
        return getProductDetails(packageId)?.subscriptionOfferDetails?.
            first()?.pricingPhases?.first{ s-> s.formattedPrice?.lowercase()?.contains("free") == false }?.formattedPrice
    }

    fun getSubscriptionPrice(packageId: String, productId: String): String? {
        return getSubscriptionDetails(packageId, productId)?.pricingPhases?.
            first{ s-> s.formattedPrice?.lowercase()?.contains("free") == false }?.formattedPrice
    }

    override fun onProductsFetched(productDetails: MutableList<ProductInfo>) {
        productDetails.forEach { s->
            Log.d(TAG, "onProductsFetched: " + s.product)
        }
        val re = Regex("[^A-Za-z0-9 ]")
        productDetails.forEach{ s->
            val key = s.product.replace(re, "")
            mProductDetails[key] = s
        }

        this.mHandleEvent.forEach { (k, e) ->
            e.onProductDetailsFetched(mProductDetails)
        }
    }

    override fun onPurchasedProductsFetched(
        productType: ProductType,
        purchases: MutableList<PurchaseInfo>
    ) {
        Log.d(TAG, "onPurchasedProductsFetched purchases: " + purchases.count())

        if (PreferencesManager.getInstance().isLifetime()){
            PreferencesManager.getInstance().purchaseLifetime()
            PreferencesManager.getInstance().purchaseAndRestoreSuccess()
        }else{
            if(purchases.isEmpty()){
                PreferencesManager.getInstance().purchaseFailed()
            }else{
                PreferencesManager.getInstance().purchaseAndRestoreSuccess()
            }
        }

        this.mHandleEvent.forEach { (k, e) ->
            e.onPurchasedProductsFetched(purchases)
        }
    }

    override fun onProductsPurchased(purchases: MutableList<PurchaseInfo>) {
        Log.d(TAG, "onProductsPurchased purchases: " + purchases.count())
        purchases.forEach { s->
            billingConnector.consumePurchase(s)
        }

        if (_isLifetime){
            _isLifetime = false
            PreferencesManager.getInstance().purchaseLifetime()
            PreferencesManager.getInstance().purchaseAndRestoreSuccess()
        }

        if (_isSub){
            _isSub = false
            PreferencesManager.getInstance().purchaseAndRestoreSuccess()
        }

//        if(_isBuyAgain){
//            val purchaseInfo = purchases.first { s -> s.product == _currentProductId }
//            _isBuyAgain = false
//            _currentProductId = ""
//            billingConnector.consumePurchase(purchaseInfo)
//        }

        this.mHandleEvent.forEach { (k, e) ->
            e.onProductsPurchased(purchases)
        }
    }

    override fun onPurchaseAcknowledged(purchase: PurchaseInfo) {
        Log.d(TAG, "onPurchaseAcknowledged: " + purchase.product)

        this.mHandleEvent.forEach { (k, e) ->
            e.onPurchaseAcknowledged(purchase)
        }
    }

    override fun onPurchaseConsumed(purchase: PurchaseInfo) {
        Log.d(TAG, "onPurchaseConsumed: " + purchase.product)

        this.mHandleEvent.forEach { (k, e) ->
            e.onPurchaseConsumed(purchase)
        }
    }

    @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
    override fun onBillingError(
        billingConnector: BillingConnector,
        response: BillingResponse
    ) {
        Log.d(TAG, "onBillingError: " + response.errorType.name)
        when (response.errorType) {
            ErrorType.CLIENT_NOT_READY -> {}
            ErrorType.CLIENT_DISCONNECTED -> {}
            ErrorType.PRODUCT_NOT_EXIST -> {}
            ErrorType.CONSUME_ERROR -> {}
            ErrorType.CONSUME_WARNING -> {}
            ErrorType.ACKNOWLEDGE_ERROR -> {}
            ErrorType.ACKNOWLEDGE_WARNING -> {}
            ErrorType.FETCH_PURCHASED_PRODUCTS_ERROR -> {}
            ErrorType.BILLING_ERROR -> {}
            ErrorType.USER_CANCELED -> {}
            ErrorType.SERVICE_UNAVAILABLE -> {}
            ErrorType.BILLING_UNAVAILABLE -> {}
            ErrorType.ITEM_UNAVAILABLE -> {}
            ErrorType.DEVELOPER_ERROR -> {}
            ErrorType.ERROR -> {}
            ErrorType.ITEM_ALREADY_OWNED -> {}
            ErrorType.ITEM_NOT_OWNED -> {}
        }

        this.mHandleEvent.forEach { (k, e) ->
            e.onBillingError(response.errorType)
        }
    }

    companion object {
        const val TAG = "BillingLifecycle"
        inline fun build(context: Context, block: Builder.() -> Unit) =
            Builder(context).apply(block).build()
    }

    class Builder(
        val context: Context
    ) {
        lateinit var licenseKey: String
        var consumableIds: List<String> = ArrayList()
        var nonConsumableIds: List<String> = ArrayList()
        var subscriptionIds: List<String> = ArrayList()
        fun build() = BillingClientLifecycle(
            this
        )
    }
}