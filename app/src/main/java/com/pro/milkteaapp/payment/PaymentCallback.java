package com.pro.milkteaapp.payment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Callback cho kết quả thanh toán
 */
public interface PaymentCallback {
    /**
     * Thanh toán thành công
     * @param transactionId ID giao dịch
     * @param paymentUrl URL để mở thanh toán (nếu cần)
     */
    void onSuccess(@NonNull String transactionId, @Nullable String paymentUrl);
    
    /**
     * Thanh toán thất bại
     * @param error Lỗi
     */
    void onError(@NonNull Exception error);
    
    /**
     * Người dùng hủy thanh toán
     */
    void onCancel();
}

