package gs.ad.utils.google_iab.models;

import androidx.annotation.NonNull;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import gs.ad.utils.google_iab.enums.ErrorType;

public class BillingResponse {

    private final ErrorType errorType;

    private final String debugMessage;
    private final int responseCode;
    private final int subResponseCode;

    public BillingResponse(ErrorType errorType, String debugMessage, int responseCode) {
        this(errorType, debugMessage, responseCode, BillingClient.OnPurchasesUpdatedSubResponseCode.NO_APPLICABLE_SUB_RESPONSE_CODE);
    }

    public BillingResponse(ErrorType errorType, String debugMessage, int responseCode, int subResponseCode) {
        this.errorType = errorType;
        this.debugMessage = debugMessage;
        this.responseCode = responseCode;
        this.subResponseCode = subResponseCode;
    }

    public BillingResponse(ErrorType errorType, BillingResult billingResult) {
        this(errorType, billingResult.getDebugMessage(), billingResult.getResponseCode(), billingResult.getOnPurchasesUpdatedSubResponseCode());
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public String getDebugMessage() {
        return debugMessage;
    }

    public int getResponseCode() {
        return responseCode;
    }

    /**
     * Returns the sub response code reported by Google Billing v8+ when a purchase is rejected
     * <p>
     * See BillingClient.OnPurchasesUpdatedSubResponseCode
     */
    public int getSubResponseCode() {
        return subResponseCode;
    }

    @NonNull
    @Override
    public String toString() {
        return "BillingResponse: Error type: " + errorType +
                " Response code: " + responseCode +
                " Sub response code: " + subResponseCode + " Message: " + debugMessage;
    }
}