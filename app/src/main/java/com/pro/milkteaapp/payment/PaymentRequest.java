package com.pro.milkteaapp.payment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;

/**
 * Request object cho thanh toán
 */
public class PaymentRequest implements Serializable {
    @NonNull private final String orderId;
    @NonNull private final String userId;
    private final long amount; // Số tiền (VND)
    @NonNull private final String description;
    @Nullable private final String returnUrl;
    @Nullable private final String cancelUrl;
    
    public PaymentRequest(@NonNull String orderId,
                         @NonNull String userId,
                         long amount,
                         @NonNull String description,
                         @Nullable String returnUrl,
                         @Nullable String cancelUrl) {
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
        this.description = description;
        this.returnUrl = returnUrl;
        this.cancelUrl = cancelUrl;
    }
    
    @NonNull public String getOrderId() { return orderId; }
    @NonNull public String getUserId() { return userId; }
    public long getAmount() { return amount; }
    @NonNull public String getDescription() { return description; }
    @Nullable public String getReturnUrl() { return returnUrl; }
    @Nullable public String getCancelUrl() { return cancelUrl; }
}

