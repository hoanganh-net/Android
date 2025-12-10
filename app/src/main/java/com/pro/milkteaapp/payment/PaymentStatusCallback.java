package com.pro.milkteaapp.payment;

import androidx.annotation.NonNull;

/**
 * Callback cho kiểm tra trạng thái thanh toán
 */
public interface PaymentStatusCallback {
    /**
     * Trạng thái thanh toán
     * @param status SUCCESS, PENDING, FAILED, CANCELLED
     * @param transactionId ID giao dịch
     */
    void onStatusReceived(@NonNull PaymentStatus status, @NonNull String transactionId);
    
    /**
     * Lỗi khi kiểm tra trạng thái
     * @param error Lỗi
     */
    void onError(@NonNull Exception error);
}

