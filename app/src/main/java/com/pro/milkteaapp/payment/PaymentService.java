package com.pro.milkteaapp.payment;

import androidx.annotation.NonNull;

/**
 * Service quản lý thanh toán
 * Hỗ trợ: COD, Momo, VNPAY, Card
 * 
 * LƯU Ý: Hiện tại chỉ sử dụng để kiểm tra phương thức thanh toán có cần xử lý online không
 * Logic thanh toán thực tế sẽ được tích hợp sau khi có payment gateway API
 */
public class PaymentService {
    
    // Payment method constants
    public static final String METHOD_COD = "COD";
    public static final String METHOD_MOMO = "Momo";
    public static final String METHOD_VNPAY = "VNPAY";
    public static final String METHOD_CARD = "CARD";
    
    /**
     * Kiểm tra xem phương thức thanh toán có cần xử lý online không
     */
    public boolean requiresOnlinePayment(@NonNull String method) {
        return !METHOD_COD.equals(method);
    }
    
    /**
     * Singleton instance - giữ lại để tương thích với code hiện tại
     */
    private static PaymentService instance;
    
    public static synchronized PaymentService getInstance(@NonNull android.content.Context context) {
        if (instance == null) {
            instance = new PaymentService();
        }
        return instance;
    }
    
    private PaymentService() {
        // Private constructor
    }
}

