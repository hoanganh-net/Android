package com.pro.milkteaapp.payment;

import androidx.annotation.NonNull;

/**
 * Interface cho các payment gateway (Momo, VNPAY, Card, etc.)
 */
public interface PaymentGateway {
    
    /**
     * Khởi tạo thanh toán
     * @param request PaymentRequest chứa thông tin đơn hàng
     * @param callback Callback để nhận kết quả
     */
    void initiatePayment(@NonNull PaymentRequest request, @NonNull PaymentCallback callback);
    
    /**
     * Kiểm tra trạng thái thanh toán
     * @param transactionId ID giao dịch
     * @param callback Callback để nhận kết quả
     */
    void checkPaymentStatus(@NonNull String transactionId, @NonNull PaymentStatusCallback callback);
    
    /**
     * Xử lý kết quả thanh toán từ deep link/redirect
     * @param data Dữ liệu từ deep link hoặc redirect
     * @return true nếu xử lý thành công
     */
    boolean handlePaymentResult(@NonNull String data);
}

