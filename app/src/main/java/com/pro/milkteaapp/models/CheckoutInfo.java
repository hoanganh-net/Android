package com.pro.milkteaapp.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CheckoutInfo {
    @NonNull public final String address;        // chuỗi gộp
    @Nullable public final Address addressObj;   // object đầy đủ
    @NonNull public final String paymentMethod;
    @NonNull public final String voucherCode;    // voucher giảm giá
    @NonNull public final String rewardVoucherCode; // voucher đổi quà
    @NonNull public final String shippingLabel;
    public final double subtotal;
    public final double discount;                // tổng giảm giá từ voucher giảm giá
    public final double rewardDiscount;          // tổng giảm giá từ voucher đổi quà
    public final double shippingFee;
    public final double grandTotal;

    public CheckoutInfo(@NonNull String address,
                        @Nullable Address addressObj,
                        @NonNull String paymentMethod,
                        @NonNull String voucherCode,
                        @NonNull String rewardVoucherCode,
                        @NonNull String shippingLabel,
                        double subtotal, double discount, double rewardDiscount, double shippingFee, double grandTotal) {
        this.address = address;
        this.addressObj = addressObj;
        this.paymentMethod = paymentMethod;
        this.voucherCode = voucherCode;
        this.rewardVoucherCode = rewardVoucherCode;
        this.shippingLabel = shippingLabel;
        this.subtotal = subtotal;
        this.discount = discount;
        this.rewardDiscount = rewardDiscount;
        this.shippingFee = shippingFee;
        this.grandTotal = grandTotal;
    }

    @NonNull public String getAddress() { return address; }
    @Nullable public Address getAddressObj() { return addressObj; }
    @NonNull public String getPaymentMethod() { return paymentMethod; }
    @NonNull public String getVoucherCode() { return voucherCode; }
    @NonNull public String getRewardVoucherCode() { return rewardVoucherCode; }
    @NonNull public String getShippingLabel() { return shippingLabel; }
    public double getSubtotal() { return subtotal; }
    public double getDiscount() { return discount; }
    public double getRewardDiscount() { return rewardDiscount; }
    public double getShippingFee() { return shippingFee; }
    public double getGrandTotal() { return grandTotal; }
}
