package com.pro.milkteaapp.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @param address           chuỗi gộp
 * @param addressObj        object đầy đủ
 * @param voucherCode       voucher giảm giá
 * @param rewardVoucherCode voucher đổi quà
 * @param discount          tổng giảm giá từ voucher giảm giá
 * @param rewardDiscount    tổng giảm giá từ voucher đổi quà
 */
public record CheckoutInfo(@NonNull String address, @Nullable Address addressObj,
                           @NonNull String paymentMethod, @NonNull String voucherCode,
                           @NonNull String rewardVoucherCode, @NonNull String shippingLabel,
                           double subtotal, double discount, double rewardDiscount,
                           double shippingFee, double grandTotal) {
}
