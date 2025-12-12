package com.pro.milkteaapp.utils;

import androidx.annotation.NonNull;

import com.google.firebase.Timestamp;
import com.pro.milkteaapp.models.Voucher;

public final class VoucherUtils {

    private VoucherUtils(){}

    /**
     * Kết quả giảm tách bạch: giảm đơn và giảm ship
     */
        public record DiscountResult(long orderDiscount, long shippingDiscount) {
    }

    /** Trong khung thời gian [startAt, endAt] (endAt inclusive) */
    public static boolean isInRangeNow(@NonNull Voucher v){
        Timestamp now = Timestamp.now();
        Timestamp start = v.getStartAt();
        Timestamp end   = v.getEndAt();
        if (start != null && now.compareTo(start) < 0) return true;
        return end != null && now.compareTo(end) > 0;
    }

    /**
     * Tính giảm theo loại:
     * - ORDER_FIXED    : giảm thẳng tổng đơn (<= subtotal)
     * - ORDER_PERCENT  : giảm % tổng đơn, trần = maxDiscount (nếu có)
     * - SHIPPING_FIXED : giảm vào phí ship (<= shippingFee)
     */
    @NonNull
    public static DiscountResult calc(@NonNull Voucher v, long subtotal, long shippingFee){
        long orderDisc = 0, shipDisc = 0;
        switch (v.getType()){
            case "ORDER_FIXED": {
                long a = Math.max(0, v.getAmount());
                orderDisc = Math.min(a, subtotal);
                break;
            }
            case "ORDER_PERCENT": {
                int p = Math.max(0, v.getPercent());
                long base = subtotal * p / 100L;
                Long cap = v.getMaxDiscount();
                if (cap != null && cap > 0) base = Math.min(base, cap);
                orderDisc = Math.min(base, subtotal);
                break;
            }
            case "SHIPPING_FIXED": {
                long a = Math.max(0, v.getAmount());
                shipDisc = Math.min(a, shippingFee);
                break;
            }
        }
        return new DiscountResult(orderDisc, shipDisc);
    }
}
