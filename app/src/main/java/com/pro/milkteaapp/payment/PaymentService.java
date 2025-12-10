package com.pro.milkteaapp.payment;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pro.milkteaapp.R;

import java.util.HashMap;
import java.util.Map;

/**
 * Service quản lý thanh toán
 * Hỗ trợ: COD, Momo, VNPAY, Card
 */
public class PaymentService {
    
    private static PaymentService instance;
    private final Map<String, PaymentGateway> gateways = new HashMap<>();
    private final Context context;
    
    // Payment method constants
    public static final String METHOD_COD = "COD";
    public static final String METHOD_MOMO = "Momo";
    public static final String METHOD_VNPAY = "VNPAY";
    public static final String METHOD_CARD = "CARD";
    
    private PaymentService(Context context) {
        this.context = context.getApplicationContext();
        initializeGateways();
    }
    
    public static synchronized PaymentService getInstance(Context context) {
        if (instance == null) {
            instance = new PaymentService(context);
        }
        return instance;
    }
    
    private void initializeGateways() {
        // Khởi tạo các payment gateway
        gateways.put(METHOD_MOMO, new MomoPaymentGateway(context));
        gateways.put(METHOD_VNPAY, new VNPAYPaymentGateway(context));
        gateways.put(METHOD_CARD, new CardPaymentGateway(context));
    }
    
    /**
     * Xử lý thanh toán
     * @param method Phương thức thanh toán (COD, Momo, VNPAY, CARD)
     * @param request PaymentRequest
     * @param callback PaymentCallback
     */
    public void processPayment(@NonNull String method,
                               @NonNull PaymentRequest request,
                               @NonNull PaymentCallback callback) {
        
        // COD không cần xử lý thanh toán online
        if (METHOD_COD.equals(method)) {
            callback.onSuccess("COD_" + request.getOrderId(), null);
            return;
        }
        
        // Lấy gateway tương ứng
        PaymentGateway gateway = gateways.get(method);
        if (gateway == null) {
            callback.onError(new Exception("Phương thức thanh toán không được hỗ trợ: " + method));
            return;
        }
        
        // Khởi tạo thanh toán
        gateway.initiatePayment(request, callback);
    }
    
    /**
     * Kiểm tra trạng thái thanh toán
     */
    public void checkPaymentStatus(@NonNull String method,
                                   @NonNull String transactionId,
                                   @NonNull PaymentStatusCallback callback) {
        PaymentGateway gateway = gateways.get(method);
        if (gateway == null) {
            callback.onError(new Exception("Phương thức thanh toán không được hỗ trợ: " + method));
            return;
        }
        
        gateway.checkPaymentStatus(transactionId, callback);
    }
    
    /**
     * Xử lý kết quả thanh toán từ deep link/redirect
     */
    public boolean handlePaymentResult(@NonNull String method, @NonNull Intent data) {
        PaymentGateway gateway = gateways.get(method);
        if (gateway == null) {
            return false;
        }
        
        Uri uri = data.getData();
        if (uri != null) {
            return gateway.handlePaymentResult(uri.toString());
        }
        
        return false;
    }
    
    /**
     * Kiểm tra xem phương thức thanh toán có cần xử lý online không
     */
    public boolean requiresOnlinePayment(@NonNull String method) {
        return !METHOD_COD.equals(method);
    }
}

