package gs.ad.utils.google_iab

import gs.ad.utils.google_iab.enums.ErrorType
import gs.ad.utils.google_iab.models.ProductInfo
import gs.ad.utils.google_iab.models.PurchaseInfo

interface OnBillingListener {
    fun onProductDetailsFetched(productInfos: HashMap<String, ProductInfo>){}
    fun onPurchasedProductsFetched(purchaseInfos: List<PurchaseInfo>){}
    fun onProductsPurchased(purchaseInfos: List<PurchaseInfo>){}
    fun onPurchaseAcknowledged(purchaseInfo: PurchaseInfo){}
    fun onPurchaseConsumed(purchaseInfo: PurchaseInfo){}
    fun onBillingError(errorType: ErrorType){}
}