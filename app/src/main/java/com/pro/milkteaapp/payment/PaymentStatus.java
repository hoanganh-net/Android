package com.pro.milkteaapp.payment;

/**
 * Trạng thái thanh toán
 */
public enum PaymentStatus {
    SUCCESS,    // Thanh toán thành công
    PENDING,    // Đang xử lý
    FAILED,     // Thanh toán thất bại
    CANCELLED   // Người dùng hủy
}

