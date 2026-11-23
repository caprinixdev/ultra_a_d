package gs.ad.utils.google_iab.models;

import com.android.billingclient.api.ProductDetails;
import gs.ad.utils.google_iab.enums.SkuProductType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProductInfo {

    private final SkuProductType skuProductType;
    private final ProductDetails productDetails;
    private final String product;
    private final String description;
    private final String title;
    private final String type;
    private final String name;
    private final String oneTimePurchaseOfferFormattedPrice;
    private final long oneTimePurchaseOfferPriceAmountMicros;
    private final String oneTimePurchaseOfferPriceCurrencyCode;
    private final String oneTimePurchaseOfferToken;
    private final List<SubscriptionOfferDetails> subscriptionOfferDetails;

    public ProductInfo(SkuProductType skuProductType, ProductDetails productDetails) {
        this.skuProductType = skuProductType;
        this.productDetails = productDetails;
        this.product = productDetails.getProductId();
        this.description = productDetails.getDescription();
        this.title = productDetails.getTitle();
        this.type = productDetails.getProductType();
        this.name = productDetails.getName();

        //starting with Google Billing v8/v9 a one-time product can expose several purchase options
        //getOneTimePurchaseOfferDetails() only returns the backwards compatible offer and is null
        //when the product has no such offer, so fall back to the list of eligible offers
        ProductDetails.OneTimePurchaseOfferDetails oneTimeOfferDetails = resolveOneTimePurchaseOfferDetails(productDetails);

        this.oneTimePurchaseOfferFormattedPrice = Optional.ofNullable(oneTimeOfferDetails).map(ProductDetails.OneTimePurchaseOfferDetails::getFormattedPrice).orElse(null);
        this.oneTimePurchaseOfferPriceAmountMicros = Optional.ofNullable(oneTimeOfferDetails).map(ProductDetails.OneTimePurchaseOfferDetails::getPriceAmountMicros).orElse(0L);
        this.oneTimePurchaseOfferPriceCurrencyCode = Optional.ofNullable(oneTimeOfferDetails).map(ProductDetails.OneTimePurchaseOfferDetails::getPriceCurrencyCode).orElse(null);
        this.oneTimePurchaseOfferToken = Optional.ofNullable(oneTimeOfferDetails).map(ProductDetails.OneTimePurchaseOfferDetails::getOfferToken).orElse(null);

        List<ProductDetails.SubscriptionOfferDetails> offerDetailsList = productDetails.getSubscriptionOfferDetails();
        this.subscriptionOfferDetails = new ArrayList<>();

        if (offerDetailsList != null) {
            for (ProductDetails.SubscriptionOfferDetails offerDetails : offerDetailsList) {
                SubscriptionOfferDetails newOfferDetails = createSubscriptionOfferDetails(offerDetails);
                this.subscriptionOfferDetails.add(newOfferDetails);
            }
        }
    }

    public SkuProductType getSkuProductType() {
        return skuProductType;
    }

    public ProductDetails getProductDetails() {
        return productDetails;
    }

    public String getProduct() {
        return product;
    }

    public String getDescription() {
        return description;
    }

    public String getTitle() {
        return title;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getOneTimePurchaseOfferFormattedPrice() {
        return oneTimePurchaseOfferFormattedPrice;
    }

    public long getOneTimePurchaseOfferPriceAmountMicros() {
        return oneTimePurchaseOfferPriceAmountMicros;
    }

    public String getOneTimePurchaseOfferPriceCurrencyCode() {
        return oneTimePurchaseOfferPriceCurrencyCode;
    }

    /**
     * Returns the offer token of the one-time purchase offer used to build the billing flow
     * <p>
     * Null for subscriptions and for one-time products without any eligible offer
     */
    public String getOneTimePurchaseOfferToken() {
        return oneTimePurchaseOfferToken;
    }

    public List<SubscriptionOfferDetails> getSubscriptionOfferDetails() {
        return subscriptionOfferDetails;
    }

    /**
     * Returns the one-time purchase offer to be used for pricing and for the billing flow
     * <p>
     * The deprecated getOneTimePurchaseOfferDetails() is preferred when available because it points
     * to the backwards compatible offer. When the product only exposes the newer multiple purchase
     * options/offers model that method returns null, so the first eligible offer is used instead
     */
    private static ProductDetails.OneTimePurchaseOfferDetails resolveOneTimePurchaseOfferDetails(ProductDetails productDetails) {
        ProductDetails.OneTimePurchaseOfferDetails legacyOfferDetails = productDetails.getOneTimePurchaseOfferDetails();
        if (legacyOfferDetails != null) {
            return legacyOfferDetails;
        }

        List<ProductDetails.OneTimePurchaseOfferDetails> offerDetailsList = productDetails.getOneTimePurchaseOfferDetailsList();
        if (offerDetailsList == null || offerDetailsList.isEmpty()) {
            return null;
        }

        return offerDetailsList.get(0);
    }

    private SubscriptionOfferDetails createSubscriptionOfferDetails(ProductDetails.SubscriptionOfferDetails offerDetails) {
        return new SubscriptionOfferDetails(offerDetails.getOfferId(), offerDetails.getPricingPhases().getPricingPhaseList(), offerDetails.getOfferTags(), offerDetails.getOfferToken(), offerDetails.getBasePlanId());
    }
}